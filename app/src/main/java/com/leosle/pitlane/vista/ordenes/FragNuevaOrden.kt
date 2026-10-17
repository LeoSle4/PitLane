package com.leosle.pitlane.vista.ordenes

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.leosle.pitlane.databinding.JoFragNuevaOrdenBinding
import com.leosle.pitlane.modelo.DatosOrden
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.TecnicoConCarga
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.VehiculoConEstado
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.viewmodel.ClienteViewModel
import com.leosle.pitlane.viewmodel.OrdenViewModel
import com.leosle.pitlane.viewmodel.SolicitudViewModel
import com.leosle.pitlane.viewmodel.TecnicoViewModel
import com.leosle.pitlane.viewmodel.VehiculoViewModel
import com.leosle.pitlane.vista.comun.BarraSuperior
import com.leosle.pitlane.vista.comun.Campos
import com.leosle.pitlane.vista.comun.Combos
import com.leosle.pitlane.vista.comun.DialogoModal
import com.leosle.pitlane.vista.comun.FragmentoVinculado
import com.leosle.pitlane.vista.comun.PasosFormulario
import com.leosle.pitlane.vista.vehiculos.FragRegistrarVehiculoJefe
import com.leosle.pitlane.vista.comun.GestorFotos
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Formulario de ingreso de un vehículo, dividido en tarjetas: cliente, vehículo, falla, estado, fotos y técnico.
 * Si recibe cliente, vehículo o solicitud, esos datos llegan llenados y los de cliente y vehículo quedan bloqueados.
 * Al registrar con éxito abre la pantalla de confirmación.
 */
class FragNuevaOrden : FragmentoVinculado<JoFragNuevaOrdenBinding>(JoFragNuevaOrdenBinding::inflate) {

    private val ordenModelo: OrdenViewModel by viewModels()
    private val clienteModelo: ClienteViewModel by viewModels()
    private val vehiculoModelo: VehiculoViewModel by viewModels()
    private val tecnicoModelo: TecnicoViewModel by viewModels()
    private val solicitudModelo: SolicitudViewModel by viewModels()

    /** Gestor de fotos del estado exterior: máximo 6; debe crearse junto con el fragmento. */
    private val gestorFotos = GestorFotos(this, AyudasOrden.MAXIMO_FOTOS, AyudasOrden.MENSAJE_MAXIMO_FOTOS) { fotos -> alCambiarFotos(fotos) }

    private var clientes: List<Usuario> = emptyList()
    private var clientesMostrados: List<Usuario> = emptyList()
    private var vehiculos: List<VehiculoConEstado> = emptyList()
    private var vehiculosMostrados: List<VehiculoConEstado> = emptyList()
    private var tecnicos: List<TecnicoConCarga> = emptyList()

    private var clienteIdActual: String? = null
    private var vehiculoIdPreferido: String? = null
    private var trabajoVehiculos: Job? = null
    private var placasPorCliente: Map<String, List<String>> = emptyMap()
    private var secuenciaBusqueda = 0
    private var falladaPrellenada = false
    private lateinit var pasos: PasosFormulario

    private val clienteIdInicial: String? get() = arguments?.getString(CLAVE_CLIENTE)
    private val vehiculoIdInicial: String? get() = arguments?.getString(CLAVE_VEHICULO)
    private val solicitudId: String? get() = arguments?.getString(CLAVE_SOLICITUD)

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        vehiculoIdPreferido = vehiculoIdInicial

        BarraSuperior.configurar(vinculo.incBarra, "Registrar ingreso", alVolver = {
            if (!pasos.retroceder()) navegador.volver()
        })
        configurarCliente()
        configurarVehiculo()
        configurarCampos()
        configurarTecnico()

        // Zona de fotos con su contador y mensajes
        vinculo.incFotos.txtEtiquetaFotos.text = "Fotos"
        gestorFotos.vincular(vinculo.incFotos)

        pasos = PasosFormulario(
            pasos = listOf(listOf(vinculo.llPasoCliente), listOf(vinculo.llPasoIngreso), listOf(vinculo.llPasoFotos)),
            titulos = listOf("Cliente y vehículo", "Falla y estado", "Fotos y técnico"),
            textoPaso = vinculo.txtPasoOrden,
            barras = listOf(vinculo.viewBarraOrdenUno, vinculo.viewBarraOrdenDos, vinculo.viewBarraOrdenTres),
            botonSiguiente = vinculo.btnSiguienteOrden,
            botonAtras = vinculo.btnAtrasOrden,
            botonFinal = vinculo.btnRegistrarOrden,
            validarPaso = ::validarPaso,
            alCambiarDePaso = {
                ocultarTeclado()
                vinculo.svNuevaOrden.scrollTo(0, 0)
            }
        )
        vinculo.btnAgregarVehiculoOrden.setOnClickListener {
            clienteIdActual?.let { navegador.irA(FragRegistrarVehiculoJefe.nuevo(it)) }
        }
        vinculo.btnRegistrarOrden.setOnClickListener { registrar() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!pasos.retroceder()) {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        prellenarDesdeSolicitud()
    }

    /** Cancela la escucha de vehículos al destruir la vista para que se reinicie al volver (por ejemplo tras agregar uno). */
    override fun onDestroyView() {
        trabajoVehiculos?.cancel()
        trabajoVehiculos = null
        super.onDestroyView()
    }

    // ---------------------------------------------------------------------------------------------
    // Cliente y vehículo
    // ---------------------------------------------------------------------------------------------

    /** Conecta la lista de clientes, el buscador y el combo; con cliente recibido lo deja bloqueado. */
    private fun configurarCliente() {
        val fijo = clienteIdInicial != null
        if (fijo) {
            // Cliente recibido: sin buscador y combo de solo lectura
            vinculo.etBuscarCliente.visibility = View.GONE
            vinculo.spnCliente.isEnabled = false
            vinculo.spnCliente.alpha = 0.6f
        } else {
            vinculo.etBuscarCliente.addTextChangedListener { actualizarComboClientes() }
        }

        vinculo.spnCliente.onItemSelectedListener = alElegir { posicion ->
            val elegido = if (fijo) clientesMostrados.getOrNull(posicion) else clientesMostrados.getOrNull(posicion - 1)
            vinculo.txtErrorCliente.visibility = View.GONE
            cambiarCliente(elegido?.id)
        }
        recolectar(clienteModelo.clientes) { lista ->
            clientes = lista
            actualizarComboClientes()
        }
    }

    /** Con 3 o más caracteres busca como en HU-15 (DNI, nombre, apellido o placa); con menos muestra a todos los clientes. */
    private fun actualizarComboClientes() {
        val fijo = clienteIdInicial != null
        val consulta = Campos.texto(vinculo.etBuscarCliente).trim()
        val numero = ++secuenciaBusqueda
        if (!fijo && consulta.length >= MINIMO_BUSQUEDA) {
            clienteModelo.buscar(consulta) { resultados ->
                if (numero != secuenciaBusqueda || view == null) return@buscar
                placasPorCliente = resultados.associate { it.cliente.id to it.placasCoincidentes }
                mostrarClientes(resultados.map { it.cliente })
            }
        } else {
            placasPorCliente = emptyMap()
            mostrarClientes(if (fijo) clientes.filter { it.id == clienteIdInicial } else clientes)
        }
    }

    /** Llena el combo con los clientes indicados, anotando la placa que coincidió con la búsqueda, y conserva la selección si sigue visible. */
    private fun mostrarClientes(lista: List<Usuario>) {
        val fijo = clienteIdInicial != null
        clientesMostrados = lista
        val titulos = clientesMostrados.map { cliente ->
            val placas = placasPorCliente[cliente.id].orEmpty()
            "${cliente.nombreCompleto} · DNI ${cliente.dni}" + if (placas.isEmpty()) "" else " · ${placas.joinToString(", ")}"
        }
        val opciones = if (fijo) titulos else listOf("Selecciona un cliente") + titulos
        Combos.configurar(vinculo.spnCliente, opciones)

        // Se vuelve a elegir al cliente anterior si sigue en la lista
        val indicePrevio = clientesMostrados.indexOfFirst { it.id == clienteIdActual }
        if (indicePrevio >= 0) vinculo.spnCliente.setSelection(if (fijo) indicePrevio else indicePrevio + 1)
        else if (!fijo) cambiarCliente(null)
    }

    /** Cuando cambia el cliente elegido, observa sus vehículos; con nulo vacía el combo de vehículos. */
    private fun cambiarCliente(nuevoId: String?) {
        if (nuevoId == clienteIdActual && trabajoVehiculos != null) return
        clienteIdActual = nuevoId
        trabajoVehiculos?.cancel()
        vehiculos = emptyList()
        actualizarComboVehiculos()
        if (nuevoId == null) {
            trabajoVehiculos = null
            return
        }
        trabajoVehiculos = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vehiculoModelo.vehiculosDeCliente(nuevoId).collect { lista ->
                    vehiculos = lista
                    actualizarComboVehiculos()
                }
            }
        }
    }

    /** Conecta el combo de vehículos; con vehículo recibido queda bloqueado. */
    private fun configurarVehiculo() {
        if (vehiculoIdInicial != null) {
            vinculo.spnVehiculo.isEnabled = false
            vinculo.spnVehiculo.alpha = 0.6f
        }
        vinculo.spnVehiculo.onItemSelectedListener = alElegir { posicion ->
            val indice = if (vehiculoIdInicial != null) posicion else posicion - 1
            vehiculoIdPreferido = vehiculosMostrados.getOrNull(indice)?.vehiculo?.id
            vinculo.txtErrorVehiculo.visibility = View.GONE
        }
        actualizarComboVehiculos()
    }

    /** Llena el combo con los vehículos del cliente elegido y marca el preferido si existe. */
    private fun actualizarComboVehiculos() {
        val fijo = vehiculoIdInicial != null
        vehiculosMostrados = if (fijo) vehiculos.filter { it.vehiculo.id == vehiculoIdInicial } else vehiculos
        val titulos = vehiculosMostrados.map {
            val enTaller = if (it.ordenActiva?.enCurso == true) " · en taller" else ""
            "${it.vehiculo.placa} · ${it.vehiculo.descripcion}$enTaller"
        }
        val sinVehiculos = clienteIdActual != null && !fijo && vehiculos.isEmpty()
        vinculo.llSinVehiculosOrden.visibility = if (sinVehiculos) View.VISIBLE else View.GONE
        val marcador = if (clienteIdActual == null) "Elige primero un cliente" else "Selecciona un vehículo"
        Combos.configurar(vinculo.spnVehiculo, if (fijo) titulos else listOf(marcador) + titulos)
        val indice = vehiculosMostrados.indexOfFirst { it.vehiculo.id == vehiculoIdPreferido }
        if (indice >= 0) vinculo.spnVehiculo.setSelection(if (fijo) indice else indice + 1)
    }

    // ---------------------------------------------------------------------------------------------
    // Falla, estado y técnico
    // ---------------------------------------------------------------------------------------------

    /** Conecta la validación al salir de cada campo y limpia el error del combustible al elegir un nivel. */
    private fun configurarCampos() {
        Campos.validarAlSalir(vinculo.etFalla, vinculo.txtErrorFalla, ::validarFalla)
        Campos.validarAlSalir(vinculo.etKilometraje, vinculo.txtErrorKilometraje, Validador::kilometraje)
        vinculo.incCombustible.rgCombustible.setOnCheckedChangeListener { _, _ ->
            vinculo.txtErrorCombustible.visibility = View.GONE
        }
    }

    /** Exige que la falla reportada no esté vacía. */
    private fun validarFalla(valor: String): String? = Validador.obligatorio(valor, "Describe la falla reportada")

    /** Conecta el combo de técnicos activos, cada uno con su número de órdenes en curso. */
    private fun configurarTecnico() {
        vinculo.spnTecnico.onItemSelectedListener = alElegir { vinculo.txtErrorTecnico.visibility = View.GONE }
        recolectar(tecnicoModelo.tecnicosActivos) { lista ->
            val previo = tecnicoElegido()?.tecnico?.id
            tecnicos = lista
            Combos.configurar(vinculo.spnTecnico, listOf("Selecciona un técnico") + lista.map(AyudasOrden::textoTecnico))
            val indice = lista.indexOfFirst { it.tecnico.id == previo }
            if (indice >= 0) vinculo.spnTecnico.setSelection(indice + 1)
        }
    }

    /** Devuelve el técnico elegido en el combo, o nulo si sigue en "Selecciona un técnico". */
    private fun tecnicoElegido(): TecnicoConCarga? = tecnicos.getOrNull(Combos.posicion(vinculo.spnTecnico) - 1)

    /** Copia la falla de la solicitud de origen al campo, una sola vez y solo si el campo sigue vacío. */
    private fun prellenarDesdeSolicitud() {
        val id = solicitudId ?: return
        recolectar(solicitudModelo.solicitud(id)) { detalle ->
            if (detalle != null && !falladaPrellenada && Campos.texto(vinculo.etFalla).isEmpty()) {
                vinculo.etFalla.setText(detalle.solicitud.descripcion)
            }
            if (detalle != null) falladaPrellenada = true
        }
    }

    /** Oculta el error de fotos en cuanto hay al menos una foto adjunta. */
    internal fun alCambiarFotos(fotos: List<Foto>) {
        if (view == null) return
        if (fotos.isNotEmpty()) vinculo.txtErrorFotos.visibility = View.GONE
    }

    // ---------------------------------------------------------------------------------------------
    // Registro
    // ---------------------------------------------------------------------------------------------

    /** Selección actual de vehículo del combo, o `null` si no se eligió ninguno. */
    private fun vehiculoElegido(): VehiculoConEstado? {
        val indice = Combos.posicion(vinculo.spnVehiculo) - (if (vehiculoIdInicial != null) 0 else 1)
        return vehiculosMostrados.getOrNull(indice)
    }

    /** Muestra [mensaje] bajo el campo indicado. */
    private fun marcarError(etiqueta: TextView, mensaje: String) {
        etiqueta.text = mensaje
        etiqueta.visibility = View.VISIBLE
    }

    /**
     * Valida solo los campos del [paso] indicado (0 cliente y vehículo, 1 falla y estado, 2 fotos y técnico),
     * mostrando todos sus errores a la vez; devuelve `true` si el paso está completo.
     */
    private fun validarPaso(paso: Int): Boolean {
        var valido = true
        when (paso) {
            0 -> {
                if (clienteIdActual == null) { marcarError(vinculo.txtErrorCliente, "Selecciona un cliente"); valido = false }
                if (vehiculoElegido() == null) { marcarError(vinculo.txtErrorVehiculo, "Selecciona un vehículo"); valido = false }
            }
            1 -> {
                if (!Campos.validar(vinculo.etFalla, vinculo.txtErrorFalla, ::validarFalla)) valido = false
                if (!Campos.validar(vinculo.etKilometraje, vinculo.txtErrorKilometraje, Validador::kilometraje)) valido = false
                if (AyudasOrden.combustibleElegido(vinculo.incCombustible.rgCombustible) == null) {
                    marcarError(vinculo.txtErrorCombustible, "Elige el nivel de combustible")
                    valido = false
                }
            }
            else -> {
                if (gestorFotos.fotos.isEmpty()) {
                    marcarError(vinculo.txtErrorFotos, "Agrega al menos una foto del estado del vehículo")
                    valido = false
                }
                if (tecnicoElegido() == null) { marcarError(vinculo.txtErrorTecnico, "Selecciona un técnico"); valido = false }
            }
        }
        return valido
    }

    /** Valida los tres pasos en orden; lleva a la persona al primero con errores y, si todo está bien, registra la orden. */
    private fun registrar() {
        ocultarTeclado()
        for (paso in 0..2) {
            if (!validarPaso(paso)) {
                pasos.irAlPaso(paso)
                return
            }
        }
        val clienteId = clienteIdActual
        val vehiculo = vehiculoElegido()
        val tecnico = tecnicoElegido()
        val combustible = AyudasOrden.combustibleElegido(vinculo.incCombustible.rgCombustible)
        val fotos = gestorFotos.fotos

        val datos = DatosOrden(
            clienteId = checkNotNull(clienteId),
            vehiculoId = checkNotNull(vehiculo).vehiculo.id,
            solicitudId = solicitudId,
            falla = Campos.texto(vinculo.etFalla),
            kilometraje = Campos.texto(vinculo.etKilometraje).toInt(),
            combustible = checkNotNull(combustible),
            observaciones = Campos.texto(vinculo.etObservaciones),
            fotos = fotos,
            tecnicoId = checkNotNull(tecnico).tecnico.id
        )
        vinculo.btnRegistrarOrden.isEnabled = false
        ordenModelo.registrarOrden(datos, usuarioActual.id) { resultado ->
            if (view == null) return@registrarOrden
            vinculo.btnRegistrarOrden.isEnabled = true
            resultado
                .alExito { navegador.irA(FragConfirmacionIngreso.nuevo(it.orden.id, it.clienteNotificado), apilar = false) }
                .alError { DialogoModal.error(requireContext(), it.mensaje) }
        }
    }

    /** Crea un oyente de selección de combo que solo avisa la posición elegida. */
    private fun alElegir(accion: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(padre: AdapterView<*>?, vista: View?, posicion: Int, id: Long) = accion(posicion)
        override fun onNothingSelected(padre: AdapterView<*>?) = Unit
    }

    companion object {
        private const val CLAVE_CLIENTE = "clienteId"
        private const val CLAVE_VEHICULO = "vehiculoId"
        private const val CLAVE_SOLICITUD = "solicitudId"
        private const val MINIMO_BUSQUEDA = 3

        /** Crea el formulario con cliente, vehículo y solicitud ya elegidos; cualquiera puede ser nulo. */
        fun nuevo(clienteId: String?, vehiculoId: String?, solicitudId: String?): FragNuevaOrden =
            FragNuevaOrden().apply {
                arguments = Bundle().apply {
                    putString(CLAVE_CLIENTE, clienteId)
                    putString(CLAVE_VEHICULO, vehiculoId)
                    putString(CLAVE_SOLICITUD, solicitudId)
                }
            }
    }
}
