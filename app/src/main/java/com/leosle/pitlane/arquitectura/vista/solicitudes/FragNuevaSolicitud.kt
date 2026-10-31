package com.leosle.pitlane.arquitectura.vista.solicitudes

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliFragNuevaSolicitudBinding
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.arquitectura.viewmodel.SolicitudViewModel
import com.leosle.pitlane.arquitectura.viewmodel.VehiculoViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AyudasVista
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.Combos
import com.leosle.pitlane.arquitectura.vista.comun.EstadoVacio
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.arquitectura.vista.comun.GestorFotos
import com.leosle.pitlane.arquitectura.vista.comun.PasosFormulario
import com.leosle.pitlane.arquitectura.vista.vehiculos.FragRegistrarVehiculo
import com.leosle.pitlane.arquitectura.vista.comun.BotonCarga

/**
 * Pantalla para enviar una solicitud de atención en dos pasos (vehículo y falla, fecha y fotos): se elige el vehículo en un combo, se marcan síntomas, se describe
 * la falla, se elige la fecha de llegada y se adjuntan hasta 3 fotos. Sin vehículos registrados muestra un aviso
 * que lleva al registro de uno.
 */
class FragNuevaSolicitud : FragmentoVinculado<CliFragNuevaSolicitudBinding>(CliFragNuevaSolicitudBinding::inflate) {

    private val vehiculoViewModel: VehiculoViewModel by viewModels()
    private val solicitudViewModel: SolicitudViewModel by viewModels()
    private val gestorFotos = GestorFotos(this, AyudasVista.MAXIMO_FOTOS, AyudasVista.MENSAJE_MAXIMO_FOTOS)

    private var controlador: ControladorFormularioSolicitud? = null
    private lateinit var pasos: PasosFormulario
    private lateinit var retrocesoPorPasos: OnBackPressedCallback
    private var vehiculos: List<Vehiculo> = emptyList()
    private var vehiculoInicialAplicado = false

    /** Configura la barra, el formulario, el aviso sin vehículos y el botón de envío, y observa los vehículos del cliente. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        BarraSuperior.configurar(vinculo.barra, getString(R.string.cli_nueva_solicitud), alVolver = {
            if (!pasos.retroceder()) navegador.volver()
        })
        controlador = ControladorFormularioSolicitud(vinculo.formulario, gestorFotos) { }
        EstadoVacio.configurar(
            vinculo.incSinVehiculos,
            getString(R.string.cli_solicitud_sin_vehiculos_titulo),
            getString(R.string.cli_solicitud_sin_vehiculos_mensaje),
            getString(R.string.cli_agregar_vehiculo)
        ) { navegador.irA(FragRegistrarVehiculo()) }
        vinculo.btnEnviar.setOnClickListener { enviar() }
        pasos = PasosFormulario(
            pasos = listOf(
                listOf(vinculo.llPasoVehiculo, vinculo.formulario.llPasoSintomas),
                listOf(vinculo.formulario.llPasoDetalle)
            ),
            titulos = listOf("Vehículo y falla", "Fecha y fotos"),
            textoPaso = vinculo.txtPasoSolicitud,
            barras = listOf(vinculo.viewBarraSolicitudUno, vinculo.viewBarraSolicitudDos),
            botonSiguiente = vinculo.btnSiguienteSolicitud,
            botonAtras = vinculo.btnAtrasSolicitud,
            botonFinal = vinculo.btnEnviar,
            validarPaso = { validarVehiculoYDescripcion() },
            alCambiarDePaso = {
                ocultarTeclado()
                vinculo.svNuevaSolicitud.scrollTo(0, 0)
            }
        )
        retrocesoPorPasos = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!pasos.retroceder()) {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, retrocesoPorPasos)

        recolectar(vehiculoViewModel.vehiculosDeCliente(usuarioActual.id)) { lista ->
            mostrarVehiculos(lista.map { it.vehiculo })
        }
    }

    /** Libera el controlador del formulario al destruirse la vista. */
    override fun onDestroyView() {
        controlador = null
        super.onDestroyView()
    }

    /** Llena el combo con los vehículos, conserva la selección actual y alterna entre el formulario y el aviso sin vehículos. */
    private fun mostrarVehiculos(lista: List<Vehiculo>) {
        val seleccionadoId = vehiculos.getOrNull(Combos.posicion(vinculo.spnVehiculo))?.id
        vehiculos = lista
        EstadoVacio.mostrar(vinculo.incSinVehiculos, lista.isEmpty())
        vinculo.svNuevaSolicitud.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        Combos.configurar(vinculo.spnVehiculo, lista.map { "${it.placa} · ${it.descripcion}" })
        val preferido = if (vehiculoInicialAplicado) seleccionadoId else requireArguments().getString(ARGUMENTO_VEHICULO)
        val indice = lista.indexOfFirst { it.id == preferido }
        if (indice >= 0) vinculo.spnVehiculo.setSelection(indice)
        if (lista.isNotEmpty()) vehiculoInicialAplicado = true
    }

    /** Valida que haya un vehículo elegido y que la descripción de la falla sea correcta (primer paso). */
    private fun validarVehiculoYDescripcion(): Boolean {
        val vehiculo = vehiculos.getOrNull(Combos.posicion(vinculo.spnVehiculo))
        if (vehiculo == null) {
            vinculo.txtErrorVehiculo.text = getString(R.string.cli_elige_vehiculo)
            vinculo.txtErrorVehiculo.visibility = View.VISIBLE
        } else {
            vinculo.txtErrorVehiculo.visibility = View.GONE
        }
        val descripcionValida = controlador?.validarDescripcion() ?: false
        return vehiculo != null && descripcionValida
    }

    /** Valida el vehículo y el formulario y, si todo es correcto, registra la solicitud bloqueando el botón mientras responde. */
    private fun enviar() {
        ocultarTeclado()
        val formulario = controlador ?: return
        if (!validarVehiculoYDescripcion()) {
            pasos.irAlPaso(0)
            return
        }
        if (!formulario.validarFecha()) {
            pasos.irAlPaso(1)
            return
        }
        val vehiculo = checkNotNull(vehiculos.getOrNull(Combos.posicion(vinculo.spnVehiculo)))
        BotonCarga.iniciar(vinculo.btnEnviar)
        solicitudViewModel.registrarSolicitud(
            usuarioActual.id,
            vehiculo.id,
            formulario.descripcion,
            formulario.sintomasMarcados,
            checkNotNull(formulario.fechaLlegada),
            formulario.fotos
        ) { resultado -> alEnviar(resultado) }
    }

    /** Si se envió confirma con un mensaje y vuelve; si falló muestra el motivo bajo el combo y en un mensaje. */
    private fun alEnviar(resultado: Resultado<Solicitud>) {
        if (view == null) return
        BotonCarga.terminar(vinculo.btnEnviar)
        when (resultado) {
            is Resultado.Exito -> {
                mostrarMensaje(getString(R.string.cli_solicitud_enviada))
                retrocesoPorPasos.isEnabled = false
                navegador.volver()
            }
            is Resultado.Error -> {
                vinculo.txtErrorVehiculo.text = resultado.mensaje
                vinculo.txtErrorVehiculo.visibility = View.VISIBLE
                mostrarMensaje(resultado.mensaje, duracionLarga = true)
            }
        }
    }

    companion object {
        private const val ARGUMENTO_VEHICULO = "vehiculoId"

        /** Crea el formulario de solicitud; con [vehiculoId] el combo queda con ese vehículo elegido. */
        fun nuevo(vehiculoId: String? = null): FragNuevaSolicitud =
            FragNuevaSolicitud().apply { arguments = Bundle().apply { putString(ARGUMENTO_VEHICULO, vehiculoId) } }
    }
}
