package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.FotoStorage
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.OrdenRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.SolicitudRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.VehiculoRepository
import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.DatosOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.util.Reloj
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Las comprobaciones de negocio usan consultas dirigidas y el detalle de una orden escucha un solo documento. */
class ConsultasDirigidasTest {

    /** Cuenta las lecturas completas de cada colección. */
    private class Conteo {
        var ordenes = 0
        var solicitudes = 0
        /** Órdenes cuya subcolección de avances se listó. */
        val avancesListados = mutableListOf<String>()
    }

    private class ColeccionContada<T>(private val real: IColeccion<T>, private val alListar: () -> Unit) : IColeccion<T> by real {
        override suspend fun listar(): List<T> {
            alListar()
            return real.listar()
        }
    }

    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private val ahora = 1_800_000_000_000L
    private val conteo = Conteo()
    private lateinit var bd: BaseDatosLocal
    private lateinit var reales: DocumentosDataSource
    private lateinit var documentos: IDocumentosDataSource
    private lateinit var ordenes: IOrdenRepository
    private lateinit var solicitudes: ISolicitudRepository
    private lateinit var vehiculos: IVehiculoRepository

    @Before
    fun preparar() {
        val reloj = Reloj { ahora }
        bd = BaseDatosLocal(conSemilla = true, reloj = reloj)
        val usuarios = UsuariosDataSource(bd)
        reales = DocumentosDataSource(bd)
        documentos = object : IDocumentosDataSource by reales {
            override val ordenes: IColeccion<Orden> = ColeccionContada(reales.ordenes) { conteo.ordenes++ }
            override val solicitudes: IColeccion<Solicitud> = ColeccionContada(reales.solicitudes) { conteo.solicitudes++ }
            override fun avancesDe(ordenId: String): IColeccion<Avance> =
                ColeccionContada(reales.avancesDe(ordenId)) { conteo.avancesListados += ordenId }
        }
        val fotos = FotoStorage(carpetaTemporal.newFolder("fotos"))
        ordenes = OrdenRepository(usuarios, documentos, fotos, reloj)
        solicitudes = SolicitudRepository(usuarios, documentos, fotos, reloj)
        vehiculos = VehiculoRepository(usuarios, documentos)
    }

    @Test
    fun listarDondeEquivaleAListarYFiltrar() = runBlocking {
        for (vehiculoId in bd.vehiculos.value.map { it.id }) {
            assertEquals(
                bd.ordenes.value.filter { it.vehiculoId == vehiculoId },
                reales.ordenes.listarDonde("vehiculoId", vehiculoId)
            )
            assertEquals(
                bd.solicitudes.value.filter { it.vehiculoId == vehiculoId },
                reales.solicitudes.listarDonde("vehiculoId", vehiculoId)
            )
        }
        for (ordenId in bd.ordenes.value.map { it.id }) {
            assertEquals(bd.avances.value.filter { it.ordenId == ordenId }, reales.avancesDe(ordenId).listar())
        }
        assertTrue(reales.ordenes.listarDonde("vehiculoId", "no-existe").isEmpty())
    }

    @Test
    fun observarEmiteElDocumentoYNuloSiNoExiste() = runBlocking {
        assertEquals(bd.ordenes.value.first { it.id == "o-2" }, reales.ordenes.observar("o-2").first())
        assertNull(reales.ordenes.observar("no-existe").first())
    }

    @Test
    fun observarSigueLosCambiosDeEsaOrden() = runBlocking {
        val original = reales.ordenes.observar("o-2").first()!!
        reales.ordenes.guardar("o-2", original.copy(falla = "Otra falla"))
        assertEquals("Otra falla", reales.ordenes.observar("o-2").first()?.falla)
    }

    @Test
    fun ordenDetalladaUsaElObservadorDeUnSoloDocumento() = runBlocking {
        val usuarios = UsuariosDataSource(bd)
        val detalle = com.leosle.pitlane.arquitectura.repositorio.impl.ordenDetallada("o-2", usuarios, reales).first()
        assertEquals("o-2", detalle?.orden?.id)
        assertNull(com.leosle.pitlane.arquitectura.repositorio.impl.ordenDetallada("no-existe", usuarios, reales).first())
        assertNotNull(detalle?.cliente)
    }

    @Test
    fun registrarOrdenSigueRechazandoUnVehiculoConOrdenEnCursoSinListarTodas() = runBlocking {
        val usuarios = UsuariosDataSource(bd)
        usuarios.iniciarSesion("jefe@freddy.pe", "Taller2026")
        val enCurso = bd.ordenes.value.first { it.enCurso }
        val datos = DatosOrden(
            clienteId = enCurso.clienteId, vehiculoId = enCurso.vehiculoId, solicitudId = null, falla = "Otra",
            kilometraje = 1, combustible = enCurso.combustible, observaciones = "", fotos = enCurso.fotos,
            tecnicoId = enCurso.tecnicoId
        )
        val resultado = ordenes.registrarOrden(datos, "u-jefe")
        assertEquals("Este vehículo ya tiene una orden en curso", (resultado as Resultado.Error).mensaje)
        assertEquals(0, conteo.ordenes)
    }

    @Test
    fun registrarSolicitudSigueRechazandoUnaAtencionEnCursoSinListarTodas() = runBlocking {
        val resultado = solicitudes.registrarSolicitud(
            "u-cli-carlos", "v-versa", "Otra descripción larga del problema", emptyList(), ahora + 1000, listOf(
                com.leosle.pitlane.modelo.Foto("f", "recurso:foto_demo_frente", ahora)
            )
        )
        assertEquals("Este vehículo ya tiene una atención en curso", (resultado as Resultado.Error).mensaje)
        assertEquals(0, conteo.solicitudes)
        assertEquals(0, conteo.ordenes)
    }

    @Test
    fun marcarListoSigueExigiendoElTrabajoYSoloLeeLosAvancesDeEsaOrden() = runBlocking {
        val resultado = ordenes.marcarListo("o-3", "u-tec-luis")
        assertEquals("Registra el trabajo realizado antes de marcar como listo", (resultado as Resultado.Error).mensaje)
        assertEquals(listOf("o-3"), conteo.avancesListados)
    }

    @Test
    fun eliminarVehiculoSigueAplicandoSusReglasSinListarTodo() = runBlocking {
        assertEquals(
            "No se puede eliminar un vehículo con historial de órdenes",
            (vehiculos.eliminarVehiculo("v-sail", solicitadoPorJefe = true) as Resultado.Error).mensaje
        )
        assertTrue(vehiculos.eliminarVehiculo("v-versa", solicitadoPorJefe = false) is Resultado.Exito)
        assertEquals(0, conteo.ordenes)
        assertEquals(0, conteo.solicitudes)
    }

    @Test
    fun registrarOrdenSinSolicitudAtiendeLasPendientesDelVehiculoSinListarTodas() = runBlocking {
        val pendiente = bd.solicitudes.value.first { it.estado == EstadoSolicitud.PENDIENTE && it.vehiculoId == "v-versa" }
        val base = bd.ordenes.value.first()
        val datos = DatosOrden(
            clienteId = pendiente.clienteId, vehiculoId = "v-versa", solicitudId = null, falla = "Batería",
            kilometraje = 10, combustible = base.combustible, observaciones = "", fotos = base.fotos,
            tecnicoId = "u-tec-luis"
        )
        assertTrue(ordenes.registrarOrden(datos, "u-jefe") is Resultado.Exito)
        assertEquals(EstadoSolicitud.ATENDIDA, bd.solicitudes.value.first { it.id == pendiente.id }.estado)
        assertEquals(0, conteo.solicitudes)
        assertEquals(0, conteo.ordenes)
    }
}
