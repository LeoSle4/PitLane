package com.leosle.pitlane.arquitectura.vista.solicitudes

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.vista.comun.MenuAcciones
import com.leosle.pitlane.databinding.CliFragMisSolicitudesBinding
import com.leosle.pitlane.databinding.CliItemSolicitudBinding
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.SolicitudDetallada
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.arquitectura.viewmodel.SolicitudViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AdaptadorLista
import com.leosle.pitlane.arquitectura.vista.comun.AyudasVista
import com.leosle.pitlane.arquitectura.vista.comun.DialogoModal
import com.leosle.pitlane.arquitectura.vista.comun.EstadoVacio
import com.leosle.pitlane.arquitectura.vista.comun.EstadosVisuales
import com.leosle.pitlane.arquitectura.vista.comun.LogosMarca
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado

/**
 * Pestaña raíz del cliente con sus solicitudes, de la más reciente a la más antigua. Las pendientes ofrecen
 * Editar y Cancelar, las atendidas abren la orden generada y las canceladas solo informan su estado.
 */
class FragMisSolicitudes : FragmentoVinculado<CliFragMisSolicitudesBinding>(CliFragMisSolicitudesBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val solicitudViewModel: SolicitudViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<SolicitudDetallada>

    /** Prepara la lista, el estado vacío y el botón de nueva solicitud, y observa las solicitudes del cliente. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adaptador = AdaptadorLista(requireContext(), R.layout.cli_item_solicitud) { vista, elemento -> enlazarFila(vista, elemento) }
        vinculo.lvSolicitudes.adapter = adaptador
        vinculo.lvSolicitudes.emptyView = vinculo.incVacio.root
        vinculo.lvSolicitudes.setOnItemClickListener { _, vista, posicion, _ -> alTocar(vista, adaptador.getItem(posicion)) }

        EstadoVacio.configurar(
            vinculo.incVacio,
            getString(R.string.cli_solicitudes_vacio_titulo),
            getString(R.string.cli_solicitudes_vacio_mensaje),
            getString(R.string.cli_nueva_solicitud)
        ) { abrirFormulario() }
        vinculo.btnNuevaSolicitud.setOnClickListener { abrirFormulario() }

        recolectar(solicitudViewModel.solicitudesDeCliente(usuarioActual.id)) { adaptador.actualizar(it) }
    }

    /** Llena una fila con placa, fecha de llegada, resumen, chip de estado, barra del color del estado y menú si está pendiente. */
    private fun enlazarFila(vista: View, elemento: SolicitudDetallada) {
        val fila = CliItemSolicitudBinding.bind(vista)
        val solicitud = elemento.solicitud
        fila.txtPlacaSolicitud.text = elemento.vehiculo?.placa ?: getString(R.string.cli_vehiculo_desconocido)
        LogosMarca.aplicar(fila.imgMarcaSolicitud, elemento.vehiculo?.marca.orEmpty())
        fila.txtFechaSolicitud.text = getString(R.string.cli_llegada_estimada, Fechas.fecha(solicitud.fechaLlegada))
        fila.txtResumenSolicitud.text = solicitud.descripcion
        EstadosVisuales.estadoSolicitud(fila.txtEstadoSolicitud, solicitud.estado)
        AyudasVista.pintarBarra(fila.viewBarraEstado, AyudasVista.colorSolicitud(solicitud.estado))
        val pendiente = solicitud.estado == EstadoSolicitud.PENDIENTE
        fila.btnMenuSolicitud.visibility = if (pendiente) View.VISIBLE else View.GONE
        fila.btnMenuSolicitud.setOnClickListener { mostrarMenu(it, elemento) }
    }

    /** Según el estado: pendiente abre el menú de opciones, atendida abre la orden y cancelada informa con un mensaje. */
    private fun alTocar(ancla: View, elemento: SolicitudDetallada) {
        val solicitud = elemento.solicitud
        when (solicitud.estado) {
            EstadoSolicitud.PENDIENTE -> mostrarMenu(ancla, elemento)
            EstadoSolicitud.ATENDIDA -> {
                val ordenId = solicitud.ordenId
                if (ordenId == null) mostrarMensaje(getString(R.string.cli_solicitud_sin_orden)) else navegador.abrirOrden(ordenId)
            }
            EstadoSolicitud.CANCELADA -> mostrarMensaje(getString(R.string.cli_solicitud_ya_cancelada))
        }
    }

    /** Abre el formulario de una nueva solicitud. */
    private fun abrirFormulario() {
        navegador.irA(FragNuevaSolicitud.nuevo())
    }

    /** Muestra el menú de una solicitud pendiente con las acciones Editar y Cancelar solicitud. */
    private fun mostrarMenu(ancla: View, elemento: SolicitudDetallada) {
        MenuAcciones.mostrar(
            ancla,
            listOf(
                MenuAcciones.Opcion(R.drawable.ic_pencil_simple, getString(R.string.cli_editar)) {
                    HojaEditarSolicitud.nueva(elemento.solicitud.id).show(parentFragmentManager, "hoja_editar_solicitud")
                },
                MenuAcciones.Opcion(R.drawable.ic_x_circle, getString(R.string.cli_cancelar_solicitud), peligro = true) {
                    confirmarCancelacion(elemento)
                }
            )
        )
    }

    /** Pide confirmar la cancelación y ejecuta la operación; un fallo se muestra en el diálogo de error. */
    private fun confirmarCancelacion(elemento: SolicitudDetallada) {
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_x_circle,
            getString(R.string.cli_cancelar_solicitud_titulo),
            getString(R.string.cli_cancelar_solicitud_mensaje),
            getString(R.string.cli_cancelar_solicitud),
            peligro = true
        ) {
            solicitudViewModel.cancelarSolicitud(elemento.solicitud.id) { resultado -> alCancelar(resultado) }
        }
    }

    /** Informa el resultado de cancelar: mensaje breve si salió bien o diálogo de error si no. */
    private fun alCancelar(resultado: Resultado<Unit>) {
        if (view == null) return
        when (resultado) {
            is Resultado.Exito -> mostrarMensaje(getString(R.string.cli_solicitud_cancelada))
            is Resultado.Error -> DialogoModal.error(requireContext(), resultado.mensaje, getString(R.string.cli_no_se_pudo_cancelar))
        }
    }
}
