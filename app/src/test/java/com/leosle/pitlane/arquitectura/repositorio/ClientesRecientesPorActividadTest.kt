package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.ClienteRepository
import com.leosle.pitlane.modelo.CambioEstado
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.ResultadoBusqueda
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.util.Reloj
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Los clientes recientes salen de `ultimaActividad` (consulta ordenada y acotada), no de recorrer las colecciones. */
class ClientesRecientesPorActividadTest {

    private val llamadas = mutableListOf<String>()
    private lateinit var bd: BaseDatosLocal
    private lateinit var sesiones: UsuariosDataSource
    private lateinit var clientes: IClienteRepository

    private class Registrada<T>(private val real: IColeccion<T>, private val nombre: String, private val llamadas: MutableList<String>) :
        IColeccion<T> by real {
        override fun observarTodos(): Flow<List<T>> = real.observarTodos().onStart { llamadas += "$nombre.observarTodos" }
        override suspend fun listar(): List<T> = real.listar().also { llamadas += "$nombre.listar" }
        override suspend fun listarDonde(campo: String, valor: String): List<T> =
            real.listarDonde(campo, valor).also { llamadas += "$nombre.listarDonde($campo)" }
    }

    private fun orden(id: String, clienteId: String, creadaEn: Long, historial: List<CambioEstado> = emptyList()) = Orden(
        id, "ORD-$id", clienteId, "v-$clienteId", null, "Falla", 0, NivelCombustible.MEDIO, "", emptyList(), "t",
        EstadoOrden.INGRESADO, historial, creadaEn
    )

    private fun solicitud(id: String, clienteId: String, creadaEn: Long) = Solicitud(
        id, clienteId, "v-$clienteId", "No arranca el motor", emptyList(), creadaEn, emptyList(), EstadoSolicitud.PENDIENTE, creadaEn
    )

    /** El algoritmo anterior: recorrer usuarios, vehículos, órdenes y solicitudes completos. */
    private fun algoritmoAnterior(limite: Int): List<ResultadoBusqueda> {
        val actividad = HashMap<String, Long>()
        fun registrar(clienteId: String, fecha: Long) {
            actividad[clienteId] = maxOf(actividad[clienteId] ?: Long.MIN_VALUE, fecha)
        }
        bd.ordenes.value.forEach { registrar(it.clienteId, it.fechaUltimoCambio) }
        bd.solicitudes.value.forEach { registrar(it.clienteId, it.creadaEn) }
        return bd.usuarios.value.filter { it.rol == Rol.CLIENTE && it.id in actividad }
            .sortedByDescending { actividad.getValue(it.id) }.take(limite)
            .map { c -> ResultadoBusqueda(c, emptyList(), bd.vehiculos.value.filter { it.clienteId == c.id }.map { it.placa }) }
    }

    private fun jefe() = runBlocking { sesiones.iniciarSesion("jefe@freddy.pe", "Taller2026") }

    @Before
    fun preparar() {
        bd = BaseDatosLocal(conSemilla = true, reloj = Reloj { 1_800_000_000_000L })
        sesiones = UsuariosDataSource(bd)
        val reales = DocumentosDataSource(bd)
        val documentos = object : IDocumentosDataSource by reales {
            override val vehiculos = Registrada(reales.vehiculos, "vehiculos", llamadas)
            override val ordenes = Registrada(reales.ordenes, "ordenes", llamadas)
            override val solicitudes = Registrada(reales.solicitudes, "solicitudes", llamadas)
        }
        val usuarios = object : IUsuariosDataSource by sesiones {
            override fun observarUsuarios(): Flow<List<Usuario>> = sesiones.observarUsuarios().onStart { llamadas += "usuarios.observarUsuarios" }
            override suspend fun listarUsuarios(): List<Usuario> = sesiones.listarUsuarios().also { llamadas += "usuarios.listarUsuarios" }
            override fun observarClientesPorActividad(limite: Int): Flow<List<Usuario>> =
                sesiones.observarClientesPorActividad(limite).onStart { llamadas += "usuarios.observarClientesPorActividad($limite)" }
        }
        clientes = ClienteRepository(usuarios, documentos)
        // Más clientes, con órdenes y solicitudes en varias fechas, sobre la semilla
        bd.usuarios.value += (1..7).map { Usuario("c$it", "Nombre", "c$it", "1$it", "900000000", "", "", Rol.CLIENTE, tieneCuenta = false) }
        bd.vehiculos.value += (1..7).map { Vehiculo("v-c$it", "c$it", "AAA-00$it", "Kia", "Rio", 2020, "Rojo") }
        bd.ordenes.value += listOf(
            orden("1", "c1", 100), orden("2", "c2", 50, listOf(CambioEstado(EstadoOrden.EN_REPARACION, 900, "t"))),
            orden("3", "c3", 300), orden("4", "c4", 200), orden("5", "c5", 150), orden("6", "c6", 120)
        )
        bd.solicitudes.value += listOf(solicitud("s1", "c3", 1_000), solicitud("s2", "c1", 80))
    }

    @Test
    fun devuelveLosMismosClientesEnElMismoOrdenYConLasMismasPlacasQueElAlgoritmoAnterior() {
        jefe()
        for (limite in listOf(1, 3, 5, 100)) {
            val esperado = algoritmoAnterior(limite)
            assertTrue("limite $limite sin datos", esperado.isNotEmpty())
            assertEquals("limite $limite", esperado, runBlocking { clientes.observarClientesRecientes(limite).first() })
        }
    }

    @Test
    fun respetaElLimiteYPideEseLimiteAlOrigen() {
        jefe()
        assertEquals(3, runBlocking { clientes.observarClientesRecientes(3).first() }.size)
        assertTrue(llamadas.toString(), "usuarios.observarClientesPorActividad(3)" in llamadas)
    }

    @Test
    fun nuncaRecorreUsuariosVehiculosOrdenesNiSolicitudesCompletos() {
        jefe()
        llamadas.clear()
        runBlocking { clientes.observarClientesRecientes().first() }
        assertTrue(llamadas.toString(), llamadas.any { it.startsWith("usuarios.observarClientesPorActividad") })
        assertTrue(llamadas.toString(), llamadas.none {
            it == "usuarios.observarUsuarios" || it == "usuarios.listarUsuarios" ||
                it.endsWith(".observarTodos") || it.endsWith(".listar")
        })
    }

    @Test
    fun unaPersonaQueNoEsElJefeRecibeUnaListaVacia() {
        for (cuenta in listOf(null, "cliente@freddy.pe" to "Cliente123", "luis@freddy.pe" to "Tecnico123")) {
            cuenta?.let { runBlocking { sesiones.iniciarSesion(it.first, it.second) } }
            assertTrue(runBlocking { clientes.observarClientesRecientes().first() }.isEmpty())
        }
    }

    @Test
    fun observarRecientesRechazaLimitesFueraDeUnoACien() {
        val ordenes = DocumentosDataSource(bd).ordenes
        for (limite in listOf(0, -1, 101)) assertThrows(IllegalArgumentException::class.java) { ordenes.observarRecientes("fechaUltimoCambio", limite) }
        assertThrows(IllegalArgumentException::class.java) { sesiones.observarClientesPorActividad(0) }
        assertThrows(IllegalArgumentException::class.java) { sesiones.observarClientesPorActividad(101) }
        assertEquals(1, runBlocking { ordenes.observarRecientes("fechaUltimoCambio", 1).first() }.size)
    }
}
