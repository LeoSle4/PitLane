package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow

/** Acceso a la sesión: inicio, cierre, registro de clientes y recuperación de contraseña. */
interface IAuthRepository {

    /** Usuario con la sesión abierta, o nulo si nadie inició sesión. */
    val usuarioActual: StateFlow<Usuario?>

    /** Espera a que se restablezca la sesión guardada y devuelve su usuario, o nulo si no hay ninguna. */
    suspend fun restaurarSesion(): Usuario?

    /** Abre la sesión con el correo y la contraseña indicados. */
    suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Usuario>

    /** Crea la cuenta de un cliente, une el registro previo del Jefe si el DNI ya existía y abre la sesión. */
    suspend fun registrarCliente(datos: DatosRegistroCliente): Resultado<Usuario>

    /** Envía un código de 6 dígitos al correo; responde igual exista o no la cuenta. */
    suspend fun solicitarCodigoDeRestablecimiento(correo: String): Resultado<Unit>

    /** Cambia la contraseña con el código recibido por correo. */
    suspend fun restablecerContrasena(correo: String, codigo: String, contrasena: String): Resultado<Unit>

    /** Cierra la sesión activa. */
    fun cerrarSesion()
}
