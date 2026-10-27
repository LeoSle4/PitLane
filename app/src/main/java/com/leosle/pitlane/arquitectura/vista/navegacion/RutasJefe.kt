package com.leosle.pitlane.arquitectura.vista.navegacion

import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.vista.clientes.FragBuscarClientes
import com.leosle.pitlane.arquitectura.vista.comun.ItemPestana
import com.leosle.pitlane.arquitectura.vista.comun.Navegador
import com.leosle.pitlane.arquitectura.vista.ordenes.FragDetalleOrdenJefe
import com.leosle.pitlane.arquitectura.vista.ordenes.FragPanelOrdenes
import com.leosle.pitlane.arquitectura.vista.perfil.PestanasComunes
import com.leosle.pitlane.arquitectura.vista.solicitudes.FragSolicitudesPendientes
import com.leosle.pitlane.arquitectura.vista.tecnicos.FragListaTecnicos

/** Puntos de entrada de las pantallas del Jefe de Taller: pestañas de la barra inferior y apertura de órdenes. */
object RutasJefe {

    /** Pestañas del Jefe en el orden de la barra: Panel, Solicitudes, Clientes, Técnicos y Perfil. */
    fun pestanas(): List<ItemPestana> = listOf(
        ItemPestana("Panel", R.drawable.ic_squares_four, R.drawable.ic_squares_four_fill) { FragPanelOrdenes() },
        ItemPestana("Solicitudes", R.drawable.ic_clipboard_text, R.drawable.ic_clipboard_text_fill) { FragSolicitudesPendientes() },
        ItemPestana("Clientes", R.drawable.ic_address_book, R.drawable.ic_address_book_fill) { FragBuscarClientes() },
        ItemPestana("Técnicos", R.drawable.ic_users_three, R.drawable.ic_users_three_fill) { FragListaTecnicos() },
        PestanasComunes.perfil()
    )

    /** Abre el detalle de la orden indicada. */
    fun abrirOrden(navegador: Navegador, ordenId: String) {
        navegador.irA(FragDetalleOrdenJefe.nuevo(ordenId))
    }
}
