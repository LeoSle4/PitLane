package com.leosle.pitlane.arquitectura.datasource.firebase

import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Usuario
import org.junit.Assert.assertEquals
import org.junit.Test

class ReservaDeDniTest {
    private val usuario = Usuario("u-cli-jorge", "Jorge", "Paredes Luna", "43219876", "934567890", "", "", Rol.CLIENTE, tieneCuenta = false)

    @Test
    fun laReservaEsUnDocumentoCuyoIdEsElDni() {
        assertEquals("dnis/43219876", rutaYDatosDeReservaDeDni(usuario).first)
    }

    @Test
    fun laReservaLlevaSoloElUsuario() {
        assertEquals(mapOf("usuarioId" to "u-cli-jorge"), rutaYDatosDeReservaDeDni(usuario).second)
    }

    @Test
    fun dosUsuariosConElMismoDniApuntanAlMismoDocumento() {
        val otro = usuario.copy(id = "u-cli-otro", nombres = "Otro")
        assertEquals(rutaYDatosDeReservaDeDni(usuario).first, rutaYDatosDeReservaDeDni(otro).first)
    }
}
