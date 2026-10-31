package com.leosle.pitlane.arquitectura.vista.avances

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.TecHojaEditarAvanceBinding
import com.leosle.pitlane.modelo.AvanceDetallado
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.arquitectura.viewmodel.AvanceViewModel
import com.leosle.pitlane.arquitectura.vista.comun.DialogoModal
import com.leosle.pitlane.arquitectura.vista.comun.GestorFotos
import com.leosle.pitlane.arquitectura.vista.comun.HojaBase
import com.leosle.pitlane.arquitectura.vista.comun.BotonCarga

/**
 * Hoja inferior para editar un avance propio: muestra tipo, descripción y fotos ya cargados y habilita
 * "Guardar cambios" solo cuando algún dato difiere del original.
 */
class HojaEditarAvance : HojaBase() {

    private val avanceViewModel: AvanceViewModel by lazy { AvanceViewModel() }
    private var vinculoInterno: TecHojaEditarAvanceBinding? = null
    private lateinit var formulario: ControladorFormularioAvance
    private var original: AvanceDetallado? = null
    private var cargado = false

    private val gestorFotos = GestorFotos(this, MAXIMO_FOTOS, MENSAJE_MAXIMO) { actualizarBoton() }

    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = TecHojaEditarAvanceBinding.inflate(inflater, contenedor, false)
        vinculoInterno = vinculo
        return vinculo.root
    }

    /** Conecta el formulario y el botón, y carga el avance original en cuanto el flujo de avances lo emite. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        val vinculo = checkNotNull(vinculoInterno)
        configurarEncabezado(vinculo.encabezado, getString(R.string.tec_titulo_editar_avance))
        formulario = ControladorFormularioAvance(requireContext(), vinculo.formulario) { actualizarBoton() }
        formulario.iniciar()
        gestorFotos.vincular(vinculo.formulario.zonaFotos)
        vinculo.formulario.zonaFotos.txtEtiquetaFotos.text = getString(R.string.tec_etiqueta_fotos_avance)
        vinculo.btnGuardarAvance.setOnClickListener { guardar() }

        val ordenId = requireArguments().getString(ARGUMENTO_ORDEN).orEmpty()
        val avanceId = requireArguments().getString(ARGUMENTO_AVANCE).orEmpty()
        recolectar(avanceViewModel.avances(ordenId, usuarioId())) { lista ->
            val encontrado = lista.firstOrNull { it.avance.id == avanceId }
            if (!cargado && encontrado != null) {
                cargado = true
                original = encontrado
                formulario.establecer(encontrado.avance.tipo, encontrado.avance.descripcion)
                gestorFotos.establecer(encontrado.avance.fotos)
                actualizarBoton()
            }
        }
    }

    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Habilita el botón solo si ya se cargó el original y tipo, descripción o fotos son distintos. */
    private fun actualizarBoton() {
        val vinculo = vinculoInterno ?: return
        val base = original?.avance
        vinculo.btnGuardarAvance.isEnabled = base != null && (
            formulario.tipo() != base.tipo ||
                formulario.descripcion() != base.descripcion ||
                identificadores(gestorFotos.fotos) != identificadores(base.fotos)
            )
    }

    /** Devuelve los identificadores de las fotos en orden, para comparar listas de fotos. */
    private fun identificadores(fotos: List<Foto>): List<String> = fotos.map { it.id }

    /** Valida y guarda los cambios; cierra la hoja al terminar bien o muestra el motivo si falla. */
    private fun guardar() {
        val base = original?.avance ?: return
        if (!formulario.validar()) return
        val tipo: TipoAvance = formulario.tipo() ?: return
        BotonCarga.iniciar(checkNotNull(vinculoInterno).btnGuardarAvance)
        avanceViewModel.editarAvance(base.id, usuarioId(), tipo, formulario.descripcion(), gestorFotos.fotos) { resultado ->
            if (!isAdded || vinculoInterno == null) return@editarAvance
            when (resultado) {
                is Resultado.Exito -> {
                    mostrarMensaje(getString(R.string.tec_mensaje_avance_actualizado))
                    dismiss()
                }
                is Resultado.Error -> {
                    vinculoInterno?.let { BotonCarga.terminar(it.btnGuardarAvance) }
                    actualizarBoton()
                    DialogoModal.error(requireContext(), resultado.mensaje)
                }
            }
        }
    }

    /** Identificador del técnico con la sesión abierta, entregado al abrir la hoja. */
    private fun usuarioId(): String = requireArguments().getString(ARGUMENTO_TECNICO).orEmpty()

    companion object {
        private const val ARGUMENTO_ORDEN = "ordenId"
        private const val ARGUMENTO_AVANCE = "avanceId"
        private const val ARGUMENTO_TECNICO = "tecnicoId"
        private const val MAXIMO_FOTOS = 4
        private const val MENSAJE_MAXIMO = "Máximo 4 fotos por avance"

        /** Abre la hoja de edición del avance indicado de una orden, a nombre del técnico indicado. */
        fun mostrar(administrador: FragmentManager, ordenId: String, avanceId: String, tecnicoId: String) {
            HojaEditarAvance().apply {
                arguments = Bundle().apply {
                    putString(ARGUMENTO_ORDEN, ordenId)
                    putString(ARGUMENTO_AVANCE, avanceId)
                    putString(ARGUMENTO_TECNICO, tecnicoId)
                }
            }.show(administrador, "hoja_editar_avance")
        }
    }
}
