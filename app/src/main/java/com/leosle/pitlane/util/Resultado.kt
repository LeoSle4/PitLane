package com.leosle.pitlane.util

/**
 * Resultado de una operación que puede fallar por una regla de negocio.
 * [Exito] trae el valor producido; [Error] trae un mensaje listo para mostrar al usuario y, cuando aplica,
 * la [Error.referencia] de un registro relacionado (por ejemplo el cliente que ya tiene ese DNI).
 */
sealed class Resultado<out T> {
    data class Exito<out T>(val valor: T) : Resultado<T>()
    data class Error(val mensaje: String, val referencia: String? = null) : Resultado<Nothing>()

    /** Ejecuta [accion] con el valor si la operación terminó bien y devuelve este mismo resultado. */
    inline fun alExito(accion: (T) -> Unit): Resultado<T> {
        if (this is Exito) accion(valor)
        return this
    }

    /** Ejecuta [accion] con el error si la operación falló y devuelve este mismo resultado. */
    inline fun alError(accion: (Error) -> Unit): Resultado<T> {
        if (this is Error) accion(this)
        return this
    }
}
