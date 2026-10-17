package com.leosle.pitlane.vista.ordenes

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JoFragPanelOrdenesBinding
import com.leosle.pitlane.databinding.JoItemOrdenPanelBinding
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.viewmodel.OrdenViewModel
import com.leosle.pitlane.vista.comun.LogosMarca
import com.leosle.pitlane.vista.comun.EstadosVisuales
import com.leosle.pitlane.vista.comun.AdaptadorLista
import com.leosle.pitlane.vista.comun.EstadoVacio
import com.leosle.pitlane.vista.comun.FragmentoVinculado

/**
 * Panel de órdenes del taller: tres pestañas segmentadas (Ingresado, En reparación y Listo para recoger) con su contador,
 * y la lista de órdenes del estado elegido. Se actualiza sola porque observa el flujo de órdenes activas.
 */
class FragPanelOrdenes : FragmentoVinculado<JoFragPanelOrdenesBinding>(JoFragPanelOrdenesBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val modelo: OrdenViewModel by viewModels()
    private var ordenes: List<OrdenDetallada> = emptyList()
    private lateinit var adaptador: AdaptadorLista<OrdenDetallada>

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)

        // Lista con su adaptador, estado vacío y apertura del detalle al tocar una orden
        adaptador = AdaptadorLista(requireContext(), R.layout.jo_item_orden_panel) { fila, orden -> enlazarOrden(fila, orden) }
        vinculo.lvOrdenes.adapter = adaptador
        vinculo.lvOrdenes.emptyView = vinculo.incVacio.root
        vinculo.lvOrdenes.setOnItemClickListener { _, _, posicion, _ ->
            navegador.irA(FragDetalleOrdenJefe.nuevo(adaptador.getItem(posicion).orden.id))
        }

        // Cambio de pestaña y acceso directo al formulario de ingreso
        vinculo.rgEstadosPanel.setOnCheckedChangeListener { _, _ -> refrescar() }
        vinculo.btnNuevoIngreso.setOnClickListener { navegador.irA(FragNuevaOrden.nuevo(null, null, null)) }

        // Cada emisión del flujo reemplaza las órdenes y vuelve a dibujar contadores y lista
        recolectar(modelo.ordenesActivas) { nuevas ->
            ordenes = nuevas
            refrescar()
        }
    }

    /** Devuelve el estado de la pestaña marcada. */
    private fun estadoSeleccionado(): EstadoOrden = when (vinculo.rgEstadosPanel.checkedRadioButtonId) {
        R.id.rbEnReparacion -> EstadoOrden.EN_REPARACION
        R.id.rbListo -> EstadoOrden.LISTO
        else -> EstadoOrden.INGRESADO
    }

    /** Actualiza el contador de cada pestaña y la lista con las órdenes del estado elegido, de la más antigua a la más reciente. */
    private fun refrescar() {
        val porEstado = ordenes.groupBy { it.orden.estado }
        vinculo.rbIngresado.text = textoSegmento(porEstado[EstadoOrden.INGRESADO].orEmpty().size, EstadoOrden.INGRESADO.etiqueta)
        vinculo.rbEnReparacion.text = textoSegmento(porEstado[EstadoOrden.EN_REPARACION].orEmpty().size, EstadoOrden.EN_REPARACION.etiqueta)
        vinculo.rbListo.text = textoSegmento(porEstado[EstadoOrden.LISTO].orEmpty().size, EstadoOrden.LISTO.etiqueta)

        val elegido = estadoSeleccionado()
        EstadoVacio.configurar(
            vinculo.incVacio,
            "No hay órdenes en \"${elegido.etiqueta}\"",
            "Cuando una orden llegue a este estado aparecerá aquí"
        )
        adaptador.actualizar(porEstado[elegido].orEmpty().sortedBy { it.orden.fechaUltimoCambio })
    }

    /** Arma el texto de una pestaña con el número grande arriba y la etiqueta debajo. */
    private fun textoSegmento(cantidad: Int, etiqueta: String): SpannableString {
        val numero = cantidad.toString()
        val texto = SpannableString("$numero\n$etiqueta")
        texto.setSpan(RelativeSizeSpan(1.6f), 0, numero.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        texto.setSpan(StyleSpan(Typeface.BOLD), 0, numero.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return texto
    }

    /** Llena una fila con número, placa, cliente, técnico y tiempo en el estado actual de la orden. */
    private fun enlazarOrden(fila: View, detalle: OrdenDetallada) {
        val celda = JoItemOrdenPanelBinding.bind(fila)
        val orden = detalle.orden
        AyudasOrden.pintarBarraEstado(celda.viewBarraEstado, orden.estado)
        LogosMarca.aplicar(celda.imgMarcaOrden, detalle.vehiculo?.marca.orEmpty())
        celda.txtNumeroOrden.text = orden.numero
        celda.txtTiempoEstado.text = Fechas.duracion(orden.fechaUltimoCambio, System.currentTimeMillis())
        celda.txtPlacaOrden.text = detalle.vehiculo?.placa.orEmpty()
        celda.txtVehiculoOrden.text = detalle.vehiculo?.descripcion.orEmpty()
        celda.txtClienteOrden.text = detalle.cliente?.nombreCompleto ?: "Cliente sin datos"
        celda.txtTecnicoOrden.text = detalle.tecnico?.nombreCompleto ?: "Sin técnico"
        // La tarjeta completa abre el detalle: el toque lo recibe la lista, no la tarjeta
        celda.mcvOrden.isClickable = false
    }
}
