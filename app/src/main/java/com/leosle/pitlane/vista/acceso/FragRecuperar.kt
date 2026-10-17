package com.leosle.pitlane.vista.acceso

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.AccFragRecuperarBinding
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.viewmodel.AuthViewModel
import com.leosle.pitlane.vista.comun.Animaciones
import com.leosle.pitlane.vista.comun.BarraSuperior
import com.leosle.pitlane.vista.comun.Campos
import com.leosle.pitlane.vista.comun.FragmentoVinculado

/**
 * Recuperación de contraseña: valida el correo, solicita el enlace y muestra siempre "Revisa tu correo",
 * exista o no la cuenta, para no revelar qué correos están registrados.
 */
class FragRecuperar : FragmentoVinculado<AccFragRecuperarBinding>(AccFragRecuperarBinding::inflate) {

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(vinculo.barraSuperior, "Recuperar contraseña", alVolver = { navegador.volver() })
        Campos.validarAlSalir(vinculo.etCorreoRecuperar, vinculo.txtErrorCorreoRecuperar) { Validador.correo(it) }
        vinculo.btnEnviarEnlace.setOnClickListener { enviarEnlace() }
        vinculo.btnVolverIngresar.setOnClickListener { navegador.volver() }
    }

    /** Valida el correo; si tiene formato válido solicita el enlace y cambia a la vista de éxito. */
    private fun enviarEnlace() {
        ocultarTeclado()
        if (!Campos.validar(vinculo.etCorreoRecuperar, vinculo.txtErrorCorreoRecuperar) { Validador.correo(it) }) return
        authViewModel.recuperarContrasena(Campos.texto(vinculo.etCorreoRecuperar)) {
            if (view == null) return@recuperarContrasena
            vinculo.llFormularioRecuperar.visibility = View.GONE
            vinculo.llExitoRecuperar.visibility = View.VISIBLE
            Animaciones.reproducir(vinculo.imgExitoRecuperar, R.drawable.avd_check_dibujado)
        }
    }
}
