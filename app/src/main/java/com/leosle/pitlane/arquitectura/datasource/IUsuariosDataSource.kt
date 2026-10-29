package com.leosle.pitlane.arquitectura.datasource

import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fuente de datos de las cuentas: inicio de sesión, colección de usuarios y funciones de cuenta
 * que solo puede ejecutar el servidor (crear técnicos, unir al cliente por DNI y desactivar cuentas).
 */
interface IUsuariosDataSource {

    /** Usuario con la sesión abierta, o nulo si nadie inició sesión. */
    val usuarioActual: StateFlow<Usuario?>

    /**
     * Espera a que termine de restablecerse la sesión que quedó abierta al cerrar la aplicación y devuelve
     * su usuario, o nulo si no había sesión o no se pudo restablecer a tiempo.
     */
    suspend fun restaurarSesion(): Usuario?

    /** Emite la lista completa de usuarios cada vez que cambia. */
    fun observarUsuarios(): Flow<List<Usuario>>

    /** Devuelve la lista actual de usuarios. */
    suspend fun listarUsuarios(): List<Usuario>

    /** Devuelve el usuario con ese identificador, o nulo si no existe. */
    suspend fun obtenerUsuario(id: String): Usuario?

    /** Guarda el usuario: reemplaza el que tenga el mismo identificador o lo agrega si es nuevo. */
    suspend fun guardarUsuario(usuario: Usuario)

    /** Elimina el registro de un usuario sin cuenta de acceso. */
    suspend fun eliminarUsuario(id: String)

    /** Genera un identificador nuevo con el prefijo indicado. */
    fun nuevoId(prefijo: String): String

    /** Abre la sesión; rechaza credenciales incorrectas y cuentas desactivadas con su mensaje. */
    suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Usuario>

    /** Cierra la sesión activa. */
    fun cerrarSesion()

    /** Solicita el enlace de restablecimiento de contraseña; no revela si el correo existe. */
    suspend fun enviarEnlaceDeRestablecimiento(correo: String)

    /** Crea la cuenta del cliente, une el registro previo del Jefe si el DNI ya existía y abre la sesión. */
    suspend fun registrarCuentaCliente(datos: DatosRegistroCliente): Resultado<Usuario>

    /** Crea la cuenta de un técnico con estado activo sin cerrar la sesión del Jefe. */
    suspend fun crearCuentaTecnico(
        nombres: String,
        apellidos: String,
        dni: String,
        telefono: String,
        correo: String,
        contrasena: String
    ): Resultado<Usuario>

    /**
     * Desactiva la cuenta. Un técnico con órdenes en curso no puede desactivarse. Con [contrasena] se trata de
     * la eliminación de la cuenta propia de un cliente: exige la contraseña y que no tenga vehículos en el taller.
     */
    suspend fun desactivarCuenta(usuarioId: String, contrasena: String?): Resultado<Unit>

    /** Vuelve a activar una cuenta desactivada. */
    suspend fun reactivarCuenta(usuarioId: String): Resultado<Unit>
}
