package com.leosle.pitlane.arquitectura.datasource.firebase

import com.leosle.pitlane.modelo.Vehiculo
import org.junit.Assert.assertEquals
import org.junit.Test

class ReservaDePlacaTest {
    private val vehiculo = Vehiculo("v-1", "u-cli-ana", "ABC-123", "Toyota", "Yaris", 2018, "Plata")

    @Test
    fun laReservaEsUnDocumentoCuyoIdEsLaPlaca() {
        assertEquals("placas/ABC-123", rutaYDatosDeReservaDePlaca(vehiculo).first)
    }

    @Test
    fun laReservaLlevaSoloElVehiculoYElDueno() {
        assertEquals(mapOf("vehiculoId" to "v-1", "clienteId" to "u-cli-ana"), rutaYDatosDeReservaDePlaca(vehiculo).second)
    }

    @Test
    fun dosVehiculosConLaMismaPlacaApuntanAlMismoDocumento() {
        val otro = vehiculo.copy(id = "v-2", clienteId = "u-cli-carlos")
        assertEquals(rutaYDatosDeReservaDePlaca(vehiculo).first, rutaYDatosDeReservaDePlaca(otro).first)
    }
}
