package com.leosle.pitlane.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusquedaTest {

    @Test
    fun terminosDePersonaReproduceElEjemploAcordado() {
        val esperado = listOf(
            "mar", "mari", "maria", "jos", "jose", "per", "pere", "perez",
            "723", "234", "345", "456", "567", "678", "7234", "2345", "3456", "4567", "5678",
            "72345", "23456", "34567", "45678", "723456", "234567", "345678",
            "7234567", "2345678", "72345678"
        ).sorted()

        assertEquals(esperado, Busqueda.terminosDePersona("María José", "Pérez", "72345678"))
    }

    @Test
    fun terminosDePlacaReproduceElEjemploAcordado() {
        val esperado = listOf("abc", "bc1", "c12", "123", "abc1", "bc12", "c123", "abc12", "bc123", "abc123").sorted()

        assertEquals(esperado, Busqueda.terminosDePlaca("ABC-123"))
        assertEquals(10, esperado.size)
    }

    @Test
    fun laEnieYLasTildesSeNormalizan() {
        val terminos = Busqueda.terminosDePersona("Ñandú", "", "")

        assertEquals(listOf("nan", "nand", "nandu"), terminos)
    }

    @Test
    fun laPalabraConMenosDeTresLetrasNoAporta() {
        val terminos = Busqueda.terminosDePersona("Al de la Cruz", "Ng", "")

        assertEquals(listOf("cru", "cruz"), terminos)
    }

    @Test
    fun laPlacaSinGuionDaLosMismosTerminos() {
        assertEquals(Busqueda.terminosDePlaca("ABC-123"), Busqueda.terminosDePlaca("abc123"))
    }

    @Test
    fun normalizarQuitaTildesYSeparadores() {
        assertEquals("maria jose", Busqueda.normalizar("  María-José "))
        assertEquals("", Busqueda.normalizar("--"))
    }

    @Test
    fun laPalabraSeCortaA20Caracteres() {
        val terminos = Busqueda.terminosDePersona("a".repeat(30), "", "")

        assertEquals(18, terminos.size)
        assertEquals("a".repeat(20), terminos.maxByOrNull { it.length })
    }

    @Test
    fun sinRepetidosYConUnMaximoDe120ConservandoLosMasCortos() {
        val nombres = (1..10).joinToString(" ") { "p".repeat(it) + "x".repeat(18) }
        val terminos = Busqueda.terminosDePersona(nombres, "Zzzzzzzzzzzzzzzzzzzz", "12345678901234567890")

        assertEquals(120, terminos.size)
        assertEquals(terminos.distinct(), terminos)
        assertEquals(terminos.sorted(), terminos)
        assertTrue("123" in terminos && "ppp" in terminos)
    }

    @Test
    fun esDeterministaConIndependenciaDelOrdenDeEntradaDeLasPalabras() {
        val a = Busqueda.terminosDePersona("Ana Luisa", "Ruiz", "11112222")
        val b = Busqueda.terminosDePersona("Ana Luisa", "Ruiz", "11112222")

        assertEquals(a, b)
    }
}
