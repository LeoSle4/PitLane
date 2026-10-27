package com.leosle.pitlane.arquitectura.vista.vehiculos

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.databinding.JcFragRegistrarVehiculoBinding
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.viewmodel.ClienteViewModel
import com.leosle.pitlane.arquitectura.viewmodel.VehiculoViewModel
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado

/**
 * Formulario para registrar un vehículo a nombre de un cliente: placa en mayúsculas, marca, modelo, año y color.
 * Si la placa ya existe muestra a nombre de quién está; al terminar regresa al detalle del cliente.
 */
class FragRegistrarVehiculoJefe : FragmentoVinculado<JcFragRegistrarVehiculoBinding>(JcFragRegistrarVehiculoBinding::inflate) {

    private val modelo: VehiculoViewModel by viewModels()
    private val modeloCliente: ClienteViewModel by viewModels()

    private val clienteId: String get() = requireArguments().getString(CLAVE_CLIENTE).orEmpty()

    /** Configura la barra, el nombre del dueño, la placa en mayúsculas, las validaciones y el botón de registro. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(vinculo.barraSuperior, "Agregar vehículo", alVolver = { navegador.volver() })
        Campos.mayusculas(vinculo.etPlaca)

        recolectar(modeloCliente.ficha(clienteId)) { ficha ->
            vinculo.txtDuenoVehiculo.text = if (ficha == null) "" else "Cliente: ${ficha.cliente.nombreCompleto} · DNI ${ficha.cliente.dni}"
        }

        Campos.validarAlSalir(vinculo.etPlaca, vinculo.txtErrorPlaca) { Validador.placa(it) }
        Campos.validarAlSalir(vinculo.etMarca, vinculo.txtErrorMarca) { Validador.obligatorio(it, "Ingresa la marca") }
        Campos.validarAlSalir(vinculo.etModelo, vinculo.txtErrorModelo) { Validador.obligatorio(it, "Ingresa el modelo") }
        Campos.validarAlSalir(vinculo.etAnio, vinculo.txtErrorAnio) { Validador.anio(it) }
        Campos.validarAlSalir(vinculo.etColor, vinculo.txtErrorColor) { Validador.obligatorio(it, "Ingresa el color") }
        vinculo.btnRegistrarVehiculo.setOnClickListener { registrar() }
    }

    /** Valida los campos y registra el vehículo como alta del Jefe; gestiona el error de placa repetida. */
    private fun registrar() {
        val validos = listOf(
            Campos.validar(vinculo.etPlaca, vinculo.txtErrorPlaca) { Validador.placa(it) },
            Campos.validar(vinculo.etMarca, vinculo.txtErrorMarca) { Validador.obligatorio(it, "Ingresa la marca") },
            Campos.validar(vinculo.etModelo, vinculo.txtErrorModelo) { Validador.obligatorio(it, "Ingresa el modelo") },
            Campos.validar(vinculo.etAnio, vinculo.txtErrorAnio) { Validador.anio(it) },
            Campos.validar(vinculo.etColor, vinculo.txtErrorColor) { Validador.obligatorio(it, "Ingresa el color") }
        )
        if (validos.any { !it }) return

        ocultarTeclado()
        vinculo.btnRegistrarVehiculo.isEnabled = false
        modelo.registrarVehiculo(
            clienteId,
            Validador.normalizarPlaca(Campos.texto(vinculo.etPlaca)),
            Campos.texto(vinculo.etMarca),
            Campos.texto(vinculo.etModelo),
            Campos.texto(vinculo.etAnio).toInt(),
            Campos.texto(vinculo.etColor),
            true
        ) { resultado ->
            resultado
                .alExito {
                    mostrarMensaje("Vehículo registrado")
                    navegador.volver()
                }
                .alError {
                    vinculo.btnRegistrarVehiculo.isEnabled = true
                    Campos.mostrarError(vinculo.etPlaca, vinculo.txtErrorPlaca, it.mensaje)
                    mostrarMensaje(it.mensaje)
                }
        }
    }

    companion object {
        private const val CLAVE_CLIENTE = "clienteId"

        /** Crea el formulario para registrar un vehículo del cliente [clienteId]. */
        fun nuevo(clienteId: String): FragRegistrarVehiculoJefe =
            FragRegistrarVehiculoJefe().apply {
                arguments = Bundle().apply { putString(CLAVE_CLIENTE, clienteId) }
            }
    }
}
