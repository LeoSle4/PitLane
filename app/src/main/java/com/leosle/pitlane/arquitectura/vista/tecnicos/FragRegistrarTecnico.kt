package com.leosle.pitlane.arquitectura.vista.tecnicos

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JcFragRegistrarTecnicoBinding
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.viewmodel.TecnicoViewModel
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado

/**
 * Formulario para crear la cuenta de un técnico: nombres, apellidos, DNI, teléfono, correo y contraseña inicial.
 * Valida cada campo al salir de él y al pulsar Registrar; al terminar avisa con un Toast y regresa a la lista.
 */
class FragRegistrarTecnico : FragmentoVinculado<JcFragRegistrarTecnicoBinding>(JcFragRegistrarTecnicoBinding::inflate) {

    private val modelo: TecnicoViewModel by viewModels()
    private var contrasenaVisible = false

    /** Configura la barra, las validaciones por campo, el ojo de la contraseña y el botón de registro. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(vinculo.barraSuperior, "Registrar técnico", alVolver = { navegador.volver() })

        Campos.validarAlSalir(vinculo.etNombres, vinculo.txtErrorNombres) { Validador.nombre(it, "los nombres") }
        Campos.validarAlSalir(vinculo.etApellidos, vinculo.txtErrorApellidos) { Validador.nombre(it, "los apellidos") }
        Campos.formatearNombreAlSalir(vinculo.etNombres, vinculo.etApellidos)
        Campos.validarAlSalir(vinculo.etDni, vinculo.txtErrorDni) { Validador.dni(it) }
        Campos.validarAlSalir(vinculo.etTelefono, vinculo.txtErrorTelefono) { Validador.telefono(it) }
        Campos.validarAlSalir(vinculo.etCorreo, vinculo.txtErrorCorreo) { Validador.correo(it) }
        Campos.validarAlSalir(vinculo.etContrasena, vinculo.txtErrorContrasena) { Validador.contrasena(it) }

        vinculo.btnVerContrasena.setOnClickListener { alternarContrasena() }
        vinculo.btnRegistrarTecnico.setOnClickListener { registrar() }
    }

    /** Muestra u oculta la contraseña escrita y cambia el ícono del ojo, conservando la posición del cursor. */
    private fun alternarContrasena() {
        contrasenaVisible = !contrasenaVisible
        val campo = vinculo.etContrasena
        val cursor = campo.selectionEnd
        campo.transformationMethod =
            if (contrasenaVisible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
        campo.setSelection(cursor.coerceAtLeast(0))
        vinculo.btnVerContrasena.setImageResource(if (contrasenaVisible) R.drawable.ic_eye_slash else R.drawable.ic_eye)
        vinculo.btnVerContrasena.contentDescription = if (contrasenaVisible) "Ocultar contraseña" else "Mostrar contraseña"
    }

    /** Valida todos los campos y, si son correctos, crea la cuenta del técnico. */
    private fun registrar() {
        val validos = listOf(
            Campos.validar(vinculo.etNombres, vinculo.txtErrorNombres) { Validador.nombre(it, "los nombres") },
            Campos.validar(vinculo.etApellidos, vinculo.txtErrorApellidos) { Validador.nombre(it, "los apellidos") },
            Campos.validar(vinculo.etDni, vinculo.txtErrorDni) { Validador.dni(it) },
            Campos.validar(vinculo.etTelefono, vinculo.txtErrorTelefono) { Validador.telefono(it) },
            Campos.validar(vinculo.etCorreo, vinculo.txtErrorCorreo) { Validador.correo(it) },
            Campos.validar(vinculo.etContrasena, vinculo.txtErrorContrasena) { Validador.contrasena(it) }
        )
        if (validos.any { !it }) return

        ocultarTeclado()
        vinculo.btnRegistrarTecnico.isEnabled = false
        modelo.registrarTecnico(
            Campos.texto(vinculo.etNombres),
            Campos.texto(vinculo.etApellidos),
            Campos.texto(vinculo.etDni),
            Campos.texto(vinculo.etTelefono),
            Campos.texto(vinculo.etCorreo),
            vinculo.etContrasena.text.toString()
        ) { resultado ->
            resultado
                .alExito {
                    mostrarMensaje("Técnico registrado")
                    navegador.volver()
                }
                .alError {
                    vinculo.btnRegistrarTecnico.isEnabled = true
                    if (it.mensaje.contains("correo", ignoreCase = true)) {
                        Campos.mostrarError(vinculo.etCorreo, vinculo.txtErrorCorreo, it.mensaje)
                    } else {
                        Campos.mostrarError(vinculo.etDni, vinculo.txtErrorDni, it.mensaje)
                    }
                    mostrarMensaje(it.mensaje)
                }
        }
    }
}
