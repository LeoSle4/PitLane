package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.modelo.FichaCliente
import com.leosle.pitlane.modelo.ResultadoBusqueda
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow

/** Gestión de clientes por parte del Jefe de Taller. */
interface IClienteRepository {

    /** Emite la lista de clientes cada vez que cambia. */
    fun observarClientes(): Flow<List<Usuario>>

    /** Emite los datos de un cliente, sus vehículos y el historial de órdenes de cada vehículo. */
    fun observarFichaCliente(clienteId: String): Flow<FichaCliente?>

    /**
     * Emite los [limite] clientes con actividad más reciente (órdenes registradas o con cambios y solicitudes
     * enviadas), con todas sus placas; solo para el Jefe.
     */
    fun observarClientesRecientes(limite: Int = 5): Flow<List<ResultadoBusqueda>>

    /** Busca por DNI, nombre, apellido o placa; devuelve vacío si el texto tiene menos de 3 caracteres. */
    suspend fun buscarClientes(consulta: String): List<ResultadoBusqueda>

    /** Registra un cliente sin cuenta de acceso; el correo puede ir vacío. */
    suspend fun registrarCliente(nombres: String, apellidos: String, dni: String, telefono: String, correo: String): Resultado<Usuario>

    /** Cambia nombres, apellidos y teléfono del cliente. */
    suspend fun editarCliente(clienteId: String, nombres: String, apellidos: String, telefono: String): Resultado<Usuario>

    /** Elimina al cliente y sus vehículos si no tiene órdenes ni cuenta en la aplicación. */
    suspend fun eliminarCliente(clienteId: String): Resultado<Unit>
}
