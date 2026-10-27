package com.leosle.pitlane.arquitectura.vista.comun

import android.animation.Animator
import android.animation.AnimatorInflater
import android.graphics.drawable.Animatable
import android.view.View
import android.widget.ImageView
import androidx.annotation.AnimatorRes
import androidx.annotation.DrawableRes
import com.leosle.pitlane.R

/** Reproduce las animaciones de la app; si el usuario quitó las animaciones del sistema, terminan al instante. */
object Animaciones {

    /** Muestra el dibujo animado en [imagen] y lo reproduce una vez. */
    fun reproducir(imagen: ImageView, @DrawableRes dibujoAnimado: Int) {
        imagen.setImageResource(dibujoAnimado)
        (imagen.drawable as? Animatable)?.start()
    }

    /** Inicia una animación repetitiva sobre [vista] y devuelve el animador para poder detenerlo. */
    fun repetir(vista: View, @AnimatorRes animador: Int = R.animator.pulso_rayo): Animator {
        val resultado = AnimatorInflater.loadAnimator(vista.context, animador)
        resultado.setTarget(vista)
        resultado.start()
        return resultado
    }
}
