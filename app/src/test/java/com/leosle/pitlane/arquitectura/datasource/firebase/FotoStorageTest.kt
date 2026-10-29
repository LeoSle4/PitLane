package com.leosle.pitlane.arquitectura.datasource.firebase

import com.leosle.pitlane.util.Validador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas de la ruta de las fotos en Cloud Storage y del tipo de imagen que se admite. */
class FotoStorageTest {

    @Test
    fun laRutaAgrupaLasFotosPorClienteDueno() {
        assertEquals("fotos/u-cli-1/f-9.jpg", rutaEnAlmacen("u-cli-1", "f-9", "jpg"))
    }

    @Test
    fun soloJpgYPngSonImagenesAdmitidas() {
        assertEquals("image/jpeg", Validador.tipoMimeDeImagen("jpg"))
        assertEquals("image/jpeg", Validador.tipoMimeDeImagen("JPEG"))
        assertEquals("image/png", Validador.tipoMimeDeImagen("png"))
        assertNull(Validador.tipoMimeDeImagen("gif"))
        assertNull(Validador.tipoMimeDeImagen(""))
    }
}
