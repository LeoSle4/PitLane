package com.leosle.pitlane.vista.acceso

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.leosle.pitlane.databinding.AccFragRegistroBinding
import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.viewmodel.AuthViewModel
import com.leosle.pitlane.vista.comun.BarraSuperior
import com.leosle.pitlane.vista.comun.Campos
import com.leosle.pitlane.vista.comun.FragmentoVinculado
import com.leosle.pitlane.vista.comun.PasosFormulario

/**
 * Registro de una cuenta de cliente en dos pasos (datos personales y cuenta): valida cada campo al salir de él,
 * no deja avanzar con errores, exige aceptar los términos y, si todo es correcto, crea la cuenta y entra a la aplicación.
 */
class FragRegistro : FragmentoVinculado<AccFragRegistroBinding>(AccFragRegistroBinding::inflate), ContenidoBajoBarras {

    private val authViewModel: AuthViewModel by activityViewModels()

    /** Campo del formulario con su etiqueta de error y la regla que lo valida. */
    private class ReglaCampo(val campo: EditText, val error: TextView, val regla: () -> String?)

    private lateinit var reglas: List<ReglaCampo>
    private lateinit var pasos: PasosFormulario

    private companion object {
        /** Cantidad de campos que pertenecen al primer paso (nombres, apellidos, DNI y celular). */
        const val PASO_DATOS_CAMPOS = 4
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        ajustarABarrasDelSistema()
        BarraSuperior.configurar(vinculo.barraSuperior, "Crear cuenta", alVolver = {
            if (!pasos.retroceder()) navegador.volver()
        })
        ContrasenaVisible.vincular(vinculo.etContrasenaRegistro, vinculo.btnVerContrasenaRegistro)
        ContrasenaVisible.vincular(vinculo.etConfirmacion, vinculo.btnVerConfirmacion)

        reglas = listOf(
            ReglaCampo(vinculo.etNombres, vinculo.txtErrorNombres) { Validador.nombre(Campos.texto(vinculo.etNombres), "tus nombres") },
            ReglaCampo(vinculo.etApellidos, vinculo.txtErrorApellidos) { Validador.nombre(Campos.texto(vinculo.etApellidos), "tus apellidos") },
            ReglaCampo(vinculo.etDni, vinculo.txtErrorDni) { Validador.dni(Campos.texto(vinculo.etDni)) },
            ReglaCampo(vinculo.etTelefono, vinculo.txtErrorTelefono) { Validador.telefono(Campos.texto(vinculo.etTelefono)) },
            ReglaCampo(vinculo.etCorreoRegistro, vinculo.txtErrorCorreoRegistro) { Validador.correo(Campos.texto(vinculo.etCorreoRegistro)) },
            ReglaCampo(vinculo.etContrasenaRegistro, vinculo.txtErrorContrasenaRegistro) {
                Validador.contrasena(vinculo.etContrasenaRegistro.text.toString())
            },
            ReglaCampo(vinculo.etConfirmacion, vinculo.txtErrorConfirmacion) {
                Validador.confirmacion(
                    vinculo.etContrasenaRegistro.text.toString(),
                    vinculo.etConfirmacion.text.toString()
                )
            }
        )
        reglas.forEach { r -> Campos.validarAlSalir(r.campo, r.error) { r.regla() } }
        Campos.formatearNombreAlSalir(vinculo.etNombres, vinculo.etApellidos)
        pasos = PasosFormulario(
            pasos = listOf(listOf(vinculo.llPasoDatos), listOf(vinculo.llPasoCuenta)),
            titulos = listOf("Tus datos", "Tu cuenta"),
            textoPaso = vinculo.txtPasoRegistro,
            barras = listOf(vinculo.viewBarraPasoUno, vinculo.viewBarraPasoDos),
            botonSiguiente = vinculo.btnSiguienteRegistro,
            botonAtras = vinculo.btnAtrasRegistro,
            botonFinal = vinculo.btnRegistrarCuenta,
            validarPaso = { validarCampos(0, PASO_DATOS_CAMPOS) },
            alCambiarDePaso = { ocultarTeclado() }
        )
        vinculo.btnRegistrarCuenta.setOnClickListener { registrar() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!pasos.retroceder()) {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /** Sube el final de la hoja sobre la barra de navegación y pone los íconos de la barra de estado según el cielo. */
    private fun ajustarABarrasDelSistema() {
        val densidad = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(vinculo.root) { _, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vinculo.llHojaRegistro.setPadding(0, (8 * densidad).toInt(), 0, barras.bottom)
            insets
        }
        EscenaViaje.ajustarBarraDeEstado(requireActivity(), vinculo.root, EscenaViaje.esNocheAhora())
    }

    /** Valida solo los campos del paso indicado, mostrando el error de cada uno; devuelve `true` si todos son correctos. */
    private fun validarCampos(desde: Int, hasta: Int): Boolean {
        var valido = true
        reglas.subList(desde, hasta).forEach { r ->
            val mensaje = r.regla()
            Campos.mostrarError(r.campo, r.error, mensaje)
            if (mensaje != null) valido = false
        }
        return valido
    }

    /** Devuelve la barra de estado a los íconos del tema al salir del registro. */
    override fun onDestroyView() {
        val oscuro = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !oscuro }
        super.onDestroyView()
    }

    /** Valida todos los campos y los términos; si hay errores los muestra y si no envía el registro. */
    private fun registrar() {
        ocultarTeclado()
        var valido = validarCampos(0, reglas.size)
        if (!valido && !validarCampos(0, PASO_DATOS_CAMPOS)) pasos.irAlPaso(0)
        if (!vinculo.chkTerminos.isChecked) {
            mostrarMensaje("Acepta los términos para continuar")
            valido = false
        }
        if (!valido) return

        val datos = DatosRegistroCliente(
            nombres = Campos.texto(vinculo.etNombres),
            apellidos = Campos.texto(vinculo.etApellidos),
            dni = Campos.texto(vinculo.etDni),
            telefono = Campos.texto(vinculo.etTelefono),
            correo = Campos.texto(vinculo.etCorreoRegistro),
            contrasena = vinculo.etContrasenaRegistro.text.toString()
        )
        vinculo.btnRegistrarCuenta.isEnabled = false
        authViewModel.registrarCliente(datos) { resultado ->
            resultado
                .alExito {
                    mostrarMensaje("Cuenta creada. ¡Bienvenido!")
                    navegador.abrirInicio()
                }
                .alError { error ->
                    if (view == null) return@alError
                    vinculo.btnRegistrarCuenta.isEnabled = true
                    mostrarErrorDelServidor(error.mensaje)
                }
        }
    }

    /** Coloca el error devuelto por el registro bajo el campo que corresponde: correo, DNI o un aviso general. */
    private fun mostrarErrorDelServidor(mensaje: String) {
        when {
            mensaje.contains("correo", ignoreCase = true) ->
                Campos.mostrarError(vinculo.etCorreoRegistro, vinculo.txtErrorCorreoRegistro, mensaje)
            mensaje.contains("DNI", ignoreCase = true) -> {
                pasos.irAlPaso(0)
                Campos.mostrarError(vinculo.etDni, vinculo.txtErrorDni, mensaje)
            }
            else -> mostrarMensaje(mensaje)
        }
    }
}
