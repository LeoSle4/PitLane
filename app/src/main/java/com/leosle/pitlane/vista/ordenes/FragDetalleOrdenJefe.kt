package com.leosle.pitlane.vista.ordenes

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JoFragDetalleOrdenBinding
import com.leosle.pitlane.databinding.JoItemHistorialBinding
import com.leosle.pitlane.modelo.CambioEstado
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.TecnicoConCarga
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.viewmodel.TecnicoViewModel
import com.leosle.pitlane.viewmodel.OrdenViewModel
import com.leosle.pitlane.vista.comun.BarraSuperior
import com.leosle.pitlane.vista.comun.DialogoModal
import com.leosle.pitlane.vista.comun.EstadosVisuales
import com.leosle.pitlane.vista.comun.FragmentoVinculado
import com.leosle.pitlane.vista.comun.GestorFotos
import com.leosle.pitlane.vista.comun.VisorFoto
import com.leosle.pitlane.vista.comun.poblar

/**
 * Detalle de una orden para el Jefe: cliente, vehículo, datos del ingreso, fotos, aviso de vehículo listo e historial.
 * Según el estado ofrece editar, anular, agregar o eliminar fotos y reenviar el aviso al cliente.
 */
class FragDetalleOrdenJefe : FragmentoVinculado<JoFragDetalleOrdenBinding>(JoFragDetalleOrdenBinding::inflate) {

    override val mostrarBarraInferior: Boolean = false

    private val modelo: OrdenViewModel by viewModels()
    private val tecnicoModelo: TecnicoViewModel by viewModels()
    private var tecnicos: List<TecnicoConCarga> = emptyList()
    private var detalle: OrdenDetallada? = null

    /** Gestor que obtiene las fotos nuevas de la cámara o la galería; debe crearse junto con el fragmento. */
    private val gestorFotos = GestorFotos(this, AyudasOrden.MAXIMO_FOTOS, AyudasOrden.MENSAJE_MAXIMO_FOTOS) { fotos -> guardarFotosNuevas(fotos) }

    private val ordenId: String get() = requireArguments().getString(CLAVE_ORDEN).orEmpty()

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(vinculo.incBarra, "Detalle de la orden", alVolver = { navegador.volver() })
        gestorFotos.vincular(vinculo.incZonaFotosOculta)

        // Acciones de la pantalla
        vinculo.btnAgregarFotosDetalle.setOnClickListener { agregarFotos() }
        vinculo.btnEditarOrden.setOnClickListener { PantallaEdicionOrden.mostrar(parentFragmentManager, ordenId) }
        vinculo.btnAnularOrden.setOnClickListener { pedirAnulacion() }
        vinculo.btnReenviarAviso.setOnClickListener { reenviarAviso() }
        vinculo.btnMarcarEntregada.setOnClickListener { confirmarEntrega() }

        // Los nombres del historial salen de la lista de técnicos, incluso de quien ya no tiene la orden asignada
        recolectar(tecnicoModelo.tecnicos) { lista ->
            tecnicos = lista
            detalle?.let { mostrarHistorial(it) }
        }

        // La pantalla se redibuja con cada cambio de la orden
        recolectar(modelo.orden(ordenId, usuarioActual.id)) { nuevo ->
            if (nuevo != null) {
                detalle = nuevo
                mostrar(nuevo)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------

    /** Escribe todas las secciones de la orden y muestra solo las acciones que permite su estado. */
    private fun mostrar(datos: OrdenDetallada) {
        val orden = datos.orden
        val ahora = System.currentTimeMillis()

        // Encabezado con barra lateral y chip del estado
        AyudasOrden.pintarBarraEstado(vinculo.viewBarraEstadoDetalle, orden.estado)
        vinculo.txtNumeroDetalle.text = orden.numero
        EstadosVisuales.estadoOrden(vinculo.txtEstadoDetalle, orden.estado)
        vinculo.txtIngresoDetalle.text = "Ingresó el ${Fechas.fechaHora(orden.creadaEn)}"

        // Cliente y vehículo
        vinculo.txtClienteNombre.text = datos.cliente?.nombreCompleto ?: "Cliente sin datos"
        vinculo.txtClienteContacto.text = datos.cliente?.let { "DNI ${it.dni} · Cel. ${it.telefono}" }.orEmpty()
        vinculo.txtVehiculoPlaca.text = datos.vehiculo?.placa.orEmpty()
        vinculo.txtVehiculoDatos.text = datos.vehiculo?.let { "${it.descripcion} · ${it.color}" }.orEmpty()

        // Datos del ingreso
        vinculo.txtFallaDetalle.text = orden.falla
        vinculo.txtKilometrajeDetalle.text = "${orden.kilometraje} km"
        vinculo.txtCombustibleDetalle.text = orden.combustible.etiqueta
        vinculo.txtObservacionesDetalle.text = orden.observaciones.ifBlank { "Sin observaciones" }
        vinculo.txtTecnicoDetalle.text = datos.tecnico?.nombreCompleto ?: "Sin técnico"

        // Motivo de anulación solo si la orden fue anulada
        vinculo.mcvMotivoAnulacion.visibility = if (orden.estado == EstadoOrden.ANULADA) View.VISIBLE else View.GONE
        vinculo.txtMotivoAnulacion.text = orden.motivoAnulacion.orEmpty()

        mostrarFotos(datos)
        mostrarAviso(datos, ahora)
        mostrarHistorial(datos)

        // Editar en Ingresado y En reparación; anular solo en Ingresado
        val puedeEditar = orden.estado == EstadoOrden.INGRESADO || orden.estado == EstadoOrden.EN_REPARACION
        vinculo.llAccionesDetalle.visibility = if (puedeEditar) View.VISIBLE else View.GONE
        vinculo.btnAnularOrden.visibility = if (orden.estado == EstadoOrden.INGRESADO) View.VISIBLE else View.GONE
    }

    /** Dibuja la galería de fotos; el botón de agregar solo aparece mientras la orden está Ingresado. */
    private fun mostrarFotos(datos: OrdenDetallada) {
        val orden = datos.orden
        AyudasOrden.llenarGaleria(vinculo.llFotosDetalle, orden.fotos) { indice -> abrirVisor(datos, indice) }
        vinculo.txtContadorFotosDetalle.text = "${orden.fotos.size} / ${AyudasOrden.MAXIMO_FOTOS}"
        vinculo.btnAgregarFotosDetalle.visibility = if (orden.estado == EstadoOrden.INGRESADO) View.VISIBLE else View.GONE
    }

    /** En estado Listo muestra el último aviso y el botón de reenviar, deshabilitado hasta pasadas 24 horas. */
    private fun mostrarAviso(datos: OrdenDetallada, ahora: Long) {
        val orden = datos.orden
        vinculo.mcvAvisoDetalle.visibility = if (orden.estado == EstadoOrden.LISTO) View.VISIBLE else View.GONE
        val ultimoAviso = orden.ultimoAviso
        vinculo.txtUltimoAviso.text = if (ultimoAviso == null) {
            "Aún no se envió ningún aviso"
        } else {
            val enviados = if (orden.avisosEnviados == 1) "enviado" else "enviados"
            "Último aviso: ${Fechas.fechaHoraRelativa(ultimoAviso, ahora)} · ${orden.avisosEnviados} $enviados"
        }
        val horas = orden.horasParaReenviar(ahora)
        vinculo.btnReenviarAviso.isEnabled = horas == 0
        vinculo.btnReenviarAviso.text = if (horas == 0) "Reenviar aviso" else "Disponible en $horas h"
    }

    /** Dibuja el historial de estados de la línea de tiempo, del más reciente al más antiguo. */
    private fun mostrarHistorial(datos: OrdenDetallada) {
        val historial = datos.orden.historial
        vinculo.llHistorial.poblar(historial.asReversed(), R.layout.jo_item_historial) { fila, cambio ->
            val celda = JoItemHistorialBinding.bind(fila)
            val color = ContextCompat.getColor(fila.context, EstadosVisuales.colorEstado(cambio.estado))
            celda.viewPuntoHistorial.backgroundTintList = ColorStateList.valueOf(color)
            celda.txtEstadoHistorial.text = cambio.estado.etiqueta
            celda.txtFechaHistorial.text = Fechas.fechaHora(cambio.fechaHora)
            celda.txtUsuarioHistorial.text = "Por ${nombreAutor(datos, cambio)}"
            celda.txtMotivoHistorial.visibility = if (cambio.motivo.isNullOrBlank()) View.GONE else View.VISIBLE
            celda.txtMotivoHistorial.text = cambio.motivo.orEmpty()
            // El cambio más antiguo cierra la línea de tiempo
            celda.viewLineaHistorial.visibility = if (cambio === historial.first()) View.INVISIBLE else View.VISIBLE
        }
    }

    /** Devuelve el nombre de quien hizo el cambio: técnico, cliente o usuario con sesión; si no se reconoce, el Jefe de Taller. */
    private fun nombreAutor(datos: OrdenDetallada, cambio: CambioEstado): String {
        val sesion = sesionViewModel.usuarioActual.value
        return when (cambio.usuarioId) {
            datos.tecnico?.id -> datos.tecnico?.nombreCompleto
            datos.cliente?.id -> datos.cliente?.nombreCompleto
            sesion?.id -> sesion?.nombreCompleto
            else -> tecnicos.firstOrNull { it.tecnico.id == cambio.usuarioId }?.tecnico?.nombreCompleto
        } ?: "Jefe de Taller"
    }

    // ---------------------------------------------------------------------------------------------
    // Fotos
    // ---------------------------------------------------------------------------------------------

    /** Abre el visor; el botón de eliminar solo se ofrece mientras la orden está Ingresado. */
    private fun abrirVisor(datos: OrdenDetallada, indice: Int) {
        val alEliminar: ((Foto) -> Unit)? = if (datos.orden.estado == EstadoOrden.INGRESADO) { foto -> pedirEliminarFoto(foto) } else null
        VisorFoto.mostrar(parentFragmentManager, datos.orden.fotos, indice, alEliminar)
    }

    /** Pide confirmar la eliminación de la foto; si es la única que queda, informa que la orden necesita al menos una. */
    private fun pedirEliminarFoto(foto: Foto) {
        val actual = detalle ?: return
        if (actual.orden.fotos.size <= 1) {
            DialogoModal.error(requireContext(), "La orden debe tener al menos una foto")
            return
        }
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_trash,
            "¿Eliminar esta foto?",
            "Se quitará de la orden. Esta acción no se puede deshacer",
            "Eliminar",
            peligro = true
        ) {
            modelo.eliminarFotoOrden(actual.orden.id, foto.id, usuarioActual.id) { resultado ->
                if (!isAdded) return@eliminarFotoOrden
                resultado
                    .alExito { mostrarMensaje("Foto eliminada") }
                    .alError { DialogoModal.error(requireContext(), it.mensaje) }
            }
        }
    }

    /** Carga las fotos actuales en el gestor, para que cuente el máximo de 6, y abre el selector de cámara o galería. */
    private fun agregarFotos() {
        val actual = detalle ?: return
        gestorFotos.establecer(actual.orden.fotos)
        gestorFotos.solicitarFoto()
    }

    /** Guarda en la orden las fotos que el gestor tiene y la orden todavía no. */
    private fun guardarFotosNuevas(fotos: List<Foto>) {
        val actual = detalle ?: return
        val conocidas = actual.orden.fotos.map { it.id }.toSet()
        val nuevas = fotos.filter { it.id !in conocidas }
        if (nuevas.isEmpty()) return
        modelo.agregarFotosOrden(actual.orden.id, nuevas, usuarioActual.id) { resultado ->
            if (!isAdded) return@agregarFotosOrden
            resultado
                .alExito { mostrarMensaje("Foto agregada") }
                .alError { DialogoModal.error(requireContext(), it.mensaje) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Anular y reenviar aviso
    // ---------------------------------------------------------------------------------------------

    /** Pide el motivo obligatorio; el botón de acción del diálogo queda deshabilitado mientras el motivo esté vacío. */
    private fun pedirAnulacion() {
        val actual = detalle ?: return
        val usuarioId = usuarioActual.id
        DialogoModal.conCampo(
            requireContext(),
            R.drawable.ic_warning_circle,
            "¿Anular esta orden?",
            "El cliente recibirá el aviso \"Tu orden ${actual.orden.numero} fue anulada\".",
            "Motivo de la anulación",
            esContrasena = false,
            textoAccion = "Anular orden"
        ) { motivo ->
            modelo.anularOrden(actual.orden.id, motivo, usuarioId) { resultado ->
                if (!isAdded) return@anularOrden
                resultado
                    .alExito { mostrarMensaje("Orden anulada") }
                    .alError { DialogoModal.error(requireContext(), it.mensaje) }
            }
        }
    }

    /** Reenvía al cliente el aviso de vehículo listo y confirma con un mensaje breve. */
    private fun reenviarAviso() {
        val actual = detalle ?: return
        modelo.reenviarAviso(actual.orden.id, usuarioActual.id) { resultado ->
            if (!isAdded) return@reenviarAviso
            resultado
                .alExito { mostrarMensaje("Aviso reenviado") }
                .alError { DialogoModal.error(requireContext(), it.mensaje) }
        }
    }

    /** Pide confirmar la entrega del vehículo al cliente y, al aceptar, cierra la orden como Entregado. */
    private fun confirmarEntrega() {
        val actual = detalle ?: return
        val usuarioId = usuarioActual.id
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_check_circle,
            "¿Marcar como entregado?",
            "Confirma que el cliente retiró el vehículo ${actual.vehiculo?.placa.orEmpty()}. La orden pasará al historial.",
            "Entregar"
        ) {
            modelo.marcarEntregada(actual.orden.id, usuarioId) { resultado ->
                if (!isAdded) return@marcarEntregada
                resultado
                    .alExito { mostrarMensaje("Vehículo entregado") }
                    .alError { DialogoModal.error(requireContext(), it.mensaje) }
            }
        }
    }

    companion object {
        private const val CLAVE_ORDEN = "ordenId"

        /** Crea el detalle de la orden indicada. */
        fun nuevo(ordenId: String): FragDetalleOrdenJefe =
            FragDetalleOrdenJefe().apply { arguments = Bundle().apply { putString(CLAVE_ORDEN, ordenId) } }
    }
}
