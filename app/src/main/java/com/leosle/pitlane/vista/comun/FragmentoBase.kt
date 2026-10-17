package com.leosle.pitlane.vista.comun

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.leosle.pitlane.viewmodel.AuthViewModel
import com.leosle.pitlane.modelo.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Base de todas las pantallas: acceso al navegador, al usuario con sesión, a los mensajes y a los flujos de datos. */
abstract class FragmentoBase : Fragment() {

    /** Indica si la barra de navegación inferior debe verse mientras esta pantalla está abierta. */
    open val mostrarBarraInferior: Boolean = false

    /** Navegador de la actividad que contiene esta pantalla. */
    protected val navegador: Navegador get() = requireActivity() as Navegador

    /** Estado de la sesión, compartido con la actividad que contiene esta pantalla. */
    protected val sesionViewModel: AuthViewModel by activityViewModels()

    /** Usuario con la sesión abierta; las pantallas solo existen mientras hay sesión. */
    protected val usuarioActual: Usuario
        get() = checkNotNull(sesionViewModel.usuarioActual.value) { "No hay sesión abierta" }

    /** Muestra un mensaje breve en pantalla con Toast. */
    protected fun mostrarMensaje(texto: String, duracionLarga: Boolean = false) {
        Toast.makeText(
            requireContext().applicationContext,
            texto,
            if (duracionLarga) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    /** Observa [flujo] mientras la vista esté visible y llama a [accion] con cada valor emitido. */
    protected fun <T> recolectar(flujo: Flow<T>, accion: (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flujo.collect { accion(it) }
            }
        }
    }

    /** Oculta el teclado en pantalla. */
    protected fun ocultarTeclado() {
        val vista = view ?: return
        val administrador = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        administrador.hideSoftInputFromWindow(vista.windowToken, 0)
    }
}

/**
 * Pantalla con ViewBinding: infla el diseño con [inflar] y expone el vínculo en [vinculo] mientras la vista exista.
 * Ejemplo: `class FragEjemplo : FragmentoVinculado<FragEjemploBinding>(FragEjemploBinding::inflate)`.
 */
abstract class FragmentoVinculado<B : ViewBinding>(
    private val inflar: (LayoutInflater, ViewGroup?, Boolean) -> B
) : FragmentoBase() {

    private var vinculoInterno: B? = null

    /** Vínculo con las vistas del diseño; solo es válido entre onCreateView y onDestroyView. */
    protected val vinculo: B get() = checkNotNull(vinculoInterno) { "La vista no está creada" }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val creado = inflar(inflater, container, false)
        vinculoInterno = creado
        return creado.root
    }

    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }
}
