package com.leosle.pitlane.arquitectura.datasource

import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Otro usuario ganó el DNI: la reserva `dnis/{dni}` ya existía cuando se intentó registrar este perfil. */
class DniYaRegistradoException : Exception("El DNI ya está registrado")

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

    /**
     * Devuelve como máximo [limite] clientes cuyos términos de búsqueda (nombre y DNI, ver `Busqueda`) incluyen
     * [termino]. Solo el Jefe obtiene resultados; los clientes anteriores al relleno de términos no aparecen.
     */
    suspend fun buscarClientesPorTermino(termino: String, limite: Int): List<Usuario>

    /**
     * Emite, cada vez que cambian, como máximo [limite] (de 1 a 100) clientes con actividad, del que tuvo actividad
     * más reciente al más antiguo. La base ordena por `ultimaActividad`, un campo que mantiene el servidor; los
     * clientes que no lo tienen (anteriores al relleno `rellenarActividad.mjs`) no aparecen.
     */
    fun observarClientesPorActividad(limite: Int): Flow<List<Usuario>>

    /** Devuelve el usuario con ese identificador, o nulo si no existe. */
    suspend fun obtenerUsuario(id: String): Usuario?

    /**
     * Guarda el usuario: reemplaza el que tenga el mismo identificador o lo agrega si es nuevo. Un perfil nuevo
     * reserva su DNI en el mismo lote; si otro usuario ya lo tiene, no se guarda nada y lanza [DniYaRegistradoException].
     */
    suspend fun guardarUsuario(usuario: Usuario)

    /** Elimina el registro de un usuario sin cuenta de acceso junto con la reserva de su DNI. */
    suspend fun eliminarUsuario(id: String)

    /** Genera un identificador nuevo con el prefijo indicado. */
    fun nuevoId(prefijo: String): String

    /** Abre la sesión; rechaza credenciales incorrectas y cuentas desactivadas con su mensaje. */
    suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Usuario>

    /** Cierra la sesión activa. */
    fun cerrarSesion()

    /** Envía al correo un código de 6 dígitos para restablecer la contraseña; responde igual exista o no la cuenta. */
    suspend fun solicitarCodigoDeRestablecimiento(correo: String): Resultado<Unit>

    /** Cambia la contraseña de la cuenta del correo si el código es correcto y sigue vigente. */
    suspend fun restablecerContrasena(correo: String, codigo: String, contrasena: String): Resultado<Unit>

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
