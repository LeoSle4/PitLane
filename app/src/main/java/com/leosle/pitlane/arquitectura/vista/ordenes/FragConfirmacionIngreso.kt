package com.leosle.pitlane.arquitectura.vista.ordenes

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JoFragConfirmacionIngresoBinding
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.arquitectura.viewmodel.OrdenViewModel
import com.leosle.pitlane.arquitectura.vista.comun.Animaciones
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado

/**
 * Confirmación de un ingreso registrado: check animado, número de orden grande y el mensaje de aviso al cliente
 * (con la vista previa del aviso si el cliente tiene la app, o el número que debe entregarse si no la tiene).
 */
class FragConfirmacionIngreso : FragmentoVinculado<JoFragConfirmacionIngresoBinding>(JoFragConfirmacionIngresoBinding::inflate) {

    private val modelo: OrdenViewModel by viewModels()

    private val ordenId: String get() = requireArguments().getString(CLAVE_ORDEN).orEmpty()
    private val clienteNotificado: Boolean get() = requireArguments().getBoolean(CLAVE_NOTIFICADO)

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)

        // El check se dibuja una vez al abrir la pantalla
        Animaciones.reproducir(vinculo.imgCheckIngreso, R.drawable.avd_check_dibujado)

        // Ver la orden reemplaza esta pantalla; ir al panel vuelve a la primera pestaña
        vinculo.btnVerOrden.setOnClickListener { navegador.irA(FragDetalleOrdenJefe.nuevo(ordenId), apilar = false) }
        vinculo.btnIrPanel.setOnClickListener { navegador.cambiarPestana(0) }

        recolectar(modelo.orden(ordenId, usuarioActual.id)) { detalle ->
            if (detalle != null) mostrar(detalle)
        }
    }

    /** Escribe el número de orden y el mensaje que corresponde a si el cliente fue notificado. */
    private fun mostrar(detalle: OrdenDetallada) {
        val numero = detalle.orden.numero
        vinculo.txtNumeroConfirmacion.text = numero
        val vistaPrevia = vinculo.incVistaPrevia
        if (clienteNotificado) {
            vinculo.txtMensajeNotificacion.text = "Cliente notificado"
            vinculo.txtEtiquetaVistaPrevia.visibility = View.VISIBLE
            vistaPrevia.root.visibility = View.VISIBLE
            vistaPrevia.txtAvisoTitulo.text = "Tu vehículo ${detalle.vehiculo?.placa.orEmpty()} fue ingresado"
            vistaPrevia.txtAvisoMensaje.text = "Orden $numero"
            vistaPrevia.txtAvisoHora.text = "ahora"
        } else {
            vinculo.txtMensajeNotificacion.text = "El cliente no tiene la app: entrégale el número de orden $numero"
            vinculo.txtEtiquetaVistaPrevia.visibility = View.GONE
            vistaPrevia.root.visibility = View.GONE
        }
    }

    companion object {
        private const val CLAVE_ORDEN = "ordenId"
        private const val CLAVE_NOTIFICADO = "clienteNotificado"

        /** Crea la confirmación de la orden indicada; [clienteNotificado] indica si el cliente recibió el aviso. */
        fun nuevo(ordenId: String, clienteNotificado: Boolean): FragConfirmacionIngreso =
            FragConfirmacionIngreso().apply {
                arguments = Bundle().apply {
                    putString(CLAVE_ORDEN, ordenId)
                    putBoolean(CLAVE_NOTIFICADO, clienteNotificado)
                }
            }
    }
}
