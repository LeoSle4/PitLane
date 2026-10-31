package com.leosle.pitlane.arquitectura.vista.acceso

import android.content.res.ColorStateList
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import com.leosle.pitlane.databinding.AccFragLoginBinding
import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.viewmodel.AuthViewModel
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.arquitectura.vista.comun.BotonCarga

/**
 * Inicio de sesión con correo y contraseña. El botón "Ingresar" se habilita cuando ambos campos tienen texto.
 */
class FragLogin : FragmentoVinculado<AccFragLoginBinding>(AccFragLoginBinding::inflate), ContenidoBajoBarras {

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        ajustarABarrasDelSistema()
        aplicarMomentoDelDia()
        ContrasenaVisible.vincular(vinculo.etContrasena, vinculo.btnVerContrasena)
        Campos.alCambiarTexto(vinculo.etCorreo, vinculo.etContrasena) { actualizarBotonIngresar() }

        vinculo.btnIngresar.setOnClickListener { iniciarSesion() }
        vinculo.etContrasena.setOnEditorActionListener { _, accion, _ ->
            val terminado = accion == EditorInfo.IME_ACTION_DONE && vinculo.btnIngresar.isEnabled
            if (terminado) iniciarSesion()
            terminado
        }
        vinculo.btnOlvidoContrasena.setOnClickListener { navegador.irA(FragRecuperar()) }
        vinculo.btnCrearCuenta.setOnClickListener { navegador.irA(FragRegistro()) }
    }

    /** Ajusta el color del saludo y de la barra de estado al cielo de la escena, que cambia entre el día y la noche. */
    private fun aplicarMomentoDelDia() {
        val esNoche = EscenaViaje.esNocheAhora()
        val colorTitulo = if (esNoche) R.color.texto_sobre_noche else R.color.texto_sobre_dia
        vinculo.txtTituloLogin.setTextColor(ContextCompat.getColor(requireContext(), colorTitulo))
        EscenaViaje.ajustarBarraDeEstado(requireActivity(), vinculo.root, esNoche)
    }

    /** Devuelve a la barra de estado los íconos que corresponden al tema al salir de la pantalla. */
    override fun onDestroyView() {
        val oscuro = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !oscuro }
        super.onDestroyView()
    }

    /** Baja el saludo bajo la barra de estado y sube el final de la hoja sobre la barra de navegación. */
    private fun ajustarABarrasDelSistema() {
        val densidad = resources.displayMetrics.density
        val lado = (24 * densidad).toInt()
        val margenHoja = (16 * densidad).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(vinculo.root) { _, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vinculo.llEncabezadoLogin.setPadding(lado, barras.top, lado, 0)
            vinculo.llHojaLogin.setPadding(lado, lado, lado, margenHoja + barras.bottom)
            insets
        }
    }

    /** Habilita "Ingresar" solo si el correo y la contraseña no están vacíos. */
    private fun actualizarBotonIngresar() {
        vinculo.btnIngresar.isEnabled =
            Campos.texto(vinculo.etCorreo).isNotEmpty() && vinculo.etContrasena.text.isNotEmpty()
        vinculo.txtErrorGeneral.visibility = View.GONE
    }

    /** Envía las credenciales; si son correctas entra a la aplicación y si no muestra el error bajo el formulario. */
    private fun iniciarSesion() {
        ocultarTeclado()
        BotonCarga.iniciar(vinculo.btnIngresar)
        authViewModel.iniciarSesion(Campos.texto(vinculo.etCorreo), vinculo.etContrasena.text.toString()) { resultado ->
            resultado
                .alExito { navegador.abrirInicio() }
                .alError { error ->
                    if (view == null) return@alError
                    vinculo.txtErrorGeneral.text = error.mensaje
                    vinculo.txtErrorGeneral.visibility = View.VISIBLE
                    BotonCarga.terminar(vinculo.btnIngresar)
                    mostrarMensaje(error.mensaje)
                }
        }
    }
}
