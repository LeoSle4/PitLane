package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.DniYaRegistradoException
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.ClienteRepository
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Reloj
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** El DNI es único como la placa: el repositorio traduce el DNI perdido en una carrera al mensaje del duplicado. */
class DniUnicoTest {

    private val mensaje = "Ya existe un cliente con ese DNI"
    private lateinit var bd: BaseDatosLocal
    private lateinit var usuarios: UsuariosDataSource
    private lateinit var documentos: DocumentosDataSource

    @Before
    fun preparar() {
        bd = BaseDatosLocal(conSemilla = true, reloj = Reloj { 1_800_000_000_000L })
        usuarios = UsuariosDataSource(bd)
        documentos = DocumentosDataSource(bd)
    }

    /** Fuente de usuarios cuyo guardado pierde la carrera por el DNI, como lo haría Firestore. */
    private fun conDniPerdido(): IUsuariosDataSource = object : IUsuariosDataSource by usuarios {
        override suspend fun guardarUsuario(usuario: Usuario) = throw DniYaRegistradoException()
    }

    @Test
    fun alPerderLaCarreraDevuelveElMensajeDelDuplicadoYNoGuardaNada() = runBlocking {
        val repositorio = ClienteRepository(conDniPerdido(), documentos)
        val total = bd.usuarios.value.size
        val resultado = repositorio.registrarCliente("Rosa", "Pinto", "70000001", "955555555", "")
        assertEquals(mensaje, (resultado as Resultado.Error).mensaje)
        assertEquals(total, bd.usuarios.value.size)
    }

    @Test
    fun unDniConocidoSigueRechazandoseConElIdDelCliente() = runBlocking {
        val resultado = ClienteRepository(usuarios, documentos).registrarCliente("Otra", "Persona", "71234567", "955555555", "")
        assertEquals(mensaje, (resultado as Resultado.Error).mensaje)
        assertEquals("u-cli-ana", resultado.referencia)
    }

    @Test
    fun unDniNuevoSeRegistra() = runBlocking {
        val resultado = ClienteRepository(usuarios, documentos).registrarCliente("rosa", "pinto", "70000001", "955555555", "")
        val cliente = (resultado as Resultado.Exito).valor
        assertEquals("70000001", cliente.dni)
        assertEquals(cliente, bd.usuarios.value.single { it.id == cliente.id })
    }

    @Test
    fun laFuenteLocalRechazaUnPerfilNuevoConUnDniYaUsadoComoElServidor() = runBlocking {
        val duplicado = Usuario("u-cli-dup", "Otra", "Persona", "71234567", "955555555", "", "", Rol.CLIENTE, tieneCuenta = false)
        try {
            usuarios.guardarUsuario(duplicado)
            fail("Debía rechazar el DNI repetido")
        } catch (_: DniYaRegistradoException) {
        }
        assertNull(bd.usuarios.value.firstOrNull { it.id == "u-cli-dup" })
    }

    @Test
    fun editarSigueCambiandoLosDatosSinTocarElDni() = runBlocking {
        val editado = (ClienteRepository(usuarios, documentos).editarCliente("u-cli-jorge", "jorge luis", "paredes", "999888777")
            as Resultado.Exito).valor
        assertEquals("43219876", editado.dni)
        assertEquals("999888777", bd.usuarios.value.first { it.id == "u-cli-jorge" }.telefono)
    }

    @Test
    fun eliminarSigueQuitandoAlClienteSinCuentaYLiberaSuDni() = runBlocking {
        val repositorio = ClienteRepository(usuarios, documentos)
        val cliente = (repositorio.registrarCliente("Rosa", "Pinto", "70000001", "955555555", "") as Resultado.Exito).valor
        assertTrue(repositorio.eliminarCliente(cliente.id) is Resultado.Exito)
        assertNull(bd.usuarios.value.firstOrNull { it.id == cliente.id })
        assertTrue(repositorio.registrarCliente("Rosa", "Pinto", "70000001", "955555555", "") is Resultado.Exito)
    }
}
