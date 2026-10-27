package com.leosle.pitlane.arquitectura.vista.notificaciones

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.vista.ordenes.FragComprobante
import com.leosle.pitlane.databinding.CliFragBandejaBinding
import com.leosle.pitlane.databinding.CliItemNotificacionBinding
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.arquitectura.viewmodel.NotificacionViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AdaptadorLista
import com.leosle.pitlane.arquitectura.vista.comun.EstadoVacio
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado

/**
 * Pestaña raíz del cliente con la bandeja de avisos, de la más reciente a la más antigua. Las no leídas se resaltan
 * con un punto y fondo de contenedor primario; tocar un aviso lo marca como leído y abre la orden relacionada.
 */
class FragBandeja : FragmentoVinculado<CliFragBandejaBinding>(CliFragBandejaBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val notificacionViewModel: NotificacionViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<Notificacion>

    /** Prepara la lista y el estado vacío, y observa las notificaciones y el contador de no leídas del cliente. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adaptador = AdaptadorLista(requireContext(), R.layout.cli_item_notificacion) { vista, elemento -> enlazarFila(vista, elemento) }
        vinculo.lvNotificaciones.adapter = adaptador
        vinculo.lvNotificaciones.emptyView = vinculo.incVacio.root
        vinculo.lvNotificaciones.setOnItemClickListener { _, _, posicion, _ -> abrir(adaptador.getItem(posicion)) }
        EstadoVacio.configurar(
            vinculo.incVacio,
            getString(R.string.cli_bandeja_vacia_titulo),
            getString(R.string.cli_bandeja_vacia_mensaje)
        )
        recolectar(notificacionViewModel.notificaciones(usuarioActual.id)) { adaptador.actualizar(it) }
        recolectar(notificacionViewModel.noLeidas(usuarioActual.id)) { mostrarContador(it) }
    }

    /** Escribe bajo el título cuántos avisos faltan por leer o que no hay pendientes. */
    private fun mostrarContador(cantidad: Int) {
        vinculo.txtSubtituloBandeja.text = if (cantidad > 0) {
            resources.getQuantityString(R.plurals.cli_avisos_sin_leer, cantidad, cantidad)
        } else {
            getString(R.string.cli_avisos_al_dia)
        }
    }

    /** Llena una fila con título, mensaje y tiempo transcurrido; las no leídas llevan punto y fondo resaltado. */
    private fun enlazarFila(vista: View, notificacion: Notificacion) {
        val fila = CliItemNotificacionBinding.bind(vista)
        fila.txtTituloNotificacion.text = notificacion.titulo
        fila.txtMensajeNotificacion.text = notificacion.mensaje
        fila.txtHoraNotificacion.text = Fechas.hace(notificacion.fechaHora, System.currentTimeMillis())
        fila.viewPuntoNoLeida.visibility = if (notificacion.leida) View.GONE else View.VISIBLE
        val colorFondo = if (notificacion.leida) R.color.superficie else R.color.contenedor_primario
        fila.mcvNotificacion.setCardBackgroundColor(ContextCompat.getColor(vista.context, colorFondo))
    }

    /** Marca el aviso como leído si hacía falta; el de ingreso abre el comprobante y los demás el estado de la orden. */
    private fun abrir(notificacion: Notificacion) {
        if (!notificacion.leida) notificacionViewModel.marcarLeida(notificacion.id)
        val ordenId = notificacion.ordenId
        when {
            ordenId == null -> Unit
            notificacion.esIngreso -> navegador.irA(FragComprobante.nuevo(ordenId))
            else -> navegador.abrirOrden(ordenId)
        }
    }
}
