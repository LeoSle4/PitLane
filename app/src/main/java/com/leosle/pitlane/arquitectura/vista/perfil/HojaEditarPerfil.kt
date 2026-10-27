package com.leosle.pitlane.arquitectura.vista.perfil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.leosle.pitlane.databinding.PerHojaEditarPerfilBinding
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.viewmodel.PerfilViewModel
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.HojaBase

/**
 * Hoja de edición del perfil: nombres, apellidos y celular vienen llenos con los datos actuales;
 * DNI y correo aparecen bloqueados. "Guardar cambios" se habilita solo cuando algún dato cambia.
 */
class HojaEditarPerfil : HojaBase() {

    private val perfilViewModel: PerfilViewModel by viewModels()
    private var vinculoInterno: PerHojaEditarPerfilBinding? = null
    private val vinculo get() = checkNotNull(vinculoInterno)
    private var original: Usuario? = null

    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val creado = PerHojaEditarPerfilBinding.inflate(inflater, contenedor, false)
        vinculoInterno = creado
        return creado.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        configurarEncabezado(vinculo.encabezado, "Editar perfil")
        val usuarioId = requireArguments().getString(ARGUMENTO_USUARIO).orEmpty()
        recolectar(perfilViewModel.perfil(usuarioId)) { usuario ->
            if (usuario != null && original == null) {
                original = usuario
                llenarFormulario(usuario)
            }
        }
    }

    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Llena los campos con los datos actuales, bloquea DNI y correo y activa la validación. */
    private fun llenarFormulario(usuario: Usuario) {
        vinculo.etNombresEditar.setText(usuario.nombres)
        vinculo.etApellidosEditar.setText(usuario.apellidos)
        vinculo.etTelefonoEditar.setText(usuario.telefono)
        vinculo.etDniBloqueado.setText(usuario.dni)
        vinculo.etCorreoBloqueado.setText(usuario.correo)
        Campos.bloquear(vinculo.etDniBloqueado)
        Campos.bloquear(vinculo.etCorreoBloqueado)

        Campos.validarAlSalir(vinculo.etNombresEditar, vinculo.txtErrorNombresEditar) { Validador.nombre(it, "tus nombres") }
        Campos.validarAlSalir(vinculo.etApellidosEditar, vinculo.txtErrorApellidosEditar) { Validador.nombre(it, "tus apellidos") }
        Campos.formatearNombreAlSalir(vinculo.etNombresEditar, vinculo.etApellidosEditar)
        Campos.validarAlSalir(vinculo.etTelefonoEditar, vinculo.txtErrorTelefonoEditar) { Validador.telefono(it) }
        Campos.alCambiarTexto(vinculo.etNombresEditar, vinculo.etApellidosEditar, vinculo.etTelefonoEditar) {
            vinculo.btnGuardarPerfil.isEnabled = hayCambios()
        }
        vinculo.btnGuardarPerfil.setOnClickListener { guardar(usuario.id) }
    }

    /** Indica si algún campo editable difiere de los datos originales. */
    private fun hayCambios(): Boolean {
        val base = original ?: return false
        return Campos.texto(vinculo.etNombresEditar) != base.nombres ||
            Campos.texto(vinculo.etApellidosEditar) != base.apellidos ||
            Campos.texto(vinculo.etTelefonoEditar) != base.telefono
    }

    /** Valida los tres campos y, si son correctos, guarda los cambios y cierra la hoja. */
    private fun guardar(usuarioId: String) {
        val nombresValido = Campos.validar(vinculo.etNombresEditar, vinculo.txtErrorNombresEditar) { Validador.nombre(it, "tus nombres") }
        val apellidosValido = Campos.validar(vinculo.etApellidosEditar, vinculo.txtErrorApellidosEditar) { Validador.nombre(it, "tus apellidos") }
        val telefonoValido = Campos.validar(vinculo.etTelefonoEditar, vinculo.txtErrorTelefonoEditar) { Validador.telefono(it) }
        if (!(nombresValido && apellidosValido && telefonoValido)) return

        perfilViewModel.actualizarPerfil(
            usuarioId,
            Campos.texto(vinculo.etNombresEditar),
            Campos.texto(vinculo.etApellidosEditar),
            Campos.texto(vinculo.etTelefonoEditar)
        ) { resultado ->
            resultado
                .alExito {
                    mostrarMensaje("Perfil actualizado")
                    dismissAllowingStateLoss()
                }
                .alError { error -> mostrarMensaje(error.mensaje) }
        }
    }

    companion object {
        private const val ARGUMENTO_USUARIO = "usuarioId"

        /** Abre la hoja de edición del perfil del usuario indicado. */
        fun mostrar(administrador: FragmentManager, usuarioId: String) {
            HojaEditarPerfil().apply {
                arguments = Bundle().apply { putString(ARGUMENTO_USUARIO, usuarioId) }
            }.show(administrador, "hoja_editar_perfil")
        }
    }
}
