package com.leosle.pitlane.arquitectura.datasource.firebase

import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Usuario

/** Campo de cada documento con los identificadores de usuario que pueden leerlo, además del Jefe. */
internal const val CAMPO_ACCESO = "acceso"

/** Parte de una colección que el usuario de la sesión puede leer; las reglas de Firestore exigen lo mismo. */
internal sealed interface Alcance {
    /** Sin sesión o con la cuenta desactivada: no lee nada. */
    data object Nada : Alcance

    /** Toda la colección. */
    data object Todo : Alcance

    /** Los documentos cuyo campo de lista [campo] contiene [valor]. */
    data class Contiene(val campo: String, val valor: String) : Alcance

    /** Los documentos cuyo campo [campo] es igual a [valor]. */
    data class Igual(val campo: String, val valor: String) : Alcance
}

/**
 * Calcula qué puede leer [actor]. Si la colección tiene un [campoPropio] (como `usuarioId` en los avisos),
 * todos, también el Jefe, leen solo los documentos suyos; si no, el Jefe lee todo y los demás los que
 * lo incluyen en su campo [CAMPO_ACCESO].
 */
internal fun alcanceDe(actor: Usuario?, campoPropio: String? = null): Alcance = when {
    actor == null || !actor.activo -> Alcance.Nada
    campoPropio != null -> Alcance.Igual(campoPropio, actor.id)
    actor.rol == Rol.JEFE -> Alcance.Todo
    else -> Alcance.Contiene(CAMPO_ACCESO, actor.id)
}
