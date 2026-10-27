package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow

/** Acceso a la sesión: inicio, cierre, registro de clientes y recuperación de contraseña. */
interface IAuthRepository {

    /** Usuario con la sesión abierta, o nulo si nadie inició sesión. */
    val usuarioActual: StateFlow<Usuario?>

    /** Abre la sesión con el correo y la contraseña indicados. */
    suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Usuario>

    /** Crea la cuenta de un cliente, une el registro previo del Jefe si el DNI ya existía y abre la sesión. */
    suspend fun registrarCliente(datos: DatosRegistroCliente): Resultado<Usuario>

    /** Envía el enlace de restablecimiento; responde igual exista o no el correo. */
    suspend fun recuperarContrasena(correo: String): Resultado<Unit>

    /** Cierra la sesión activa. */
    fun cerrarSesion()
}
