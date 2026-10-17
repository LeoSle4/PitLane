package com.leosle.pitlane.vista.vehiculos

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.vista.comun.MenuAcciones
import com.leosle.pitlane.databinding.CliFragMisVehiculosBinding
import com.leosle.pitlane.databinding.CliItemVehiculoBinding
import com.leosle.pitlane.modelo.VehiculoConEstado
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.viewmodel.VehiculoViewModel
import com.leosle.pitlane.vista.comun.AdaptadorLista
import com.leosle.pitlane.vista.comun.AyudasVista
import com.leosle.pitlane.vista.comun.DialogoModal
import com.leosle.pitlane.vista.comun.EstadoVacio
import com.leosle.pitlane.vista.comun.EstadosVisuales
import com.leosle.pitlane.vista.comun.LogosMarca
import com.leosle.pitlane.vista.comun.FragmentoVinculado
import com.leosle.pitlane.vista.solicitudes.FragNuevaSolicitud

/**
 * Pestaña raíz del cliente con sus vehículos. Muestra un saludo, la lista con el estado de cada vehículo en el taller
 * y un menú por tarjeta para editar, eliminar o solicitar atención; tocar un vehículo con orden activa abre su estado.
 */
class FragMisVehiculos : FragmentoVinculado<CliFragMisVehiculosBinding>(CliFragMisVehiculosBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val vehiculoViewModel: VehiculoViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<VehiculoConEstado>

    /** Prepara el saludo, la lista, el estado vacío y los botones, y empieza a observar los vehículos del cliente. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vinculo.txtSaludo.text = getString(R.string.cli_saludo, usuarioActual.nombres)

        adaptador = AdaptadorLista(requireContext(), R.layout.cli_item_vehiculo) { vista, elemento -> enlazarFila(vista, elemento) }
        vinculo.lvVehiculos.adapter = adaptador
        vinculo.lvVehiculos.emptyView = vinculo.incVacio.root
        vinculo.lvVehiculos.setOnItemClickListener { _, _, posicion, _ -> abrirEstado(adaptador.getItem(posicion)) }

        EstadoVacio.configurar(
            vinculo.incVacio,
            getString(R.string.cli_vehiculos_vacio_titulo),
            getString(R.string.cli_vehiculos_vacio_mensaje),
            getString(R.string.cli_agregar_vehiculo)
        ) { abrirRegistro() }
        vinculo.btnAgregarVehiculo.setOnClickListener { abrirRegistro() }

        recolectar(vehiculoViewModel.vehiculosDeCliente(usuarioActual.id)) { adaptador.actualizar(it) }
    }

    /** Llena una fila con la placa, la descripción, el chip de estado, la barra del color del estado y el menú. */
    private fun enlazarFila(vista: View, elemento: VehiculoConEstado) {
        val fila = CliItemVehiculoBinding.bind(vista)
        val vehiculo = elemento.vehiculo
        fila.txtPlaca.text = vehiculo.placa
        LogosMarca.aplicar(fila.imgMarcaVehiculo, vehiculo.marca)
        fila.txtDescripcionVehiculo.text = "${vehiculo.descripcion} · ${vehiculo.color}"
        EstadosVisuales.estadoVehiculo(fila.txtEstadoVehiculo, elemento.ordenActiva)
        AyudasVista.pintarBarra(fila.viewBarraEstado, AyudasVista.colorOrden(elemento.ordenActiva?.estado))
        fila.btnMenuVehiculo.setOnClickListener { mostrarMenu(it, elemento) }
    }

    /** Abre el estado de la orden activa del vehículo; sin orden avisa con un mensaje breve. */
    private fun abrirEstado(elemento: VehiculoConEstado) {
        val orden = elemento.ordenActiva
        if (orden == null) {
            mostrarMensaje(getString(R.string.cli_vehiculo_sin_orden))
        } else {
            navegador.abrirOrden(orden.id)
        }
    }

    /** Abre la pantalla de registro de un vehículo nuevo. */
    private fun abrirRegistro() {
        navegador.irA(FragRegistrarVehiculo())
    }

    /** Muestra el menú de la tarjeta con las acciones Editar, Eliminar y Solicitar atención. */
    private fun mostrarMenu(ancla: View, elemento: VehiculoConEstado) {
        MenuAcciones.mostrar(
            ancla,
            listOf(
                MenuAcciones.Opcion(R.drawable.ic_pencil_simple, getString(R.string.cli_editar)) {
                    HojaEditarVehiculo.nueva(elemento.vehiculo.id).show(parentFragmentManager, "hoja_editar_vehiculo")
                },
                MenuAcciones.Opcion(R.drawable.ic_clipboard_text, getString(R.string.cli_solicitar_atencion)) {
                    navegador.irA(FragNuevaSolicitud.nuevo(elemento.vehiculo.id))
                },
                MenuAcciones.Opcion(R.drawable.ic_trash, getString(R.string.cli_eliminar), peligro = true) { confirmarEliminacion(elemento) }
            )
        )
    }

    /** Pide confirmar la eliminación y, si el repositorio la rechaza, muestra el diálogo de error con el motivo. */
    private fun confirmarEliminacion(elemento: VehiculoConEstado) {
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_trash,
            getString(R.string.cli_eliminar_vehiculo_titulo),
            getString(R.string.cli_eliminar_vehiculo_mensaje, elemento.vehiculo.placa),
            getString(R.string.cli_eliminar),
            peligro = true
        ) {
            vehiculoViewModel.eliminarVehiculo(elemento.vehiculo.id, false) { resultado -> alEliminar(resultado) }
        }
    }

    /** Informa el resultado de eliminar: un mensaje breve si salió bien o un diálogo de error si no. */
    private fun alEliminar(resultado: Resultado<Unit>) {
        if (view == null) return
        when (resultado) {
            is Resultado.Exito -> mostrarMensaje(getString(R.string.cli_vehiculo_eliminado))
            is Resultado.Error -> DialogoModal.error(requireContext(), resultado.mensaje, getString(R.string.cli_no_se_pudo_eliminar))
        }
    }
}
