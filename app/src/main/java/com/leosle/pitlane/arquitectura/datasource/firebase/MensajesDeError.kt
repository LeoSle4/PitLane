package com.leosle.pitlane.arquitectura.datasource.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.functions.FirebaseFunctionsException

internal const val MENSAJE_CREDENCIALES = "Correo o contraseña incorrectos"
internal const val MENSAJE_DESACTIVADA = "Tu cuenta está desactivada. Comunícate con el taller"
internal const val MENSAJE_SIN_CONEXION = "Sin conexión. Revisa tu internet e inténtalo de nuevo"
internal const val MENSAJE_GENERICO = "No se pudo completar la operación. Inténtalo de nuevo"

/** Traduce el código de error de Firebase Authentication a un mensaje para el usuario. */
internal fun mensajeDeAuth(codigo: String?): String = when (codigo) {
    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL" ->
        MENSAJE_CREDENCIALES
    "ERROR_USER_DISABLED" -> MENSAJE_DESACTIVADA
    else -> MENSAJE_GENERICO
}

/**
 * Las funciones del servidor rechazan con estos códigos cuando incumple una regla de negocio y el mensaje
 * ya está escrito para el usuario; cualquier otro código es una falla técnica.
 */
private val CODIGOS_CON_MENSAJE =
    setOf("INVALID_ARGUMENT", "ALREADY_EXISTS", "NOT_FOUND", "PERMISSION_DENIED", "FAILED_PRECONDITION")

/** Elige el mensaje que se muestra cuando una función del servidor responde con error de ese [codigo]. */
internal fun mensajeDeFunciones(codigo: String, mensaje: String?): String =
    when {
        codigo in CODIGOS_CON_MENSAJE && !mensaje.isNullOrBlank() -> mensaje
        codigo == "UNAVAILABLE" -> MENSAJE_SIN_CONEXION
        else -> MENSAJE_GENERICO
    }

/** Mensaje para cualquier excepción que pueda salir de Authentication, de las funciones o de la red. */
internal fun mensajeDe(error: Exception): String = when (error) {
    is FirebaseAuthException -> mensajeDeAuth(error.errorCode)
    is FirebaseFunctionsException -> mensajeDeFunciones(error.code.name, error.message)
    is FirebaseNetworkException -> MENSAJE_SIN_CONEXION
    else -> MENSAJE_GENERICO
}
