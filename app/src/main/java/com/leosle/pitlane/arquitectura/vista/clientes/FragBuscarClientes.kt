package com.leosle.pitlane.arquitectura.vista.clientes

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JcFragBuscarClientesBinding
import com.leosle.pitlane.databinding.JcItemResultadoClienteBinding
import com.leosle.pitlane.modelo.ResultadoBusqueda
import com.leosle.pitlane.arquitectura.viewmodel.ClienteViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AdaptadorLista
import com.leosle.pitlane.arquitectura.vista.comun.EstadoVacio
import com.leosle.pitlane.arquitectura.vista.comun.EstadosVisuales
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.util.Textos

/**
 * Pestaña de clientes: antes de escribir muestra los clientes atendidos recientemente; busca por DNI, nombre,
 * apellido o placa mientras se escribe (desde 3 caracteres), resalta las placas que coincidieron y abre el detalle
 * del cliente elegido. Sin coincidencias ofrece registrarlo.
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
        mostrarRecientes(modelo.recientes.value)
        recolectar(modelo.recientes) { recientes -> if (consultaCorta()) mostrarRecientes(recientes) }
    }

    private fun consultaCorta(): Boolean = vinculo.etBuscar.text.toString().trim().length < MINIMO_CARACTERES

    /** Muestra los clientes atendidos recientemente con su título; sin ninguno, la ayuda para buscar. */
    private fun mostrarRecientes(recientes: List<ResultadoBusqueda>) {
        mostrarEstadoInicial()
        vinculo.txtTituloLista.visibility = if (recientes.isEmpty()) View.GONE else View.VISIBLE
        adaptador.actualizar(recientes)
    }

    /** Repite la búsqueda vigente al volver a la pestaña para reflejar altas, cambios o bajas de clientes. */
    override fun onStart() {
        super.onStart()
        if (view != null) buscar(vinculo.etBuscar.text.toString())
    }

    /** Busca con el texto indicado; con menos de 3 caracteres vuelve a mostrar los clientes recientes. */
    private fun buscar(texto: String) {
        val consulta = texto.trim()
        val numero = ++secuenciaBusqueda
        if (consulta.length < MINIMO_CARACTERES) {
            mostrarRecientes(modelo.recientes.value)
            return
        }
        modelo.buscar(consulta) { resultados ->
            if (numero != secuenciaBusqueda || view == null) return@buscar
            mostrarEstadoSinResultados()
            vinculo.txtTituloLista.visibility = View.GONE
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

    /**
     * Llena una fila con avatar, nombre, celular y DNI (cada uno con su ícono), y una fila de chips: las placas del cliente (resaltadas
     * las que coincidieron con la búsqueda, hasta [MAXIMO_PLACAS] y un contador del resto) y si tiene cuenta en la app.
     */
    private fun enlazarFila(fila: View, item: ResultadoBusqueda) {
        val tarjeta = JcItemResultadoClienteBinding.bind(fila)
        val cliente = item.cliente
        val contexto = fila.context
        tarjeta.txtInicialesCliente.text = cliente.iniciales
        tarjeta.txtNombreCliente.text = cliente.nombreCompleto
        tarjeta.txtTelefonoCliente.text = Textos.celular(cliente.telefono)
        tarjeta.txtDniCliente.text = cliente.dni
        tarjeta.txtDniCliente.contentDescription = "DNI ${cliente.dni}"

        val grupo = tarjeta.cgEtiquetasCliente
        grupo.removeAllViews()
        val placas = item.placas.ifEmpty { item.placasCoincidentes }
        placas.sortedByDescending { it in item.placasCoincidentes }.take(MAXIMO_PLACAS).forEach { placa ->
            val coincide = placa in item.placasCoincidentes
            grupo.addView(
                EstadosVisuales.nuevoChip(
                    contexto,
                    placa,
                    if (coincide) R.color.estado_proceso_fondo else R.color.estado_neutro_fondo,
                    if (coincide) R.color.estado_proceso_texto else R.color.estado_neutro_texto,
                    R.drawable.ic_car_profile
                )
            )
        }
        if (placas.size > MAXIMO_PLACAS) {
            grupo.addView(
                EstadosVisuales.nuevoChip(
                    contexto, "+${placas.size - MAXIMO_PLACAS}", R.color.estado_neutro_fondo, R.color.estado_neutro_texto, R.drawable.ic_car_profile
                ).apply { setCompoundDrawablesRelative(null, null, null, null) }
            )
        }
        grupo.addView(
            if (cliente.tieneCuenta) {
                EstadosVisuales.nuevoChip(contexto, "Con cuenta", R.color.estado_exito_fondo, R.color.estado_exito_texto, R.drawable.ic_user_circle)
            } else {
                EstadosVisuales.nuevoChip(contexto, "Sin cuenta", R.color.estado_neutro_fondo, R.color.estado_neutro_texto, R.drawable.ic_user)
            }
        )
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

        /** Placas que se muestran como chips antes de resumir el resto con un contador. */
        const val MAXIMO_PLACAS = 3
    }
}
