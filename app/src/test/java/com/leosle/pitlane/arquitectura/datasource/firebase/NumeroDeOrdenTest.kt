package com.leosle.pitlane.arquitectura.datasource.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pruebas del formato del número de orden que arma el contador transaccional. */
class NumeroDeOrdenTest {

    @Test
    fun elPrimerCorrelativoSeRellenaConCeros() {
        assertEquals("ORD-0001", numeroDeOrden(1))
    }

    @Test
    fun correlativosGrandesNoSeTruncan() {
        assertEquals("ORD-0123", numeroDeOrden(123))
        assertEquals("ORD-12345", numeroDeOrden(12_345))
    }
}
