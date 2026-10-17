package com.leosle.pitlane.vista.acceso

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
import com.leosle.pitlane.viewmodel.AuthViewModel
import com.leosle.pitlane.vista.comun.Campos
import com.leosle.pitlane.vista.comun.Combos
import com.leosle.pitlane.vista.comun.FragmentoVinculado

/**
 * Inicio de sesión con correo y contraseña. El botón "Ingresar" se habilita cuando ambos campos tienen texto.
 * Incluye un combo con cuentas de demostración que rellena el formulario.
 */
class FragLogin : FragmentoVinculado<AccFragLoginBinding>(AccFragLoginBinding::inflate), ContenidoBajoBarras {

    private val authViewModel: AuthViewModel by activityViewModels()


    /** Cuenta de ejemplo: texto del combo, correo y contraseña. */
    private data class CuentaDemostracion(val etiqueta: String, val correo: String, val contrasena: String)

    private val cuentas = listOf(
        CuentaDemostracion("Jefe de Taller · jefe@freddy.pe", "jefe@freddy.pe", "Taller2026"),
        CuentaDemostracion("Técnico · luis@freddy.pe", "luis@freddy.pe", "Tecnico123"),
        CuentaDemostracion("Técnico · marco@freddy.pe", "marco@freddy.pe", "Tecnico123"),
        CuentaDemostracion("Cliente con vehículos · cliente@freddy.pe", "cliente@freddy.pe", "Cliente123"),
        CuentaDemostracion("Cliente con orden en reparación · carlos@freddy.pe", "carlos@freddy.pe", "Cliente123"),
        CuentaDemostracion("Cliente nuevo sin vehículos · nuevo@freddy.pe", "nuevo@freddy.pe", "Cliente123")
    )

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        ajustarABarrasDelSistema()
        aplicarMomentoDelDia()
        ContrasenaVisible.vincular(vinculo.etContrasena, vinculo.btnVerContrasena)
        Campos.alCambiarTexto(vinculo.etCorreo, vinculo.etContrasena) { actualizarBotonIngresar() }
        Combos.configurar(vinculo.spnCuentaDemostracion, cuentas.map { it.etiqueta })

        vinculo.btnIngresar.setOnClickListener { iniciarSesion() }
        vinculo.etContrasena.setOnEditorActionListener { _, accion, _ ->
            val terminado = accion == EditorInfo.IME_ACTION_DONE && vinculo.btnIngresar.isEnabled
            if (terminado) iniciarSesion()
            terminado
        }
        vinculo.btnOlvidoContrasena.setOnClickListener { navegador.irA(FragRecuperar()) }
        vinculo.btnCrearCuenta.setOnClickListener { navegador.irA(FragRegistro()) }
        vinculo.btnUsarCuentaDemostracion.setOnClickListener { usarCuentaElegida() }
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
        vinculo.btnIngresar.isEnabled = false
        authViewModel.iniciarSesion(Campos.texto(vinculo.etCorreo), vinculo.etContrasena.text.toString()) { resultado ->
            resultado
                .alExito { navegador.abrirInicio() }
                .alError { error ->
                    if (view == null) return@alError
                    vinculo.txtErrorGeneral.text = error.mensaje
                    vinculo.txtErrorGeneral.visibility = View.VISIBLE
                    vinculo.btnIngresar.isEnabled = true
                    mostrarMensaje(error.mensaje)
                }
        }
    }

    /** Copia el correo y la contraseña de la cuenta elegida en el combo a los campos del formulario. */
    private fun usarCuentaElegida() {
        val cuenta = cuentas.getOrNull(Combos.posicion(vinculo.spnCuentaDemostracion)) ?: return
        vinculo.etCorreo.setText(cuenta.correo)
        vinculo.etContrasena.setText(cuenta.contrasena)
        ocultarTeclado()
    }
}
