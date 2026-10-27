package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.repositorio.ISolicitudRepository
import kotlinx.coroutines.flow.combine
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.IFotoStorage
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.SolicitudDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.util.Reloj
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Solicitudes de atención sobre las fuentes de documentos, usuarios y fotos. */
class SolicitudRepository(
    private val usuarios: IUsuariosDataSource,
    private val documentos: IDocumentosDataSource,
    private val fotos: IFotoStorage,
    private val reloj: Reloj
) : ISolicitudRepository {

    private companion object {
        const val MAXIMO_FOTOS = 3
        const val MINIMO_DESCRIPCION = 10
    }

    /** Emite las solicitudes del cliente de la más reciente a la más antigua. */
    override fun observarSolicitudesDeCliente(clienteId: String): Flow<List<SolicitudDetallada>> =
        combine(solicitudesDetalladas(usuarios, documentos), usuarios.usuarioActual) { lista, actor ->
            if (!actor.esJefeOPropio(clienteId)) emptyList()
            else lista.filter { it.solicitud.clienteId == clienteId }.sortedByDescending { it.solicitud.creadaEn }
        }

    /** Emite las solicitudes pendientes ordenadas por la fecha estimada de llegada. */
    override fun observarSolicitudesPendientes(): Flow<List<SolicitudDetallada>> =
        combine(solicitudesDetalladas(usuarios, documentos), usuarios.usuarioActual) { lista, actor ->
            if (!actor.esJefe()) emptyList()
            else lista.filter { it.solicitud.estado == EstadoSolicitud.PENDIENTE }.sortedBy { it.solicitud.fechaLlegada }
        }

    /** Emite la solicitud con ese identificador cada vez que cambia. */
    override fun observarSolicitud(solicitudId: String): Flow<SolicitudDetallada?> =
        combine(solicitudesDetalladas(usuarios, documentos), usuarios.usuarioActual) { lista, actor ->
            lista.firstOrNull { it.solicitud.id == solicitudId }?.takeIf { actor.esJefeOPropio(it.solicitud.clienteId) }
        }.distinctUntilChanged()

    /** Valida las reglas de la solicitud, guarda sus fotos y la registra como pendiente. */
    override suspend fun registrarSolicitud(
        clienteId: String,
        vehiculoId: String,
        descripcion: String,
        sintomas: List<String>,
        fechaLlegada: Long,
        fotos: List<Foto>
    ): Resultado<Solicitud> {
        validar(descripcion, fechaLlegada, fotos)?.let { return Resultado.Error(it) }
        val tieneAtencion = documentos.solicitudes.listar().any { it.vehiculoId == vehiculoId && it.estado == EstadoSolicitud.PENDIENTE } ||
            documentos.ordenes.listar().any { it.vehiculoId == vehiculoId && it.enCurso }
        if (tieneAtencion) return Resultado.Error("Este vehículo ya tiene una atención en curso")
        val guardadas = when (val subida = this.fotos.subir(fotos)) {
            is Resultado.Error -> return subida
            is Resultado.Exito -> subida.valor
        }
        val solicitud = Solicitud(
            id = documentos.solicitudes.nuevoId(),
            clienteId = clienteId,
            vehiculoId = vehiculoId,
            descripcion = descripcion.trim(),
            sintomas = sintomas,
            fechaLlegada = fechaLlegada,
            fotos = guardadas,
            estado = EstadoSolicitud.PENDIENTE,
            creadaEn = reloj.ahora()
        )
        documentos.solicitudes.guardar(solicitud.id, solicitud)
        return Resultado.Exito(solicitud)
    }

    /** Actualiza una solicitud que siga pendiente y elimina las fotos que ya no se usan. */
    override suspend fun editarSolicitud(
        solicitudId: String,
        descripcion: String,
        sintomas: List<String>,
        fechaLlegada: Long,
        fotos: List<Foto>
    ): Resultado<Solicitud> {
        val solicitud = documentos.solicitudes.obtener(solicitudId) ?: return Resultado.Error("No se encontró la solicitud")
        if (solicitud.estado != EstadoSolicitud.PENDIENTE) {
            return Resultado.Error("Solo se puede editar una solicitud pendiente")
        }
        validar(descripcion, fechaLlegada, fotos)?.let { return Resultado.Error(it) }
        val guardadas = when (val subida = this.fotos.subir(fotos)) {
            is Resultado.Error -> return subida
            is Resultado.Exito -> subida.valor
        }
        val idsNuevos = guardadas.map { it.id }.toSet()
        this.fotos.eliminar(solicitud.fotos.filter { it.id !in idsNuevos })
        val actualizada = solicitud.copy(
            descripcion = descripcion.trim(),
            sintomas = sintomas,
            fechaLlegada = fechaLlegada,
            fotos = guardadas
        )
        documentos.solicitudes.guardar(solicitudId, actualizada)
        return Resultado.Exito(actualizada)
    }

    /** Cambia a cancelada una solicitud pendiente. */
    override suspend fun cancelarSolicitud(solicitudId: String): Resultado<Unit> {
        val solicitud = documentos.solicitudes.obtener(solicitudId) ?: return Resultado.Error("No se encontró la solicitud")
        if (solicitud.estado != EstadoSolicitud.PENDIENTE) {
            return Resultado.Error("Solo se puede cancelar una solicitud pendiente")
        }
        documentos.solicitudes.guardar(solicitudId, solicitud.copy(estado = EstadoSolicitud.CANCELADA))
        return Resultado.Exito(Unit)
    }

    /** Comprueba descripción mínima, fecha no anterior a hoy y máximo de fotos; devuelve el primer error. */
    private fun validar(descripcion: String, fechaLlegada: Long, fotos: List<Foto>): String? = when {
        descripcion.trim().length < MINIMO_DESCRIPCION -> "Describe la falla con al menos 10 caracteres"
        Fechas.esAnteriorAHoy(fechaLlegada, reloj.ahora()) -> "La fecha de llegada no puede ser anterior a hoy"
        fotos.size > MAXIMO_FOTOS -> "Máximo 3 fotos"
        else -> null
    }
}
