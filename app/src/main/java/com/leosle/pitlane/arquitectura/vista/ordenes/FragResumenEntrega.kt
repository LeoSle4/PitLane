package com.leosle.pitlane.arquitectura.vista.ordenes

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.viewmodel.AvanceViewModel
import com.leosle.pitlane.arquitectura.viewmodel.OrdenViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AdaptadorLista
import com.leosle.pitlane.arquitectura.vista.comun.AyudasVista
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.EstadoVacio
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.databinding.JoFragResumenEntregaBinding
import com.leosle.pitlane.modelo.AvanceDetallado

/**
 * Resumen de trabajos de una orden lista para recoger: la falla reportada, el técnico que la atendió y los avances
 * en orden cronológico con tipo, descripción, fotos y fecha. Es solo de lectura, para explicárselos al cliente.
 */
class FragResumenEntrega : FragmentoVinculado<JoFragResumenEntregaBinding>(JoFragResumenEntregaBinding::inflate) {

    private val ordenModelo: OrdenViewModel by viewModels()
    private val avanceModelo: AvanceViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<AvanceDetallado>

    /** Prepara la barra, la lista de avances con su estado vacío y observa la orden y sus avances. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        val ordenId = requireArguments().getString(CLAVE_ORDEN).orEmpty()
        BarraSuperior.configurar(vinculo.incBarra, getString(R.string.jo_resumen_titulo), alVolver = { navegador.volver() })

        adaptador = AdaptadorLista(requireContext(), R.layout.cli_item_hallazgo) { fila, avance ->
            AyudasVista.pintarHallazgo(fila, avance, parentFragmentManager)
        }
        vinculo.lvResumen.adapter = adaptador
        vinculo.lvResumen.emptyView = vinculo.incVacio.root
        EstadoVacio.configurar(
            vinculo.incVacio,
            getString(R.string.jo_resumen_vacio_titulo),
            getString(R.string.jo_resumen_vacio_mensaje)
        )

        recolectar(ordenModelo.orden(ordenId, usuarioActual.id)) { detalle ->
            if (detalle == null) return@recolectar
            vinculo.txtFallaResumen.text = detalle.orden.falla
            vinculo.txtTecnicoResumen.text = detalle.tecnico?.nombreCompleto ?: getString(R.string.jo_resumen_sin_tecnico)
        }
        recolectar(avanceModelo.avances(ordenId, usuarioActual.id)) { adaptador.actualizar(it) }
    }

    companion object {
        private const val CLAVE_ORDEN = "ordenId"

        /** Crea el resumen de trabajos de la orden indicada. */
        fun nuevo(ordenId: String): FragResumenEntrega =
            FragResumenEntrega().apply { arguments = Bundle().apply { putString(CLAVE_ORDEN, ordenId) } }
    }
}
