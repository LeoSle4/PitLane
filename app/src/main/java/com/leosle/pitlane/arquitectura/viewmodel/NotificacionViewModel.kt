package com.leosle.pitlane.arquitectura.viewmodel

import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.arquitectura.repositorio.INotificacionRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Bandeja de notificaciones, contador de no leídas y avisos nuevos. */
class NotificacionViewModel(
    private val repositorio: INotificacionRepository = Dependencias.repositorioNotificacion
) : ViewModelBase() {

    /** Notificaciones del usuario de la más reciente a la más antigua. */
    fun notificaciones(usuarioId: String): StateFlow<List<Notificacion>> =
        estadoPorClave("notificaciones:$usuarioId", emptyList()) { repositorio.observarNotificaciones(usuarioId) }

    /** Cantidad de notificaciones sin leer del usuario. */
    fun noLeidas(usuarioId: String): StateFlow<Int> =
        estadoPorClave("no-leidas:$usuarioId", 0) { repositorio.observarNoLeidas(usuarioId) }

    /** Cada notificación nueva dirigida al usuario mientras la aplicación está abierta. */
    fun nuevas(usuarioId: String): Flow<Notificacion> = repositorio.observarNuevas(usuarioId)

    /** Guarda el token del dispositivo del usuario para recibir avisos. */
    fun guardarTokenDispositivo(usuarioId: String, token: String, alTerminar: (Resultado<Unit>) -> Unit = {}) =
        ejecutar({ repositorio.guardarTokenDispositivo(usuarioId, token) }, alTerminar)

    /** Marca la notificación como leída. */
    fun marcarLeida(notificacionId: String, alTerminar: (Resultado<Unit>) -> Unit = {}) =
        ejecutar({ repositorio.marcarLeida(notificacionId) }, alTerminar)
}
