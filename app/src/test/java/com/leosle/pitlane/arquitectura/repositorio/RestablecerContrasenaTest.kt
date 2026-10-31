package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.AuthRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pruebas del restablecimiento de contraseña con un código enviado por correo. */
class RestablecerContrasenaTest {

    private lateinit var usuarios: UsuariosDataSource
    private lateinit var auth: IAuthRepository

    @Before
    fun preparar() {
        usuarios = UsuariosDataSource(BaseDatosLocal(conSemilla = true))
        auth = AuthRepository(usuarios)
    }

    @Test
    fun conElCodigoCorrectoLaContrasenaNuevaReemplazaALaAnterior() = runBlocking {
        assertTrue(auth.solicitarCodigoDeRestablecimiento("cliente@freddy.pe") is Resultado.Exito)
        val codigo = checkNotNull(usuarios.ultimoCodigoEnviado)

        assertTrue(auth.restablecerContrasena("cliente@freddy.pe", codigo, "NuevaClave9") is Resultado.Exito)

        assertTrue(auth.iniciarSesion("cliente@freddy.pe", "NuevaClave9") is Resultado.Exito)
        auth.cerrarSesion()
        assertTrue(auth.iniciarSesion("cliente@freddy.pe", "Cliente123") is Resultado.Error)
    }

    @Test
    fun unCodigoIncorrectoNoCambiaLaContrasena() = runBlocking {
        auth.solicitarCodigoDeRestablecimiento("cliente@freddy.pe")

        val resultado = auth.restablecerContrasena("cliente@freddy.pe", "000000", "NuevaClave9")

        assertEquals("El código no es correcto", (resultado as Resultado.Error).mensaje)
        assertTrue(auth.iniciarSesion("cliente@freddy.pe", "Cliente123") is Resultado.Exito)
    }

    @Test
    fun pedirUnCodigoParaUnCorreoSinCuentaRespondeIgual() = runBlocking {
        assertTrue(auth.solicitarCodigoDeRestablecimiento("nadie@freddy.pe") is Resultado.Exito)
    }
}
