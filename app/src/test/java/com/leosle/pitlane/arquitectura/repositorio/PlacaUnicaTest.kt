package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.PlacaYaRegistradaException
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.VehiculoRepository
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.util.Reloj
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** El repositorio traduce la placa perdida en una carrera al mismo mensaje del duplicado conocido. */
class PlacaUnicaTest {

    private val mensaje = "Esta placa ya está registrada. Si es tuya, comunícate con el taller"
    private lateinit var bd: BaseDatosLocal
    private lateinit var usuarios: UsuariosDataSource
    private lateinit var documentos: DocumentosDataSource

    @Before
    fun preparar() {
        bd = BaseDatosLocal(conSemilla = true, reloj = Reloj { 1_800_000_000_000L })
        usuarios = UsuariosDataSource(bd)
        documentos = DocumentosDataSource(bd)
    }

    /** Fuente de datos cuyo guardado de vehículos pierde la carrera por la placa, como lo haría Firestore. */
    private fun conPlacaPerdida(): IDocumentosDataSource = object : IDocumentosDataSource by documentos {
        override val vehiculos: IColeccion<Vehiculo> = object : IColeccion<Vehiculo> by documentos.vehiculos {
            override suspend fun guardar(id: String, valor: Vehiculo) = throw PlacaYaRegistradaException()
        }
    }

    @Test
    fun alPerderLaCarreraDevuelveElMensajeDelDuplicadoYNoGuardaNada() = runBlocking {
        val repositorio = VehiculoRepository(usuarios, conPlacaPerdida())
        val total = bd.vehiculos.value.size
        val resultado = repositorio.registrarVehiculo("u-cli-lucia", "qwe987", "Kia", "Rio", 2020, "Rojo", false)
        assertEquals(mensaje, (resultado as Resultado.Error).mensaje)
        assertEquals(total, bd.vehiculos.value.size)
    }

    @Test
    fun unaPlacaConocidaSigueRechazandoseConsultandoPrimero() = runBlocking {
        val repositorio = VehiculoRepository(usuarios, documentos)
        val resultado = repositorio.registrarVehiculo("u-cli-lucia", "abc123", "Kia", "Rio", 2020, "Rojo", false)
        assertEquals(mensaje, (resultado as Resultado.Error).mensaje)
    }

    @Test
    fun unaPlacaNuevaSeRegistraNormalizada() = runBlocking {
        val repositorio = VehiculoRepository(usuarios, documentos)
        val resultado = repositorio.registrarVehiculo("u-cli-lucia", "qwe987", "Kia", "Rio", 2020, "Rojo", false)
        val vehiculo = (resultado as Resultado.Exito).valor
        assertEquals("QWE-987", vehiculo.placa)
        assertTrue(bd.vehiculos.value.any { it.id == vehiculo.id })
    }

    @Test
    fun editarSigueCambiandoLosDatosSinTocarLaPlaca() = runBlocking {
        val repositorio = VehiculoRepository(usuarios, documentos)
        val editado = (repositorio.editarVehiculo("v-yaris", "Kia", "Rio", 2021, "Negro") as Resultado.Exito).valor
        assertEquals("ABC-123", editado.placa)
        assertEquals("Kia", bd.vehiculos.value.first { it.id == "v-yaris" }.marca)
    }

    @Test
    fun eliminarSigueQuitandoElVehiculoSinHistorial() = runBlocking {
        val repositorio = VehiculoRepository(usuarios, documentos)
        assertTrue(repositorio.eliminarVehiculo("v-accent", solicitadoPorJefe = false) is Resultado.Exito)
        assertNull(bd.vehiculos.value.firstOrNull { it.id == "v-accent" })
    }
}
