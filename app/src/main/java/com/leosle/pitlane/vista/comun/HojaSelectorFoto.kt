package com.leosle.pitlane.vista.comun

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.leosle.pitlane.databinding.HojaSelectorFotoBinding

/** Origen desde el que se obtiene una foto. */
enum class OrigenFoto { CAMARA, GALERIA }

/** Hoja modal con las opciones "Tomar foto" y "Elegir de la galería". */
class HojaSelectorFoto : HojaBase() {

    private var vinculoInterno: HojaSelectorFotoBinding? = null
    private var alElegir: ((OrigenFoto) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = HojaSelectorFotoBinding.inflate(inflater, contenedor, false)
        vinculoInterno = vinculo
        configurarEncabezado(vinculo.encabezado, "Agregar foto")
        vinculo.btnTomarFoto.setOnClickListener { elegir(OrigenFoto.CAMARA) }
        vinculo.btnElegirGaleria.setOnClickListener { elegir(OrigenFoto.GALERIA) }
        return vinculo.root
    }

    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Cierra la hoja y avisa qué origen eligió el usuario. */
    private fun elegir(origen: OrigenFoto) {
        val retorno = alElegir
        dismiss()
        retorno?.invoke(origen)
    }

    companion object {
        /** Abre la hoja y llama a [alElegir] con el origen que el usuario seleccione. */
        fun mostrar(administrador: androidx.fragment.app.FragmentManager, alElegir: (OrigenFoto) -> Unit) {
            HojaSelectorFoto().apply { this.alElegir = alElegir }.show(administrador, "hoja_selector_foto")
        }
    }
}
