package com.leosle.pitlane

import android.app.Application
import com.leosle.pitlane.arquitectura.vista.notificaciones.MensajesPushService
import com.leosle.pitlane.arquitectura.vista.perfil.PreferenciasTema

/** Aplicación: al arrancar prepara las dependencias, aplica el tema elegido por el usuario y activa los avisos. */
class AplicacionPitLane : Application() {

    override fun onCreate() {
        super.onCreate()
        Dependencias.inicializar(this)
        PreferenciasTema.aplicarGuardado(this)
        MensajesPushService.iniciar(this)
    }
}
