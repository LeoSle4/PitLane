package com.leosle.pitlane.arquitectura.datasource.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pruebas de los mensajes que se muestran cuando Firebase rechaza una operación. */
class MensajesDeErrorTest {

    @Test
    fun credencialesInvalidasNoRevelanSiElCorreoExiste() {
        listOf("ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL")
            .forEach { assertEquals(MENSAJE_CREDENCIALES, mensajeDeAuth(it)) }
    }

    @Test
    fun cuentaDesactivadaTieneSuPropioMensaje() {
        assertEquals(MENSAJE_DESACTIVADA, mensajeDeAuth("ERROR_USER_DISABLED"))
    }

    @Test
    fun codigoDeAuthDesconocidoUsaElMensajeGenerico() {
        assertEquals(MENSAJE_GENERICO, mensajeDeAuth("ERROR_OTRO"))
        assertEquals(MENSAJE_GENERICO, mensajeDeAuth(null))
    }

    @Test
    fun funcionesMuestranElMensajeDelServidorEnLasReglasDeNegocio() {
        listOf("INVALID_ARGUMENT", "ALREADY_EXISTS", "NOT_FOUND", "PERMISSION_DENIED", "FAILED_PRECONDITION")
            .forEach { assertEquals("Ya existe un usuario con ese DNI", mensajeDeFunciones(it, "Ya existe un usuario con ese DNI")) }
    }

    @Test
    fun funcionesSinMensajeOConFallaTecnicaUsanUnMensajeGenerico() {
        assertEquals(MENSAJE_GENERICO, mensajeDeFunciones("ALREADY_EXISTS", null))
        assertEquals(MENSAJE_GENERICO, mensajeDeFunciones("ALREADY_EXISTS", " "))
        assertEquals(MENSAJE_GENERICO, mensajeDeFunciones("INTERNAL", "detalle técnico"))
    }

    @Test
    fun funcionesSinRedAvisanQueNoHayConexion() {
        assertEquals(MENSAJE_SIN_CONEXION, mensajeDeFunciones("UNAVAILABLE", "UNAVAILABLE"))
    }
}
