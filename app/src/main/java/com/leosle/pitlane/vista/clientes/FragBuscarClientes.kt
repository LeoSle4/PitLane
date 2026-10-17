package com.leosle.pitlane.vista.clientes

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JcFragBuscarClientesBinding
import com.leosle.pitlane.databinding.JcItemResultadoClienteBinding
import com.leosle.pitlane.modelo.ResultadoBusqueda
import com.leosle.pitlane.viewmodel.ClienteViewModel
import com.leosle.pitlane.vista.comun.AdaptadorLista
import com.leosle.pitlane.vista.comun.EstadoVacio
import com.leosle.pitlane.vista.comun.EstadosVisuales
import com.leosle.pitlane.vista.comun.FragmentoVinculado

/**
 * Pestaña de clientes: busca por DNI, nombre, apellido o placa mientras se escribe (desde 3 caracteres),
 * muestra las placas que coincidieron y abre el detalle del cliente elegido. Sin coincidencias ofrece registrarlo.
 */
class FragBuscarClientes : FragmentoVinculado<JcFragBuscarClientesBinding>(JcFragBuscarClientesBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val modelo: ClienteViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<ResultadoBusqueda>
    private var secuenciaBusqueda = 0

    /** Prepara la lista de resultados, el campo de búsqueda y los botones de registro. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        adaptador = AdaptadorLista(requireContext(), R.layout.jc_item_resultado_cliente) { fila, item -> enlazarFila(fila, item) }
        vinculo.lvResultados.adapter = adaptador
        vinculo.lvResultados.emptyView = vinculo.includeVacio.root
        vinculo.lvResultados.setOnItemClickListener { _, _, posicion, _ -> abrirDetalle(adaptador.getItem(posicion)) }

        vinculo.etBuscar.addTextChangedListener { buscar(it?.toString().orEmpty()) }
        vinculo.etBuscar.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEARCH) ocultarTeclado()
            accion == EditorInfo.IME_ACTION_SEARCH
        }
        vinculo.btnAgregarCliente.setOnClickListener { abrirRegistro() }
        mostrarEstadoInicial()
    }

    /** Repite la búsqueda vigente al volver a la pestaña para reflejar altas, cambios o bajas de clientes. */
    override fun onStart() {
        super.onStart()
        if (view != null) buscar(vinculo.etBuscar.text.toString())
    }

    /** Busca con el texto indicado; con menos de 3 caracteres limpia la lista y muestra la ayuda inicial. */
    private fun buscar(texto: String) {
        val consulta = texto.trim()
        val numero = ++secuenciaBusqueda
        if (consulta.length < MINIMO_CARACTERES) {
            mostrarEstadoInicial()
            adaptador.actualizar(emptyList())
            return
        }
        modelo.buscar(consulta) { resultados ->
            if (numero != secuenciaBusqueda || view == null) return@buscar
            mostrarEstadoSinResultados()
            adaptador.actualizar(resultados)
        }
    }

    /** Configura el estado vacío que invita a escribir la búsqueda. */
    private fun mostrarEstadoInicial() {
        EstadoVacio.configurar(
            vinculo.includeVacio,
            "Busca a un cliente",
            "Escribe el DNI, el nombre, el apellido o la placa del vehículo."
        )
    }

    /** Configura el estado vacío de una búsqueda sin coincidencias, con el botón para registrar al cliente. */
    private fun mostrarEstadoSinResultados() {
        EstadoVacio.configurar(
            vinculo.includeVacio,
            "No se encontró el cliente",
            "Revisa lo escrito o registra a la persona como cliente nuevo.",
            "Registrar cliente"
        ) { abrirRegistro() }
    }

    /** Llena una fila con avatar, nombre, DNI, teléfono y las placas que coincidieron con la búsqueda. */
    private fun enlazarFila(fila: View, item: ResultadoBusqueda) {
        val tarjeta = JcItemResultadoClienteBinding.bind(fila)
        val cliente = item.cliente
        tarjeta.txtInicialesCliente.text = cliente.iniciales
        tarjeta.txtNombreCliente.text = cliente.nombreCompleto
        tarjeta.txtDatosCliente.text = "DNI ${cliente.dni} · ${cliente.telefono}"
        if (item.placasCoincidentes.isEmpty()) {
            tarjeta.txtPlacasCoincidentes.visibility = View.GONE
        } else {
            tarjeta.txtPlacasCoincidentes.visibility = View.VISIBLE
            EstadosVisuales.aplicar(
                tarjeta.txtPlacasCoincidentes,
                item.placasCoincidentes.joinToString(", "),
                R.color.estado_neutro_fondo,
                R.color.estado_neutro_texto,
                R.drawable.ic_car_profile
            )
        }
    }

    /** Abre el detalle del cliente del resultado tocado. */
    private fun abrirDetalle(item: ResultadoBusqueda) {
        ocultarTeclado()
        navegador.irA(FragDetalleCliente.nuevo(item.cliente.id))
    }

    /** Abre el registro de cliente; si lo escrito es un DNI completo lo entrega como valor inicial. */
    private fun abrirRegistro() {
        val consulta = vinculo.etBuscar.text.toString().trim()
        val dni = if (consulta.length == 8 && consulta.all { it.isDigit() }) consulta else null
        navegador.irA(FragRegistrarCliente.nuevo(dni))
    }

    private companion object {
        /** Cantidad mínima de caracteres para iniciar la búsqueda. */
        const val MINIMO_CARACTERES = 3
    }
}
