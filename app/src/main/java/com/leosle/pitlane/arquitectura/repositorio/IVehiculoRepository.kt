package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.modelo.VehiculoConEstado
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow

/** Registro, edición y eliminación de vehículos de los clientes. */
interface IVehiculoRepository {

    /** Emite los vehículos visibles de un cliente con el estado de su orden más reciente. */
    fun observarVehiculosDeCliente(clienteId: String): Flow<List<VehiculoConEstado>>

    /** Emite los datos de un vehículo cada vez que cambian. */
    fun observarVehiculo(vehiculoId: String): Flow<Vehiculo?>

    /** Registra un vehículo; [registradoPorJefe] ajusta el mensaje cuando la placa ya existe. */
    suspend fun registrarVehiculo(
        clienteId: String,
        placa: String,
        marca: String,
        modelo: String,
        anio: Int,
        color: String,
        registradoPorJefe: Boolean
    ): Resultado<Vehiculo>

    /** Cambia marca, modelo, año y color; la placa no se puede modificar. */
    suspend fun editarVehiculo(vehiculoId: String, marca: String, modelo: String, anio: Int, color: String): Resultado<Vehiculo>

    /** Elimina el vehículo; si el cliente lo pide y tiene historial, solo lo oculta. */
    suspend fun eliminarVehiculo(vehiculoId: String, solicitadoPorJefe: Boolean): Resultado<Unit>
}
