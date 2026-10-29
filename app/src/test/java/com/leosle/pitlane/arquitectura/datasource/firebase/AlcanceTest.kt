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
        assertEquals(Alcance.Nada, alcanceDe(null, "usuarioId"))
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
    fun conCampoPropioTodosLeenSoloLoSuyoIncluidoElJefe() {
        assertEquals(Alcance.Igual("usuarioId", "j1"), alcanceDe(usuario("j1", Rol.JEFE), "usuarioId"))
        assertEquals(Alcance.Igual("usuarioId", "c1"), alcanceDe(usuario("c1", Rol.CLIENTE), "usuarioId"))
    }
}
