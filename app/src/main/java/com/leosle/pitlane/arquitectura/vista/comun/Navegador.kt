package com.leosle.pitlane.arquitectura.vista.comun

import androidx.fragment.app.Fragment

/**
 * Operaciones de navegación que la actividad principal ofrece a las pantallas.
 * Las pantallas lo obtienen con `requireActivity() as Navegador`.
 */
interface Navegador {

    /** Muestra [fragmento] encima de la pantalla actual; con [apilar] en verdadero se puede volver con atrás. */
    fun irA(fragmento: Fragment, apilar: Boolean = true)

    /** Regresa a la pantalla anterior de la pila. */
    fun volver()

    /** Cambia a la pestaña del índice indicado y descarta las pantallas apiladas. */
    fun cambiarPestana(indice: Int)

    /** Cierra la sesión y muestra la pantalla de inicio de sesión. */
    fun cerrarSesion()

    /** Abre la vista de la orden indicada según el rol del usuario con sesión. */
    fun abrirOrden(ordenId: String)

    /** Entra a la aplicación con la sesión abierta: abre las pestañas del rol del usuario. */
    fun abrirInicio()
}
