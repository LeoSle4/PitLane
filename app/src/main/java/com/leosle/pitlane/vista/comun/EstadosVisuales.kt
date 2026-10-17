package com.leosle.pitlane.vista.comun

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.leosle.pitlane.R
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Orden

/** Pinta los chips de estado con su color, ícono y texto; el color nunca es lo único que distingue el estado. */
object EstadosVisuales {

    /** Aplica a [chip] el texto, el fondo redondeado, el color de texto y el ícono indicados. */
    fun aplicar(
        chip: TextView,
        etiqueta: String,
        @ColorRes fondo: Int,
        @ColorRes texto: Int,
        @DrawableRes icono: Int
    ) {
        val contexto = chip.context
        val colorTexto = ContextCompat.getColor(contexto, texto)
        chip.text = etiqueta
        chip.setTextColor(colorTexto)
        chip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6f * contexto.resources.displayMetrics.density
            setColor(ContextCompat.getColor(contexto, fondo))
        }
        val lado = (14 * contexto.resources.displayMetrics.density).toInt()
        val dibujo = ContextCompat.getDrawable(contexto, icono)?.mutate()
        dibujo?.setTint(colorTexto)
        dibujo?.setBounds(0, 0, lado, lado)
        chip.setCompoundDrawablesRelative(dibujo, null, null, null)
    }

    /** Pinta el chip con el estado de una orden: Ingresado, En reparación, Listo para recoger, Entregado o Anulada. */
    fun estadoOrden(chip: TextView, estado: EstadoOrden) {
        when (estado) {
            EstadoOrden.INGRESADO -> aplicar(chip, estado.etiqueta, R.color.estado_neutro_fondo, R.color.estado_neutro_texto, R.drawable.ic_clipboard_text)
            EstadoOrden.EN_REPARACION -> aplicar(chip, estado.etiqueta, R.color.estado_proceso_fondo, R.color.estado_proceso_texto, R.drawable.ic_wrench)
            EstadoOrden.LISTO -> aplicar(chip, estado.etiqueta, R.color.estado_exito_fondo, R.color.estado_exito_texto, R.drawable.ic_check_circle)
            EstadoOrden.ENTREGADO -> aplicar(chip, estado.etiqueta, R.color.estado_neutro_fondo, R.color.estado_neutro_texto, R.drawable.ic_check_circle)
            EstadoOrden.ANULADA -> aplicar(chip, estado.etiqueta, R.color.estado_error_fondo, R.color.estado_error_texto, R.drawable.ic_x_circle)
        }
    }

    /** Pinta el chip con el estado de una solicitud: Pendiente, Atendida o Cancelada. */
    fun estadoSolicitud(chip: TextView, estado: EstadoSolicitud) {
        when (estado) {
            EstadoSolicitud.PENDIENTE -> aplicar(chip, estado.etiqueta, R.color.estado_proceso_fondo, R.color.estado_proceso_texto, R.drawable.ic_hourglass_medium)
            EstadoSolicitud.ATENDIDA -> aplicar(chip, estado.etiqueta, R.color.estado_exito_fondo, R.color.estado_exito_texto, R.drawable.ic_check_circle)
            EstadoSolicitud.CANCELADA -> aplicar(chip, estado.etiqueta, R.color.estado_error_fondo, R.color.estado_error_texto, R.drawable.ic_x_circle)
        }
    }

    /** Pinta el chip de un vehículo: el estado de su orden activa o "Sin orden activa" si no tiene. */
    fun estadoVehiculo(chip: TextView, ordenActiva: Orden?) {
        if (ordenActiva == null) {
            aplicar(chip, "Sin orden activa", R.color.estado_neutro_fondo, R.color.estado_neutro_texto, R.drawable.ic_car_profile)
        } else {
            estadoOrden(chip, ordenActiva.estado)
        }
    }

    /** Pinta el chip de una cuenta: Activo en verde o Inactivo en neutro. */
    fun estadoCuenta(chip: TextView, activo: Boolean) {
        if (activo) {
            aplicar(chip, "Activo", R.color.estado_exito_fondo, R.color.estado_exito_texto, R.drawable.ic_check_circle)
        } else {
            aplicar(chip, "Inactivo", R.color.estado_neutro_fondo, R.color.estado_neutro_texto, R.drawable.ic_x_circle)
        }
    }

    /** Devuelve el color de texto asociado al estado de una orden. */
    @ColorRes
    fun colorEstado(estado: EstadoOrden): Int = when (estado) {
        EstadoOrden.INGRESADO -> R.color.estado_neutro_texto
        EstadoOrden.EN_REPARACION -> R.color.estado_proceso_texto
        EstadoOrden.LISTO -> R.color.estado_exito_texto
        EstadoOrden.ENTREGADO -> R.color.estado_neutro_texto
        EstadoOrden.ANULADA -> R.color.estado_error_texto
    }

    /** Devuelve el color de fondo suave asociado al estado de una orden. */
    @ColorRes
    fun colorFondoEstado(estado: EstadoOrden): Int = when (estado) {
        EstadoOrden.INGRESADO -> R.color.estado_neutro_fondo
        EstadoOrden.EN_REPARACION -> R.color.estado_proceso_fondo
        EstadoOrden.LISTO -> R.color.estado_exito_fondo
        EstadoOrden.ENTREGADO -> R.color.estado_neutro_fondo
        EstadoOrden.ANULADA -> R.color.estado_error_fondo
    }

    /** Devuelve el ícono asociado al estado de una orden. */
    @DrawableRes
    fun iconoEstado(estado: EstadoOrden): Int = when (estado) {
        EstadoOrden.INGRESADO -> R.drawable.ic_clipboard_text
        EstadoOrden.EN_REPARACION -> R.drawable.ic_wrench
        EstadoOrden.LISTO -> R.drawable.ic_check_circle
        EstadoOrden.ENTREGADO -> R.drawable.ic_check_circle
        EstadoOrden.ANULADA -> R.drawable.ic_x_circle
    }
}
