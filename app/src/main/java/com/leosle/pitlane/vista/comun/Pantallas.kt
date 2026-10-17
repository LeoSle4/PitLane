package com.leosle.pitlane.vista.comun

import android.view.View
import androidx.annotation.DrawableRes
import com.leosle.pitlane.databinding.IncludeBarraSuperiorBinding
import com.leosle.pitlane.databinding.IncludeEstadoVacioBinding

/** Configuración de la barra superior común: botón de volver, título y una acción opcional. */
object BarraSuperior {

    /**
     * Escribe el [titulo]; muestra el botón de volver solo si hay [alVolver] y un botón de acción
     * con el [iconoAccion] indicado solo si se entrega [alAccion].
     */
    fun configurar(
        barra: IncludeBarraSuperiorBinding,
        titulo: String,
        alVolver: (() -> Unit)? = null,
        @DrawableRes iconoAccion: Int? = null,
        descripcionAccion: String? = null,
        alAccion: (() -> Unit)? = null
    ) {
        barra.txtTituloBarra.text = titulo
        barra.btnAtras.visibility = if (alVolver != null) View.VISIBLE else View.GONE
        barra.btnAtras.setOnClickListener { alVolver?.invoke() }
        if (iconoAccion != null && alAccion != null) {
            barra.btnAccionBarra.visibility = View.VISIBLE
            barra.btnAccionBarra.setImageResource(iconoAccion)
            barra.btnAccionBarra.contentDescription = descripcionAccion
            barra.btnAccionBarra.setOnClickListener { alAccion() }
        } else {
            barra.btnAccionBarra.visibility = View.GONE
        }
    }
}

/** Configuración del estado vacío: ilustración, título, mensaje y un botón opcional. */
object EstadoVacio {

    /** Escribe los textos del estado vacío; el botón solo aparece si se entregan [textoBoton] y [alBoton]. */
    fun configurar(
        vacio: IncludeEstadoVacioBinding,
        titulo: String,
        mensaje: String,
        textoBoton: String? = null,
        alBoton: (() -> Unit)? = null
    ) {
        vacio.txtVacioTitulo.text = titulo
        vacio.txtVacioMensaje.text = mensaje
        if (textoBoton != null && alBoton != null) {
            vacio.btnVacioAccion.visibility = View.VISIBLE
            vacio.btnVacioAccion.text = textoBoton
            vacio.btnVacioAccion.setOnClickListener { alBoton() }
        } else {
            vacio.btnVacioAccion.visibility = View.GONE
        }
    }

    /** Muestra u oculta el estado vacío; sirve para pantallas sin ListView. */
    fun mostrar(vacio: IncludeEstadoVacioBinding, visible: Boolean) {
        vacio.root.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
