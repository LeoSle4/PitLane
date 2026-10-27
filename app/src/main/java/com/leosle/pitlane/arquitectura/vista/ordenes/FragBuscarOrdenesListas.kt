package com.leosle.pitlane.arquitectura.vista.ordenes

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.viewmodel.OrdenViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AdaptadorLista
import com.leosle.pitlane.arquitectura.vista.comun.BarraSuperior
import com.leosle.pitlane.arquitectura.vista.comun.EstadoVacio
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import com.leosle.pitlane.databinding.JoFragBuscarOrdenesListasBinding
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.util.Fechas

/**
 * Búsqueda de órdenes Listo para recoger por placa, nombre o DNI del cliente o número de orden. Con el campo vacío
 * muestra todas las listas; al tocar una orden abre su detalle para registrar la conformidad y la entrega.
 */
class FragBuscarOrdenesListas : FragmentoVinculado<JoFragBuscarOrdenesListasBinding>(JoFragBuscarOrdenesListasBinding::inflate) {

    private val modelo: OrdenViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<OrdenDetallada>
    private var secuenciaBusqueda = 0

    /** Prepara la barra, la lista de resultados y el campo de búsqueda, y muestra de inicio todas las órdenes listas. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        BarraSuperior.configurar(vinculo.incBarra, getString(R.string.jo_listas_titulo), alVolver = { navegador.volver() })

        adaptador = AdaptadorLista(requireContext(), R.layout.jo_item_orden_panel) { fila, orden -> enlazarOrden(fila, orden) }
        vinculo.lvListas.adapter = adaptador
        vinculo.lvListas.emptyView = vinculo.incVacio.root
        vinculo.lvListas.setOnItemClickListener { _, _, posicion, _ ->
            ocultarTeclado()
            navegador.irA(FragDetalleOrdenJefe.nuevo(adaptador.getItem(posicion).orden.id))
        }

        vinculo.etBuscarListas.addTextChangedListener { buscar(it?.toString().orEmpty()) }
        vinculo.etBuscarListas.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEARCH) ocultarTeclado()
            accion == EditorInfo.IME_ACTION_SEARCH
        }
    }

    /** Repite la búsqueda vigente al volver para reflejar las órdenes que cambiaron de estado. */
    override fun onStart() {
        super.onStart()
        if (view != null) buscar(vinculo.etBuscarListas.text.toString())
    }

    /** Busca con el texto escrito y descarta la respuesta si ya se lanzó una búsqueda más reciente. */
    private fun buscar(texto: String) {
        val consulta = texto.trim()
        val numero = ++secuenciaBusqueda
        modelo.buscarOrdenesListas(consulta) { resultados ->
            if (numero != secuenciaBusqueda || view == null) return@buscarOrdenesListas
            if (consulta.isEmpty()) {
                EstadoVacio.configurar(
                    vinculo.incVacio,
                    getString(R.string.jo_listas_sin_ordenes),
                    getString(R.string.jo_listas_sin_ordenes_detalle)
                )
            } else {
                EstadoVacio.configurar(
                    vinculo.incVacio,
                    getString(R.string.jo_listas_vacio),
                    getString(R.string.jo_listas_vacio_detalle)
                )
            }
            adaptador.actualizar(resultados)
        }
    }

    /** Llena una fila con la tarjeta de la orden y la fecha en que quedó lista. */
    private fun enlazarOrden(fila: View, detalle: OrdenDetallada) {
        val listaEn = detalle.orden.fechaDeEstado(EstadoOrden.LISTO) ?: detalle.orden.fechaUltimoCambio
        AyudasOrden.pintarTarjetaOrden(fila, detalle, getString(R.string.jo_listas_fecha, Fechas.fecha(listaEn)))
    }
}
