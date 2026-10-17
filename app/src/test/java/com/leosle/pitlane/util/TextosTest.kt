package com.leosle.pitlane.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextosTest {

    @Test
    fun nombrePropioCorrigeMayusculasYMinusculas() {
        assertEquals("Leopoldo Choque Flores", Textos.nombrePropio("Leopoldo CHOque floRES"))
    }

    @Test
    fun nombrePropioQuitaEspaciosSobrantes() {
        assertEquals("Ana María", Textos.nombrePropio("  ana    maría "))
    }

    @Test
    fun nombrePropioDejaParticulasEnMinusculaSalvoAlInicio() {
        assertEquals("María de los Ángeles", Textos.nombrePropio("MARÍA DE LOS ÁNGELES"))
        assertEquals("De la Cruz", Textos.nombrePropio("de la cruz"))
    }

    @Test
    fun nombrePropioCapitalizaDespuesDeGuionYApostrofo() {
        assertEquals("Ana-Lucía O'Brien", Textos.nombrePropio("ana-lucía o'brien"))
    }

    @Test
    fun conInicialesMayusculasRespetaLasPalabrasConMezclaDeLetras() {
        assertEquals("Santa Fe", Textos.conInicialesMayusculas("santa fe"))
        assertEquals("CX-5", Textos.conInicialesMayusculas("CX-5"))
        assertEquals("RAV4 Hybrid", Textos.conInicialesMayusculas("RAV4 hybrid"))
    }

    @Test
    fun textoVacioQuedaVacio() {
        assertEquals("", Textos.nombrePropio("   "))
    }
}
