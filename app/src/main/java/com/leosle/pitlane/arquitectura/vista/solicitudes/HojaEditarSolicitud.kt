package com.leosle.pitlane.arquitectura.vista.solicitudes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliHojaEditarSolicitudBinding
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.SolicitudDetallada
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.arquitectura.viewmodel.SolicitudViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AyudasVista
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.GestorFotos
import com.leosle.pitlane.arquitectura.vista.comun.HojaBase
import com.leosle.pitlane.arquitectura.vista.comun.BotonCarga

/**
 * Hoja inferior para editar una solicitud pendiente: el vehículo aparece bloqueado, se pueden cambiar síntomas,
 * descripción, fecha y fotos, y el botón "Guardar cambios" solo se activa cuando algún dato difiere del original.
 */
class HojaEditarSolicitud : HojaBase() {

    private var vinculoInterno: CliHojaEditarSolicitudBinding? = null
    private val solicitudViewModel: SolicitudViewModel by viewModels()
    private val gestorFotos = GestorFotos(this, AyudasVista.MAXIMO_FOTOS, AyudasVista.MENSAJE_MAXIMO_FOTOS) { actualizarBoton() }

    private var controlador: ControladorFormularioSolicitud? = null
    private var original: Solicitud? = null

    /** Infla el diseño de la hoja. */
    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = CliHojaEditarSolicitudBinding.inflate(inflater, contenedor, false)
        vinculoInterno = vinculo
        return vinculo.root
    }

    /** Configura el encabezado, bloquea el vehículo, crea el controlador del formulario y rellena los datos al llegar la solicitud. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vinculo = vinculoInterno ?: return
        configurarEncabezado(vinculo.encabezado, getString(R.string.cli_editar_solicitud_titulo))
        Campos.bloquear(vinculo.etVehiculoBloqueado)
        controlador = ControladorFormularioSolicitud(vinculo.formulario, gestorFotos) { actualizarBoton() }
        vinculo.btnGuardar.setOnClickListener { guardar() }

        val solicitudId = requireArguments().getString(ARGUMENTO_SOLICITUD).orEmpty()
        recolectar(solicitudViewModel.solicitud(solicitudId)) { detalle -> rellenar(detalle) }
    }

    /** Libera el controlador y el vínculo con las vistas al destruirse la vista de la hoja. */
    override fun onDestroyView() {
        controlador = null
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Escribe los datos de la solicitud en el formulario la primera vez que llegan y los guarda como referencia. */
    private fun rellenar(detalle: SolicitudDetallada?) {
        val vinculo = vinculoInterno ?: return
        val formulario = controlador ?: return
        if (detalle == null || original != null) return
        val solicitud = detalle.solicitud
        original = solicitud
        vinculo.etVehiculoBloqueado.setText(detalle.vehiculo?.let { "${it.placa} · ${it.descripcion}" }.orEmpty())
        formulario.establecer(solicitud.sintomas, solicitud.descripcion, solicitud.fechaLlegada, solicitud.fotos)
        actualizarBoton()
    }

    /** Activa el botón de guardar solo si hay datos originales y síntomas, descripción, fecha o fotos cambiaron. */
    private fun actualizarBoton() {
        val vinculo = vinculoInterno ?: return
        val formulario = controlador ?: return
        val referencia = original
        vinculo.btnGuardar.isEnabled = referencia != null && (
            formulario.sintomasMarcados.toSet() != referencia.sintomas.toSet() ||
                formulario.descripcion != referencia.descripcion.trim() ||
                formulario.fechaLlegada != referencia.fechaLlegada ||
                formulario.fotos.map { it.id } != referencia.fotos.map { it.id }
            )
    }

    /** Valida el formulario y envía los cambios al ViewModel bloqueando el botón mientras responde. */
    private fun guardar() {
        val vinculo = vinculoInterno ?: return
        val formulario = controlador ?: return
        val referencia = original ?: return
        if (!formulario.validar()) return
        BotonCarga.iniciar(vinculo.btnGuardar)
        solicitudViewModel.editarSolicitud(
            referencia.id,
            formulario.descripcion,
            formulario.sintomasMarcados,
            checkNotNull(formulario.fechaLlegada),
            formulario.fotos
        ) { resultado -> alGuardar(resultado) }
    }

    /** Si se guardó confirma con un mensaje y cierra la hoja; si falló muestra el motivo y reactiva el botón. */
    private fun alGuardar(resultado: Resultado<Solicitud>) {
        if (!isAdded || vinculoInterno == null) return
        when (resultado) {
            is Resultado.Exito -> {
                mostrarMensaje(getString(R.string.cli_solicitud_actualizada))
                dismiss()
            }
            is Resultado.Error -> {
                mostrarMensaje(resultado.mensaje)
                vinculoInterno?.let { BotonCarga.terminar(it.btnGuardar) }
                actualizarBoton()
            }
        }
    }

    companion object {
        private const val ARGUMENTO_SOLICITUD = "solicitudId"

        /** Crea la hoja de edición de la solicitud indicada. */
        fun nueva(solicitudId: String): HojaEditarSolicitud =
            HojaEditarSolicitud().apply { arguments = Bundle().apply { putString(ARGUMENTO_SOLICITUD, solicitudId) } }
    }
}
