package com.leosle.pitlane.vista.comun

import android.text.InputFilter
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.core.widget.addTextChangedListener
import com.leosle.pitlane.R
import com.leosle.pitlane.util.Textos

/** Utilidades para los campos de formulario formados por un EditText y una etiqueta de error debajo. */
object Campos {

    /** Devuelve el texto escrito sin espacios en los extremos. */
    fun texto(campo: EditText): String = campo.text.toString().trim()

    /** Muestra [mensaje] bajo el campo y lo marca en rojo; con nulo quita el error. */
    fun mostrarError(campo: EditText, etiquetaError: TextView, mensaje: String?) {
        if (mensaje == null) {
            etiquetaError.visibility = View.GONE
            campo.setBackgroundResource(R.drawable.fondo_campo)
        } else {
            etiquetaError.text = mensaje
            etiquetaError.visibility = View.VISIBLE
            campo.setBackgroundResource(R.drawable.fondo_campo_error)
        }
    }

    /** Aplica [regla] al texto del campo, muestra el error resultante y devuelve verdadero si el valor es válido. */
    fun validar(campo: EditText, etiquetaError: TextView, regla: (String) -> String?): Boolean {
        val error = regla(texto(campo))
        mostrarError(campo, etiquetaError, error)
        return error == null
    }

    /** Valida el campo cuando pierde el foco y limpia el error en cuanto el usuario vuelve a escribir. */
    fun validarAlSalir(campo: EditText, etiquetaError: TextView, regla: (String) -> String?) {
        campo.setOnFocusChangeListener { _, tieneFoco ->
            if (!tieneFoco) validar(campo, etiquetaError, regla)
        }
        campo.addTextChangedListener {
            if (etiquetaError.visibility == View.VISIBLE) mostrarError(campo, etiquetaError, null)
        }
    }

    /** Al salir de cada campo corrige su formato de nombre propio, por ejemplo "leopoldo CHOque" pasa a "Leopoldo Choque". */
    fun formatearNombreAlSalir(vararg campos: EditText) {
        campos.forEach { campo ->
            val anterior = campo.onFocusChangeListener
            campo.setOnFocusChangeListener { vista, tieneFoco ->
                if (!tieneFoco) {
                    val actual = campo.text.toString()
                    val formateado = Textos.nombrePropio(actual)
                    if (formateado != actual) campo.setText(formateado)
                }
                anterior?.onFocusChange(vista, tieneFoco)
            }
        }
    }

    /** Deja el campo de solo lectura con fondo gris y un candado a la derecha. */
    fun bloquear(campo: EditText) {
        campo.isEnabled = false
        campo.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_lock_simple, 0)
        TextViewCompat.setCompoundDrawableTintList(
            campo,
            ContextCompat.getColorStateList(campo.context, R.color.texto_secundario)
        )
        campo.setTextColor(ContextCompat.getColor(campo.context, R.color.texto_secundario))
    }

    /** Llama a [accion] cada vez que cambia el texto de cualquiera de los [campos]. */
    fun alCambiarTexto(vararg campos: EditText, accion: () -> Unit) {
        campos.forEach { campo -> campo.addTextChangedListener { accion() } }
    }

    /** Convierte automáticamente a mayúsculas lo que se escribe en el campo. */
    fun mayusculas(campo: EditText) {
        campo.filters = campo.filters + InputFilter.AllCaps()
    }
}

/** Utilidades para los combos desplegables (`Spinner`). */
object Combos {

    /** Llena el combo con [opciones] usando el estilo común. */
    fun configurar(combo: Spinner, opciones: List<String>) {
        val adaptador = ArrayAdapter(combo.context, R.layout.item_combo, opciones)
        adaptador.setDropDownViewResource(R.layout.item_combo_desplegable)
        combo.adapter = adaptador
    }

    /** Devuelve la posición elegida o -1 si el combo está vacío. */
    fun posicion(combo: Spinner): Int = if (combo.adapter == null || combo.adapter.count == 0) -1 else combo.selectedItemPosition
}

/** Utilidades para grupos de opciones (`RadioGroup` con `RadioButton`). */
object Opciones {

    /** Devuelve el índice de la opción marcada o -1 si no hay ninguna. */
    fun indiceMarcado(grupo: RadioGroup): Int {
        val marcado = grupo.checkedRadioButtonId
        if (marcado == -1) return -1
        return (0 until grupo.childCount).indexOfFirst { grupo.getChildAt(it).id == marcado }
    }

    /** Marca la opción del índice indicado. */
    fun marcar(grupo: RadioGroup, indice: Int) {
        if (indice in 0 until grupo.childCount) grupo.check(grupo.getChildAt(indice).id)
    }
}
