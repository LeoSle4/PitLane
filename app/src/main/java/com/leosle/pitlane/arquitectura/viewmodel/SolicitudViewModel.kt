package com.leosle.pitlane.arquitectura.viewmodel

import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.SolicitudDetallada
import com.leosle.pitlane.arquitectura.repositorio.ISolicitudRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow

/** Solicitudes de atención: listas del cliente y del Jefe, registro, edición y cancelación. */
class SolicitudViewModel(
    private val repositorio: ISolicitudRepository = Dependencias.repositorioSolicitud
) : ViewModelBase() {

    /** Solicitudes del cliente de la más reciente a la más antigua. */
    fun solicitudesDeCliente(clienteId: String): StateFlow<List<SolicitudDetallada>> =
        estadoPorClave("solicitudes:$clienteId", emptyList()) { repositorio.observarSolicitudesDeCliente(clienteId) }

    /** Solicitudes pendientes de todo el taller ordenadas por fecha de llegada. */
    val solicitudesPendientes: StateFlow<List<SolicitudDetallada>> by lazy {
        repositorio.observarSolicitudesPendientes().comoEstado(emptyList())
    }

    /** Una solicitud con su cliente y vehículo, actualizada cada vez que cambia. */
    fun solicitud(solicitudId: String): StateFlow<SolicitudDetallada?> =
        estadoPorClave("solicitud:$solicitudId", null) { repositorio.observarSolicitud(solicitudId) }

    /** Registra una solicitud pendiente. */
    fun registrarSolicitud(
        clienteId: String,
        vehiculoId: String,
        descripcion: String,
        sintomas: List<String>,
        fechaLlegada: Long,
        fotos: List<Foto>,
        alTerminar: (Resultado<Solicitud>) -> Unit
    ) = ejecutar({ repositorio.registrarSolicitud(clienteId, vehiculoId, descripcion, sintomas, fechaLlegada, fotos) }, alTerminar)

    /** Guarda los cambios de una solicitud pendiente. */
    fun editarSolicitud(
        solicitudId: String,
        descripcion: String,
        sintomas: List<String>,
        fechaLlegada: Long,
        fotos: List<Foto>,
        alTerminar: (Resultado<Solicitud>) -> Unit
    ) = ejecutar({ repositorio.editarSolicitud(solicitudId, descripcion, sintomas, fechaLlegada, fotos) }, alTerminar)

    /** Cancela una solicitud pendiente. */
    fun cancelarSolicitud(solicitudId: String, alTerminar: (Resultado<Unit>) -> Unit) =
        ejecutar({ repositorio.cancelarSolicitud(solicitudId) }, alTerminar)
}
