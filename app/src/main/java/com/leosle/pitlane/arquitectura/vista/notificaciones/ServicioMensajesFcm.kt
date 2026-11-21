package com.leosle.pitlane.arquitectura.vista.notificaciones

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.leosle.pitlane.modelo.Notificacion

/**
 * Recibe los mensajes de Firebase Cloud Messaging. El servidor envía solo datos, así que la notificación del sistema
 * la arma la aplicación, con la aplicación abierta, en segundo plano o cerrada.
 */
class ServicioMensajesFcm : FirebaseMessagingService() {

    /** FCM renovó el token del dispositivo: se guarda para el usuario con sesión. */
    override fun onNewToken(token: String) {
        MensajesPushService.registrarTokenNuevo(token)
    }

    /**
     * Muestra el aviso si la aplicación no está a la vista. Usa el id de la notificación de la bandeja, el mismo que usa
     * el oyente de Firestore, así que si llegan los dos la notificación del sistema se reemplaza y no se duplica.
     */
    override fun onMessageReceived(mensaje: RemoteMessage) {
        val notificacion = notificacionDe(mensaje.data) ?: return
        if (!MensajesPushService.enPrimerPlano) MensajesPushService.mostrar(applicationContext, notificacion)
    }
}

/** Arma el aviso con los datos del mensaje, o devuelve nulo si falta alguno de los indispensables. */
internal fun notificacionDe(datos: Map<String, String>): Notificacion? {
    val id = datos[MensajesPushService.EXTRA_NOTIFICACION_ID] ?: return null
    val titulo = datos["titulo"] ?: return null
    val mensaje = datos["mensaje"] ?: return null
    return Notificacion(
        id = id,
        usuarioId = "",
        titulo = titulo,
        mensaje = mensaje,
        fechaHora = System.currentTimeMillis(),
        ordenId = datos[MensajesPushService.EXTRA_ORDEN_ID],
        esIngreso = datos[MensajesPushService.EXTRA_ES_INGRESO] == "true"
    )
}
