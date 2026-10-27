package com.leosle.pitlane.arquitectura.vista.vehiculos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliHojaEditarVehiculoBinding
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.arquitectura.viewmodel.VehiculoViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AyudasVista
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.HojaBase

/**
 * Hoja inferior para editar marca, modelo, año y color de un vehículo. La placa se muestra bloqueada y el botón
 * "Guardar cambios" solo se activa cuando algún campo difiere de los datos originales.
 */
class HojaEditarVehiculo : HojaBase() {

    private var vinculoInterno: CliHojaEditarVehiculoBinding? = null
    private val vehiculoViewModel: VehiculoViewModel by viewModels()
    private var original: Vehiculo? = null

    /** Infla el diseño de la hoja. */
    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = CliHojaEditarVehiculoBinding.inflate(inflater, contenedor, false)
        vinculoInterno = vinculo
        return vinculo.root
    }

    /** Configura el encabezado, bloquea la placa, conecta los campos y rellena el formulario al llegar el vehículo. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vinculo = vinculoInterno ?: return
        val campos = vinculo.campos
        configurarEncabezado(vinculo.encabezado, getString(R.string.cli_editar_vehiculo_titulo))
        Campos.bloquear(campos.etPlaca)
        AyudasVista.configurarCamposVehiculo(campos, conPlaca = false)
        Campos.alCambiarTexto(campos.etMarca, campos.etModelo, campos.etAnio, campos.etColor) { actualizarBoton() }
        vinculo.btnGuardar.setOnClickListener { guardar() }

        val vehiculoId = requireArguments().getString(ARGUMENTO_VEHICULO).orEmpty()
        recolectar(vehiculoViewModel.vehiculo(vehiculoId)) { vehiculo -> rellenar(vehiculo) }
    }

    /** Libera el vínculo con las vistas al destruirse la vista de la hoja. */
    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Escribe los datos actuales del vehículo en los campos la primera vez que llegan y los guarda como referencia. */
    private fun rellenar(vehiculo: Vehiculo?) {
        val vinculo = vinculoInterno ?: return
        if (vehiculo == null || original != null) return
        original = vehiculo
        val campos = vinculo.campos
        campos.etPlaca.setText(vehiculo.placa)
        campos.etMarca.setText(vehiculo.marca)
        campos.etModelo.setText(vehiculo.modelo)
        campos.etAnio.setText(vehiculo.anio.toString())
        campos.etColor.setText(vehiculo.color)
        actualizarBoton()
    }

    /** Activa el botón de guardar solo si hay datos originales y algún campo cambió. */
    private fun actualizarBoton() {
        val vinculo = vinculoInterno ?: return
        val referencia = original
        val campos = vinculo.campos
        vinculo.btnGuardar.isEnabled = referencia != null && (
            Campos.texto(campos.etMarca) != referencia.marca ||
                Campos.texto(campos.etModelo) != referencia.modelo ||
                Campos.texto(campos.etAnio) != referencia.anio.toString() ||
                Campos.texto(campos.etColor) != referencia.color
            )
    }

    /** Valida los campos editables y envía los cambios al ViewModel. */
    private fun guardar() {
        val vinculo = vinculoInterno ?: return
        val referencia = original ?: return
        val campos = vinculo.campos
        if (!AyudasVista.validarCamposVehiculo(campos, conPlaca = false)) return
        vinculo.btnGuardar.isEnabled = false
        vehiculoViewModel.editarVehiculo(
            referencia.id,
            Campos.texto(campos.etMarca),
            Campos.texto(campos.etModelo),
            Campos.texto(campos.etAnio).toInt(),
            Campos.texto(campos.etColor)
        ) { resultado -> alGuardar(resultado) }
    }

    /** Si se guardó confirma con un mensaje y cierra la hoja; si falló muestra el motivo y reactiva el botón. */
    private fun alGuardar(resultado: Resultado<Vehiculo>) {
        if (!isAdded || vinculoInterno == null) return
        when (resultado) {
            is Resultado.Exito -> {
                mostrarMensaje(getString(R.string.cli_vehiculo_actualizado))
                dismiss()
            }
            is Resultado.Error -> {
                mostrarMensaje(resultado.mensaje)
                actualizarBoton()
            }
        }
    }

    companion object {
        private const val ARGUMENTO_VEHICULO = "vehiculoId"

        /** Crea la hoja de edición del vehículo indicado. */
        fun nueva(vehiculoId: String): HojaEditarVehiculo =
            HojaEditarVehiculo().apply { arguments = Bundle().apply { putString(ARGUMENTO_VEHICULO, vehiculoId) } }
    }
}
