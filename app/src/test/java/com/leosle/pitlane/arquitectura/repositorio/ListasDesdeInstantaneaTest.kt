package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.FotoStorage
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.OrdenRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.SolicitudRepository
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.modelo.comoResumen
import com.leosle.pitlane.modelo.comoResumenCliente
import com.leosle.pitlane.modelo.comoResumenTecnico
import com.leosle.pitlane.util.Reloj
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Las listas de órdenes y solicitudes se arman con la instantánea y solo cruzan colecciones si faltan datos. */
class ListasDesdeInstantaneaTest {

    /** Cuenta cuántas veces se empezó a escuchar cada colección completa. */
    private class Conteo {
        var usuarios = 0
        var vehiculos = 0
        var solicitudes = 0
        val total get() = usuarios + vehiculos + solicitudes
    }

    private class ColeccionContada<T>(private val real: IColeccion<T>, private val alEscuchar: () -> Unit) : IColeccion<T> by real {
        override fun observarTodos(): Flow<List<T>> = real.observarTodos().onStart { alEscuchar() }
    }

    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private val ahora = 1_800_000_000_000L
    private val conteo = Conteo()
    private lateinit var bd: BaseDatosLocal
    private lateinit var sesiones: UsuariosDataSource
    private lateinit var ordenes: IOrdenRepository
    private lateinit var solicitudes: ISolicitudRepository

    @Before
    fun preparar() {
        val reloj = Reloj { ahora }
        bd = BaseDatosLocal(conSemilla = true, reloj = reloj)
        sesiones = UsuariosDataSource(bd)
        val reales = DocumentosDataSource(bd)
        val usuariosContados = object : IUsuariosDataSource by sesiones {
            override fun observarUsuarios() = sesiones.observarUsuarios().onStart { conteo.usuarios++ }
        }
        val documentosContados = object : IDocumentosDataSource by reales {
            override val vehiculos: IColeccion<Vehiculo> = ColeccionContada(reales.vehiculos) { conteo.vehiculos++ }
            override val solicitudes: IColeccion<Solicitud> = ColeccionContada(reales.solicitudes) { conteo.solicitudes++ }
        }
        val fotos = FotoStorage(carpetaTemporal.newFolder("fotos"))
        ordenes = OrdenRepository(usuariosContados, documentosContados, fotos, reloj)
        solicitudes = SolicitudRepository(usuariosContados, documentosContados, fotos, reloj)
    }

    private fun iniciarComoJefe() = runBlocking { sesiones.iniciarSesion("jefe@freddy.pe", "Taller2026") }

    /** Agrega la instantánea a todas las órdenes y solicitudes del seed, tomada de los perfiles vigentes. */
    private fun conInstantaneas() {
        bd.ordenes.value = bd.ordenes.value.map { orden ->
            orden.copy(
                cliente = bd.usuarios.value.first { it.id == orden.clienteId }.comoResumenCliente(),
                vehiculo = bd.vehiculos.value.first { it.id == orden.vehiculoId }.comoResumen(),
                tecnico = bd.usuarios.value.first { it.id == orden.tecnicoId }.comoResumenTecnico()
            )
        }
        bd.solicitudes.value = bd.solicitudes.value.map { solicitud ->
            solicitud.copy(
                cliente = bd.usuarios.value.first { it.id == solicitud.clienteId }.comoResumenCliente(),
                vehiculo = bd.vehiculos.value.first { it.id == solicitud.vehiculoId }.comoResumen()
            )
        }
    }

    @Test
    fun laListaDeOrdenesSeArmaConLaInstantaneaSinEscucharOtrasColecciones() = runBlocking {
        conInstantaneas()
        // La instantánea manda: un cambio posterior del perfil no se refleja en la lista.
        bd.ordenes.value = bd.ordenes.value.map {
            if (it.id == "o-2") it.copy(cliente = it.cliente!!.copy(telefono = "900000000")) else it
        }
        iniciarComoJefe()
        val lista = ordenes.observarOrdenesActivas().first()
        assertEquals(3, lista.size)
        val o2 = lista.first { it.orden.id == "o-2" }
        assertEquals("Ana Torres Vega", o2.cliente?.nombreCompleto)
        assertEquals("71234567", o2.cliente?.dni)
        assertEquals("900000000", o2.cliente?.telefono)
        assertEquals("u-cli-ana", o2.cliente?.id)
        assertEquals("ABC-123", o2.vehiculo?.placa)
        assertEquals("Toyota Yaris 2018", o2.vehiculo?.descripcion)
        assertEquals("Plata", o2.vehiculo?.color)
        assertEquals("Luis Ramos Huanca", o2.tecnico?.nombreCompleto)
        assertNull(o2.solicitud)
        assertEquals(0, conteo.total)
    }

    @Test
    fun laListaDeSolicitudesSeArmaConLaInstantaneaSinEscucharOtrasColecciones() = runBlocking {
        conInstantaneas()
        iniciarComoJefe()
        val lista = solicitudes.observarSolicitudesPendientes().first()
        assertTrue(lista.isNotEmpty())
        assertTrue(lista.all { it.cliente != null && it.vehiculo != null })
        val s = lista.first()
        val esperado = bd.solicitudes.value.first { it.id == s.solicitud.id }
        assertEquals(esperado.cliente!!.dni, s.cliente?.dni)
        assertEquals(esperado.vehiculo!!.placa, s.vehiculo?.placa)
        assertEquals(esperado.clienteId, s.cliente?.id)
        assertEquals(esperado.vehiculoId, s.vehiculo?.id)
        // La colección de solicitudes es la propia lista; las otras dos no se escuchan.
        assertEquals(0, conteo.usuarios + conteo.vehiculos)
    }

    @Test
    fun conUnaOrdenAnteriorALaInstantaneaLaListaCruzaLasColecciones() = runBlocking {
        conInstantaneas()
        // o-3 queda como documento anterior al relleno.
        bd.ordenes.value = bd.ordenes.value.map { if (it.id == "o-3") it.copy(cliente = null, vehiculo = null, tecnico = null) else it }
        iniciarComoJefe()
        val lista = ordenes.observarOrdenesActivas().first()
        val o3 = lista.first { it.orden.id == "o-3" }
        assertEquals("Carlos Mendoza Díaz", o3.cliente?.nombreCompleto)
        assertEquals("BCD-456", o3.vehiculo?.placa)
        assertEquals("s-4", o3.solicitud?.id)
        assertEquals("carlos@freddy.pe", o3.cliente?.correo)
        assertTrue(conteo.usuarios > 0 && conteo.vehiculos > 0 && conteo.solicitudes > 0)
    }

    @Test
    fun conUnaSolicitudAnteriorALaInstantaneaLaListaCruzaLasColecciones() = runBlocking {
        conInstantaneas()
        bd.solicitudes.value = bd.solicitudes.value.map { if (it.id == "s-3") it.copy(cliente = null, vehiculo = null) else it }
        iniciarComoJefe()
        val s2 = solicitudes.observarSolicitudesPendientes().first().first { it.solicitud.id == "s-3" }
        assertEquals("JKL-654", s2.vehiculo?.placa)
        assertEquals("carlos@freddy.pe", s2.cliente?.correo)
        assertTrue(conteo.usuarios > 0 && conteo.vehiculos > 0)
    }

    @Test
    fun buscarOrdenesListasEncuentraPorNumeroPlacaDniYNombreConYSinInstantanea() = runBlocking {
        iniciarComoJefe()
        suspend fun consultas() = listOf("ORD-0002", "abc123", "71234567", "ana torres").map { c ->
            ordenes.buscarOrdenesListas(c).map { it.orden.id }
        }
        val sin = consultas()
        assertTrue(sin.all { it == listOf("o-2") })
        assertTrue(conteo.total > 0)

        conInstantaneas()
        val antes = conteo.total
        val con = consultas()
        assertTrue(con.all { it == listOf("o-2") })
        assertEquals("sin lecturas extra con instantánea", antes, conteo.total)
        assertEquals(emptyList<Orden>(), ordenes.buscarOrdenesListas("zzz-nada"))
    }

    @Test
    fun elDetalleDeLaOrdenResuelveLosDocumentosReales() = runBlocking {
        conInstantaneas()
        iniciarComoJefe()
        val detalle = ordenes.observarOrden("o-2", "u-jefe").first()
        assertNotNull(detalle)
        assertEquals("s-1", detalle!!.solicitud?.id)
        assertEquals("cliente@freddy.pe", detalle.cliente?.correo)
        assertEquals("v-yaris", detalle.vehiculo?.id)
        assertEquals("luis@freddy.pe", detalle.tecnico?.correo)
        assertEquals(0, conteo.total)
    }

    @Test
    fun elDetalleSigueOcultandoLaOrdenAQuienNoPuedeVerla() = runBlocking {
        conInstantaneas()
        sesiones.iniciarSesion("cliente@freddy.pe", "Cliente123")
        assertNull(ordenes.observarOrden("o-3", "u-cli-ana").first())
        assertNotNull(ordenes.observarOrden("o-2", "u-cli-ana").first())
    }
}
