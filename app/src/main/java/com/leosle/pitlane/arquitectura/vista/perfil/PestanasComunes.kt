package com.leosle.pitlane.arquitectura.vista.perfil

import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.vista.comun.ItemPestana

/** Pestañas que aparecen en la barra de más de un rol. */
object PestanasComunes {

    /** Pestaña "Perfil" con los datos personales y la configuración de la cuenta. */
    fun perfil(): ItemPestana = ItemPestana(
        titulo = "Perfil",
        icono = R.drawable.ic_user_circle,
        iconoLleno = R.drawable.ic_user_circle_fill,
        crearFragmento = { FragPerfil() }
    )
}
