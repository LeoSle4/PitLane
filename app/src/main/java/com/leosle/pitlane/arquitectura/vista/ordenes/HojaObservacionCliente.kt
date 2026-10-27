package com.leosle.pitlane.arquitectura.vista.ordenes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.viewmodel.OrdenViewModel
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.HojaBase
import com.leosle.pitlane.databinding.JoHojaObservacionClienteBinding
import com.leosle.pitlane.util.Validador

/**
 * Hoja para registrar lo que el cliente observa antes de recibir su vehículo y lo acordado para atenderlo.
 * La observación exige al menos 10 caracteres; guardarla no cambia el estado de la orden.
 */
class HojaObservacionCliente : HojaBase() {

    private val modelo: OrdenViewModel by viewModels()

    /** Infla la hoja y conecta la validación al salir del campo y el guardado. */
    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = JoHojaObservacionClienteBinding.inflate(inflater, contenedor, false)
        configurarEncabezado(vinculo.encabezado, getString(R.string.jo_obs_titulo))
        Campos.validarAlSalir(vinculo.etObservacion, vinculo.txtErrorObservacion) { validarObservacion(it) }
        vinculo.btnGuardarObservacion.setOnClickListener { guardar(vinculo) }
        return vinculo.root
    }

    /** Devuelve el error de una observación demasiado corta, o nulo si ya se puede guardar. */
    private fun validarObservacion(texto: String): String? =
        Validador.longitudMinima(texto, MINIMO_CARACTERES, getString(R.string.jo_obs_error_corta))

    /** Valida la observación y la guarda con lo acordado; al terminar avisa y cierra la hoja. */
    private fun guardar(vinculo: JoHojaObservacionClienteBinding) {
        if (!Campos.validar(vinculo.etObservacion, vinculo.txtErrorObservacion) { validarObservacion(it) }) return
        val ordenId = requireArguments().getString(CLAVE_ORDEN).orEmpty()
        val usuarioId = requireArguments().getString(CLAVE_USUARIO).orEmpty()
        vinculo.btnGuardarObservacion.isEnabled = false
        modelo.registrarObservacionCliente(
            ordenId,
            Campos.texto(vinculo.etObservacion),
            Campos.texto(vinculo.etAcuerdo),
            usuarioId
        ) { resultado ->
            resultado
                .alExito {
                    mostrarMensaje(getString(R.string.jo_obs_registrada))
                    dismiss()
                }
                .alError {
                    vinculo.btnGuardarObservacion.isEnabled = true
                    mostrarMensaje(it.mensaje)
                }
        }
    }

    companion object {
        private const val CLAVE_ORDEN = "ordenId"
        private const val CLAVE_USUARIO = "usuarioId"
        private const val MINIMO_CARACTERES = 10

        /** Abre la hoja para que el usuario indicado registre una observación del cliente en la orden indicada. */
        fun mostrar(administrador: FragmentManager, ordenId: String, usuarioId: String) {
            HojaObservacionCliente().apply {
                arguments = Bundle().apply {
                    putString(CLAVE_ORDEN, ordenId)
                    putString(CLAVE_USUARIO, usuarioId)
                }
            }.show(administrador, "hoja_observacion_cliente")
        }
    }
}
