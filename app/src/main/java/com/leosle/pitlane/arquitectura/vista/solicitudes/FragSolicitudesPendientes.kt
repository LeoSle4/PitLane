package com.leosle.pitlane.arquitectura.vista.solicitudes

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JoFragSolicitudesPendientesBinding
import com.leosle.pitlane.databinding.JoItemSolicitudBinding
import com.leosle.pitlane.modelo.SolicitudDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.arquitectura.viewmodel.SolicitudViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AdaptadorLista
import com.leosle.pitlane.arquitectura.vista.comun.EstadoVacio
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado

/**
 * Pestaña de solicitudes pendientes: lista las solicitudes de atención de los clientes ordenadas por fecha de llegada.
 * Al tocar una se abre una hoja con la descripción completa, las fotos y el botón para registrar el ingreso.
 */
class FragSolicitudesPendientes : FragmentoVinculado<JoFragSolicitudesPendientesBinding>(JoFragSolicitudesPendientesBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val modelo: SolicitudViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<SolicitudDetallada>

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)

        // Lista con su adaptador y estado vacío
        adaptador = AdaptadorLista(requireContext(), R.layout.jo_item_solicitud) { fila, solicitud -> enlazarSolicitud(fila, solicitud) }
        vinculo.lvSolicitudes.adapter = adaptador
        vinculo.lvSolicitudes.emptyView = vinculo.incVacio.root
        EstadoVacio.configurar(
            vinculo.incVacio,
            "No hay solicitudes pendientes",
            "Cuando un cliente avise que llevará su vehículo, la solicitud aparecerá aquí"
        )

        // Al tocar una solicitud se abre la hoja con su detalle
        vinculo.lvSolicitudes.setOnItemClickListener { _, _, posicion, _ ->
            HojaSolicitudJefe.mostrar(parentFragmentManager, adaptador.getItem(posicion).solicitud.id)
        }

        // Cada emisión reemplaza la lista, ordenada por fecha de llegada, y actualiza el resumen
        recolectar(modelo.solicitudesPendientes) { solicitudes ->
            adaptador.actualizar(solicitudes.sortedBy { it.solicitud.fechaLlegada })
            vinculo.txtResumenSolicitudes.text = when (solicitudes.size) {
                0 -> "Todo al día"
                1 -> "1 vehículo por atender"
                else -> "${solicitudes.size} vehículos por atender"
            }
        }
    }

    /** Llena una fila con placa, cliente, falla resumida y fecha de llegada de la solicitud. */
    private fun enlazarSolicitud(fila: View, detalle: SolicitudDetallada) {
        val celda = JoItemSolicitudBinding.bind(fila)
        val solicitud = detalle.solicitud
        celda.viewBarraSolicitud.setBackgroundColor(ContextCompat.getColor(fila.context, R.color.estado_proceso_texto))
        celda.txtPlacaSolicitud.text = detalle.vehiculo?.placa.orEmpty()
        celda.txtLlegadaSolicitud.text = Fechas.fecha(solicitud.fechaLlegada)
        celda.txtClienteSolicitud.text = detalle.cliente?.nombreCompleto ?: "Cliente sin datos"
        celda.txtFallaSolicitud.text = solicitud.descripcion
    }
}
