package com.leosle.pitlane.arquitectura.vista.comun

import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.leosle.pitlane.R

/**
 * Divide un formulario largo en pasos (cada uno con una o varias vistas): muestra un paso a la vez, indica "Paso n de total" con barras de avance y
 * cambia entre "Siguiente", "Atrás" y el botón final. Solo avanza si el paso actual pasa su validación.
 */
class PasosFormulario(
    private val pasos: List<List<View>>,
    private val titulos: List<String>,
    private val textoPaso: TextView,
    private val barras: List<View>,
    private val botonSiguiente: Button,
    private val botonAtras: Button,
    private val botonFinal: Button,
    private val validarPaso: (Int) -> Boolean,
    private val alCambiarDePaso: () -> Unit = {}
) {

    /** Posición del paso visible, empezando en cero. */
    var actual: Int = 0
        private set

    init {
        botonSiguiente.setOnClickListener { avanzar() }
        botonAtras.setOnClickListener { retroceder() }
        mostrar(0)
    }

    /** Pasa al paso siguiente si el actual es válido. */
    fun avanzar() {
        if (actual < pasos.lastIndex && validarPaso(actual)) mostrar(actual + 1)
    }

    /** Vuelve al paso anterior; devuelve `false` si ya estaba en el primero. */
    fun retroceder(): Boolean {
        if (actual == 0) return false
        mostrar(actual - 1)
        return true
    }

    /** Lleva al primer paso que contiene un error para que la persona lo corrija. */
    fun irAlPaso(indice: Int) = mostrar(indice.coerceIn(0, pasos.lastIndex))

    private fun mostrar(indice: Int) {
        actual = indice
        val contexto = botonSiguiente.context
        pasos.forEachIndexed { i, vistas ->
            vistas.forEach { vista ->
                if (i == indice && vista.visibility != View.VISIBLE) {
                    vista.alpha = 0f
                    vista.visibility = View.VISIBLE
                    vista.animate().alpha(1f).setDuration(180).start()
                } else if (i != indice) {
                    vista.visibility = View.GONE
                }
            }
        }
        textoPaso.text = "Paso ${indice + 1} de ${pasos.size} · ${titulos[indice]}"
        barras.forEachIndexed { i, barra ->
            val color = if (i <= indice) R.color.primario else R.color.contorno
            barra.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, color))
        }
        val ultimo = indice == pasos.lastIndex
        botonSiguiente.visibility = if (ultimo) View.GONE else View.VISIBLE
        botonFinal.visibility = if (ultimo) View.VISIBLE else View.GONE
        botonAtras.visibility = if (indice == 0) View.GONE else View.VISIBLE
        alCambiarDePaso()
    }
}
