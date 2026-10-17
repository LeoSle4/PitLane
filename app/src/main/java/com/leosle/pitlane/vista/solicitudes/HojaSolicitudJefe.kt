package com.leosle.pitlane.vista.solicitudes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.leosle.pitlane.databinding.JoHojaSolicitudBinding
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.SolicitudDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.viewmodel.SolicitudViewModel
import com.leosle.pitlane.vista.comun.HojaBase
import com.leosle.pitlane.vista.comun.Navegador
import com.leosle.pitlane.vista.comun.VisorFoto
import com.leosle.pitlane.vista.ordenes.AyudasOrden
import com.leosle.pitlane.vista.ordenes.FragNuevaOrden

/**
 * Hoja con el detalle de una solicitud pendiente: vehículo, cliente, descripción completa, síntomas y fotos.
 * El botón "Registrar ingreso" cierra la hoja y abre el formulario de orden con los datos ya llenados.
 */
class HojaSolicitudJefe : HojaBase() {

    private var vinculoInterno: JoHojaSolicitudBinding? = null
    private val modelo: SolicitudViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = JoHojaSolicitudBinding.inflate(inflater, contenedor, false)
        vinculoInterno = vinculo
        configurarEncabezado(vinculo.encabezado, "Solicitud de atención")
        return vinculo.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        // La hoja se mantiene al día con los cambios de la solicitud
        val solicitudId = requireArguments().getString(CLAVE_SOLICITUD).orEmpty()
        recolectar(modelo.solicitud(solicitudId)) { detalle ->
            if (detalle != null) mostrar(detalle)
        }
    }

    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Escribe todos los datos de la solicitud y conecta el botón de registrar ingreso. */
    private fun mostrar(detalle: SolicitudDetallada) {
        val vinculo = vinculoInterno ?: return
        val solicitud = detalle.solicitud

        // Datos del vehículo, del cliente y de la fecha de llegada
        vinculo.txtPlacaHoja.text = detalle.vehiculo?.placa.orEmpty()
        vinculo.txtVehiculoHoja.text = detalle.vehiculo?.let { "${it.descripcion} · ${it.color}" }.orEmpty()
        vinculo.txtClienteHoja.text = detalle.cliente?.nombreCompleto ?: "Cliente sin datos"
        vinculo.txtLlegadaHoja.text = "Llega el ${Fechas.fecha(solicitud.fechaLlegada)}"

        // Descripción completa y síntomas (la sección de síntomas se oculta si no hay)
        vinculo.txtDescripcionHoja.text = solicitud.descripcion
        val haySintomas = solicitud.sintomas.isNotEmpty()
        vinculo.txtEtiquetaSintomasHoja.visibility = if (haySintomas) View.VISIBLE else View.GONE
        vinculo.txtSintomasHoja.visibility = if (haySintomas) View.VISIBLE else View.GONE
        vinculo.txtSintomasHoja.text = solicitud.sintomas.joinToString(", ")

        // Fotos del cliente; al tocar una se abre el visor a pantalla completa
        vinculo.txtSinFotosHoja.visibility = if (solicitud.fotos.isEmpty()) View.VISIBLE else View.GONE
        AyudasOrden.llenarGaleria(vinculo.llFotosHoja, solicitud.fotos) { indice ->
            VisorFoto.mostrar(parentFragmentManager, solicitud.fotos, indice)
        }

        // El ingreso solo se registra mientras la solicitud siga pendiente
        vinculo.btnRegistrarIngreso.visibility = if (solicitud.estado == EstadoSolicitud.PENDIENTE) View.VISIBLE else View.GONE
        vinculo.btnRegistrarIngreso.setOnClickListener {
            val navegador = requireActivity() as Navegador
            dismiss()
            navegador.irA(FragNuevaOrden.nuevo(solicitud.clienteId, solicitud.vehiculoId, solicitud.id))
        }
    }

    companion object {
        private const val CLAVE_SOLICITUD = "solicitudId"

        /** Abre la hoja con el detalle de la solicitud indicada. */
        fun mostrar(administrador: FragmentManager, solicitudId: String) {
            HojaSolicitudJefe().apply {
                arguments = Bundle().apply { putString(CLAVE_SOLICITUD, solicitudId) }
            }.show(administrador, "hoja_solicitud_jefe")
        }
    }
}
