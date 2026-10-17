package com.leosle.pitlane.vista.ordenes

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.leosle.pitlane.R
import com.leosle.pitlane.vista.comun.MenuAcciones
import com.leosle.pitlane.databinding.TecFragDetalleOrdenBinding
import com.leosle.pitlane.modelo.AvanceDetallado
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.viewmodel.AvanceViewModel
import com.leosle.pitlane.viewmodel.OrdenViewModel
import com.leosle.pitlane.vista.avances.FragRegistrarAvance
import com.leosle.pitlane.vista.avances.HojaEditarAvance
import com.leosle.pitlane.vista.comun.AdaptadorLista
import com.leosle.pitlane.vista.comun.BarraSuperior
import com.leosle.pitlane.vista.comun.DialogoModal
import com.leosle.pitlane.vista.comun.EstadoVacio
import com.leosle.pitlane.vista.comun.EstadosVisuales
import com.leosle.pitlane.vista.comun.FragmentoVinculado
import com.leosle.pitlane.vista.comun.Imagenes
import com.leosle.pitlane.vista.comun.VisorFoto
import com.leosle.pitlane.vista.comun.poblar

/**
 * Detalle de una orden asignada al técnico con dos secciones intercambiables (Detalle y Avances).
 * El botón principal depende del estado: iniciar reparación, o registrar avance y marcar como listo.
 * Todo se actualiza en vivo al emitir nuevos datos la orden y sus avances.
 */
class FragDetalleOrdenTecnico : FragmentoVinculado<TecFragDetalleOrdenBinding>(TecFragDetalleOrdenBinding::inflate) {

    override val mostrarBarraInferior: Boolean = false

    private val ordenViewModel: OrdenViewModel by lazy { OrdenViewModel() }
    private val avanceViewModel: AvanceViewModel by lazy { AvanceViewModel() }
    private val ordenId: String get() = requireArguments().getString(ARGUMENTO_ORDEN).orEmpty()

    private lateinit var adaptadorAvances: AdaptadorLista<AvanceDetallado>
    private var ordenActual: OrdenDetallada? = null
    private var vehiculoObservado: String? = null
    private val previasObservadas = mutableSetOf<String>()
    private var previas: List<OrdenDetallada> = emptyList()
    private val hallazgosPorOrden = mutableMapOf<String, List<String>>()

    /** Prepara barra, selector de sección, lista de avances y las observaciones de orden y avances. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(vinculo.barra, getString(R.string.tec_titulo_detalle), alVolver = { navegador.volver() })

        vinculo.rgSeccion.setOnCheckedChangeListener { _, marcado -> mostrarSeccion(marcado == R.id.rbSeccionAvances) }

        adaptadorAvances = AdaptadorLista(requireContext(), R.layout.tec_item_avance) { fila, elemento -> enlazarAvance(fila, elemento) }
        vinculo.lvAvances.adapter = adaptadorAvances
        vinculo.lvAvances.emptyView = vinculo.vacioAvances.root
        EstadoVacio.configurar(
            vinculo.vacioAvances,
            getString(R.string.tec_avances_vacio_titulo),
            getString(R.string.tec_avances_vacio_mensaje)
        )

        vinculo.btnAccionPrincipal.setOnClickListener { alPulsarAccionPrincipal() }
        vinculo.btnMarcarListo.setOnClickListener { confirmarListo() }

        recolectar(ordenViewModel.orden(ordenId, usuarioActual.id)) { detalle -> detalle?.let { pintarOrden(it) } }
        recolectar(avanceViewModel.avances(ordenId, usuarioActual.id)) { avances ->
            adaptadorAvances.actualizar(avances)
        }
    }

    /** Alterna entre la sección Avances (lista) y la sección Detalle (scroll). */
    private fun mostrarSeccion(avances: Boolean) {
        vinculo.flAvances.visibility = if (avances) View.VISIBLE else View.GONE
        vinculo.svDetalle.visibility = if (avances) View.GONE else View.VISIBLE
    }

    /** Pinta el resumen, los datos, las fotos y los botones con el estado actual de la orden. */
    private fun pintarOrden(detalle: OrdenDetallada) {
        val estadoPrevio = ordenActual?.orden?.estado
        ordenActual = detalle
        val orden = detalle.orden
        vinculo.txtNumeroResumen.text = getString(R.string.tec_orden_numero, orden.numero)
        EstadosVisuales.estadoOrden(vinculo.txtEstadoResumen, orden.estado)
        vinculo.txtPlacaResumen.text = detalle.vehiculo?.placa.orEmpty()
        vinculo.txtVehiculoResumen.text = detalle.vehiculo?.descripcion.orEmpty()

        vinculo.txtFallaDetalle.text = orden.falla
        val fotosSolicitud = detalle.solicitud?.fotos.orEmpty()
        vinculo.llFotosSolicitud.visibility = if (fotosSolicitud.isEmpty()) View.GONE else View.VISIBLE
        pintarMiniaturas(vinculo.llMiniaturasSolicitud, fotosSolicitud)

        val datos = listOf(
            getString(R.string.tec_etiqueta_color) to detalle.vehiculo?.color.orEmpty(),
            getString(R.string.tec_etiqueta_kilometraje) to getString(R.string.tec_kilometraje_valor, "%,d".format(orden.kilometraje)),
            getString(R.string.tec_etiqueta_combustible) to orden.combustible.etiqueta
        )
        vinculo.llDatosIngreso.poblar(datos, R.layout.tec_item_dato) { fila, dato ->
            fila.findViewById<TextView>(R.id.txtDatoEtiqueta).text = dato.first
            fila.findViewById<TextView>(R.id.txtDatoValor).text = dato.second
        }
        vinculo.txtObservacionesDetalle.text = orden.observaciones.ifBlank { getString(R.string.tec_sin_observaciones) }
        pintarMiniaturas(vinculo.llMiniaturasExterior, orden.fotos)

        pintarBotones(orden.estado, orden.tecnicoId == usuarioActual.id)
        observarPrevias(detalle)
        if (estadoPrevio != null && estadoPrevio != orden.estado) adaptadorAvances.notifyDataSetChanged()
    }

    /** Muestra el botón principal según el estado: iniciar (Ingresado) o registrar avance y marcar listo (En reparación). */
    private fun pintarBotones(estado: EstadoOrden, esPropia: Boolean) {
        val acciones = esPropia && (estado == EstadoOrden.INGRESADO || estado == EstadoOrden.EN_REPARACION)
        vinculo.llAcciones.visibility = if (acciones) View.VISIBLE else View.GONE
        vinculo.btnAccionPrincipal.text = getString(
            if (estado == EstadoOrden.INGRESADO) R.string.tec_boton_iniciar else R.string.tec_boton_registrar_avance
        )
        vinculo.btnMarcarListo.visibility = if (estado == EstadoOrden.EN_REPARACION) View.VISIBLE else View.GONE
    }

    /** Llena un contenedor horizontal con miniaturas que abren el visor al tocarlas. */
    private fun pintarMiniaturas(contenedor: LinearLayout, fotos: List<Foto>) {
        contenedor.poblar(fotos.withIndex().toList(), R.layout.tec_item_miniatura) { vista, elemento ->
            val imagen = vista as ImageView
            Imagenes.cargarFoto(imagen, elemento.value, 256)
            imagen.setOnClickListener { VisorFoto.mostrar(parentFragmentManager, fotos, elemento.index) }
        }
    }

    /** Observa una sola vez las órdenes del mismo vehículo y los hallazgos de cada una para mostrar el historial. */
    private fun observarPrevias(detalle: OrdenDetallada) {
        val vehiculoId = detalle.orden.vehiculoId
        if (vehiculoObservado == vehiculoId) return
        vehiculoObservado = vehiculoId
        recolectar(ordenViewModel.ordenesDeVehiculo(vehiculoId)) { lista ->
            previas = lista.filter { it.orden.id != ordenId }
            previas.forEach { previa ->
                if (previasObservadas.add(previa.orden.id)) {
                    recolectar(avanceViewModel.avances(previa.orden.id, usuarioActual.id)) { avances ->
                        hallazgosPorOrden[previa.orden.id] = avances
                            .filter { it.avance.tipo == TipoAvance.HALLAZGO }
                            .map { it.avance.descripcion }
                        pintarPrevias()
                    }
                }
            }
            pintarPrevias()
        }
    }

    /** Dibuja las órdenes anteriores del vehículo con su falla y sus hallazgos, o un texto si no hay ninguna. */
    private fun pintarPrevias() {
        vinculo.txtSinPrevias.visibility = if (previas.isEmpty()) View.VISIBLE else View.GONE
        vinculo.llOrdenesPrevias.poblar(previas, R.layout.tec_item_orden_previa) { fila, previa ->
            fila.findViewById<TextView>(R.id.txtPreviaNumero).text = getString(R.string.tec_orden_numero, previa.orden.numero)
            EstadosVisuales.estadoOrden(fila.findViewById(R.id.txtPreviaEstado), previa.orden.estado)
            fila.findViewById<TextView>(R.id.txtPreviaFecha).text = Fechas.fecha(previa.orden.creadaEn)
            fila.findViewById<TextView>(R.id.txtPreviaFalla).text = previa.orden.falla
            val hallazgos = hallazgosPorOrden[previa.orden.id].orEmpty()
            fila.findViewById<TextView>(R.id.txtPreviaHallazgos).text =
                if (hallazgos.isEmpty()) getString(R.string.tec_sin_hallazgos)
                else getString(R.string.tec_hallazgos_previa) + ": " + hallazgos.joinToString("; ")
        }
    }

    /** Llena una fila de avance: ícono por tipo, descripción, miniaturas, fecha, autor y menú si es propio y editable. */
    private fun enlazarAvance(fila: View, elemento: AvanceDetallado) {
        val avance = elemento.avance
        val esHallazgo = avance.tipo == TipoAvance.HALLAZGO
        fila.findViewById<ImageView>(R.id.imgTipoAvance).setImageResource(
            if (esHallazgo) R.drawable.ic_magnifying_glass else R.drawable.ic_wrench
        )
        fila.findViewById<TextView>(R.id.txtTipoAvance).text = avance.tipo.etiqueta
        fila.findViewById<TextView>(R.id.txtFechaAvance).text = Fechas.fechaHora(avance.fechaHora)
        fila.findViewById<TextView>(R.id.txtDescripcionAvance).text = avance.descripcion
        fila.findViewById<TextView>(R.id.txtAutorAvance).text =
            getString(R.string.tec_avance_por, elemento.tecnico?.nombreCompleto.orEmpty())

        fila.findViewById<View>(R.id.hsvFotosAvance).visibility = if (avance.fotos.isEmpty()) View.GONE else View.VISIBLE
        pintarMiniaturas(fila.findViewById(R.id.llFotosAvance), avance.fotos)

        val opciones = fila.findViewById<View>(R.id.btnOpcionesAvance)
        val editable = avance.tecnicoId == usuarioActual.id && ordenActual?.orden?.estado == EstadoOrden.EN_REPARACION
        opciones.visibility = if (editable) View.VISIBLE else View.GONE
        opciones.setOnClickListener { mostrarMenuAvance(opciones, elemento) }
    }

    /** Abre el menú de Editar y Eliminar anclado al botón de opciones del avance. */
    private fun mostrarMenuAvance(ancla: View, elemento: AvanceDetallado) {
        MenuAcciones.mostrar(
            ancla,
            listOf(
                MenuAcciones.Opcion(R.drawable.ic_pencil_simple, getString(R.string.tec_menu_editar)) {
                    HojaEditarAvance.mostrar(parentFragmentManager, ordenId, elemento.avance.id, usuarioActual.id)
                },
                MenuAcciones.Opcion(R.drawable.ic_trash, getString(R.string.tec_menu_eliminar), peligro = true) { confirmarEliminar(elemento) }
            )
        )
    }

    /** Con la orden Ingresado pide confirmar el inicio de la reparación; con En reparación abre el formulario de avance. */
    private fun alPulsarAccionPrincipal() {
        when (ordenActual?.orden?.estado) {
            EstadoOrden.INGRESADO -> confirmarInicio()
            EstadoOrden.EN_REPARACION -> navegador.irA(FragRegistrarAvance.nuevo(ordenId))
            else -> Unit
        }
    }

    /** Pide confirmar el inicio de la reparación y, al aceptar, cambia el estado de la orden. */
    private fun confirmarInicio() {
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_wrench,
            getString(R.string.tec_dialogo_iniciar_titulo),
            getString(R.string.tec_dialogo_iniciar_mensaje),
            getString(R.string.tec_dialogo_iniciar_accion)
        ) {
            ordenViewModel.iniciarReparacion(ordenId, usuarioActual.id) { resultado ->
                if (!isAdded || view == null) return@iniciarReparacion
                when (resultado) {
                    is Resultado.Exito -> mostrarMensaje(getString(R.string.tec_mensaje_reparacion_iniciada))
                    is Resultado.Error -> DialogoModal.error(requireContext(), resultado.mensaje)
                }
            }
        }
    }

    /** Pide confirmar que el vehículo está listo; si hay trabajo realizado cambia el estado y regresa a la lista. */
    private fun confirmarListo() {
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_check_circle,
            getString(R.string.tec_dialogo_listo_titulo),
            getString(R.string.tec_dialogo_listo_mensaje),
            getString(R.string.tec_dialogo_listo_accion)
        ) {
            ordenViewModel.marcarListo(ordenId, usuarioActual.id) { resultado ->
                if (!isAdded || view == null) return@marcarListo
                when (resultado) {
                    is Resultado.Exito -> {
                        mostrarMensaje(getString(R.string.tec_mensaje_orden_lista))
                        navegador.volver()
                    }
                    is Resultado.Error -> DialogoModal.error(requireContext(), resultado.mensaje)
                }
            }
        }
    }

    /** Pide confirmar la eliminación del avance y, al aceptar, lo elimina junto con sus fotos. */
    private fun confirmarEliminar(elemento: AvanceDetallado) {
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_trash,
            getString(R.string.tec_dialogo_eliminar_titulo),
            getString(R.string.tec_dialogo_eliminar_mensaje),
            getString(R.string.tec_dialogo_eliminar_accion),
            peligro = true
        ) {
            avanceViewModel.eliminarAvance(elemento.avance.id, usuarioActual.id) { resultado ->
                if (!isAdded || view == null) return@eliminarAvance
                when (resultado) {
                    is Resultado.Exito -> mostrarMensaje(getString(R.string.tec_mensaje_avance_eliminado))
                    is Resultado.Error -> DialogoModal.error(requireContext(), resultado.mensaje)
                }
            }
        }
    }

    companion object {
        private const val ARGUMENTO_ORDEN = "ordenId"

        /** Crea el detalle de la orden asignada indicada. */
        fun nuevo(ordenId: String): FragDetalleOrdenTecnico =
            FragDetalleOrdenTecnico().apply { arguments = Bundle().apply { putString(ARGUMENTO_ORDEN, ordenId) } }
    }
}
