package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.repositorio.INotificacionRepository
import kotlinx.coroutines.flow.combine
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Notificaciones de los usuarios sobre la fuente de documentos. */
class NotificacionRepository(
    private val usuarios: IUsuariosDataSource,
    private val documentos: IDocumentosDataSource
) : INotificacionRepository {

    /** Emite las notificaciones de la bandeja del usuario de la más reciente a la más antigua. */
    override fun observarNotificaciones(usuarioId: String): Flow<List<Notificacion>> =
        combine(documentos.notificacionesDe(usuarioId).observarTodos(), usuarios.usuarioActual) { lista, actor ->
            if (actor?.id != usuarioId) emptyList() else lista.sortedByDescending { it.fechaHora }
        }

    /** Emite la cantidad de notificaciones sin leer de la bandeja del usuario. */
    override fun observarNoLeidas(usuarioId: String): Flow<Int> =
        combine(documentos.notificacionesDe(usuarioId).observarTodos(), usuarios.usuarioActual) { lista, actor ->
            if (actor?.id != usuarioId) 0 else lista.count { !it.leida }
        }.distinctUntilChanged()

    /**
     * Emite cada notificación que llega a la bandeja del usuario después de empezar a observar. La primera lectura
     * solo registra las que ya existían, para no repetir avisos antiguos.
     */
    override fun observarNuevas(usuarioId: String): Flow<Notificacion> = flow {
        var conocidas: Set<String>? = null
        documentos.notificacionesDe(usuarioId).observarTodos().collect { lista ->
            if (usuarios.usuarioActual.value?.id != usuarioId) return@collect
            val previas = conocidas
            if (previas != null) {
                lista.filter { it.id !in previas }.sortedBy { it.fechaHora }.forEach { emit(it) }
            }
            conocidas = lista.map { it.id }.toSet()
        }
    }

    /**
     * Marca como leída la notificación indicada dentro de la bandeja del usuario con sesión: la ruta ya dice que es
     * suya. Una que no está en su bandeja (no existe o es de otro) se ignora, igual que Firestore no la deja leer.
     */
    override suspend fun marcarLeida(notificacionId: String): Resultado<Unit> {
        val actor = usuarios.usuarioActual.value ?: return Resultado.Error("Solo puedes marcar tus propios avisos")
        val bandeja = documentos.notificacionesDe(actor.id)
        val notificacion = bandeja.obtener(notificacionId) ?: return Resultado.Exito(Unit)
        bandeja.guardar(notificacionId, notificacion.copy(leida = true))
        return Resultado.Exito(Unit)
    }

    /** Guarda el token del dispositivo del usuario; cada dispositivo tiene su propio documento y vencen solos. */
    override suspend fun guardarTokenDispositivo(usuarioId: String, token: String): Resultado<Unit> {
        documentos.guardarTokenDispositivo(usuarioId, token)
        return Resultado.Exito(Unit)
    }
}
