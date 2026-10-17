package com.leosle.pitlane.vista.avances

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliFragHallazgosBinding
import com.leosle.pitlane.databinding.CliItemHallazgoBinding
import com.leosle.pitlane.modelo.AvanceDetallado
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.viewmodel.AvanceViewModel
import com.leosle.pitlane.vista.comun.AdaptadorLista
import com.leosle.pitlane.vista.comun.AyudasVista
import com.leosle.pitlane.vista.comun.BarraSuperior
import com.leosle.pitlane.vista.comun.EstadoVacio
import com.leosle.pitlane.vista.comun.FragmentoVinculado

/**
 * Hallazgos y trabajos que el técnico registró en una orden, en orden cronológico y solo de lectura. Cada tarjeta
 * muestra el ícono del tipo, la descripción, una galería horizontal de fotos que abre el visor y la fecha.
 */
class FragHallazgos : FragmentoVinculado<CliFragHallazgosBinding>(CliFragHallazgosBinding::inflate) {

    private val avanceViewModel: AvanceViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<AvanceDetallado>

    /** Configura la barra, la lista y el estado vacío, y observa los avances de la orden. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ordenId = requireArguments().getString(ARGUMENTO_ORDEN).orEmpty()
        BarraSuperior.configurar(vinculo.barra, getString(R.string.cli_hallazgos_titulo), alVolver = { navegador.volver() })
        adaptador = AdaptadorLista(requireContext(), R.layout.cli_item_hallazgo) { vista, elemento -> enlazarFila(vista, elemento) }
        vinculo.lvHallazgos.adapter = adaptador
        vinculo.lvHallazgos.emptyView = vinculo.incVacio.root
        EstadoVacio.configurar(
            vinculo.incVacio,
            getString(R.string.cli_hallazgos_vacio_titulo),
            getString(R.string.cli_hallazgos_vacio_mensaje)
        )
        recolectar(avanceViewModel.avances(ordenId, usuarioActual.id)) { adaptador.actualizar(it) }
    }

    /** Llena una tarjeta con el ícono y nombre del tipo, la descripción, la galería de fotos, la fecha y el técnico. */
    private fun enlazarFila(vista: View, elemento: AvanceDetallado) {
        val fila = CliItemHallazgoBinding.bind(vista)
        val avance = elemento.avance
        val icono = if (avance.tipo == TipoAvance.HALLAZGO) R.drawable.ic_magnifying_glass else R.drawable.ic_wrench
        fila.imgIconoTipo.setImageResource(icono)
        fila.txtTipoHallazgo.text = avance.tipo.etiqueta
        fila.txtFechaHallazgo.text = Fechas.fechaHora(avance.fechaHora)
        fila.txtDescripcionHallazgo.text = avance.descripcion
        fila.hsvGaleria.visibility = if (avance.fotos.isEmpty()) View.GONE else View.VISIBLE
        AyudasVista.llenarGaleria(fila.llGaleria, avance.fotos, parentFragmentManager)
        val tecnico = elemento.tecnico
        fila.txtTecnicoHallazgo.visibility = if (tecnico != null) View.VISIBLE else View.GONE
        fila.txtTecnicoHallazgo.text = getString(R.string.cli_registrado_por, tecnico?.nombreCompleto.orEmpty())
    }

    companion object {
        private const val ARGUMENTO_ORDEN = "ordenId"

        /** Crea la pantalla de hallazgos de la orden indicada. */
        fun nuevo(ordenId: String): FragHallazgos =
            FragHallazgos().apply { arguments = Bundle().apply { putString(ARGUMENTO_ORDEN, ordenId) } }
    }
}
