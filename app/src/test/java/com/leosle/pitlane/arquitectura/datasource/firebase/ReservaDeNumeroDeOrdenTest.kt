package com.leosle.pitlane.arquitectura.datasource.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

class ReservaDeNumeroDeOrdenTest {
    @Test
    fun laReservaEsUnDocumentoCuyoIdEsElNumero() {
        assertEquals("numerosOrden/ORD-0007", rutaYDatosDeReservaDeNumero("ORD-0007", "o-1").first)
    }

    @Test
    fun laReservaLlevaSoloLaOrden() {
        assertEquals(mapOf("ordenId" to "o-1"), rutaYDatosDeReservaDeNumero("ORD-0007", "o-1").second)
    }

    @Test
    fun dosOrdenesConElMismoNumeroApuntanAlMismoDocumento() {
        assertEquals(
            rutaYDatosDeReservaDeNumero("ORD-0007", "o-1").first,
            rutaYDatosDeReservaDeNumero("ORD-0007", "o-2").first
        )
    }
}
