package com.leosle.pitlane.repositorio

import kotlinx.coroutines.flow.combine
import com.leosle.pitlane.fuente.IDocumentosDataSource
import com.leosle.pitlane.fuente.IUsuariosDataSource
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Notificaciones de los usuarios sobre la fuente de documentos. */
class NotificacionRepository(
    private val usuarios: IUsuariosDataSource,
    private val documentos: IDocumentosDataSource
) : INotificacionRepository {

    /** Emite las notificaciones del usuario de la más reciente a la más antigua. */
    override fun observarNotificaciones(usuarioId: String): Flow<List<Notificacion>> =
        combine(documentos.notificaciones.observarTodos(), usuarios.usuarioActual) { lista, actor ->
            if (actor?.id != usuarioId) emptyList()
            else lista.filter { it.usuarioId == usuarioId }.sortedByDescending { it.fechaHora }
        }

    /** Emite la cantidad de notificaciones sin leer del usuario. */
    override fun observarNoLeidas(usuarioId: String): Flow<Int> =
        combine(documentos.notificaciones.observarTodos(), usuarios.usuarioActual) { lista, actor ->
            if (actor?.id != usuarioId) 0 else lista.count { it.usuarioId == usuarioId && !it.leida }
        }.distinctUntilChanged()

    /**
     * Emite cada notificación del usuario que aparece después de empezar a observar. La primera lectura solo
     * registra las que ya existían, para no repetir avisos antiguos.
     */
    override fun observarNuevas(usuarioId: String): Flow<Notificacion> = flow {
        var conocidas: Set<String>? = null
        documentos.notificaciones.observarTodos().collect { lista ->
            if (usuarios.usuarioActual.value?.id != usuarioId) return@collect
            val propias = lista.filter { it.usuarioId == usuarioId }
            val previas = conocidas
            if (previas != null) {
                propias.filter { it.id !in previas }.sortedBy { it.fechaHora }.forEach { emit(it) }
            }
            conocidas = propias.map { it.id }.toSet()
        }
    }

    /** Marca como leída la notificación indicada. */
    override suspend fun marcarLeida(notificacionId: String): Resultado<Unit> {
        val notificacion = documentos.notificaciones.obtener(notificacionId) ?: return Resultado.Exito(Unit)
        if (usuarios.usuarioActual.value?.id != notificacion.usuarioId) return Resultado.Error("Solo puedes marcar tus propios avisos")
        documentos.notificaciones.guardar(notificacionId, notificacion.copy(leida = true))
        return Resultado.Exito(Unit)
    }

    /** Guarda el token del dispositivo del usuario; uno nuevo reemplaza al anterior. */
    override suspend fun guardarTokenDispositivo(usuarioId: String, token: String): Resultado<Unit> {
        documentos.guardarTokenDispositivo(usuarioId, token)
        return Resultado.Exito(Unit)
    }
}
