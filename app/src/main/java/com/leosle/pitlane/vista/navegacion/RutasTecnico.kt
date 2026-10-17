package com.leosle.pitlane.vista.navegacion

import com.leosle.pitlane.R
import com.leosle.pitlane.vista.comun.ItemPestana
import com.leosle.pitlane.vista.comun.Navegador
import com.leosle.pitlane.vista.ordenes.FragDetalleOrdenTecnico
import com.leosle.pitlane.vista.ordenes.FragMisOrdenesAsignadas
import com.leosle.pitlane.vista.perfil.PestanasComunes

/** Puntos de entrada de las pantallas del técnico: pestañas de la barra inferior y apertura de órdenes. */
object RutasTecnico {

    /** Pestañas del técnico en el orden de la barra: Mis órdenes y Perfil. */
    fun pestanas(): List<ItemPestana> = listOf(
        ItemPestana("Mis órdenes", R.drawable.ic_wrench, R.drawable.ic_wrench_fill) { FragMisOrdenesAsignadas() },
        PestanasComunes.perfil()
    )

    /** Abre el detalle de la orden asignada indicada. */
    fun abrirOrden(navegador: Navegador, ordenId: String) {
        navegador.irA(FragDetalleOrdenTecnico.nuevo(ordenId))
    }
}
