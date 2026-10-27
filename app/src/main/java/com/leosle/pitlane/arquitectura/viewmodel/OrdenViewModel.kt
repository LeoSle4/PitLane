package com.leosle.pitlane.arquitectura.viewmodel

import androidx.lifecycle.viewModelScope
import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.modelo.DatosOrden
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.modelo.ResultadoIngreso
import com.leosle.pitlane.arquitectura.repositorio.IOrdenRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Órdenes de ingreso: consultas por rol, registro, edición, anulación y cambios de estado. */
class OrdenViewModel(
    private val repositorio: IOrdenRepository = Dependencias.repositorioOrden
) : ViewModelBase() {

    /** Una orden con su cliente, vehículo, técnico y solicitud, actualizada cada vez que cambia; vacía si [usuarioId] no puede verla. */
    fun orden(ordenId: String, usuarioId: String): StateFlow<OrdenDetallada?> =
        estadoPorClave("orden:$ordenId:$usuarioId", null) { repositorio.observarOrden(ordenId, usuarioId) }

    /** Órdenes del cliente de la más reciente a la más antigua. */
    fun ordenesDeCliente(clienteId: String): StateFlow<List<OrdenDetallada>> =
        estadoPorClave("ordenes-cliente:$clienteId", emptyList()) { repositorio.observarOrdenesDeCliente(clienteId) }

    /** Órdenes vivas del taller para el panel del Jefe. */
    val ordenesActivas: StateFlow<List<OrdenDetallada>> by lazy { repositorio.observarOrdenesActivas().comoEstado(emptyList()) }

    /** Órdenes Ingresado o En reparación asignadas al técnico. */
    fun ordenesDeTecnico(tecnicoId: String): StateFlow<List<OrdenDetallada>> =
        estadoPorClave("ordenes-tecnico:$tecnicoId", emptyList()) { repositorio.observarOrdenesDeTecnico(tecnicoId) }

    /** Todas las órdenes de un vehículo, de la más reciente a la más antigua. */
    fun ordenesDeVehiculo(vehiculoId: String): StateFlow<List<OrdenDetallada>> =
        estadoPorClave("ordenes-vehiculo:$vehiculoId", emptyList()) { repositorio.observarOrdenesDeVehiculo(vehiculoId) }

    /** Busca entre las órdenes Listo para recoger por placa, nombre o DNI del cliente o número de orden y entrega las coincidencias a [alTerminar]. */
    fun buscarOrdenesListas(consulta: String, alTerminar: (List<OrdenDetallada>) -> Unit) {
        viewModelScope.launch { alTerminar(repositorio.buscarOrdenesListas(consulta)) }
    }

    /** Registra una orden de ingreso y avisa al cliente. */
    fun registrarOrden(datos: DatosOrden, jefeId: String, alTerminar: (Resultado<ResultadoIngreso>) -> Unit) =
        ejecutar({ repositorio.registrarOrden(datos, jefeId) }, alTerminar)

    /** Guarda los cambios de una orden en curso. */
    fun editarOrden(
        ordenId: String,
        falla: String,
        kilometraje: Int,
        combustible: NivelCombustible,
        observaciones: String,
        tecnicoId: String,
        usuarioId: String,
        alTerminar: (Resultado<Orden>) -> Unit
    ) = ejecutar({ repositorio.editarOrden(ordenId, falla, kilometraje, combustible, observaciones, tecnicoId, usuarioId) }, alTerminar)

    /** Anula una orden Ingresado con su motivo. */
    fun anularOrden(ordenId: String, motivo: String, usuarioId: String, alTerminar: (Resultado<Orden>) -> Unit) =
        ejecutar({ repositorio.anularOrden(ordenId, motivo, usuarioId) }, alTerminar)

    /** Agrega fotos del estado exterior a una orden Ingresado. */
    fun agregarFotosOrden(ordenId: String, fotos: List<Foto>, usuarioId: String, alTerminar: (Resultado<Orden>) -> Unit) =
        ejecutar({ repositorio.agregarFotosOrden(ordenId, fotos, usuarioId) }, alTerminar)

    /** Quita una foto de una orden Ingresado. */
    fun eliminarFotoOrden(ordenId: String, fotoId: String, usuarioId: String, alTerminar: (Resultado<Orden>) -> Unit) =
        ejecutar({ repositorio.eliminarFotoOrden(ordenId, fotoId, usuarioId) }, alTerminar)

    /** Pasa la orden a En reparación. */
    fun iniciarReparacion(ordenId: String, tecnicoId: String, alTerminar: (Resultado<Orden>) -> Unit) =
        ejecutar({ repositorio.iniciarReparacion(ordenId, tecnicoId) }, alTerminar)

    /** Registra que el cliente comprobó el resultado y quedó conforme; solo el Jefe puede hacerlo. */
    fun registrarConformidad(ordenId: String, usuarioId: String, alTerminar: (Resultado<Orden>) -> Unit) =
        ejecutar({ repositorio.registrarConformidad(ordenId, usuarioId) }, alTerminar)

    /** Registra la observación del cliente y lo acordado sin cambiar el estado de la orden. */
    fun registrarObservacionCliente(
        ordenId: String,
        observacion: String,
        acuerdo: String,
        usuarioId: String,
        alTerminar: (Resultado<Orden>) -> Unit
    ) = ejecutar({ repositorio.registrarObservacionCliente(ordenId, observacion, acuerdo, usuarioId) }, alTerminar)

    /** Pasa la orden a Entregado cuando el cliente retira el vehículo con su conformidad registrada; solo el Jefe puede hacerlo. */
    fun marcarEntregada(ordenId: String, usuarioId: String, alTerminar: (Resultado<Orden>) -> Unit) =
        ejecutar({ repositorio.marcarEntregada(ordenId, usuarioId) }, alTerminar)

    /** Pasa la orden a Listo para recoger. */
    fun marcarListo(ordenId: String, tecnicoId: String, alTerminar: (Resultado<Orden>) -> Unit) =
        ejecutar({ repositorio.marcarListo(ordenId, tecnicoId) }, alTerminar)

    /** Reenvía el aviso de vehículo listo al cliente. */
    fun reenviarAviso(ordenId: String, usuarioId: String, alTerminar: (Resultado<Orden>) -> Unit) =
        ejecutar({ repositorio.reenviarAviso(ordenId, usuarioId) }, alTerminar)
}
