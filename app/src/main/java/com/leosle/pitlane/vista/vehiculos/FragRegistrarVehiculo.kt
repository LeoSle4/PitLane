package com.leosle.pitlane.vista.vehiculos

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliFragRegistrarVehiculoBinding
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.viewmodel.VehiculoViewModel
import com.leosle.pitlane.vista.comun.AyudasVista
import com.leosle.pitlane.vista.comun.BarraSuperior
import com.leosle.pitlane.vista.comun.Campos
import com.leosle.pitlane.vista.comun.FragmentoVinculado

/**
 * Pantalla para registrar un vehículo del cliente. La placa se escribe en mayúsculas, cada campo se valida al salir
 * y al enviar, y una placa repetida se informa bajo el campo. Al guardar vuelve a la lista de vehículos.
 */
class FragRegistrarVehiculo : FragmentoVinculado<CliFragRegistrarVehiculoBinding>(CliFragRegistrarVehiculoBinding::inflate) {

    private val vehiculoViewModel: VehiculoViewModel by viewModels()

    /** Configura la barra superior, la validación de los campos y el botón de registro. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        BarraSuperior.configurar(vinculo.barra, getString(R.string.cli_registrar_vehiculo_titulo), alVolver = { navegador.volver() })
        AyudasVista.configurarCamposVehiculo(vinculo.campos, conPlaca = true)
        vinculo.btnRegistrar.setOnClickListener { registrar() }
    }

    /** Valida los campos y, si son correctos, envía el registro al ViewModel bloqueando el botón mientras responde. */
    private fun registrar() {
        ocultarTeclado()
        val campos = vinculo.campos
        if (!AyudasVista.validarCamposVehiculo(campos, conPlaca = true)) return
        vinculo.btnRegistrar.isEnabled = false
        vehiculoViewModel.registrarVehiculo(
            usuarioActual.id,
            Validador.normalizarPlaca(Campos.texto(campos.etPlaca)),
            Campos.texto(campos.etMarca),
            Campos.texto(campos.etModelo),
            Campos.texto(campos.etAnio).toInt(),
            Campos.texto(campos.etColor),
            false
        ) { resultado -> alRegistrar(resultado) }
    }

    /** Si el registro salió bien confirma con un mensaje y vuelve; si falló muestra el motivo bajo la placa y en un mensaje. */
    private fun alRegistrar(resultado: Resultado<Vehiculo>) {
        if (view == null) return
        vinculo.btnRegistrar.isEnabled = true
        when (resultado) {
            is Resultado.Exito -> {
                mostrarMensaje(getString(R.string.cli_vehiculo_registrado))
                navegador.volver()
            }
            is Resultado.Error -> {
                Campos.mostrarError(vinculo.campos.etPlaca, vinculo.campos.txtErrorPlaca, resultado.mensaje)
                mostrarMensaje(resultado.mensaje, duracionLarga = true)
            }
        }
    }
}
