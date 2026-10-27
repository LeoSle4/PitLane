package com.leosle.pitlane.arquitectura.vista.clientes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.leosle.pitlane.databinding.JcHojaEditarClienteBinding
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.viewmodel.ClienteViewModel
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.HojaBase

/**
 * Hoja de edición de un cliente: nombres, apellidos y teléfono son editables y el DNI queda bloqueado.
 * El botón Guardar cambios se habilita solo cuando algún valor editable difiere del original.
 */
class HojaEditarCliente : HojaBase() {

    private var vinculoInterno: JcHojaEditarClienteBinding? = null
    private val modelo: ClienteViewModel by viewModels()

    /** Infla la hoja, rellena los datos recibidos, bloquea el DNI y conecta validaciones y guardado. */
    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = JcHojaEditarClienteBinding.inflate(inflater, contenedor, false)
        vinculoInterno = vinculo
        val datos = requireArguments()
        configurarEncabezado(vinculo.encabezado, "Editar cliente")

        val nombres = datos.getString(CLAVE_NOMBRES).orEmpty()
        val apellidos = datos.getString(CLAVE_APELLIDOS).orEmpty()
        val telefono = datos.getString(CLAVE_TELEFONO).orEmpty()
        vinculo.etNombres.setText(nombres)
        vinculo.etApellidos.setText(apellidos)
        vinculo.etTelefono.setText(telefono)
        vinculo.etDni.setText(datos.getString(CLAVE_DNI).orEmpty())
        Campos.bloquear(vinculo.etDni)

        Campos.validarAlSalir(vinculo.etNombres, vinculo.txtErrorNombres) { Validador.nombre(it, "los nombres") }
        Campos.validarAlSalir(vinculo.etApellidos, vinculo.txtErrorApellidos) { Validador.nombre(it, "los apellidos") }
        Campos.formatearNombreAlSalir(vinculo.etNombres, vinculo.etApellidos)
        Campos.validarAlSalir(vinculo.etTelefono, vinculo.txtErrorTelefono) { Validador.telefono(it) }

        Campos.alCambiarTexto(vinculo.etNombres, vinculo.etApellidos, vinculo.etTelefono) {
            vinculo.btnGuardar.isEnabled = Campos.texto(vinculo.etNombres) != nombres ||
                Campos.texto(vinculo.etApellidos) != apellidos ||
                Campos.texto(vinculo.etTelefono) != telefono
        }
        vinculo.btnGuardar.setOnClickListener { guardar(vinculo, datos.getString(CLAVE_ID).orEmpty()) }
        return vinculo.root
    }

    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Valida los campos editables y guarda los cambios del cliente; al terminar avisa y cierra la hoja. */
    private fun guardar(vinculo: JcHojaEditarClienteBinding, clienteId: String) {
        val validos = listOf(
            Campos.validar(vinculo.etNombres, vinculo.txtErrorNombres) { Validador.nombre(it, "los nombres") },
            Campos.validar(vinculo.etApellidos, vinculo.txtErrorApellidos) { Validador.nombre(it, "los apellidos") },
            Campos.validar(vinculo.etTelefono, vinculo.txtErrorTelefono) { Validador.telefono(it) }
        )
        if (validos.any { !it }) return
        vinculo.btnGuardar.isEnabled = false
        modelo.editarCliente(
            clienteId,
            Campos.texto(vinculo.etNombres),
            Campos.texto(vinculo.etApellidos),
            Campos.texto(vinculo.etTelefono)
        ) { resultado ->
            resultado
                .alExito {
                    mostrarMensaje("Cliente actualizado")
                    dismiss()
                }
                .alError {
                    vinculo.btnGuardar.isEnabled = true
                    mostrarMensaje(it.mensaje)
                }
        }
    }

    companion object {
        private const val CLAVE_ID = "id"
        private const val CLAVE_NOMBRES = "nombres"
        private const val CLAVE_APELLIDOS = "apellidos"
        private const val CLAVE_DNI = "dni"
        private const val CLAVE_TELEFONO = "telefono"

        /** Abre la hoja con los datos actuales de [cliente] para editarlos. */
        fun mostrar(administrador: FragmentManager, cliente: Usuario) {
            HojaEditarCliente().apply {
                arguments = Bundle().apply {
                    putString(CLAVE_ID, cliente.id)
                    putString(CLAVE_NOMBRES, cliente.nombres)
                    putString(CLAVE_APELLIDOS, cliente.apellidos)
                    putString(CLAVE_DNI, cliente.dni)
                    putString(CLAVE_TELEFONO, cliente.telefono)
                }
            }.show(administrador, "hoja_editar_cliente")
        }
    }
}
