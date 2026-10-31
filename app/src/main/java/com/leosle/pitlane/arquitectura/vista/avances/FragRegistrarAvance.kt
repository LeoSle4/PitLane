package com.leosle.pitlane.arquitectura.vista.avances

import android.os.Bundle
import android.view.View
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.TecFragRegistrarAvanceBinding
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.arquitectura.viewmodel.AvanceViewModel
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.DialogoModal
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.arquitectura.vista.comun.GestorFotos
import com.leosle.pitlane.arquitectura.vista.comun.BotonCarga

/**
 * Formulario para registrar un hallazgo o un trabajo realizado en una orden: tipo, descripción y hasta 4 fotos.
 * Si el repositorio rechaza el registro (por ejemplo la orden no está En reparación) muestra el motivo en un diálogo.
 */
class FragRegistrarAvance : FragmentoVinculado<TecFragRegistrarAvanceBinding>(TecFragRegistrarAvanceBinding::inflate) {

    override val mostrarBarraInferior: Boolean = false

    private val avanceViewModel: AvanceViewModel by lazy { AvanceViewModel() }
    private val ordenId: String get() = requireArguments().getString(ARGUMENTO_ORDEN).orEmpty()
    private lateinit var formulario: ControladorFormularioAvance

    private val gestorFotos = GestorFotos(this, MAXIMO_FOTOS, MENSAJE_MAXIMO)

    /** Configura la barra, el formulario con su zona de fotos y el botón de registrar. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(vinculo.barra, getString(R.string.tec_titulo_registrar_avance), alVolver = { navegador.volver() })
        formulario = ControladorFormularioAvance(requireContext(), vinculo.formulario)
        formulario.iniciar()
        gestorFotos.vincular(vinculo.formulario.zonaFotos)
        vinculo.formulario.zonaFotos.txtEtiquetaFotos.text = getString(R.string.tec_etiqueta_fotos_avance)
        vinculo.btnRegistrarAvance.setOnClickListener { registrar() }
    }

    /** Valida el formulario y, si es correcto, registra el avance con las fotos adjuntas. */
    private fun registrar() {
        ocultarTeclado()
        if (!formulario.validar()) return
        val tipo = formulario.tipo() ?: return
        BotonCarga.iniciar(vinculo.btnRegistrarAvance)
        avanceViewModel.registrarAvance(ordenId, usuarioActual.id, tipo, formulario.descripcion(), gestorFotos.fotos) { resultado ->
            if (!isAdded || view == null) return@registrarAvance
            BotonCarga.terminar(vinculo.btnRegistrarAvance)
            when (resultado) {
                is Resultado.Exito -> {
                    mostrarMensaje(getString(R.string.tec_mensaje_avance_registrado))
                    navegador.volver()
                }
                is Resultado.Error -> DialogoModal.error(requireContext(), resultado.mensaje)
            }
        }
    }

    companion object {
        private const val ARGUMENTO_ORDEN = "ordenId"
        private const val MAXIMO_FOTOS = 4
        private const val MENSAJE_MAXIMO = "Máximo 4 fotos por avance"

        /** Crea el formulario de avance para la orden indicada. */
        fun nuevo(ordenId: String): FragRegistrarAvance =
            FragRegistrarAvance().apply { arguments = Bundle().apply { putString(ARGUMENTO_ORDEN, ordenId) } }
    }
}
