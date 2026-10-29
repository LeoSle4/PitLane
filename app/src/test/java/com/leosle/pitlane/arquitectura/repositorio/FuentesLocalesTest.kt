package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.repositorio.impl.OrdenRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.NotificacionRepository
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.FotoStorage
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.modelo.DatosOrden
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.util.Reloj
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Pruebas de las fuentes de datos locales: almacén de fotos, avisos simulados del servidor y token. */
class FuentesLocalesTest {

    private var ahora = 1_800_000_000_000L
    private lateinit var bd: BaseDatosLocal
    private lateinit var carpetaFotos: File
    private lateinit var fotos: FotoStorage
    private lateinit var ordenes: OrdenRepository
    private lateinit var notificaciones: NotificacionRepository
    private lateinit var usuarios: UsuariosDataSource

    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    @Before
    fun preparar() {
        val reloj = Reloj { ahora }
        bd = BaseDatosLocal(conSemilla = true, reloj = reloj)
        usuarios = UsuariosDataSource(bd)
        val documentos = DocumentosDataSource(bd)
        carpetaFotos = carpetaTemporal.newFolder("fotos")
        fotos = FotoStorage(carpetaFotos)
        ordenes = OrdenRepository(usuarios, documentos, fotos, reloj)
        notificaciones = NotificacionRepository(usuarios, documentos)
    }

    private fun archivoFoto(nombre: String, bytes: Int = 100): Foto {
        val archivo = File(carpetaTemporal.newFolder(), nombre)
        archivo.writeBytes(ByteArray(bytes))
        return Foto("foto-$nombre", archivo.absolutePath, ahora)
    }

    // ---------------------------------------------------------------------------------------------
    // Almacén de fotos
    // ---------------------------------------------------------------------------------------------

    @Test
    fun fotoStorageCopiaLaFotoValidaALaCarpetaPermanente() = runBlocking {
        val resultado = fotos.subir("u-cli", listOf(archivoFoto("a.jpg")))
        val guardada = (resultado as Resultado.Exito).valor.single()
        assertEquals(carpetaFotos, File(guardada.ruta).parentFile)
        assertTrue(File(guardada.ruta).exists())
    }

    @Test
    fun fotoStorageRechazaFormatoYTamanoNoPermitidos() = runBlocking {
        val gif = fotos.subir("u-cli", listOf(archivoFoto("a.gif")))
        assertEquals("La foto debe ser JPG o PNG", (gif as Resultado.Error).mensaje)
        val grande = fotos.subir("u-cli", listOf(archivoFoto("b.png", bytes = 5 * 1024 * 1024 + 1)))
        assertEquals("La foto no debe superar los 5 MB", (grande as Resultado.Error).mensaje)
    }

    @Test
    fun fotoStorageDejaIgualLasFotosDeEjemploYElimina_SoloLasPropias() = runBlocking {
        val ejemplo = Foto("e", "recurso:foto_demo_frente", ahora)
        val guardada = (fotos.subir("u-cli", listOf(archivoFoto("c.png"), ejemplo)) as Resultado.Exito).valor
        assertEquals("recurso:foto_demo_frente", guardada[1].ruta)
        fotos.eliminar(guardada)
        assertFalse(File(guardada[0].ruta).exists())
    }

    // ---------------------------------------------------------------------------------------------
    // Avisos que genera el servidor simulado al escribir una orden
    // ---------------------------------------------------------------------------------------------

    @Test
    fun cambiarElEstadoDeLaOrdenCreaElAvisoParaElCliente() = runBlocking {
        val antes = bd.notificaciones.value.size
        ordenes.iniciarReparacion("o-4", "u-tec-marco")
        // o-4 es de un cliente sin cuenta: no recibe aviso
        assertEquals(antes, bd.notificaciones.value.size)
        bd.ordenes.value = bd.ordenes.value.map { if (it.id == "o-3") it.copy(estado = com.leosle.pitlane.modelo.EstadoOrden.INGRESADO) else it }
        ordenes.iniciarReparacion("o-3", "u-tec-luis")
        val nueva = bd.notificaciones.value.last()
        assertEquals("u-cli-carlos", nueva.usuarioId)
        assertEquals("Tu vehículo BCD-456 está en reparación", nueva.mensaje)
        assertEquals("o-3", nueva.ordenId)
    }

    @Test
    fun reenviarElAvisoCreaUnaNotificacionNuevaSoloDespuesDe24Horas() = runBlocking {
        val inicial = bd.notificaciones.value.size
        assertTrue(ordenes.reenviarAviso("o-2", "u-jefe") is Resultado.Error)
        assertEquals(inicial, bd.notificaciones.value.size)
        ahora += 25 * 3_600_000L
        assertTrue(ordenes.reenviarAviso("o-2", "u-jefe") is Resultado.Exito)
        assertEquals(inicial + 1, bd.notificaciones.value.size)
        assertEquals("Tu vehículo ABC-123 está listo para recoger", bd.notificaciones.value.last().mensaje)
    }

    @Test
    fun registrarOrdenAvisaAlClienteConCuentaYGuardaLasFotosPermanentes() = runBlocking {
        val datos = DatosOrden(
            "u-cli-carlos", "v-versa", "s-3", "La batería se descarga sola", 5000,
            NivelCombustible.MEDIO, "", listOf(archivoFoto("d.jpg")), "u-tec-marco"
        )
        val ingreso = (ordenes.registrarOrden(datos, "u-jefe") as Resultado.Exito).valor
        assertEquals(carpetaFotos, File(ingreso.orden.fotos.single().ruta).parentFile)
        assertEquals("Tu vehículo JKL-654 fue ingresado. Orden ORD-0005", bd.notificaciones.value.last().mensaje)
    }

    // ---------------------------------------------------------------------------------------------
    // Notificaciones nuevas y token
    // ---------------------------------------------------------------------------------------------

    @Test
    fun observarNuevasEmiteSoloLasNotificacionesPosterioresALaObservacion() = runBlocking {
        usuarios.iniciarSesion("carlos@freddy.pe", "Cliente123")
        val recibidas = mutableListOf<String>()
        val trabajo = launch { notificaciones.observarNuevas("u-cli-carlos").take(1).toList().forEach { recibidas.add(it.mensaje) } }
        yield()
        bd.ordenes.value = bd.ordenes.value.map { if (it.id == "o-3") it.copy(estado = com.leosle.pitlane.modelo.EstadoOrden.INGRESADO) else it }
        ordenes.iniciarReparacion("o-3", "u-tec-luis")
        trabajo.join()
        assertEquals(listOf("Tu vehículo BCD-456 está en reparación"), recibidas)
    }

    @Test
    fun guardarTokenDispositivoReemplazaElTokenAnterior() = runBlocking {
        notificaciones.guardarTokenDispositivo("u-cli-ana", "token-1")
        notificaciones.guardarTokenDispositivo("u-cli-ana", "token-2")
        assertEquals("token-2", bd.tokensDispositivo.value["u-cli-ana"])
    }
}
