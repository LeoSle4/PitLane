package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.ClienteRepository
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.util.Reloj
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** La búsqueda de clientes consulta los términos indexados y no recorre usuarios ni vehículos. */
class BusquedaClientesTest {

    private val llamadas = mutableListOf<String>()
    private lateinit var sesiones: UsuariosDataSource
    private lateinit var clientes: IClienteRepository
    private lateinit var vehiculosEnvueltos: VehiculosRegistrados
    private lateinit var vehiculosReales: IColeccion<Vehiculo>

    private class VehiculosRegistrados(
        private val real: IColeccion<Vehiculo>,
        private val llamadas: MutableList<String>
    ) : IColeccion<Vehiculo> by real {
        /** Vehículos con `terminosPlaca` falsificados: el servidor los devolvería aunque su placa real no coincida. */
        var falsificados: List<Vehiculo> = emptyList()

        override suspend fun listar(): List<Vehiculo> = real.listar().also { llamadas += "vehiculos.listar" }
        override fun observarTodos(): Flow<List<Vehiculo>> = real.observarTodos().also { llamadas += "vehiculos.observarTodos" }
        override suspend fun listarDonde(campo: String, valor: String): List<Vehiculo> =
            real.listarDonde(campo, valor).also { llamadas += "vehiculos.listarDonde($campo)" }
        override suspend fun buscarPorTermino(campo: String, termino: String, limite: Int): List<Vehiculo> =
            (real.buscarPorTermino(campo, termino, limite) + falsificados)
                .also { llamadas += "vehiculos.buscarPorTermino($campo=$termino,$limite)" }
    }

    @Before
    fun preparar() {
        val bd = BaseDatosLocal(conSemilla = true, reloj = Reloj { 1_800_000_000_000L })
        sesiones = UsuariosDataSource(bd)
        val reales = DocumentosDataSource(bd)
        val documentos = object : IDocumentosDataSource by reales {
            override val vehiculos: IColeccion<Vehiculo> = VehiculosRegistrados(reales.vehiculos, llamadas).also { vehiculosEnvueltos = it }
        }
        val usuarios = object : IUsuariosDataSource by sesiones {
            override suspend fun listarUsuarios(): List<Usuario> = sesiones.listarUsuarios().also { llamadas += "usuarios.listarUsuarios" }
            override fun observarUsuarios(): Flow<List<Usuario>> = sesiones.observarUsuarios().also { llamadas += "usuarios.observarUsuarios" }
            override suspend fun buscarClientesPorTermino(termino: String, limite: Int): List<Usuario> =
                sesiones.buscarClientesPorTermino(termino, limite).also { llamadas += "usuarios.buscarClientesPorTermino($termino,$limite)" }
        }
        vehiculosReales = reales.vehiculos
        clientes = ClienteRepository(usuarios, documentos)
    }

    @Test
    fun unaPlacaFalsificadaEnLosTerminosNoHaceAparecerAUnClienteAjeno() = runBlocking {
        jefe()
        vehiculosEnvueltos.falsificados = runBlocking { vehiculosReales.listar() }.take(2)
        // Ninguna placa real contiene "zzz": el servidor igual devolvería esos vehículos por sus términos falsos
        assertEquals(emptyList<String>(), ids("zzz"))
    }

    private fun jefe() = runBlocking { sesiones.iniciarSesion("jefe@freddy.pe", "Taller2026") }

    private fun ids(consulta: String) = runBlocking { clientes.buscarClientes(consulta).map { it.cliente.id } }

    @Test
    fun encuentraPorFragmentoDeDni() {
        jefe()
        assertEquals(listOf("u-cli-ana"), ids("71234"))
        assertEquals(listOf("u-cli-carlos"), ids("5678"))
    }

    @Test
    fun encuentraPorPrefijoDeUnaPalabraDelNombreSinTildesNiMayusculas() {
        jefe()
        assertEquals(listOf("u-cli-ana"), ids("Torres"))
        assertEquals(listOf("u-cli-lucia"), ids("LUCIA"))
        assertEquals(listOf("u-cli-carlos"), ids("mendoza diaz"))
        assertEquals(listOf("u-cli-carlos"), ids("Díaz"))
    }

    @Test
    fun conDosPalabrasExigeQueAparezcanLasDos() {
        jefe()
        assertEquals(listOf("u-cli-ana"), ids("ana torres"))
        assertEquals(listOf("u-cli-ana"), ids("torres ana"))
        assertTrue(ids("ana mendoza").isEmpty())
        assertTrue(ids("ana 99999").isEmpty())
    }

    @Test
    fun encuentraPorFragmentoDePlacaConYSinGuion() {
        jefe()
        for (consulta in listOf("xyz789", "xyz-789", "XYZ 789", "z78")) {
            val resultado = runBlocking { clientes.buscarClientes(consulta) }.single()
            assertEquals(consulta, "u-cli-ana", resultado.cliente.id)
            assertEquals(consulta, listOf("XYZ-789"), resultado.placasCoincidentes)
            assertEquals(consulta, listOf("ABC-123", "XYZ-789"), resultado.placas)
        }
        assertEquals(listOf("u-cli-ana"), ids("abc-123"))
    }

    @Test
    fun nuncaRecorreUsuariosNiVehiculos() {
        jefe()
        for (consulta in listOf("71234", "torres", "ana torres", "xyz-789", "mendoza diaz")) {
            llamadas.clear()
            ids(consulta)
            assertTrue("$consulta: $llamadas", llamadas.isNotEmpty())
            assertTrue("$consulta: $llamadas", llamadas.none {
                it == "usuarios.listarUsuarios" || it == "usuarios.observarUsuarios" ||
                    it == "vehiculos.listar" || it == "vehiculos.observarTodos"
            })
        }
    }

    @Test
    fun consultaConLosTerminosYLimitesEsperados() {
        jefe()
        ids("ana torres")
        assertEquals(
            listOf(
                "usuarios.buscarClientesPorTermino(torres,30)",
                "vehiculos.buscarPorTermino(terminosPlaca=anatorres,30)",
                "vehiculos.listarDonde(clienteId)"
            ),
            llamadas
        )
    }

    @Test
    fun menosDeTresCaracteresOUnaPersonaQueNoEsElJefeDevuelveVacio() {
        jefe()
        assertTrue(ids("71").isEmpty())
        assertTrue(ids("a b").isEmpty())
        assertTrue(ids("  ").isEmpty())
        assertTrue(llamadas.isEmpty())
        for (cuenta in listOf("cliente@freddy.pe" to "Cliente123", "luis@freddy.pe" to "Tecnico123")) {
            runBlocking { sesiones.iniciarSesion(cuenta.first, cuenta.second) }
            assertTrue(ids("torres").isEmpty())
        }
        assertTrue(llamadas.isEmpty())
    }

    @Test
    fun unFragmentoDelMedioDelNombreYaNoCoincidePeroElPrefijoSi() {
        jefe()
        assertTrue(ids("ndoza").isEmpty())
        assertEquals(listOf("u-cli-carlos"), ids("mend"))
    }

    @Test
    fun losResultadosVienenOrdenadosPorNombreCompleto() {
        jefe()
        // "456" está en el DNI de Ana, Carlos y Lucía y en la placa BCD-456 de Carlos.
        val resultado = runBlocking { clientes.buscarClientes("456") }
        assertEquals(listOf("u-cli-ana", "u-cli-carlos", "u-cli-lucia"), resultado.map { it.cliente.id })
        assertEquals(listOf("BCD-456"), resultado[1].placasCoincidentes)
        assertEquals(listOf("BCD-456", "JKL-654"), resultado[1].placas)
        assertTrue(resultado[0].placasCoincidentes.isEmpty())
        assertTrue(resultado[2].placas.isEmpty())
    }

    @Test
    fun uneLasCoincidenciasDePersonaYPlacaSinRepetirAlCliente() {
        jefe()
        // "123" es parte del DNI de Ana (71234567) y de su placa ABC-123.
        val resultado = runBlocking { clientes.buscarClientes("123") }
        assertEquals(1, resultado.count { it.cliente.id == "u-cli-ana" })
        val ana = resultado.first { it.cliente.id == "u-cli-ana" }
        assertEquals(listOf("ABC-123"), ana.placasCoincidentes)
        assertEquals(listOf("ABC-123", "XYZ-789"), ana.placas)
    }

    @Test
    fun buscarPorTerminoRechazaLimitesFueraDeRangoYRespetaElLimite() = runBlocking {
        val bd = BaseDatosLocal(conSemilla = true, reloj = Reloj { 1L })
        bd.vehiculos.value = bd.vehiculos.value + Vehiculo("v-otro", "u-cli-ana", "ABC-999", "Kia", "Rio", 2020, "Rojo")
        val vehiculos = DocumentosDataSource(bd).vehiculos
        for (limite in listOf(0, -1, 101)) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { vehiculos.buscarPorTermino("terminosPlaca", "abc", limite) } }
        }
        assertEquals(listOf("v-yaris", "v-otro"), vehiculos.buscarPorTermino("terminosPlaca", "abc", 100).map { it.id })
        assertEquals(listOf("v-yaris"), vehiculos.buscarPorTermino("terminosPlaca", "abc", 1).map { it.id })
        assertEquals(listOf("v-versa"), vehiculos.buscarPorTermino("terminosPlaca", "654", 5).map { it.id })
        assertTrue(vehiculos.buscarPorTermino("terminosPlaca", "zzz", 5).isEmpty())
    }
}
