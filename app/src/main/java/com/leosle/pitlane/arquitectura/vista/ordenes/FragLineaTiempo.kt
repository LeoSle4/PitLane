package com.leosle.pitlane.arquitectura.vista.ordenes

import android.animation.Animator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliFragLineaTiempoBinding
import com.leosle.pitlane.databinding.CliItemEtapaBinding
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.arquitectura.viewmodel.OrdenViewModel
import com.leosle.pitlane.arquitectura.vista.comun.Animaciones
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.EstadosVisuales
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.arquitectura.vista.comun.poblar

/**
 * Línea de tiempo vertical de una orden: etapas cumplidas en verde con su fecha, etapa actual resaltada con un halo
 * que pulsa, etapas pendientes atenuadas, cada una con su ícono en un círculo de color. Una orden anulada termina en "Anulada" con el motivo.
 */
class FragLineaTiempo : FragmentoVinculado<CliFragLineaTiempoBinding>(CliFragLineaTiempoBinding::inflate) {

    private val ordenViewModel: OrdenViewModel by viewModels()
    private var pulso: Animator? = null

    /** Situación de una etapa dentro de la línea de tiempo. */
    private enum class Situacion { CUMPLIDA, ACTUAL, PENDIENTE, ANULADA }

    /** Datos de una etapa lista para dibujar. */
    private data class Etapa(
        val estado: EstadoOrden,
        val fecha: String,
        val situacion: Situacion,
        val detalle: String? = null
    )

    /** Configura la barra superior y observa la orden para dibujar sus etapas. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ordenId = requireArguments().getString(ARGUMENTO_ORDEN).orEmpty()
        BarraSuperior.configurar(vinculo.barra, getString(R.string.cli_linea_tiempo_titulo), alVolver = { navegador.volver() })
        recolectar(ordenViewModel.orden(ordenId, usuarioActual.id)) { detalle -> pintar(detalle) }
    }

    /** Detiene el pulso del punto actual al destruirse la vista para no dejar animadores activos. */
    override fun onDestroyView() {
        pulso?.cancel()
        pulso = null
        super.onDestroyView()
    }

    /** Escribe el encabezado y dibuja las etapas de la orden; ignora órdenes de otro cliente. */
    private fun pintar(detalle: OrdenDetallada?) {
        if (detalle == null || detalle.orden.clienteId != usuarioActual.id) return
        vinculo.txtNumeroOrden.text = detalle.orden.numero
        vinculo.txtVehiculoOrden.text = detalle.vehiculo?.let { "${it.placa} · ${it.descripcion}" }.orEmpty()
        val etapas = armarEtapas(detalle)
        pulso?.cancel()
        pulso = null
        vinculo.llEtapas.poblar(etapas.withIndex().toList(), R.layout.cli_item_etapa) { vista, elemento ->
            enlazarEtapa(vista, elemento.value, elemento.index, etapas)
        }
    }

    /**
     * Arma las etapas: Ingresado, En reparación y Listo con su situación según el historial, más Entregado (pendiente hasta que el cliente retira el vehículo); si la orden fue anulada
     * muestra solo las etapas alcanzadas y cierra con "Anulada" y su motivo.
     */
    private fun armarEtapas(detalle: OrdenDetallada): List<Etapa> {
        val orden = detalle.orden
        val principales = listOf(EstadoOrden.INGRESADO, EstadoOrden.EN_REPARACION, EstadoOrden.LISTO)
        if (orden.estado == EstadoOrden.ANULADA) {
            val alcanzadas = principales.mapNotNull { estado ->
                orden.fechaDeEstado(estado)?.let { Etapa(estado, Fechas.fechaHora(it), Situacion.CUMPLIDA) }
            }
            val fechaAnulacion = orden.fechaDeEstado(EstadoOrden.ANULADA) ?: orden.fechaUltimoCambio
            val motivo = getString(R.string.cli_motivo_anulacion, orden.motivoAnulacion.orEmpty())
            return alcanzadas + Etapa(EstadoOrden.ANULADA, Fechas.fechaHora(fechaAnulacion), Situacion.ANULADA, motivo)
        }
        val etapas = principales.map { estado ->
            val fecha = orden.fechaDeEstado(estado)
            when {
                estado == orden.estado -> Etapa(estado, Fechas.fechaHora(fecha ?: orden.fechaUltimoCambio), Situacion.ACTUAL)
                fecha != null -> Etapa(estado, Fechas.fechaHora(fecha), Situacion.CUMPLIDA)
                else -> Etapa(estado, getString(R.string.cli_etapa_pendiente), Situacion.PENDIENTE)
            }
        }
        val fechaEntrega = orden.fechaDeEstado(EstadoOrden.ENTREGADO)
        return etapas + if (fechaEntrega != null) {
            Etapa(EstadoOrden.ENTREGADO, Fechas.fechaHora(fechaEntrega), Situacion.CUMPLIDA)
        } else {
            Etapa(EstadoOrden.ENTREGADO, getString(R.string.cli_etapa_pendiente), Situacion.PENDIENTE)
        }
    }

    /**
     * Llena una fila: ilustración de la etapa con halo y marca de cumplida, tarjeta con nombre, situación y fecha, y
     * riel hacia la etapa siguiente que se tiñe de verde cuando el tramo ya fue recorrido.
     */
    private fun enlazarEtapa(vista: View, etapa: Etapa, indice: Int, etapas: List<Etapa>) {
        val fila = CliItemEtapaBinding.bind(vista)
        val contexto = vista.context
        fila.txtNombreEtapa.text = etapa.estado.etiqueta
        fila.txtFechaEtapa.text = etapa.fecha
        fila.txtDetalleEtapa.visibility = if (etapa.detalle != null) View.VISIBLE else View.GONE
        fila.txtDetalleEtapa.text = etapa.detalle
        fila.imgEtapa.setImageResource(EstadosVisuales.iconoEstado(etapa.estado))

        val colorHalo = when (etapa.situacion) {
            Situacion.CUMPLIDA -> R.color.estado_exito_texto
            Situacion.ACTUAL, Situacion.ANULADA -> R.color.primario
            Situacion.PENDIENTE -> R.color.linea_pendiente
        }
        fila.viewHaloEtapa.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, colorHalo))
        val colorIcono = if (etapa.situacion == Situacion.PENDIENTE) R.color.texto_secundario else R.color.sobre_primario
        fila.imgEtapa.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, colorHalo))
        fila.imgEtapa.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, colorIcono))
        if (etapa.situacion == Situacion.ACTUAL) pulso = Animaciones.repetir(fila.viewHaloEtapa, R.animator.pulso_halo)

        val cumplida = etapa.situacion == Situacion.CUMPLIDA
        val conMarca = cumplida && etapa.estado != EstadoOrden.LISTO
        fila.imgInsigniaEtapa.visibility = if (conMarca) View.VISIBLE else View.GONE
        if (conMarca) {
            fila.imgInsigniaEtapa.setImageResource(R.drawable.ic_check_circle_fill)
            fila.imgInsigniaEtapa.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, R.color.estado_exito_texto))
        }
        pintarSituacion(fila, etapa)

        val esUltima = indice == etapas.lastIndex
        fila.viewLineaInferior.visibility = if (esUltima) View.INVISIBLE else View.VISIBLE
        val siguiente = etapas.getOrNull(indice + 1)
        val tramoAlcanzado = cumplida && siguiente != null && siguiente.situacion != Situacion.PENDIENTE
        fila.viewLineaInferior.backgroundTintList =
            if (tramoAlcanzado) ColorStateList.valueOf(ContextCompat.getColor(contexto, R.color.estado_exito_texto)) else null
    }

    /** Colorea el chip y el borde de la tarjeta según la situación; la etapa actual se resalta con borde y fondo rojizos. */
    private fun pintarSituacion(fila: CliItemEtapaBinding, etapa: Etapa) {
        val contexto = fila.root.context
        val chip = fila.txtSituacionEtapa
        when (etapa.situacion) {
            Situacion.CUMPLIDA -> EstadosVisuales.aplicar(chip, "Completado", R.color.estado_exito_fondo, R.color.estado_exito_texto, R.drawable.ic_check_circle)
            Situacion.ACTUAL -> EstadosVisuales.aplicar(chip, "En curso", R.color.estado_proceso_fondo, R.color.estado_proceso_texto, R.drawable.ic_timer)
            Situacion.PENDIENTE -> EstadosVisuales.aplicar(chip, "Pendiente", R.color.estado_neutro_fondo, R.color.estado_neutro_texto, R.drawable.ic_clock)
            Situacion.ANULADA -> EstadosVisuales.aplicar(chip, "Anulada", R.color.estado_error_fondo, R.color.estado_error_texto, R.drawable.ic_x_circle)
        }
        val destacada = etapa.situacion == Situacion.ACTUAL || etapa.situacion == Situacion.ANULADA
        val densidad = contexto.resources.displayMetrics.density
        fila.mcvEtapa.strokeColor = ContextCompat.getColor(contexto, if (destacada) R.color.primario else R.color.contorno)
        fila.mcvEtapa.strokeWidth = ((if (destacada) 2 else 1) * densidad).toInt()
        fila.mcvEtapa.setCardBackgroundColor(ContextCompat.getColor(contexto, if (destacada) R.color.contenedor_primario else R.color.superficie))
        fila.mcvEtapa.alpha = if (etapa.situacion == Situacion.PENDIENTE) 0.7f else 1f
    }

    companion object {
        private const val ARGUMENTO_ORDEN = "ordenId"

        /** Crea la línea de tiempo de la orden indicada. */
        fun nuevo(ordenId: String): FragLineaTiempo =
            FragLineaTiempo().apply { arguments = Bundle().apply { putString(ARGUMENTO_ORDEN, ordenId) } }
    }
}
