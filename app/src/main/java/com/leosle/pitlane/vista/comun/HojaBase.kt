package com.leosle.pitlane.vista.comun

import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.HojaEncabezadoBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Base de las hojas modales que suben desde abajo (edición y selección). Se abre completa, limita su alto
 * al 90 % de la pantalla y ofrece el encabezado común con agarradera, título y botón de cerrar.
 */
abstract class HojaBase : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.Tema_HojaModal

    override fun onStart() {
        super.onStart()
        val hoja = dialog as? BottomSheetDialog ?: return
        hoja.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            maxHeight = (resources.displayMetrics.heightPixels * 0.9).toInt()
        }
        hoja.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    /** Escribe el título en el encabezado y hace que el botón de cerrar descarte la hoja. */
    protected fun configurarEncabezado(encabezado: HojaEncabezadoBinding, titulo: String) {
        encabezado.txtTituloHoja.text = titulo
        encabezado.btnCerrarHoja.setOnClickListener { dismiss() }
    }

    /** Muestra un mensaje breve en pantalla con Toast. */
    protected fun mostrarMensaje(texto: String) {
        Toast.makeText(requireContext().applicationContext, texto, Toast.LENGTH_SHORT).show()
    }

    /** Observa [flujo] mientras la hoja esté visible y llama a [accion] con cada valor emitido. */
    protected fun <T> recolectar(flujo: Flow<T>, accion: (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flujo.collect { accion(it) }
            }
        }
    }
}
