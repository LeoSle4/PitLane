package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.ClienteRepository
import com.leosle.pitlane.modelo.CambioEstado
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.Vehiculo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pruebas de los clientes recientes que el Jefe ve antes de escribir una búsqueda. */
class ClientesRecientesTest {

    private lateinit var bd: BaseDatosLocal
    private lateinit var sesiones: UsuariosDataSource
    private lateinit var clientes: IClienteRepository

    private fun cliente(id: String) = Usuario(id, "Nombre", id, "1$id", "900000000", "", "", Rol.CLIENTE, tieneCuenta = false)

    private fun orden(id: String, clienteId: String, creadaEn: Long, historial: List<CambioEstado> = emptyList()) = Orden(
        id, "ORD-$id", clienteId, "v-$clienteId", null, "Falla", 0, NivelCombustible.MEDIO, "", emptyList(), "t",
        EstadoOrden.INGRESADO, historial, creadaEn
    )

    private fun solicitud(id: String, clienteId: String, creadaEn: Long) = Solicitud(
        id, clienteId, "v-$clienteId", "No arranca el motor", emptyList(), creadaEn, emptyList(), EstadoSolicitud.PENDIENTE, creadaEn
    )

    @Before
    fun preparar() {
        bd = BaseDatosLocal(conSemilla = false)
        sesiones = UsuariosDataSource(bd)
        clientes = ClienteRepository(sesiones, DocumentosDataSource(bd))
        bd.usuarios.value = listOf(
            Usuario("j", "Jefe", "Taller", "10000000", "900000000", "jefe@taller.pe", "Clave1234", Rol.JEFE),
            Usuario("c-cuenta", "Con", "Cuenta", "20000000", "900000000", "cliente@taller.pe", "Clave1234", Rol.CLIENTE)
        ) + (1..7).map { cliente("c$it") }
        bd.vehiculos.value = (1..7).map { Vehiculo("v-c$it", "c$it", "AAA-00$it", "Kia", "Rio", 2020, "Rojo") } +
            Vehiculo("v-c1-b", "c1", "BBB-001", "Kia", "Rio", 2020, "Rojo")
    }

    @Test
    fun ordenaPorLaActividadMasRecienteYMuestraCincoConTodasSusPlacas() = runBlocking {
        sesiones.iniciarSesion("jefe@taller.pe", "Clave1234")
        bd.ordenes.value = listOf(
            orden("1", "c1", 100),
            orden("2", "c2", 50, listOf(CambioEstado(EstadoOrden.EN_REPARACION, 900, "t"))),
            orden("3", "c3", 300),
            orden("4", "c4", 200),
            orden("5", "c5", 150),
            orden("6", "c6", 120)
        )
        bd.solicitudes.value = listOf(solicitud("s1", "c3", 1_000), solicitud("s2", "c1", 80))

        val recientes = clientes.observarClientesRecientes().first()

        assertEquals(listOf("c3", "c2", "c4", "c5", "c6"), recientes.map { it.cliente.id })
        assertEquals(listOf("AAA-003"), recientes.first().placas)
        assertTrue(recientes.all { it.placasCoincidentes.isEmpty() })
    }

    @Test
    fun incluyeTodasLasPlacasDelClienteYOmiteALosQueNoTienenActividad() = runBlocking {
        sesiones.iniciarSesion("jefe@taller.pe", "Clave1234")
        bd.ordenes.value = listOf(orden("1", "c1", 100))

        val recientes = clientes.observarClientesRecientes().first()

        assertEquals(listOf("c1"), recientes.map { it.cliente.id })
        assertEquals(listOf("AAA-001", "BBB-001"), recientes.single().placas)
    }

    @Test
    fun soloElJefeVeLosClientesRecientes() = runBlocking {
        bd.ordenes.value = listOf(orden("1", "c1", 100))
        assertTrue(clientes.observarClientesRecientes().first().isEmpty())

        sesiones.iniciarSesion("cliente@taller.pe", "Clave1234")
        assertTrue(clientes.observarClientesRecientes().first().isEmpty())
    }
}
