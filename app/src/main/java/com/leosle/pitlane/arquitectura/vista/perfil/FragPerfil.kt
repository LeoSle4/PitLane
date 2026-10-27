package com.leosle.pitlane.arquitectura.vista.perfil

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.PerFragPerfilBinding
import com.leosle.pitlane.databinding.PerItemDatoBinding
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.arquitectura.viewmodel.PerfilViewModel
import com.leosle.pitlane.arquitectura.vista.acceso.DialogoCierreSesion
import com.leosle.pitlane.arquitectura.vista.comun.DialogoModal
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.arquitectura.vista.comun.poblar

/**
 * Pestaña de perfil: muestra los datos personales del usuario, permite elegir la apariencia, editar el perfil,
 * cerrar la sesión y, para los clientes, eliminar la cuenta.
 */
class FragPerfil : FragmentoVinculado<PerFragPerfilBinding>(PerFragPerfilBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val perfilViewModel: PerfilViewModel by viewModels()

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        val usuarioId = usuarioActual.id
        val esCliente = usuarioActual.rol == Rol.CLIENTE
        vinculo.btnEliminarCuenta.visibility = if (esCliente) View.VISIBLE else View.GONE

        configurarApariencia()
        vinculo.btnEditarPerfil.setOnClickListener { HojaEditarPerfil.mostrar(parentFragmentManager, usuarioId) }
        vinculo.btnCerrarSesion.setOnClickListener { confirmarCierreDeSesion() }
        vinculo.btnEliminarCuenta.setOnClickListener { pedirEliminacionDeCuenta(usuarioId) }

        recolectar(perfilViewModel.perfil(usuarioId)) { usuario -> if (usuario != null) mostrarPerfil(usuario) }
    }

    /** Dibuja el avatar con iniciales, el nombre, el correo y las filas de DNI y celular del usuario. */
    private fun mostrarPerfil(usuario: Usuario) {
        vinculo.txtAvatar.text = usuario.iniciales
        vinculo.txtNombrePerfil.text = usuario.nombreCompleto
        vinculo.txtCorreoPerfil.text = usuario.correo
        val filas = listOf(
            Triple("DNI", usuario.dni, R.drawable.ic_identification_card),
            Triple("Celular", usuario.telefono, R.drawable.ic_phone)
        )
        vinculo.llDatosPerfil.poblar(filas, R.layout.per_item_dato) { fila, dato ->
            val vistaFila = PerItemDatoBinding.bind(fila)
            vistaFila.txtEtiquetaDato.text = dato.first
            vistaFila.txtValorDato.text = dato.second
            vistaFila.imgIconoDato.setImageResource(dato.third)
        }
    }

    /** Marca la opción de apariencia guardada y aplica el nuevo modo cuando el usuario elige otro. */
    private fun configurarApariencia() {
        val actual = PreferenciasTema.leer(requireContext())
        vinculo.rgTema.check(
            when (actual) {
                ModoTema.CLARO -> R.id.rbTemaClaro
                ModoTema.OSCURO -> R.id.rbTemaOscuro
                ModoTema.SISTEMA -> R.id.rbTemaSistema
            }
        )
        vinculo.rgTema.setOnCheckedChangeListener { _, identificador ->
            val elegido = when (identificador) {
                R.id.rbTemaClaro -> ModoTema.CLARO
                R.id.rbTemaOscuro -> ModoTema.OSCURO
                else -> ModoTema.SISTEMA
            }
            if (elegido != PreferenciasTema.leer(requireContext())) {
                PreferenciasTema.guardarYAplicar(requireContext(), elegido)
            }
        }
    }

    /** Pide confirmación antes de cerrar la sesión. */
    private fun confirmarCierreDeSesion() {
        DialogoCierreSesion.mostrar(requireContext()) { navegador.cerrarSesion() }
    }

    /** Pide la contraseña para eliminar la cuenta y muestra el error del repositorio si no se puede. */
    private fun pedirEliminacionDeCuenta(usuarioId: String) {
        DialogoModal.conCampo(
            contexto = requireContext(),
            icono = R.drawable.ic_trash,
            titulo = "¿Eliminar tu cuenta?",
            mensaje = "Se desactivará tu cuenta y se cerrará la sesión. El taller conserva el historial de tus órdenes.",
            etiquetaCampo = "Confirma con tu contraseña",
            esContrasena = true,
            textoAccion = "Eliminar cuenta"
        ) { contrasena ->
            perfilViewModel.eliminarCuenta(usuarioId, contrasena) { resultado ->
                resultado
                    .alExito { mostrarMensaje("Cuenta eliminada") }
                    .alError { error -> if (context != null) DialogoModal.error(requireContext(), error.mensaje) }
            }
        }
    }
}
