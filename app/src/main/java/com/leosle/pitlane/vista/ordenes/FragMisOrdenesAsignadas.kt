package com.leosle.pitlane.vista.ordenes

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.TecFragMisOrdenesBinding
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.viewmodel.OrdenViewModel
import com.leosle.pitlane.vista.comun.LogosMarca
import com.leosle.pitlane.vista.comun.AdaptadorLista
import com.leosle.pitlane.vista.comun.EstadoVacio
import com.leosle.pitlane.vista.comun.EstadosVisuales
import com.leosle.pitlane.vista.comun.FragmentoVinculado
import com.leosle.pitlane.vista.navegacion.RutasTecnico

/**
 * Pestaña raíz del técnico: saluda, resume cuántas órdenes tiene por atender y las lista en tarjetas.
 * Muestra primero las órdenes En reparación y luego las Ingresado, de la más antigua a la más reciente.
 */
class FragMisOrdenesAsignadas : FragmentoVinculado<TecFragMisOrdenesBinding>(TecFragMisOrdenesBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val ordenViewModel: OrdenViewModel by lazy { OrdenViewModel() }
    private lateinit var adaptador: AdaptadorLista<OrdenDetallada>

    /** Prepara el saludo, el adaptador de la lista, el estado vacío y la observación de las órdenes del técnico. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        vinculo.txtSaludoTecnico.text = getString(R.string.tec_saludo, usuarioActual.nombres)

        adaptador = AdaptadorLista(requireContext(), R.layout.tec_item_orden) { fila, elemento -> enlazarFila(fila, elemento) }
        vinculo.lvOrdenesAsignadas.adapter = adaptador
        vinculo.lvOrdenesAsignadas.emptyView = vinculo.vacioOrdenes.root
        EstadoVacio.configurar(
            vinculo.vacioOrdenes,
            getString(R.string.tec_lista_vacia_titulo),
            getString(R.string.tec_lista_vacia_mensaje)
        )
        vinculo.lvOrdenesAsignadas.setOnItemClickListener { _, _, posicion, _ ->
            RutasTecnico.abrirOrden(navegador, adaptador.getItem(posicion).orden.id)
        }

        recolectar(ordenViewModel.ordenesDeTecnico(usuarioActual.id)) { ordenes -> mostrarOrdenes(ordenes) }
    }

    /** Ordena las órdenes (En reparación primero, luego por fecha de creación ascendente) y actualiza lista y resumen. */
    private fun mostrarOrdenes(ordenes: List<OrdenDetallada>) {
        val ordenadas = ordenes.sortedWith(
            compareBy<OrdenDetallada> { if (it.orden.estado == EstadoOrden.EN_REPARACION) 0 else 1 }
                .thenBy { it.orden.creadaEn }
        )
        adaptador.actualizar(ordenadas)
        vinculo.txtResumenTecnico.text = when (ordenadas.size) {
            0 -> getString(R.string.tec_resumen_ninguna)
            1 -> getString(R.string.tec_resumen_una)
            else -> getString(R.string.tec_resumen_varias, ordenadas.size)
        }
    }

    /** Llena una fila: barra lateral con el color del estado, número, chip, placa, vehículo, falla y fecha. */
    private fun enlazarFila(fila: View, elemento: OrdenDetallada) {
        val orden = elemento.orden
        fila.findViewById<View>(R.id.viewBarraEstado).backgroundTintList =
            ContextCompat.getColorStateList(fila.context, EstadosVisuales.colorEstado(orden.estado))
        LogosMarca.aplicar(fila.findViewById<android.widget.ImageView>(R.id.imgMarcaOrden), elemento.vehiculo?.marca.orEmpty())
        fila.findViewById<android.widget.TextView>(R.id.txtNumeroOrden).text = getString(R.string.tec_orden_numero, orden.numero)
        EstadosVisuales.estadoOrden(fila.findViewById(R.id.txtEstadoOrden), orden.estado)
        fila.findViewById<android.widget.TextView>(R.id.txtPlacaVehiculo).text = elemento.vehiculo?.placa.orEmpty()
        fila.findViewById<android.widget.TextView>(R.id.txtDescripcionVehiculo).text = elemento.vehiculo?.descripcion.orEmpty()
        fila.findViewById<android.widget.TextView>(R.id.txtFallaResumen).text = orden.falla
        fila.findViewById<android.widget.TextView>(R.id.txtFechaIngreso).text =
            getString(R.string.tec_orden_ingreso, Fechas.fechaHora(orden.creadaEn))
    }
}
