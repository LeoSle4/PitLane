package com.leosle.pitlane.arquitectura.vista.comun

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.DlgModalBinding

/**
 * Diálogos modales centrados: confirmación de acciones, variante de error y confirmación con un campo de texto.
 * No se cierran al tocar fuera para obligar a elegir una opción.
 */
object DialogoModal {

    /** Pide confirmar una acción; [peligro] pinta el botón de acción en rojo oscuro. */
    fun confirmar(
        contexto: Context,
        @DrawableRes icono: Int,
        titulo: String,
        mensaje: String,
        textoAccion: String,
        peligro: Boolean = false,
        alConfirmar: () -> Unit
    ): AlertDialog = construir(contexto, icono, titulo, mensaje, textoAccion, peligro, true, null, false) { alConfirmar() }

    /** Informa que una acción no se puede realizar; solo tiene el botón "Entendido". */
    fun error(contexto: Context, mensaje: String, titulo: String = "No se pudo completar"): AlertDialog =
        construir(contexto, R.drawable.ic_warning_circle, titulo, mensaje, "Entendido", false, false, null, false) { }

    /**
     * Pide confirmar una acción que exige escribir un texto (motivo o contraseña).
     * El botón de acción queda deshabilitado mientras el campo esté vacío.
     */
    fun conCampo(
        contexto: Context,
        @DrawableRes icono: Int,
        titulo: String,
        mensaje: String,
        etiquetaCampo: String,
        esContrasena: Boolean,
        textoAccion: String,
        peligro: Boolean = true,
        alConfirmar: (String) -> Unit
    ): AlertDialog = construir(contexto, icono, titulo, mensaje, textoAccion, peligro, true, etiquetaCampo, esContrasena) { alConfirmar(it) }

    /** Arma el diálogo con el diseño común y conecta sus botones. */
    private fun construir(
        contexto: Context,
        @DrawableRes icono: Int,
        titulo: String,
        mensaje: String,
        textoAccion: String,
        peligro: Boolean,
        mostrarCancelar: Boolean,
        etiquetaCampo: String?,
        esContrasena: Boolean,
        alAceptar: (String) -> Unit
    ): AlertDialog {
        val vinculo = DlgModalBinding.inflate(LayoutInflater.from(contexto))
        vinculo.imgIconoDialogo.setImageResource(icono)
        vinculo.txtTituloDialogo.text = titulo
        vinculo.txtMensajeDialogo.text = mensaje
        vinculo.btnAccionDialogo.text = textoAccion
        vinculo.btnCancelarDialogo.visibility = if (mostrarCancelar) android.view.View.VISIBLE else android.view.View.GONE
        if (peligro) {
            vinculo.btnAccionDialogo.backgroundTintList = ContextCompat.getColorStateList(contexto, R.color.sel_fondo_boton_peligro)
        }

        if (etiquetaCampo != null) {
            vinculo.llCampoDialogo.visibility = android.view.View.VISIBLE
            vinculo.txtEtiquetaCampoDialogo.text = etiquetaCampo
            vinculo.etCampoDialogo.inputType = if (esContrasena) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            vinculo.btnAccionDialogo.isEnabled = false
            vinculo.etCampoDialogo.addTextChangedListener { texto ->
                vinculo.btnAccionDialogo.isEnabled = !texto.isNullOrBlank()
            }
        }

        val dialogo = AlertDialog.Builder(contexto, R.style.Tema_Dialogo)
            .setView(vinculo.root)
            .setCancelable(true)
            .create()
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)

        vinculo.btnCancelarDialogo.setOnClickListener { dialogo.dismiss() }
        vinculo.btnAccionDialogo.setOnClickListener {
            val texto = vinculo.etCampoDialogo.text.toString().trim()
            dialogo.dismiss()
            alAceptar(texto)
        }
        dialogo.show()
        return dialogo
    }
}
