package com.leosle.pitlane.arquitectura.datasource.firebase

import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenDispositivoTest {
    @Test
    fun laRutaEsUnDocumentoPorTokenDentroDelUsuario() {
        val (ruta, _) = rutaYDatosDeTokenDispositivo("u-cli-ana", "tok-1", 1700000000000)
        assertEquals("usuarios/u-cli-ana/tokensDispositivo/tok-1", ruta)
    }

    @Test
    fun elDocumentoLlevaSoloTokenYLaFechaEnQueExpira() {
        val (_, datos) = rutaYDatosDeTokenDispositivo("u-cli-ana", "tok-1", 1700000000000)
        val expira = Timestamp(Date(1700000000000 + VIGENCIA_TOKEN_DISPOSITIVO_MS))
        assertEquals(mapOf("token" to "tok-1", "expiraEn" to expira), datos)
    }

    @Test
    fun laVigenciaCabeEnLoQueAceptanLasReglas() {
        // Las reglas aceptan una expiración futura de como máximo 90 días
        val noventaDias = 90L * 24 * 60 * 60 * 1000
        assertEquals(true, VIGENCIA_TOKEN_DISPOSITIVO_MS > 0 && VIGENCIA_TOKEN_DISPOSITIVO_MS < noventaDias)
    }

    @Test
    fun dosDispositivosDelMismoUsuarioTienenRutasDistintas() {
        val a = rutaYDatosDeTokenDispositivo("u-cli-ana", "tok-1", 1).first
        val b = rutaYDatosDeTokenDispositivo("u-cli-ana", "tok-2", 1).first
        assertEquals(false, a == b)
    }

    @Test
    fun elMismoTokenSiempreCaeEnElMismoDocumento() {
        val a = rutaYDatosDeTokenDispositivo("u-cli-ana", "tok-1", 1).first
        val b = rutaYDatosDeTokenDispositivo("u-cli-ana", "tok-1", 99).first
        assertEquals(a, b)
    }
}
