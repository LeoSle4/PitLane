package com.leosle.pitlane.arquitectura.vista.acceso

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.fragment.app.activityViewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.AccFragRecuperarBinding
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.viewmodel.AuthViewModel
import com.leosle.pitlane.arquitectura.vista.comun.Animaciones
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.BotonCarga
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado

/**
 * Recuperación de contraseña en dos pasos: primero se pide un código de 6 dígitos para el correo (la respuesta es
 * la misma exista o no la cuenta) y luego, con ese código, se escribe la contraseña nueva dentro de la app.
 * El código se puede volver a pedir pasado un minuto.
 */
class FragRecuperar : FragmentoVinculado<AccFragRecuperarBinding>(AccFragRecuperarBinding::inflate) {

    private val authViewModel: AuthViewModel by activityViewModels()

    /** Correo al que se envió el código; se usa al cambiar la contraseña y al reenviar el código. */
    private var correo = ""
    private var cuentaRegresiva: CountDownTimer? = null

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(vinculo.barraSuperior, "Recuperar contraseña", alVolver = { volver() })
        Campos.validarAlSalir(vinculo.etCorreoRecuperar, vinculo.txtErrorCorreoRecuperar) { Validador.correo(it) }
        Campos.validarAlSalir(vinculo.etCodigo, vinculo.txtErrorCodigo) { Validador.codigo(it) }
        Campos.validarAlSalir(vinculo.etNuevaContrasena, vinculo.txtErrorNuevaContrasena) { Validador.contrasena(it) }
        Campos.validarAlSalir(vinculo.etConfirmarNueva, vinculo.txtErrorConfirmarNueva) {
            Validador.confirmacion(vinculo.etNuevaContrasena.text.toString(), it)
        }
        ContrasenaVisible.vincular(vinculo.etNuevaContrasena, vinculo.btnVerNuevaContrasena)
        ContrasenaVisible.vincular(vinculo.etConfirmarNueva, vinculo.btnVerConfirmarNueva)

        vinculo.btnEnviarCodigo.setOnClickListener { pedirCodigo() }
        vinculo.btnReenviarCodigo.setOnClickListener { reenviarCodigo() }
        vinculo.btnCambiarContrasena.setOnClickListener { cambiarContrasena() }
        vinculo.btnVolverIngresar.setOnClickListener { navegador.volver() }
    }

    override fun onDestroyView() {
        cuentaRegresiva?.cancel()
        cuentaRegresiva = null
        super.onDestroyView()
    }

    /** Desde el paso del código vuelve al del correo, por si se escribió mal; desde el primero sale de la pantalla. */
    private fun volver() {
        if (vinculo.svCodigoRecuperar.visibility == View.VISIBLE) {
            cuentaRegresiva?.cancel()
            vinculo.svCodigoRecuperar.visibility = View.GONE
            vinculo.llFormularioRecuperar.visibility = View.VISIBLE
        } else {
            navegador.volver()
        }
    }

    /** Valida el correo y pide el código; si el servidor lo acepta pasa al paso del código. */
    private fun pedirCodigo() {
        ocultarTeclado()
        if (!Campos.validar(vinculo.etCorreoRecuperar, vinculo.txtErrorCorreoRecuperar) { Validador.correo(it) }) return
        val escrito = Campos.texto(vinculo.etCorreoRecuperar)
        BotonCarga.iniciar(vinculo.btnEnviarCodigo)
        authViewModel.solicitarCodigoDeRestablecimiento(escrito) { resultado ->
            if (view == null) return@solicitarCodigoDeRestablecimiento
            BotonCarga.terminar(vinculo.btnEnviarCodigo)
            when (resultado) {
                is Resultado.Exito -> {
                    correo = escrito
                    mostrarPasoCodigo()
                }
                is Resultado.Error ->
                    Campos.mostrarError(vinculo.etCorreoRecuperar, vinculo.txtErrorCorreoRecuperar, resultado.mensaje)
            }
        }
    }

    private fun mostrarPasoCodigo() {
        vinculo.llFormularioRecuperar.visibility = View.GONE
        vinculo.svCodigoRecuperar.visibility = View.VISIBLE
        vinculo.txtIntroCodigo.text = getString(R.string.acc_codigo_intro, correo)
        vinculo.etCodigo.requestFocus()
        iniciarCuentaRegresiva()
    }

    /** El botón de reenviar se habilita recién cuando el servidor permite pedir otro código. */
    private fun iniciarCuentaRegresiva() {
        cuentaRegresiva?.cancel()
        vinculo.btnReenviarCodigo.isEnabled = false
        cuentaRegresiva = object : CountDownTimer(ESPERA_REENVIO_MS, 1_000) {
            override fun onTick(restante: Long) {
                vinculo.btnReenviarCodigo.text = getString(R.string.acc_reenviar_codigo_en, ((restante + 999) / 1_000).toInt())
            }

            override fun onFinish() {
                vinculo.btnReenviarCodigo.text = getString(R.string.acc_reenviar_codigo)
                vinculo.btnReenviarCodigo.isEnabled = true
            }
        }.start()
    }

    private fun reenviarCodigo() {
        BotonCarga.iniciar(vinculo.btnReenviarCodigo)
        authViewModel.solicitarCodigoDeRestablecimiento(correo) { resultado ->
            if (view == null) return@solicitarCodigoDeRestablecimiento
            BotonCarga.terminar(vinculo.btnReenviarCodigo)
            when (resultado) {
                is Resultado.Exito -> {
                    mostrarMensaje(getString(R.string.acc_codigo_reenviado))
                    iniciarCuentaRegresiva()
                }
                is Resultado.Error -> mostrarMensaje(resultado.mensaje, duracionLarga = true)
            }
        }
    }

    /** Valida código y contraseña nueva; si el servidor los acepta muestra que la contraseña quedó actualizada. */
    private fun cambiarContrasena() {
        ocultarTeclado()
        val validos = listOf(
            Campos.validar(vinculo.etCodigo, vinculo.txtErrorCodigo) { Validador.codigo(it) },
            Campos.validar(vinculo.etNuevaContrasena, vinculo.txtErrorNuevaContrasena) { Validador.contrasena(it) },
            Campos.validar(vinculo.etConfirmarNueva, vinculo.txtErrorConfirmarNueva) {
                Validador.confirmacion(vinculo.etNuevaContrasena.text.toString(), it)
            }
        )
        if (validos.any { !it }) return
        BotonCarga.iniciar(vinculo.btnCambiarContrasena)
        authViewModel.restablecerContrasena(
            correo,
            Campos.texto(vinculo.etCodigo),
            vinculo.etNuevaContrasena.text.toString()
        ) { resultado ->
            if (view == null) return@restablecerContrasena
            when (resultado) {
                is Resultado.Exito -> mostrarExito()
                is Resultado.Error -> {
                    BotonCarga.terminar(vinculo.btnCambiarContrasena)
                    if (resultado.mensaje.contains("código", ignoreCase = true)) {
                        Campos.mostrarError(vinculo.etCodigo, vinculo.txtErrorCodigo, resultado.mensaje)
                    } else {
                        mostrarMensaje(resultado.mensaje, duracionLarga = true)
                    }
                }
            }
        }
    }

    private fun mostrarExito() {
        cuentaRegresiva?.cancel()
        vinculo.svCodigoRecuperar.visibility = View.GONE
        vinculo.llExitoRecuperar.visibility = View.VISIBLE
        Animaciones.reproducir(vinculo.imgExitoRecuperar, R.drawable.avd_check_dibujado)
    }

    private companion object {
        /** Igual al tiempo mínimo entre pedidos que exige el servidor. */
        const val ESPERA_REENVIO_MS = 60_000L
    }
}
