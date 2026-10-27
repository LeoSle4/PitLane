package com.leosle.pitlane.arquitectura.vista.ordenes

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliFragEstadoOrdenBinding
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.arquitectura.viewmodel.OrdenViewModel
import com.leosle.pitlane.arquitectura.vista.avances.FragHallazgos
import com.leosle.pitlane.arquitectura.vista.comun.Animaciones
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.EstadosVisuales
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.arquitectura.vista.comun.LogosMarca

/**
 * Estado actual de una orden del cliente: encabezado con la marca, foto del taller, estado actual con su ícono, fecha del último cambio, técnico,
 * progreso en tres pasos y accesos a la línea de tiempo, los hallazgos, el comprobante y, si ya se entregó, la constancia de entrega. Se actualiza sola porque observa el flujo de la orden; en "Listo para recoger" reproduce un check animado.
 */
class FragEstadoOrden : FragmentoVinculado<CliFragEstadoOrdenBinding>(CliFragEstadoOrdenBinding::inflate) {

    override val mostrarBarraInferior: Boolean = false

    private val ordenViewModel: OrdenViewModel by viewModels()
    private var estadoMostrado: EstadoOrden? = null

    /** Configura la barra superior y los botones de acceso, y observa la orden indicada en los argumentos. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ordenId = requireArguments().getString(ARGUMENTO_ORDEN).orEmpty()
        BarraSuperior.configurar(vinculo.barra, getString(R.string.cli_estado_orden_titulo), alVolver = { navegador.volver() })
        vinculo.btnLineaTiempo.setOnClickListener { navegador.irA(FragLineaTiempo.nuevo(ordenId)) }
        vinculo.btnHallazgos.setOnClickListener { navegador.irA(FragHallazgos.nuevo(ordenId)) }
        vinculo.btnComprobante.setOnClickListener { navegador.irA(FragComprobante.nuevo(ordenId)) }
        vinculo.btnConstancia.setOnClickListener { navegador.irA(FragConstancia.nuevo(ordenId)) }
        recolectar(ordenViewModel.orden(ordenId, usuarioActual.id)) { detalle -> pintar(detalle) }
    }

    /** Escribe en pantalla los datos de la orden; si pertenece a otro cliente no la muestra y vuelve atrás. */
    private fun pintar(detalle: OrdenDetallada?) {
        if (detalle == null) return
        if (detalle.orden.clienteId != usuarioActual.id) {
            mostrarMensaje(getString(R.string.cli_orden_no_disponible))
            navegador.volver()
            return
        }
        val orden = detalle.orden
        val estado = orden.estado
        vinculo.svEstadoOrden.visibility = View.VISIBLE
        vinculo.txtNumeroOrden.text = orden.numero
        vinculo.txtVehiculoOrden.text = detalle.vehiculo?.let { "${it.placa} · ${it.descripcion}" }.orEmpty()
        vinculo.txtEstadoGrande.text = estado.etiqueta
        vinculo.txtEstadoGrande.setTextColor(ContextCompat.getColor(requireContext(), EstadosVisuales.colorEstado(estado)))
        vinculo.txtUltimoCambio.text = getString(R.string.cli_ultimo_cambio, Fechas.fechaHora(orden.fechaUltimoCambio))
        vinculo.txtTecnicoOrden.text = getString(
            R.string.cli_tecnico_asignado,
            detalle.tecnico?.nombreCompleto ?: getString(R.string.cli_sin_tecnico)
        )
        val motivo = orden.motivoAnulacion
        vinculo.txtMotivoAnulacion.visibility = if (estado == EstadoOrden.ANULADA && motivo != null) View.VISIBLE else View.GONE
        vinculo.txtMotivoAnulacion.text = getString(R.string.cli_motivo_anulacion, motivo.orEmpty())
        vinculo.btnConstancia.visibility = if (estado == EstadoOrden.ENTREGADO) View.VISIBLE else View.GONE
        LogosMarca.aplicar(vinculo.imgMarcaOrdenEstado, detalle.vehiculo?.marca.orEmpty())
        pintarIcono(estado)
        pintarProgreso(orden)
    }

    /**
     * Pinta los tres pasos: el actual en el color de marca, los ya alcanzados en verde y los pendientes en gris
     * atenuado, con el riel en verde entre pasos recorridos.
     */
    private fun pintarProgreso(orden: Orden) {
        val contexto = requireContext()
        val progreso = vinculo.progreso
        val pasos = listOf(
            Triple(EstadoOrden.INGRESADO, progreso.imgPasoIngreso, progreso.txtPasoIngreso),
            Triple(EstadoOrden.EN_REPARACION, progreso.imgPasoReparacion, progreso.txtPasoReparacion),
            Triple(EstadoOrden.LISTO, progreso.imgPasoListo, progreso.txtPasoListo)
        )
        val alcanzados = pasos.map { (estado, _, _) -> orden.fechaDeEstado(estado) != null || orden.estado == estado }
        pasos.forEachIndexed { indice, (estado, imagen, texto) ->
            val enCurso = orden.estado == estado && estado != EstadoOrden.LISTO
            val colorFondo = when {
                enCurso -> R.color.primario
                alcanzados[indice] -> R.color.estado_exito_texto
                else -> R.color.linea_pendiente
            }
            val colorIcono = if (alcanzados[indice]) R.color.sobre_primario else R.color.texto_secundario
            imagen.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, colorFondo))
            imagen.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, colorIcono))
            texto.alpha = if (alcanzados[indice]) 1f else 0.5f
        }
        val verde = ColorStateList.valueOf(ContextCompat.getColor(contexto, R.color.estado_exito_texto))
        progreso.viewRielPrimero.backgroundTintList = if (alcanzados[1]) verde else null
        progreso.viewRielSegundo.backgroundTintList = if (alcanzados[2]) verde else null
    }

    /** Muestra el ícono del estado con sus colores; al pasar a "Listo para recoger" reproduce una vez el check con destellos. */
    private fun pintarIcono(estado: EstadoOrden) {
        val contexto = requireContext()
        val icono = vinculo.imgIconoEstado
        icono.setImageResource(EstadosVisuales.iconoEstado(estado))
        icono.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, EstadosVisuales.colorEstado(estado)))
        icono.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, EstadosVisuales.colorFondoEstado(estado)))
        val esListo = estado == EstadoOrden.LISTO
        icono.visibility = if (esListo) View.INVISIBLE else View.VISIBLE
        vinculo.imgCheckListo.visibility = if (esListo) View.VISIBLE else View.GONE
        if (esListo && estadoMostrado != EstadoOrden.LISTO) {
            Animaciones.reproducir(vinculo.imgCheckListo, R.drawable.avd_check_destellos)
        }
        estadoMostrado = estado
    }

    companion object {
        private const val ARGUMENTO_ORDEN = "ordenId"

        /** Crea el estado de la orden indicada. */
        fun nuevo(ordenId: String): FragEstadoOrden =
            FragEstadoOrden().apply { arguments = Bundle().apply { putString(ARGUMENTO_ORDEN, ordenId) } }
    }
}
