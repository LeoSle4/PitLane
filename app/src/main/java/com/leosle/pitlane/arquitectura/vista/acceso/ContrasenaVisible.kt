package com.leosle.pitlane.arquitectura.vista.acceso

import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import com.leosle.pitlane.R

/** Conecta un botón con ícono de ojo para mostrar u ocultar el texto de un campo de contraseña. */
object ContrasenaVisible {

    /** Al tocar [boton], alterna entre texto oculto y visible en [campo] y cambia el ícono; conserva el cursor. */
    fun vincular(campo: EditText, boton: ImageButton) {
        var visible = false
        boton.setOnClickListener {
            visible = !visible
            val posicion = campo.selectionEnd
            campo.inputType = InputType.TYPE_CLASS_TEXT or
                if (visible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            campo.setSelection(posicion.coerceAtLeast(0))
            boton.setImageResource(if (visible) R.drawable.ic_eye_slash else R.drawable.ic_eye)
        }
    }
}
