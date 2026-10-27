package com.leosle.pitlane.arquitectura.vista.ordenes

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliFragComprobanteBinding
import com.leosle.pitlane.databinding.CliItemDatoBinding
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.arquitectura.viewmodel.OrdenViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AyudasVista
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.arquitectura.vista.comun.poblar

/**
 * Comprobante de ingreso con estilo de ticket de borde punteado: número de orden, fecha y hora, vehículo, falla,
 * kilometraje, combustible, observaciones, técnico y fotos del estado exterior. Solo muestra órdenes del cliente.
 */
class FragComprobante : FragmentoVinculado<CliFragComprobanteBinding>(CliFragComprobanteBinding::inflate) {

    private val ordenViewModel: OrdenViewModel by viewModels()

    /** Par de etiqueta y valor que forma una fila del ticket. */
    private data class Dato(val etiqueta: String, val valor: String)

    /** Configura la barra superior y observa la orden para dibujar el ticket. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ordenId = requireArguments().getString(ARGUMENTO_ORDEN).orEmpty()
        BarraSuperior.configurar(vinculo.barra, getString(R.string.cli_comprobante_titulo), alVolver = { navegador.volver() })
        recolectar(ordenViewModel.orden(ordenId, usuarioActual.id)) { detalle -> pintar(detalle) }
    }

    /** Escribe el número, las filas de datos y la galería del ticket; una orden de otro cliente no se muestra. */
    private fun pintar(detalle: OrdenDetallada?) {
        if (detalle == null) return
        if (detalle.orden.clienteId != usuarioActual.id) {
            mostrarMensaje(getString(R.string.cli_orden_no_disponible))
            navegador.volver()
            return
        }
        val orden = detalle.orden
        val vehiculo = detalle.vehiculo
        vinculo.svComprobante.visibility = View.VISIBLE
        vinculo.txtNumeroComprobante.text = orden.numero

        val datos = listOf(
            Dato(getString(R.string.cli_dato_fecha_hora), Fechas.fechaHora(orden.creadaEn)),
            Dato(getString(R.string.cli_dato_vehiculo), vehiculo?.let { "${it.placa} · ${it.descripcion} · ${it.color}" }.orEmpty()),
            Dato(getString(R.string.cli_dato_falla), orden.falla),
            Dato(getString(R.string.cli_dato_kilometraje), getString(R.string.cli_kilometros, orden.kilometraje)),
            Dato(getString(R.string.cli_dato_combustible), orden.combustible.etiqueta),
            Dato(
                getString(R.string.cli_dato_observaciones),
                orden.observaciones.ifBlank { getString(R.string.cli_sin_observaciones) }
            ),
            Dato(getString(R.string.cli_dato_tecnico), detalle.tecnico?.nombreCompleto ?: getString(R.string.cli_sin_tecnico))
        )
        vinculo.llDatos.poblar(datos, R.layout.cli_item_dato) { vista, dato ->
            val fila = CliItemDatoBinding.bind(vista)
            fila.txtEtiquetaDato.text = dato.etiqueta
            fila.txtValorDato.text = dato.valor
        }

        vinculo.llFotosComprobante.visibility = if (orden.fotos.isEmpty()) View.GONE else View.VISIBLE
        AyudasVista.llenarGaleria(vinculo.llGaleria, orden.fotos, parentFragmentManager)
    }

    companion object {
        private const val ARGUMENTO_ORDEN = "ordenId"

        /** Crea el comprobante de la orden indicada. */
        fun nuevo(ordenId: String): FragComprobante =
            FragComprobante().apply { arguments = Bundle().apply { putString(ARGUMENTO_ORDEN, ordenId) } }
    }
}
