package com.leosle.pitlane.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas de las reglas de formato de los formularios. */
class ValidadorTest {

    @Test
    fun dniAceptaSoloOchoDigitos() {
        assertNull(Validador.dni("12345678"))
        assertNotNull(Validador.dni("1234567"))
        assertNotNull(Validador.dni("1234567a"))
    }

    @Test
    fun telefonoDebeTenerNueveDigitosYEmpezarConNueve() {
        assertNull(Validador.telefono("912345678"))
        assertNotNull(Validador.telefono("812345678"))
        assertNotNull(Validador.telefono("91234567"))
    }

    @Test
    fun contrasenaExigeLongitudLetraYNumero() {
        assertNull(Validador.contrasena("Taller2026"))
        assertNotNull(Validador.contrasena("corta1"))
        assertNotNull(Validador.contrasena("sinnumeros"))
        assertNotNull(Validador.contrasena("12345678"))
    }

    @Test
    fun confirmacionDebeCoincidir() {
        assertNull(Validador.confirmacion("Abc12345", "Abc12345"))
        assertEquals("Las contraseñas no coinciden", Validador.confirmacion("Abc12345", "Abc12346"))
    }

    @Test
    fun placaSeNormalizaAMayusculasConGuion() {
        assertNull(Validador.placa("abc123"))
        assertNull(Validador.placa("abc-123"))
        assertNotNull(Validador.placa("ab-123"))
        assertEquals("ABC-123", Validador.normalizarPlaca("abc123"))
        assertEquals("ABC-123", Validador.normalizarPlaca(" abc-123 "))
    }

    @Test
    fun anioDebeEstarEntre1950YAnioActualMasUno() {
        assertNull(Validador.anio("2018", anioActual = 2026))
        assertNull(Validador.anio("2027", anioActual = 2026))
        assertEquals("Año no válido", Validador.anio("2028", anioActual = 2026))
        assertEquals("Año no válido", Validador.anio("1949", anioActual = 2026))
        assertEquals("Año no válido", Validador.anio("abcd", anioActual = 2026))
    }

    @Test
    fun correoOpcionalPermiteVacioPeroValidaFormato() {
        assertNull(Validador.correoOpcional(""))
        assertNull(Validador.correoOpcional("ana@correo.com"))
        assertNotNull(Validador.correoOpcional("ana@"))
    }

    @Test
    fun fotoSoloAceptaJpgOPngDeHastaCincoMegas() {
        assertNull(Validador.foto("image/jpeg", 1_000))
        assertNull(Validador.foto("image/png", Validador.MAXIMO_BYTES_FOTO))
        assertNotNull(Validador.foto("image/gif", 1_000))
        assertNotNull(Validador.foto("image/jpeg", Validador.MAXIMO_BYTES_FOTO + 1))
    }
}
