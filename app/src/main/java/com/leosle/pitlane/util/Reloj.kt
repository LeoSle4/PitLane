package com.leosle.pitlane.util

/** Fuente de la hora actual en milisegundos; permite reemplazar el reloj del sistema en las pruebas. */
fun interface Reloj {

    /** Devuelve la hora actual en milisegundos desde el 1 de enero de 1970. */
    fun ahora(): Long
}

/** Reloj que entrega la hora real del teléfono. */
object RelojSistema : Reloj {
    override fun ahora(): Long = System.currentTimeMillis()
}
