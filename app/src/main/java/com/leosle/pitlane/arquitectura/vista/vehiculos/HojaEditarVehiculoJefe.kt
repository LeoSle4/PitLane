package com.leosle.pitlane.arquitectura.vista.vehiculos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.leosle.pitlane.databinding.JcHojaEditarVehiculoBinding
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.viewmodel.VehiculoViewModel
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.HojaBase

/**
 * Hoja de edición de un vehículo: marca, modelo, año y color son editables y la placa queda bloqueada.
 * El botón Guardar cambios se habilita solo cuando algún valor editable difiere del original.
 */
class HojaEditarVehiculoJefe : HojaBase() {

    private var vinculoInterno: JcHojaEditarVehiculoBinding? = null
    private val modelo: VehiculoViewModel by viewModels()

    /** Infla la hoja, rellena los datos recibidos, bloquea la placa y conecta validaciones y guardado. */
    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = JcHojaEditarVehiculoBinding.inflate(inflater, contenedor, false)
        vinculoInterno = vinculo
        val datos = requireArguments()
        configurarEncabezado(vinculo.encabezado, "Editar vehículo")

        val marca = datos.getString(CLAVE_MARCA).orEmpty()
        val modeloVehiculo = datos.getString(CLAVE_MODELO).orEmpty()
        val anio = datos.getInt(CLAVE_ANIO).toString()
        val color = datos.getString(CLAVE_COLOR).orEmpty()
        vinculo.etMarca.setText(marca)
        vinculo.etModelo.setText(modeloVehiculo)
        vinculo.etAnio.setText(anio)
        vinculo.etColor.setText(color)
        vinculo.etPlaca.setText(datos.getString(CLAVE_PLACA).orEmpty())
        Campos.bloquear(vinculo.etPlaca)

        Campos.validarAlSalir(vinculo.etMarca, vinculo.txtErrorMarca) { Validador.obligatorio(it, "Ingresa la marca") }
        Campos.validarAlSalir(vinculo.etModelo, vinculo.txtErrorModelo) { Validador.obligatorio(it, "Ingresa el modelo") }
        Campos.validarAlSalir(vinculo.etAnio, vinculo.txtErrorAnio) { Validador.anio(it) }
        Campos.validarAlSalir(vinculo.etColor, vinculo.txtErrorColor) { Validador.obligatorio(it, "Ingresa el color") }

        Campos.alCambiarTexto(vinculo.etMarca, vinculo.etModelo, vinculo.etAnio, vinculo.etColor) {
            vinculo.btnGuardar.isEnabled = Campos.texto(vinculo.etMarca) != marca ||
                Campos.texto(vinculo.etModelo) != modeloVehiculo ||
                Campos.texto(vinculo.etAnio) != anio ||
                Campos.texto(vinculo.etColor) != color
        }
        vinculo.btnGuardar.setOnClickListener { guardar(vinculo, datos.getString(CLAVE_ID).orEmpty()) }
        return vinculo.root
    }

    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Valida los campos editables y guarda los cambios del vehículo; al terminar avisa y cierra la hoja. */
    private fun guardar(vinculo: JcHojaEditarVehiculoBinding, vehiculoId: String) {
        val validos = listOf(
            Campos.validar(vinculo.etMarca, vinculo.txtErrorMarca) { Validador.obligatorio(it, "Ingresa la marca") },
            Campos.validar(vinculo.etModelo, vinculo.txtErrorModelo) { Validador.obligatorio(it, "Ingresa el modelo") },
            Campos.validar(vinculo.etAnio, vinculo.txtErrorAnio) { Validador.anio(it) },
            Campos.validar(vinculo.etColor, vinculo.txtErrorColor) { Validador.obligatorio(it, "Ingresa el color") }
        )
        if (validos.any { !it }) return
        vinculo.btnGuardar.isEnabled = false
        modelo.editarVehiculo(
            vehiculoId,
            Campos.texto(vinculo.etMarca),
            Campos.texto(vinculo.etModelo),
            Campos.texto(vinculo.etAnio).toInt(),
            Campos.texto(vinculo.etColor)
        ) { resultado ->
            resultado
                .alExito {
                    mostrarMensaje("Vehículo actualizado")
                    dismiss()
                }
                .alError {
                    vinculo.btnGuardar.isEnabled = true
                    mostrarMensaje(it.mensaje)
                }
        }
    }

    companion object {
        private const val CLAVE_ID = "id"
        private const val CLAVE_PLACA = "placa"
        private const val CLAVE_MARCA = "marca"
        private const val CLAVE_MODELO = "modelo"
        private const val CLAVE_ANIO = "anio"
        private const val CLAVE_COLOR = "color"

        /** Abre la hoja con los datos actuales de [vehiculo] para editarlos. */
        fun mostrar(administrador: FragmentManager, vehiculo: Vehiculo) {
            HojaEditarVehiculoJefe().apply {
                arguments = Bundle().apply {
                    putString(CLAVE_ID, vehiculo.id)
                    putString(CLAVE_PLACA, vehiculo.placa)
                    putString(CLAVE_MARCA, vehiculo.marca)
                    putString(CLAVE_MODELO, vehiculo.modelo)
                    putInt(CLAVE_ANIO, vehiculo.anio)
                    putString(CLAVE_COLOR, vehiculo.color)
                }
            }.show(administrador, "hoja_editar_vehiculo")
        }
    }
}
