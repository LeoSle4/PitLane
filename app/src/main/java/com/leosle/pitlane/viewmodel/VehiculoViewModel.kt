package com.leosle.pitlane.viewmodel

import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.modelo.VehiculoConEstado
import com.leosle.pitlane.repositorio.IVehiculoRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow

/** Registro, consulta, edición y eliminación de vehículos. */
class VehiculoViewModel(
    private val repositorio: IVehiculoRepository = Dependencias.repositorioVehiculo
) : ViewModelBase() {

    /** Vehículos visibles del cliente con el estado de su orden más reciente. */
    fun vehiculosDeCliente(clienteId: String): StateFlow<List<VehiculoConEstado>> =
        estadoPorClave("vehiculos:$clienteId", emptyList()) { repositorio.observarVehiculosDeCliente(clienteId) }

    /** Datos de un vehículo, actualizados cada vez que cambian. */
    fun vehiculo(vehiculoId: String): StateFlow<Vehiculo?> =
        estadoPorClave("vehiculo:$vehiculoId", null) { repositorio.observarVehiculo(vehiculoId) }

    /** Registra un vehículo a nombre del cliente. */
    fun registrarVehiculo(
        clienteId: String,
        placa: String,
        marca: String,
        modelo: String,
        anio: Int,
        color: String,
        registradoPorJefe: Boolean,
        alTerminar: (Resultado<Vehiculo>) -> Unit
    ) = ejecutar({ repositorio.registrarVehiculo(clienteId, placa, marca, modelo, anio, color, registradoPorJefe) }, alTerminar)

    /** Guarda los datos editables del vehículo. */
    fun editarVehiculo(
        vehiculoId: String,
        marca: String,
        modelo: String,
        anio: Int,
        color: String,
        alTerminar: (Resultado<Vehiculo>) -> Unit
    ) = ejecutar({ repositorio.editarVehiculo(vehiculoId, marca, modelo, anio, color) }, alTerminar)

    /** Elimina u oculta el vehículo según las reglas del rol que lo pide. */
    fun eliminarVehiculo(vehiculoId: String, solicitadoPorJefe: Boolean, alTerminar: (Resultado<Unit>) -> Unit) =
        ejecutar({ repositorio.eliminarVehiculo(vehiculoId, solicitadoPorJefe) }, alTerminar)
}
