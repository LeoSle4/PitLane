package com.leosle.pitlane.arquitectura.vista.acceso

import android.content.Context
import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.vista.comun.DialogoModal

/** Diálogo de confirmación para cerrar la sesión del usuario. */
object DialogoCierreSesion {

    /** Pide confirmación y, si el usuario acepta, llama a [alConfirmar]. */
    fun mostrar(contexto: Context, alConfirmar: () -> Unit) {
        DialogoModal.confirmar(
            contexto = contexto,
            icono = R.drawable.ic_sign_out,
            titulo = "¿Cerrar sesión?",
            mensaje = "Tendrás que ingresar de nuevo con tu correo y contraseña.",
            textoAccion = "Cerrar sesión"
        ) { alConfirmar() }
    }
}
