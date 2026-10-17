package com.leosle.pitlane.vista.navegacion

import com.leosle.pitlane.R
import com.leosle.pitlane.vista.comun.ItemPestana
import com.leosle.pitlane.vista.comun.Navegador
import com.leosle.pitlane.vista.notificaciones.FragBandeja
import com.leosle.pitlane.vista.ordenes.FragEstadoOrden
import com.leosle.pitlane.vista.ordenes.FragMisOrdenes
import com.leosle.pitlane.vista.perfil.PestanasComunes
import com.leosle.pitlane.vista.solicitudes.FragMisSolicitudes
import com.leosle.pitlane.vista.vehiculos.FragMisVehiculos

/** Puntos de entrada de las pantallas del cliente: pestañas de la barra inferior y apertura de órdenes. */
object RutasCliente {

    /** Pestañas del cliente en el orden de la barra: Vehículos, Solicitudes, Órdenes, Avisos y Perfil. */
    fun pestanas(): List<ItemPestana> = listOf(
        ItemPestana("Vehículos", R.drawable.ic_car_profile, R.drawable.ic_car_profile_fill) { FragMisVehiculos() },
        ItemPestana("Solicitudes", R.drawable.ic_clipboard_text, R.drawable.ic_clipboard_text_fill) { FragMisSolicitudes() },
        ItemPestana("Órdenes", R.drawable.ic_gear_six, R.drawable.ic_gear_six_fill) { FragMisOrdenes() },
        ItemPestana("Avisos", R.drawable.ic_bell, R.drawable.ic_bell_fill) { FragBandeja() },
        PestanasComunes.perfil()
    )

    /** Abre el estado de la orden indicada. */
    fun abrirOrden(navegador: Navegador, ordenId: String) {
        navegador.irA(FragEstadoOrden.nuevo(ordenId))
    }
}
