package com.leosle.pitlane.vista.notificaciones

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.leosle.pitlane.R
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.viewmodel.AuthViewModel
import com.leosle.pitlane.viewmodel.NotificacionViewModel
import com.leosle.pitlane.vista.PrincipalActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Recibe los avisos nuevos del usuario con sesión y, cuando la aplicación no está a la vista, los muestra como
 * notificación del sistema; al tocarla se abre la orden relacionada. También entrega el token del dispositivo.
 */
object MensajesPushService {

    /** Clave del dato extra con el identificador de la orden que debe abrirse. */
    const val EXTRA_ORDEN_ID = "orden_id"

    /** Clave del dato extra con el identificador de la notificación tocada. */
    const val EXTRA_NOTIFICACION_ID = "notificacion_id"

    /** Clave del dato extra que indica que el aviso es el de ingreso y debe abrir el comprobante. */
    const val EXTRA_ES_INGRESO = "es_ingreso"

    private const val CANAL = "avisos_ordenes"
    private const val ARCHIVO = "preferencias_mensajes"
    private const val CLAVE_TOKEN = "token_dispositivo"

    /** Indica si alguna pantalla de la aplicación está visible; la actividad principal lo actualiza. */
    @Volatile
    var enPrimerPlano: Boolean = false

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val authViewModel by lazy { AuthViewModel() }
    private val notificacionViewModel by lazy { NotificacionViewModel() }

    /** Crea el canal de avisos y empieza a escuchar las notificaciones nuevas del usuario con sesión. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun iniciar(aplicacion: Application) {
        crearCanal(aplicacion)
        alcance.launch {
            authViewModel.usuarioActual
                .flatMapLatest { usuario ->
                    if (usuario == null) emptyFlow() else notificacionViewModel.nuevas(usuario.id)
                }
                .collect { notificacion -> if (!enPrimerPlano) mostrar(aplicacion, notificacion) }
        }
    }

    /** Devuelve el token de este dispositivo; se genera la primera vez y se conserva después. */
    fun tokenDelDispositivo(contexto: Context): String {
        val preferencias = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
        return preferencias.getString(CLAVE_TOKEN, null)
            ?: UUID.randomUUID().toString().also { preferencias.edit().putString(CLAVE_TOKEN, it).apply() }
    }

    /** Indica si el sistema permite mostrar notificaciones de esta aplicación. */
    fun puedeNotificar(contexto: Context): Boolean {
        val concedido = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return concedido && NotificationManagerCompat.from(contexto).areNotificationsEnabled()
    }

    /** Muestra la notificación del sistema; al tocarla abre la aplicación en la orden relacionada. */
    @SuppressLint("MissingPermission")
    fun mostrar(contexto: Context, notificacion: Notificacion) {
        if (!puedeNotificar(contexto)) return
        val intencion = Intent(contexto, PrincipalActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_NOTIFICACION_ID, notificacion.id)
            .putExtra(EXTRA_ES_INGRESO, notificacion.esIngreso)
        notificacion.ordenId?.let { intencion.putExtra(EXTRA_ORDEN_ID, it) }
        val pendiente = PendingIntent.getActivity(
            contexto,
            notificacion.id.hashCode(),
            intencion,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val construida = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(notificacion.titulo)
            .setContentText(notificacion.mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificacion.mensaje))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendiente)
            .build()
        NotificationManagerCompat.from(contexto).notify(notificacion.id.hashCode(), construida)
    }

    /** Crea el canal de notificaciones de las órdenes en los dispositivos que lo exigen. */
    private fun crearCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(CANAL, "Avisos de órdenes", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ingreso, reparación y vehículo listo"
        }
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}
