package com.leosle.pitlane.viewmodel

import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.repositorio.IPerfilRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow

/** Datos personales del usuario con sesión: consulta, edición y eliminación de cuenta. */
class PerfilViewModel(
    private val repositorio: IPerfilRepository = Dependencias.repositorioPerfil
) : ViewModelBase() {

    /** Datos del usuario, actualizados cada vez que cambian. */
    fun perfil(usuarioId: String): StateFlow<Usuario?> =
        estadoPorClave("perfil:$usuarioId", null) { repositorio.observarPerfil(usuarioId) }

    /** Guarda nombres, apellidos y teléfono. */
    fun actualizarPerfil(
        usuarioId: String,
        nombres: String,
        apellidos: String,
        telefono: String,
        alTerminar: (Resultado<Usuario>) -> Unit
    ) = ejecutar({ repositorio.actualizarPerfil(usuarioId, nombres, apellidos, telefono) }, alTerminar)

    /** Desactiva la cuenta del cliente tras confirmar su contraseña. */
    fun eliminarCuenta(usuarioId: String, contrasena: String, alTerminar: (Resultado<Unit>) -> Unit) =
        ejecutar({ repositorio.eliminarCuenta(usuarioId, contrasena) }, alTerminar)
}
