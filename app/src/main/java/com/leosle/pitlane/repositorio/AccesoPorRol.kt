package com.leosle.pitlane.repositorio

import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Usuario

/*
 * Quién consulta: el usuario con la sesión abierta. Las lecturas se filtran con él (RN-06, RN-07 y RN-08), igual
 * que lo harían las reglas de seguridad con el usuario autenticado.
 */

/** Verdadero si quien consulta es el Jefe de Taller. */
internal fun Usuario?.esJefe(): Boolean = this?.rol == Rol.JEFE

/** Verdadero si quien consulta es el Jefe o es el propio usuario [id]. */
internal fun Usuario?.esJefeOPropio(id: String): Boolean = esJefe() || (this != null && this.id == id)
