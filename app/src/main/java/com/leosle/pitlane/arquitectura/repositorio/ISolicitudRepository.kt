package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.SolicitudDetallada
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow

/** Solicitudes de atención que los clientes envían antes de llevar su vehículo. */
interface ISolicitudRepository {

    /** Emite las solicitudes de un cliente de la más reciente a la más antigua. */
    fun observarSolicitudesDeCliente(clienteId: String): Flow<List<SolicitudDetallada>>

    /** Emite las solicitudes pendientes ordenadas por fecha estimada de llegada. */
    fun observarSolicitudesPendientes(): Flow<List<SolicitudDetallada>>

    /** Emite una solicitud con su cliente y vehículo. */
    fun observarSolicitud(solicitudId: String): Flow<SolicitudDetallada?>

    /** Registra una solicitud pendiente. */
    suspend fun registrarSolicitud(
        clienteId: String,
        vehiculoId: String,
        descripcion: String,
        sintomas: List<String>,
        fechaLlegada: Long,
        fotos: List<Foto>
    ): Resultado<Solicitud>

    /** Cambia descripción, síntomas, fecha y fotos mientras la solicitud siga pendiente. */
    suspend fun editarSolicitud(
        solicitudId: String,
        descripcion: String,
        sintomas: List<String>,
        fechaLlegada: Long,
        fotos: List<Foto>
    ): Resultado<Solicitud>

    /** Cancela una solicitud pendiente. */
    suspend fun cancelarSolicitud(solicitudId: String): Resultado<Unit>
}
