package com.leosle.pitlane.arquitectura.datasource.firebase

import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Usuario
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pruebas de lo que puede leer cada rol; las reglas de Firestore deben exigir lo mismo. */
class AlcanceTest {

    private fun usuario(id: String, rol: Rol, activo: Boolean = true) =
        Usuario(id, "N", "A", "1", "", "", "", rol, activo)

    @Test
    fun sinSesionNoSeLeeNada() {
        assertEquals(Alcance.Nada, alcanceDe(null))
        assertEquals(Alcance.Nada, alcanceDe(null, dueno = "c1"))
    }

    @Test
    fun unaCuentaDesactivadaNoLeeNada() {
        assertEquals(Alcance.Nada, alcanceDe(usuario("u1", Rol.JEFE, activo = false)))
    }

    @Test
    fun elJefeLeeTodaLaColeccion() {
        assertEquals(Alcance.Todo, alcanceDe(usuario("j1", Rol.JEFE)))
    }

    @Test
    fun clienteYTecnicoLeenSoloLosDocumentosQueLosIncluyenEnAcceso() {
        assertEquals(Alcance.Contiene("acceso", "c1"), alcanceDe(usuario("c1", Rol.CLIENTE)))
        assertEquals(Alcance.Contiene("acceso", "t1"), alcanceDe(usuario("t1", Rol.TECNICO)))
    }

    @Test
    fun enLaBandejaDeUnUsuarioSoloSuDuenoLeeTodaLaSubcoleccionNiSiquieraElJefe() {
        assertEquals(Alcance.Todo, alcanceDe(usuario("c1", Rol.CLIENTE), dueno = "c1"))
        assertEquals(Alcance.Todo, alcanceDe(usuario("j1", Rol.JEFE), dueno = "j1"))
        assertEquals(Alcance.Nada, alcanceDe(usuario("j1", Rol.JEFE), dueno = "c1"))
        assertEquals(Alcance.Nada, alcanceDe(usuario("t1", Rol.TECNICO), dueno = "c1"))
        assertEquals(Alcance.Nada, alcanceDe(usuario("c1", Rol.CLIENTE, activo = false), dueno = "c1"))
    }

    @Test
    fun losAvancesDeUnaOrdenLosLeeEnterosQuienTengaSesionYLasReglasDecidenPorLaOrdenPadre() {
        assertEquals(Alcance.Todo, alcanceDe(usuario("c1", Rol.CLIENTE), delPadre = true))
        assertEquals(Alcance.Todo, alcanceDe(usuario("t1", Rol.TECNICO), delPadre = true))
        assertEquals(Alcance.Todo, alcanceDe(usuario("j1", Rol.JEFE), delPadre = true))
        assertEquals(Alcance.Nada, alcanceDe(usuario("t1", Rol.TECNICO, activo = false), delPadre = true))
        assertEquals(Alcance.Nada, alcanceDe(null, delPadre = true))
    }

    @Test
    fun losAvancesSonUnaSubcoleccionDeSuOrden() {
        assertEquals("ordenes/o1/avances", rutaDeAvances("o1"))
    }

    @Test
    fun laBandejaDeNotificacionesEsUnaSubcoleccionDelUsuario() {
        assertEquals("usuarios/c1/notificaciones", rutaDeNotificaciones("c1"))
    }
}
