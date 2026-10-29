package com.leosle.pitlane.arquitectura.viewmodel

import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Pruebas de la protección que evita que una falla técnica tumbe la aplicación. */
class ProtegidoTest {

    @Test
    fun entregaElResultadoDeLaOperacionCuandoNoFalla() = runBlocking {
        assertEquals(Resultado.Exito(5), protegido { Resultado.Exito(5) })
        assertEquals(Resultado.Error("Regla de negocio"), protegido<Int> { Resultado.Error("Regla de negocio") })
    }

    @Test
    fun convierteUnaFallaTecnicaEnUnErrorConMensaje() = runBlocking {
        val resultado = protegido<Int> { throw IllegalStateException("sin red") }

        assertEquals(Resultado.Error(MENSAJE_FALLA_TECNICA), resultado)
    }

    @Test
    fun noTragaLaCancelacion() = runBlocking {
        try {
            protegido<Int> { throw CancellationException("cancelada") }
            fail("La cancelación debía propagarse")
        } catch (e: CancellationException) {
            assertEquals("cancelada", e.message)
        }
    }
}
