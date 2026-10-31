package com.leosle.pitlane.arquitectura.vista.comun

import android.graphics.drawable.Drawable
import android.widget.Button
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.leosle.pitlane.R
import com.leosle.pitlane.util.Textos

/**
 * Muestra en un botón que su operación está en curso: lo deshabilita, cambia el texto a lo que está pasando
 * ("Registrar" pasa a "Registrando…") y pone una rueda girando junto al texto. Al terminar lo deja como estaba.
 */
object BotonCarga {

    /** Lo que el botón tenía antes de empezar la operación. */
    private class Original(val texto: CharSequence, val icono: Drawable?, val gravedadIcono: Int)

    /** Pone el botón en estado de carga; si ya lo estaba, no hace nada. */
    fun iniciar(boton: Button, texto: String = Textos.gerundio(boton.text.toString())) {
        if (boton.getTag(R.id.boton_carga) != null) return
        val material = boton as? MaterialButton
        boton.setTag(R.id.boton_carga, Original(boton.text, material?.icon, material?.iconGravity ?: 0))
        boton.isEnabled = false
        boton.text = texto
        material?.apply {
            val especificacion = CircularProgressIndicatorSpec(
                context, null, 0, com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall
            )
            especificacion.indicatorColors = intArrayOf(currentTextColor)
            icon = IndeterminateDrawable.createCircularDrawable(context, especificacion)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        }
    }

    /** Devuelve el botón a su texto e ícono originales y lo habilita. */
    fun terminar(boton: Button) {
        val original = boton.getTag(R.id.boton_carga) as? Original
        boton.setTag(R.id.boton_carga, null)
        if (original != null) {
            boton.text = original.texto
            (boton as? MaterialButton)?.apply {
                icon = original.icono
                iconGravity = original.gravedadIcono
            }
        }
        boton.isEnabled = true
    }
}
