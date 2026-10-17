package com.leosle.pitlane.vista.clientes

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.vista.comun.MenuAcciones
import com.leosle.pitlane.databinding.JcFragDetalleClienteBinding
import com.leosle.pitlane.databinding.JcItemHistorialOrdenBinding
import com.leosle.pitlane.databinding.JcItemVehiculoClienteBinding
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.FichaCliente
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.VehiculoConHistorial
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.viewmodel.ClienteViewModel
import com.leosle.pitlane.viewmodel.VehiculoViewModel
import com.leosle.pitlane.vista.comun.BarraSuperior
import com.leosle.pitlane.vista.comun.DialogoModal
import com.leosle.pitlane.vista.comun.EstadoVacio
import com.leosle.pitlane.vista.comun.EstadosVisuales
import com.leosle.pitlane.vista.comun.FragmentoVinculado
import com.leosle.pitlane.vista.comun.LogosMarca
import com.leosle.pitlane.vista.comun.poblar
import com.leosle.pitlane.vista.ordenes.FragNuevaOrden
import com.leosle.pitlane.vista.vehiculos.FragRegistrarVehiculoJefe
import com.leosle.pitlane.vista.vehiculos.HojaEditarVehiculoJefe

/**
 * Detalle de un cliente: datos personales, insignia de cuenta, vehículos con su historial de órdenes desplegable
 * y las acciones de editar, agregar vehículo, registrar ingreso y eliminar. Se redibuja con cada cambio de la ficha.
 */
class FragDetalleCliente : FragmentoVinculado<JcFragDetalleClienteBinding>(JcFragDetalleClienteBinding::inflate) {

    private val modelo: ClienteViewModel by viewModels()
    private val modeloVehiculo: VehiculoViewModel by viewModels()

    private val clienteId: String get() = requireArguments().getString(CLAVE_CLIENTE).orEmpty()
    private var fichaActual: FichaCliente? = null
    private val vehiculosAbiertos = mutableSetOf<String>()

    /** Configura la barra con su menú, los botones de acción, el estado vacío y la observación de la ficha. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(
            vinculo.barraSuperior,
            "Detalle del cliente",
            alVolver = { navegador.volver() },
            iconoAccion = R.drawable.ic_dots_three_vertical,
            descripcionAccion = "Más opciones",
            alAccion = { mostrarMenuCliente(vinculo.barraSuperior.btnAccionBarra) }
        )
        vinculo.btnEditarCliente.setOnClickListener { fichaActual?.let { HojaEditarCliente.mostrar(parentFragmentManager, it.cliente) } }
        vinculo.btnAgregarVehiculo.setOnClickListener { navegador.irA(FragRegistrarVehiculoJefe.nuevo(clienteId)) }
        vinculo.btnRegistrarIngreso.setOnClickListener { navegador.irA(FragNuevaOrden.nuevo(clienteId, null, null)) }
        EstadoVacio.configurar(
            vinculo.includeVacioVehiculos,
            "Sin vehículos",
            "Agrega el primer vehículo de este cliente para poder generar su orden de ingreso.",
            "Agregar vehículo"
        ) { navegador.irA(FragRegistrarVehiculoJefe.nuevo(clienteId)) }

        recolectar(modelo.ficha(clienteId)) { ficha ->
            if (ficha != null) {
                fichaActual = ficha
                dibujar(ficha)
            }
        }
    }

    /** Escribe los datos del cliente y dibuja la lista de sus vehículos. */
    private fun dibujar(ficha: FichaCliente) {
        val cliente = ficha.cliente
        vinculo.txtInicialesDetalle.text = cliente.iniciales
        vinculo.txtNombreDetalle.text = cliente.nombreCompleto
        vinculo.txtDniDetalle.text = "DNI ${cliente.dni}"
        vinculo.txtTelefonoDetalle.text = cliente.telefono.ifBlank { "Sin teléfono registrado" }
        vinculo.txtCorreoDetalle.text = cliente.correo.ifBlank { "Sin correo registrado" }
        if (cliente.tieneCuenta) {
            vinculo.txtInsigniaApp.visibility = View.VISIBLE
            EstadosVisuales.aplicar(
                vinculo.txtInsigniaApp,
                "Tiene la app",
                R.color.estado_exito_fondo,
                R.color.estado_exito_texto,
                R.drawable.ic_seal_check
            )
        } else {
            vinculo.txtInsigniaApp.visibility = View.VISIBLE
            EstadosVisuales.aplicar(
                vinculo.txtInsigniaApp,
                "Sin cuenta en la app",
                R.color.estado_neutro_fondo,
                R.color.estado_neutro_texto,
                R.drawable.ic_x_circle
            )
        }

        vinculo.txtTituloVehiculos.text = "Vehículos (${ficha.vehiculos.size})"
        EstadoVacio.mostrar(vinculo.includeVacioVehiculos, ficha.vehiculos.isEmpty())
        vinculo.llVehiculos.poblar(ficha.vehiculos, R.layout.jc_item_vehiculo_cliente) { fila, item -> enlazarVehiculo(fila, item) }
    }

    /** Llena la tarjeta de un vehículo con sus datos, su estado, su menú y su historial desplegable. */
    private fun enlazarVehiculo(fila: View, item: VehiculoConHistorial) {
        val tarjeta = JcItemVehiculoClienteBinding.bind(fila)
        val vehiculo = item.vehiculo
        tarjeta.txtPlacaVehiculo.text = vehiculo.placa
        LogosMarca.aplicar(tarjeta.imgIconoVehiculo, vehiculo.marca)
        tarjeta.txtDescripcionVehiculo.text = "${vehiculo.descripcion} · ${vehiculo.color}"
        EstadosVisuales.estadoVehiculo(tarjeta.txtChipVehiculo, item.ordenes.firstOrNull { it.estado != EstadoOrden.ANULADA })
        tarjeta.btnMenuVehiculo.setOnClickListener { mostrarMenuVehiculo(it, item) }

        tarjeta.txtTituloHistorial.text = "Historial de órdenes (${item.ordenes.size})"
        tarjeta.txtSinHistorial.visibility = if (item.ordenes.isEmpty()) View.VISIBLE else View.GONE
        tarjeta.llOrdenesHistorial.poblar(item.ordenes, R.layout.jc_item_historial_orden) { filaOrden, orden -> enlazarOrden(filaOrden, orden) }

        aplicarDespliegue(tarjeta, vehiculo.id in vehiculosAbiertos)
        tarjeta.llEncabezadoHistorial.setOnClickListener {
            val abrir = !vehiculosAbiertos.contains(vehiculo.id)
            if (abrir) vehiculosAbiertos.add(vehiculo.id) else vehiculosAbiertos.remove(vehiculo.id)
            aplicarDespliegue(tarjeta, abrir)
        }
    }

    /** Muestra u oculta el historial de un vehículo y gira la flecha según el estado. */
    private fun aplicarDespliegue(tarjeta: JcItemVehiculoClienteBinding, abierto: Boolean) {
        tarjeta.llHistorial.visibility = if (abierto) View.VISIBLE else View.GONE
        tarjeta.imgFlechaHistorial.rotation = if (abierto) 180f else 0f
    }

    /** Llena una fila del historial con fecha, número, falla, chip del estado final y barra del color del estado. */
    private fun enlazarOrden(fila: View, orden: Orden) {
        val tarjeta = JcItemHistorialOrdenBinding.bind(fila)
        tarjeta.txtFechaOrden.text = "${Fechas.fecha(orden.creadaEn)} · Orden ${orden.numero}"
        tarjeta.txtFallaOrden.text = orden.falla
        EstadosVisuales.estadoOrden(tarjeta.txtChipOrden, orden.estado)
        ViewCompat.setBackgroundTintList(
            tarjeta.viewBarraEstado,
            ContextCompat.getColorStateList(requireContext(), EstadosVisuales.colorEstado(orden.estado))
        )
        tarjeta.llFilaHistorial.setOnClickListener { navegador.abrirOrden(orden.id) }
    }

    /** Despliega el menú de la barra con la opción de eliminar al cliente. */
    private fun mostrarMenuCliente(ancla: View) {
        MenuAcciones.mostrar(
            ancla,
            listOf(MenuAcciones.Opcion(R.drawable.ic_trash, "Eliminar cliente", peligro = true) { confirmarEliminarCliente() })
        )
    }

    /** Despliega el menú de un vehículo con Editar y Eliminar. */
    private fun mostrarMenuVehiculo(ancla: View, item: VehiculoConHistorial) {
        MenuAcciones.mostrar(
            ancla,
            listOf(
                MenuAcciones.Opcion(R.drawable.ic_pencil_simple, "Editar") { HojaEditarVehiculoJefe.mostrar(parentFragmentManager, item.vehiculo) },
                MenuAcciones.Opcion(R.drawable.ic_trash, "Eliminar", peligro = true) { confirmarEliminarVehiculo(item) }
            )
        )
    }

    /** Pide confirmar la eliminación del cliente; si no se puede muestra el diálogo con el motivo. */
    private fun confirmarEliminarCliente() {
        val cliente = fichaActual?.cliente ?: return
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_trash,
            "¿Eliminar cliente?",
            "Se eliminará a ${cliente.nombreCompleto} y todos sus vehículos. Esta acción no se puede deshacer.",
            "Eliminar",
            peligro = true
        ) {
            modelo.eliminarCliente(cliente.id) { resultado ->
                resultado
                    .alExito {
                        mostrarMensaje("Cliente eliminado")
                        navegador.volver()
                    }
                    .alError { DialogoModal.error(requireContext(), it.mensaje, "No se puede eliminar") }
            }
        }
    }

    /** Pide confirmar la eliminación de un vehículo; si tiene historial muestra el diálogo con el motivo. */
    private fun confirmarEliminarVehiculo(item: VehiculoConHistorial) {
        val vehiculo = item.vehiculo
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_trash,
            "¿Eliminar vehículo?",
            "Se eliminará el vehículo ${vehiculo.placa} (${vehiculo.descripcion}). Esta acción no se puede deshacer.",
            "Eliminar",
            peligro = true
        ) {
            modeloVehiculo.eliminarVehiculo(vehiculo.id, true) { resultado ->
                resultado
                    .alExito { mostrarMensaje("Vehículo eliminado") }
                    .alError { DialogoModal.error(requireContext(), it.mensaje, "No se puede eliminar") }
            }
        }
    }

    companion object {
        private const val CLAVE_CLIENTE = "clienteId"

        /** Crea el detalle del cliente indicado. */
        fun nuevo(clienteId: String): FragDetalleCliente =
            FragDetalleCliente().apply {
                arguments = Bundle().apply { putString(CLAVE_CLIENTE, clienteId) }
            }
    }
}
