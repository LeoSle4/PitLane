package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow

/** Avisos dirigidos a un usuario y su estado de lectura. */
interface INotificacionRepository {

    /** Emite las notificaciones del usuario de la más reciente a la más antigua. */
    fun observarNotificaciones(usuarioId: String): Flow<List<Notificacion>>

    /** Emite cuántas notificaciones sin leer tiene el usuario. */
    fun observarNoLeidas(usuarioId: String): Flow<Int>

    /** Emite cada notificación nueva que llega al usuario mientras la aplicación está abierta. */
    fun observarNuevas(usuarioId: String): Flow<Notificacion>

    /** Marca una notificación como leída. */
    suspend fun marcarLeida(notificacionId: String): Resultado<Unit>

    /** Guarda el token del dispositivo con el que se envían los avisos al usuario. */
    suspend fun guardarTokenDispositivo(usuarioId: String, token: String): Resultado<Unit>
}
