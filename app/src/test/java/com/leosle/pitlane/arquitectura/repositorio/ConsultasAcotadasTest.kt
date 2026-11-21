package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.FotoStorage
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.AvanceRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.ClienteRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.OrdenRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.VehiculoRepository
import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.comoResumen
import com.leosle.pitlane.modelo.comoResumenCliente
import com.leosle.pitlane.modelo.comoResumenTecnico
import com.leosle.pitlane.util.Reloj
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Los paneles y la ficha piden al servidor solo lo que muestran, sin cambiar lo que muestran. */
class ConsultasAcotadasTest {

    /** Registra qué colecciones se escuchan completas y con qué consulta se escuchan las demás. */
    private class Registro {
        val completas = mutableListOf<String>()
        val consultas = mutableListOf<String>()
        fun completa(nombre: String) = completas.count { it == nombre }
    }

    private class ColeccionRegistrada<T>(
        private val nombre: String,
        private val real: IColeccion<T>,
        private val registro: Registro
    ) : IColeccion<T> by real {
        override fun observarTodos(): Flow<List<T>> = real.observarTodos().onStart { registro.completas += nombre }
        override fun observarDonde(campo: String, valor: String): Flow<List<T>> =
            real.observarDonde(campo, valor).onStart { registro.consultas += "$nombre.$campo==$valor" }
        override fun observarEnLista(campo: String, valores: List<String>): Flow<List<T>> =
            real.observarEnLista(campo, valores).onStart { registro.consultas += "$nombre.$campo in ${valores.sorted()}" }
    }

    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private val ahora = 1_800_000_000_000L
    private val registro = Registro()
    private lateinit var bd: BaseDatosLocal
    private lateinit var sesiones: UsuariosDataSource
    private lateinit var reales: DocumentosDataSource
    private lateinit var ordenes: IOrdenRepository
    private lateinit var vehiculos: IVehiculoRepository
    private lateinit var clientes: IClienteRepository
    private lateinit var avancesRepo: IAvanceRepository

    @Before
    fun preparar() {
        val reloj = Reloj { ahora }
        bd = BaseDatosLocal(conSemilla = true, reloj = reloj)
        sesiones = UsuariosDataSource(bd)
        reales = DocumentosDataSource(bd)
        val documentos = object : IDocumentosDataSource by reales {
            override val ordenes: IColeccion<Orden> = ColeccionRegistrada("ordenes", reales.ordenes, registro)
            override val vehiculos: IColeccion<Vehiculo> = ColeccionRegistrada("vehiculos", reales.vehiculos, registro)
            override val solicitudes: IColeccion<Solicitud> = ColeccionRegistrada("solicitudes", reales.solicitudes, registro)
            override fun avancesDe(ordenId: String): IColeccion<Avance> =
                ColeccionRegistrada("ordenes/$ordenId/avances", reales.avancesDe(ordenId), registro)
        }
        avancesRepo = AvanceRepository(sesiones, documentos, FotoStorage(carpetaTemporal.newFolder("fotosAvances")), reloj)
        val fotos = FotoStorage(carpetaTemporal.newFolder("fotos"))
        ordenes = OrdenRepository(sesiones, documentos, fotos, reloj)
        vehiculos = VehiculoRepository(sesiones, documentos)
        clientes = ClienteRepository(sesiones, documentos)
        conInstantaneas()
        agregarOrdenesDeLuis()
    }

    private fun conInstantaneas() {
        bd.ordenes.value = bd.ordenes.value.map { orden ->
            orden.copy(
                cliente = bd.usuarios.value.first { it.id == orden.clienteId }.comoResumenCliente(),
                vehiculo = bd.vehiculos.value.first { it.id == orden.vehiculoId }.comoResumen(),
                tecnico = bd.usuarios.value.first { it.id == orden.tecnicoId }.comoResumenTecnico()
            )
        }
    }

    /** Luis tiene además una orden Ingresado antigua y una Entregada, para probar el orden y el filtro por estado. */
    private fun agregarOrdenesDeLuis() {
        val base = bd.ordenes.value.first { it.id == "o-2" }
        bd.ordenes.value = bd.ordenes.value + listOf(
            base.copy(id = "o-x-ingresada", numero = "0099", estado = EstadoOrden.INGRESADO, creadaEn = base.creadaEn - 5 * 86_400_000L),
            base.copy(id = "o-x-entregada", numero = "0098", estado = EstadoOrden.ENTREGADO, creadaEn = base.creadaEn - 9 * 86_400_000L)
        )
    }

    private fun iniciar(correo: String, clave: String) = runBlocking { sesiones.iniciarSesion(correo, clave) }

    @Test
    fun elPanelDelJefeNoEscuchaTodasLasOrdenesYMuestraLasMismas() = runBlocking {
        iniciar("jefe@freddy.pe", "Taller2026")
        val esperado = bd.ordenes.value.filter { it.estado in setOf(EstadoOrden.INGRESADO, EstadoOrden.EN_REPARACION, EstadoOrden.LISTO) }
            .sortedByDescending { it.creadaEn }.map { it.id }
        val lista = ordenes.observarOrdenesActivas().first()
        assertEquals(esperado, lista.map { it.orden.id })
        assertTrue(lista.none { it.orden.estado == EstadoOrden.ENTREGADO || it.orden.estado == EstadoOrden.ANULADA })
        assertEquals(0, registro.completa("ordenes"))
        assertEquals(listOf("ordenes.estado in [EN_REPARACION, INGRESADO, LISTO]"), registro.consultas)
    }

    @Test
    fun elPanelDelTecnicoMuestraSusOrdenesEnCursoEnElMismoOrden() = runBlocking {
        iniciar("luis@freddy.pe", "Tecnico123")
        val lista = ordenes.observarOrdenesDeTecnico("u-tec-luis").first()
        // En reparación primero; luego Ingresado de la más antigua a la más reciente.
        val esperado = bd.ordenes.value.filter { it.tecnicoId == "u-tec-luis" && it.enCurso }
            .sortedWith(compareBy<Orden> { if (it.estado == EstadoOrden.EN_REPARACION) 0 else 1 }.thenBy { it.creadaEn })
            .map { it.id }
        assertEquals(esperado, lista.map { it.orden.id })
        assertTrue(lista.isNotEmpty() && lista.all { it.orden.tecnicoId == "u-tec-luis" && it.orden.enCurso })
        assertFalse(lista.any { it.orden.id == "o-x-entregada" })
        assertEquals(0, registro.completa("ordenes"))
        assertEquals(listOf("ordenes.estado in [EN_REPARACION, INGRESADO]"), registro.consultas)
    }

    @Test
    fun laBusquedaDeOrdenesListasSigueEncontrandoPorNumeroPlacaDniYNombre() = runBlocking {
        iniciar("jefe@freddy.pe", "Taller2026")
        val lista = bd.ordenes.value.first { it.estado == EstadoOrden.LISTO }
        val cliente = bd.usuarios.value.first { it.id == lista.clienteId }
        val placa = bd.vehiculos.value.first { it.id == lista.vehiculoId }.placa
        for (texto in listOf(lista.numero, placa, placa.replace("-", "").lowercase(), cliente.dni, cliente.nombres.lowercase())) {
            assertEquals(texto, listOf(lista.id), ordenes.buscarOrdenesListas(texto).map { it.orden.id })
        }
        // Una orden en otro estado no aparece aunque coincida el texto.
        val enReparacion = bd.ordenes.value.first { it.estado == EstadoOrden.EN_REPARACION }
        assertTrue(ordenes.buscarOrdenesListas(enReparacion.numero).none { it.orden.id == enReparacion.id })
        assertEquals(0, registro.completa("ordenes"))
        assertTrue(registro.consultas.all { it == "ordenes.estado==LISTO" })
    }

    @Test
    fun losVehiculosDelClienteSeConsultanPorClienteIdYDanLoMismo() = runBlocking {
        iniciar("jefe@freddy.pe", "Taller2026")
        val lista = vehiculos.observarVehiculosDeCliente("u-cli-carlos").first()
        val esperados = bd.vehiculos.value.filter { it.clienteId == "u-cli-carlos" && it.activo }
        assertEquals(esperados.map { it.id }, lista.map { it.vehiculo.id })
        for (item in lista) {
            val activa = bd.ordenes.value.filter { it.vehiculoId == item.vehiculo.id && it.estado != EstadoOrden.ANULADA && it.estado != EstadoOrden.ENTREGADO }
                .maxByOrNull { it.creadaEn }
            assertEquals(activa, item.ordenActiva)
        }
        assertTrue(lista.size >= 2)
        assertEquals(0, registro.completa("vehiculos"))
        assertEquals(0, registro.completa("ordenes"))
        assertTrue(registro.consultas.containsAll(listOf("vehiculos.clienteId==u-cli-carlos", "ordenes.clienteId==u-cli-carlos")))
    }

    @Test
    fun lasOrdenesDeUnClienteSeConsultanPorClienteIdYDanLoMismo() = runBlocking {
        iniciar("jefe@freddy.pe", "Taller2026")
        val esperado = bd.ordenes.value.filter { it.clienteId == "u-cli-carlos" }.sortedByDescending { it.creadaEn }.map { it.id }
        val lista = ordenes.observarOrdenesDeCliente("u-cli-carlos").first()
        assertEquals(esperado, lista.map { it.orden.id })
        assertTrue(lista.isNotEmpty())
        assertEquals(0, registro.completa("ordenes"))
        assertEquals(listOf("ordenes.clienteId==u-cli-carlos"), registro.consultas)
    }

    @Test
    fun lasOrdenesDeUnVehiculoSeConsultanPorVehiculoIdYDanLoMismo() = runBlocking {
        iniciar("jefe@freddy.pe", "Taller2026")
        val esperado = bd.ordenes.value.filter { it.vehiculoId == "v-rio" }.sortedByDescending { it.creadaEn }.map { it.id }
        val lista = ordenes.observarOrdenesDeVehiculo("v-rio").first()
        assertEquals(esperado, lista.map { it.orden.id })
        assertTrue(lista.isNotEmpty())
        assertEquals(0, registro.completa("ordenes"))
        assertEquals(listOf("ordenes.vehiculoId==v-rio"), registro.consultas)
    }

    @Test
    fun unVehiculoSeObservaPorSuDocumentoSinEscucharTodosLosVehiculos() = runBlocking {
        iniciar("jefe@freddy.pe", "Taller2026")
        val esperado = bd.vehiculos.value.first { it.id == "v-rio" }
        assertEquals(esperado, vehiculos.observarVehiculo("v-rio").first())
        assertEquals(0, registro.completa("vehiculos"))
        iniciar("cliente@freddy.pe", "Cliente123")
        assertEquals(null, vehiculos.observarVehiculo("v-rio").first())
    }

    @Test
    fun losAvancesDeUnaOrdenSeLeenComoSuSubcoleccionSinFiltrarYEnOrden() = runBlocking {
        iniciar("jefe@freddy.pe", "Taller2026")
        val esperado = bd.avances.value.filter { it.ordenId == "o-2" }.sortedBy { it.fechaHora }.map { it.id }
        val lista = avancesRepo.observarAvances("o-2", "u-jefe").first()
        assertEquals(esperado, lista.map { it.avance.id })
        assertTrue(esperado.size > 1)
        assertTrue(lista.all { it.avance.ordenId == "o-2" && it.tecnico != null })
        // Se escucha entera la subcolección de esa orden y ninguna consulta con filtro
        assertEquals(1, registro.completa("ordenes/o-2/avances"))
        assertTrue(registro.consultas.isEmpty())
    }

    @Test
    fun laFichaDelClienteSeConsultaPorClienteIdYDaLoMismo() = runBlocking {
        iniciar("jefe@freddy.pe", "Taller2026")
        val ficha = clientes.observarFichaCliente("u-cli-ana").first()!!
        assertEquals("u-cli-ana", ficha.cliente.id)
        val vehiculosDeAna = bd.vehiculos.value.filter { it.clienteId == "u-cli-ana" }
        assertEquals(vehiculosDeAna.map { it.id }, ficha.vehiculos.map { it.vehiculo.id })
        for (historial in ficha.vehiculos) {
            val esperado = bd.ordenes.value.filter { it.vehiculoId == historial.vehiculo.id }.sortedByDescending { it.creadaEn }
            assertEquals(esperado, historial.ordenes)
        }
        assertEquals(0, registro.completa("vehiculos"))
        assertEquals(0, registro.completa("ordenes"))
    }

    @Test
    fun observarDondeYObservarEnListaEquivalenAObservarTodosYFiltrar() = runBlocking {
        for (estado in EstadoOrden.values()) {
            assertEquals(
                bd.ordenes.value.filter { it.estado == estado },
                reales.ordenes.observarDonde("estado", estado.name).first()
            )
        }
        val pedidos = listOf(EstadoOrden.INGRESADO.name, EstadoOrden.LISTO.name)
        assertEquals(bd.ordenes.value.filter { it.estado.name in pedidos }, reales.ordenes.observarEnLista("estado", pedidos).first())
        for (clienteId in bd.vehiculos.value.map { it.clienteId }.distinct()) {
            assertEquals(bd.vehiculos.value.filter { it.clienteId == clienteId }, reales.vehiculos.observarDonde("clienteId", clienteId).first())
        }
        assertTrue(reales.ordenes.observarDonde("clienteId", "no-existe").first().isEmpty())
    }

    @Test
    fun observarEnListaRechazaListasVaciasYDeMasDeTreintaValores() {
        assertThrows(IllegalArgumentException::class.java) { reales.ordenes.observarEnLista("estado", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { reales.ordenes.observarEnLista("estado", List(31) { "v$it" }) }
        reales.ordenes.observarEnLista("estado", List(30) { "v$it" })
    }
}
