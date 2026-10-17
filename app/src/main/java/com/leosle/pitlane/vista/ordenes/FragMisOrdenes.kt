package com.leosle.pitlane.vista.ordenes

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliFragMisOrdenesBinding
import com.leosle.pitlane.databinding.CliItemOrdenBinding
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.viewmodel.OrdenViewModel
import com.leosle.pitlane.vista.comun.LogosMarca
import com.leosle.pitlane.vista.comun.AdaptadorLista
import com.leosle.pitlane.vista.comun.AyudasVista
import com.leosle.pitlane.vista.comun.EstadoVacio
import com.leosle.pitlane.vista.comun.EstadosVisuales
import com.leosle.pitlane.vista.comun.FragmentoVinculado

/**
 * Pestaña raíz del cliente con sus órdenes agrupadas en las secciones "En curso" (vehículo todavía en el taller)
 * y "Anteriores". Tocar una orden abre su estado actual.
 */
class FragMisOrdenes : FragmentoVinculado<CliFragMisOrdenesBinding>(CliFragMisOrdenesBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val ordenViewModel: OrdenViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<FilaOrden>

    /** Fila de la lista: un título de sección o una orden. */
    private sealed class FilaOrden {
        data class Seccion(val titulo: String) : FilaOrden()
        data class Elemento(val detalle: OrdenDetallada) : FilaOrden()
    }

    /** Prepara la lista con secciones y el estado vacío, y observa las órdenes del cliente. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adaptador = AdaptadorLista(requireContext(), R.layout.cli_item_orden) { vista, fila -> enlazarFila(vista, fila) }
        vinculo.lvOrdenes.adapter = adaptador
        vinculo.lvOrdenes.emptyView = vinculo.incVacio.root
        vinculo.lvOrdenes.setOnItemClickListener { _, _, posicion, _ ->
            val fila = adaptador.getItem(posicion)
            if (fila is FilaOrden.Elemento) navegador.abrirOrden(fila.detalle.orden.id)
        }
        EstadoVacio.configurar(
            vinculo.incVacio,
            getString(R.string.cli_ordenes_vacio_titulo),
            getString(R.string.cli_ordenes_vacio_mensaje)
        )
        recolectar(ordenViewModel.ordenesDeCliente(usuarioActual.id)) { adaptador.actualizar(armarFilas(it)) }
    }

    /** Separa las órdenes en "En curso" (vehículo en el taller) y "Anteriores", omitiendo la sección que quede vacía. */
    private fun armarFilas(ordenes: List<OrdenDetallada>): List<FilaOrden> {
        val (enTaller, anteriores) = ordenes.partition { it.orden.enTaller }
        val filas = mutableListOf<FilaOrden>()
        if (enTaller.isNotEmpty()) {
            filas.add(FilaOrden.Seccion(getString(R.string.cli_seccion_en_curso)))
            enTaller.forEach { filas.add(FilaOrden.Elemento(it)) }
        }
        if (anteriores.isNotEmpty()) {
            filas.add(FilaOrden.Seccion(getString(R.string.cli_seccion_anteriores)))
            anteriores.forEach { filas.add(FilaOrden.Elemento(it)) }
        }
        return filas
    }

    /** Llena la fila como título de sección o como tarjeta de orden con número, vehículo, fecha, chip y barra de estado. */
    private fun enlazarFila(vista: View, fila: FilaOrden) {
        val vinculoFila = CliItemOrdenBinding.bind(vista)
        when (fila) {
            is FilaOrden.Seccion -> {
                vinculoFila.txtSeccionOrdenes.visibility = View.VISIBLE
                vinculoFila.txtSeccionOrdenes.text = fila.titulo
                vinculoFila.mcvOrden.visibility = View.GONE
            }
            is FilaOrden.Elemento -> {
                val orden = fila.detalle.orden
                val vehiculo = fila.detalle.vehiculo
                vinculoFila.txtSeccionOrdenes.visibility = View.GONE
                vinculoFila.mcvOrden.visibility = View.VISIBLE
                vinculoFila.txtNumeroOrden.text = orden.numero
                vinculoFila.txtVehiculoOrden.text = vehiculo?.let { "${it.placa} · ${it.descripcion}" }.orEmpty()
                vinculoFila.txtFechaOrden.text = getString(R.string.cli_ingreso_fecha, Fechas.fecha(orden.creadaEn))
                EstadosVisuales.estadoOrden(vinculoFila.txtEstadoOrden, orden.estado)
                AyudasVista.pintarBarra(vinculoFila.viewBarraEstado, AyudasVista.colorOrden(orden.estado))
                LogosMarca.aplicar(vinculoFila.imgMarcaOrden, vehiculo?.marca.orEmpty())
            }
        }
    }
}
