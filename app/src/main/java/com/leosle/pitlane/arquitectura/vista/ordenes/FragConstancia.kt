package com.leosle.pitlane.arquitectura.vista.ordenes

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.viewmodel.OrdenViewModel
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.arquitectura.vista.comun.poblar
import com.leosle.pitlane.databinding.CliFragConstanciaBinding
import com.leosle.pitlane.databinding.CliItemDatoBinding
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.util.Fechas

/**
 * Constancia de entrega de una orden Entregada, de solo lectura: número de orden, placa, fecha y hora de la entrega,
 * quién la registró y la fecha de la conformidad del cliente. Solo muestra órdenes del cliente con sesión.
 */
class FragConstancia : FragmentoVinculado<CliFragConstanciaBinding>(CliFragConstanciaBinding::inflate) {

    private val ordenViewModel: OrdenViewModel by viewModels()

    /** Par de etiqueta y valor que forma una fila del ticket. */
    private data class Dato(val etiqueta: String, val valor: String)

    /** Configura la barra superior y observa la orden para dibujar la constancia. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ordenId = requireArguments().getString(ARGUMENTO_ORDEN).orEmpty()
        BarraSuperior.configurar(vinculo.barra, getString(R.string.cli_constancia_titulo), alVolver = { navegador.volver() })
        recolectar(ordenViewModel.orden(ordenId, usuarioActual.id)) { detalle -> pintar(detalle) }
    }

    /** Escribe el número y las filas de la constancia; una orden ajena o aún no entregada no se muestra. */
    private fun pintar(detalle: OrdenDetallada?) {
        if (detalle == null) return
        if (detalle.orden.clienteId != usuarioActual.id || detalle.orden.estado != EstadoOrden.ENTREGADO) {
            mostrarMensaje(getString(R.string.cli_orden_no_disponible))
            navegador.volver()
            return
        }
        val orden = detalle.orden
        vinculo.svConstancia.visibility = View.VISIBLE
        vinculo.txtNumeroConstancia.text = orden.numero

        val fechaEntrega = orden.fechaDeEstado(EstadoOrden.ENTREGADO)
        val entrega = orden.historial.lastOrNull { it.estado == EstadoOrden.ENTREGADO }
        // Solo el Jefe registra entregas; si la orden no guardó su nombre, se muestra el cargo.
        val registradaPor = detalle.entregadaPor?.nombreCompleto ?: entrega?.usuarioNombre
            ?: entrega?.let { Rol.JEFE.etiqueta } ?: getString(R.string.cli_sin_dato)
        val conformidad = orden.conformidad
        val datos = listOf(
            Dato(getString(R.string.cli_dato_placa), detalle.vehiculo?.placa.orEmpty()),
            Dato(getString(R.string.cli_dato_entrega), fechaEntrega?.let { Fechas.fechaHora(it) } ?: getString(R.string.cli_sin_dato)),
            Dato(getString(R.string.cli_dato_registrada_por), registradaPor),
            Dato(getString(R.string.cli_dato_conformidad), conformidad?.let { Fechas.fecha(it.fechaHora) } ?: getString(R.string.cli_sin_dato))
        )
        vinculo.llDatosConstancia.poblar(datos, R.layout.cli_item_dato) { vista, dato ->
            val fila = CliItemDatoBinding.bind(vista)
            fila.txtEtiquetaDato.text = dato.etiqueta
            fila.txtValorDato.text = dato.valor
        }
    }

    companion object {
        private const val ARGUMENTO_ORDEN = "ordenId"

        /** Crea la constancia de la orden indicada. */
        fun nuevo(ordenId: String): FragConstancia =
            FragConstancia().apply { arguments = Bundle().apply { putString(ARGUMENTO_ORDEN, ordenId) } }
    }
}
