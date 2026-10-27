package com.leosle.pitlane.arquitectura.vista.comun

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.leosle.pitlane.R

/** Menú emergente de acciones: tarjeta redondeada con icono y texto por fila; la opción de peligro se pinta en rojo. */
object MenuAcciones {

    /** Una fila del menú: su [icono], su [texto] y la [accion] que se ejecuta al tocarla. */
    class Opcion(
        @DrawableRes val icono: Int,
        val texto: String,
        val peligro: Boolean = false,
        val accion: () -> Unit
    )

    /** Despliega [opciones] bajo [ancla], alineado a su borde derecho, y se cierra al elegir o al tocar fuera. */
    fun mostrar(ancla: View, opciones: List<Opcion>) {
        val contexto = ancla.context
        val densidad = contexto.resources.displayMetrics.density
        val fondoSeleccionable = TypedValue().also { contexto.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }.resourceId
        val dp = { valor: Int -> (valor * densidad).toInt() }

        val contenedor = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.fondo_menu_acciones)
            setPadding(0, dp(6), 0, dp(6))
            minimumWidth = dp(200)
        }
        val ventana = PopupWindow(contenedor, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = 12 * densidad
            animationStyle = R.style.Animacion_MenuEmergente
        }

        opciones.forEach { opcion ->
            val color = ContextCompat.getColor(contexto, if (opcion.peligro) R.color.peligro else R.color.texto_primario)
            val fila = TextView(contexto).apply {
                text = opcion.texto
                setTextColor(color)
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                compoundDrawablePadding = dp(14)
                setPadding(dp(18), 0, dp(22), 0)
                minHeight = dp(48)
                ContextCompat.getDrawable(contexto, opcion.icono)?.mutate()?.let { icono ->
                    icono.setTint(color)
                    icono.setBounds(0, 0, dp(20), dp(20))
                    setCompoundDrawablesRelative(icono, null, null, null)
                }
                setBackgroundResource(fondoSeleccionable)
                setOnClickListener {
                    ventana.dismiss()
                    opcion.accion()
                }
            }
            contenedor.addView(fila, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        contenedor.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val margen = dp(12)
        val posicion = IntArray(2).also { ancla.getLocationOnScreen(it) }
        val anchoPantalla = contexto.resources.displayMetrics.widthPixels
        val x = (posicion[0] + ancla.width - contenedor.measuredWidth).coerceIn(margen, (anchoPantalla - contenedor.measuredWidth - margen).coerceAtLeast(margen))
        ventana.showAtLocation(ancla.rootView, Gravity.TOP or Gravity.START, x, posicion[1] + ancla.height + dp(4))
    }
}
