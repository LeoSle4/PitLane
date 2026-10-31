package com.leosle.pitlane.arquitectura.vista.clientes

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JcFragRegistrarClienteBinding
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.viewmodel.ClienteViewModel
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.DialogoModal
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.arquitectura.vista.comun.BotonCarga

/**
 * Formulario para registrar un cliente sin cuenta: nombres, apellidos, DNI, teléfono y correo opcional.
 * Si el DNI ya existe muestra el error y ofrece abrir al cliente existente; si todo sale bien abre su detalle.
 */
class FragRegistrarCliente : FragmentoVinculado<JcFragRegistrarClienteBinding>(JcFragRegistrarClienteBinding::inflate) {

    private val modelo: ClienteViewModel by viewModels()

    /** Configura la barra, el DNI inicial recibido, las validaciones por campo y el botón de registro. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(vinculo.barraSuperior, "Registrar cliente", alVolver = { navegador.volver() })
        if (estado == null) vinculo.etDni.setText(arguments?.getString(CLAVE_DNI).orEmpty())

        Campos.validarAlSalir(vinculo.etNombres, vinculo.txtErrorNombres) { Validador.nombre(it, "los nombres") }
        Campos.validarAlSalir(vinculo.etApellidos, vinculo.txtErrorApellidos) { Validador.nombre(it, "los apellidos") }
        Campos.formatearNombreAlSalir(vinculo.etNombres, vinculo.etApellidos)
        Campos.validarAlSalir(vinculo.etDni, vinculo.txtErrorDni) { Validador.dni(it) }
        Campos.validarAlSalir(vinculo.etTelefono, vinculo.txtErrorTelefono) { Validador.telefono(it) }
        Campos.validarAlSalir(vinculo.etCorreo, vinculo.txtErrorCorreo) { Validador.correoOpcional(it) }
        vinculo.btnRegistrarCliente.setOnClickListener { registrar() }
    }

    /** Valida los campos y registra al cliente; abre su detalle al terminar o gestiona el error devuelto. */
    private fun registrar() {
        val validos = listOf(
            Campos.validar(vinculo.etNombres, vinculo.txtErrorNombres) { Validador.nombre(it, "los nombres") },
            Campos.validar(vinculo.etApellidos, vinculo.txtErrorApellidos) { Validador.nombre(it, "los apellidos") },
            Campos.validar(vinculo.etDni, vinculo.txtErrorDni) { Validador.dni(it) },
            Campos.validar(vinculo.etTelefono, vinculo.txtErrorTelefono) { Validador.telefono(it) },
            Campos.validar(vinculo.etCorreo, vinculo.txtErrorCorreo) { Validador.correoOpcional(it) }
        )
        if (validos.any { !it }) return

        ocultarTeclado()
        BotonCarga.iniciar(vinculo.btnRegistrarCliente)
        modelo.registrarCliente(
            Campos.texto(vinculo.etNombres),
            Campos.texto(vinculo.etApellidos),
            Campos.texto(vinculo.etDni),
            Campos.texto(vinculo.etTelefono),
            Campos.texto(vinculo.etCorreo)
        ) { resultado ->
            resultado
                .alExito {
                    mostrarMensaje("Cliente registrado")
                    abrirDetalle(it.id)
                }
                .alError { error ->
                    BotonCarga.terminar(vinculo.btnRegistrarCliente)
                    val existente = error.referencia
                    if (existente != null) {
                        Campos.mostrarError(vinculo.etDni, vinculo.txtErrorDni, error.mensaje)
                        ofrecerAbrirExistente(error.mensaje, existente)
                    } else {
                        Campos.mostrarError(vinculo.etCorreo, vinculo.txtErrorCorreo, error.mensaje)
                        mostrarMensaje(error.mensaje)
                    }
                }
        }
    }

    /** Pregunta si se quiere abrir el detalle del cliente que ya tiene ese DNI. */
    private fun ofrecerAbrirExistente(mensaje: String, clienteId: String) {
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_warning_circle,
            "Cliente ya registrado",
            "$mensaje. ¿Quieres abrir su detalle?",
            "Ver cliente"
        ) { abrirDetalle(clienteId) }
    }

    /** Reemplaza este formulario por el detalle del cliente: regresa y abre la ficha. */
    private fun abrirDetalle(clienteId: String) {
        navegador.volver()
        navegador.irA(FragDetalleCliente.nuevo(clienteId))
    }

    companion object {
        private const val CLAVE_DNI = "dni"

        /** Crea el formulario; [dniInicial] rellena el campo DNI cuando ya se conoce. */
        fun nuevo(dniInicial: String? = null): FragRegistrarCliente =
            FragRegistrarCliente().apply {
                arguments = Bundle().apply { putString(CLAVE_DNI, dniInicial) }
            }
    }
}
