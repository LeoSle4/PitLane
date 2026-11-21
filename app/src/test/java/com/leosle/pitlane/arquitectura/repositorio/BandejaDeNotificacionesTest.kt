package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.NotificacionRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La bandeja de cada usuario es la subcolección `usuarios/{usuarioId}/notificaciones`: la app la lee entera, sin filtrar
 * por `usuarioId`, y marca como leído dentro de ella.
 */
class BandejaDeNotificacionesTest {

    private lateinit var bd: BaseDatosLocal
    private lateinit var documentos: DocumentosDataSource
    private lateinit var usuarios: UsuariosDataSource
    private lateinit var notificaciones: NotificacionRepository

    @Before
    fun preparar() = runBlocking {
        bd = BaseDatosLocal()
        documentos = DocumentosDataSource(bd)
        usuarios = UsuariosDataSource(bd)
        notificaciones = NotificacionRepository(usuarios, documentos)
        usuarios.iniciarSesion("cliente@freddy.pe", "Cliente123")
        Unit
    }

    @Test
    fun laBandejaDeUnUsuarioSoloTieneSusNotificaciones() = runBlocking {
        assertEquals(listOf("n-1", "n-2", "n-3"), documentos.notificacionesDe("u-cli-ana").listar().map { it.id })
        assertEquals(listOf("n-4", "n-5"), documentos.notificacionesDe("u-cli-carlos").listar().map { it.id })
        assertEquals(null, documentos.notificacionesDe("u-cli-ana").obtener("n-4"))
    }

    @Test
    fun laBandejaListaLasPropiasDeLaMasRecienteALaMasAntigua() = runBlocking {
        assertEquals(listOf("n-3", "n-2", "n-1"), notificaciones.observarNotificaciones("u-cli-ana").first().map { it.id })
    }

    @Test
    fun laBandejaDeOtroUsuarioSeVeVacia() = runBlocking {
        assertEquals(emptyList<String>(), notificaciones.observarNotificaciones("u-cli-carlos").first().map { it.id })
        assertEquals(0, notificaciones.observarNoLeidas("u-cli-carlos").first())
    }

    @Test
    fun elContadorCuentaSoloLasNoLeidasDeLaBandeja() = runBlocking {
        assertEquals(1, notificaciones.observarNoLeidas("u-cli-ana").first())
    }

    @Test
    fun marcarLeidaCambiaSoloLeidaEnLaBandejaDelUsuario() = runBlocking {
        val antes = bd.notificaciones.value.first { it.id == "n-3" }
        assertEquals(Resultado.Exito(Unit), notificaciones.marcarLeida("n-3"))
        val despues = documentos.notificacionesDe("u-cli-ana").obtener("n-3")!!
        assertEquals(antes.copy(leida = true), despues)
        assertEquals(0, notificaciones.observarNoLeidas("u-cli-ana").first())
        assertEquals(5, bd.notificaciones.value.size)
    }

    @Test
    fun marcarLeidaUnaNotificacionAjenaNoLaEncuentraNiLaCambia() = runBlocking {
        // n-5 está en la bandeja de Carlos: en la de Ana no existe, igual que en Firestore
        assertEquals(Resultado.Exito(Unit), notificaciones.marcarLeida("n-5"))
        assertFalse(bd.notificaciones.value.first { it.id == "n-5" }.leida)
    }

    @Test
    fun sinSesionNoSeMarcaNada() = runBlocking {
        usuarios.cerrarSesion()
        assertTrue(notificaciones.marcarLeida("n-3") is Resultado.Error)
        assertFalse(bd.notificaciones.value.first { it.id == "n-3" }.leida)
    }
}
