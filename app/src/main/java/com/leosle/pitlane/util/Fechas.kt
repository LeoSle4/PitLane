package com.leosle.pitlane.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatos de fecha y hora en español usados en toda la aplicación. */
object Fechas {

    private val local = Locale.forLanguageTag("es-PE")
    private val formatoFecha = DateTimeFormatter.ofPattern("d MMM yyyy", local)
    private val formatoHora = DateTimeFormatter.ofPattern("HH:mm", local)
    private val formatoCorto = DateTimeFormatter.ofPattern("d MMM", local)
    private const val MILIS_HORA = 3_600_000L
    private const val MILIS_DIA = 24 * MILIS_HORA

    private fun zona(): ZoneId = ZoneId.systemDefault()

    /** Escribe la fecha como "16 oct 2026". */
    fun fecha(milis: Long): String = formatoFecha.format(Instant.ofEpochMilli(milis).atZone(zona())).replace(".", "")

    /** Escribe la fecha sin año, como "16 oct". */
    fun fechaCorta(milis: Long): String = formatoCorto.format(Instant.ofEpochMilli(milis).atZone(zona())).replace(".", "")

    /** Escribe la hora como "14:05". */
    fun hora(milis: Long): String = formatoHora.format(Instant.ofEpochMilli(milis).atZone(zona()))

    /** Escribe la fecha y la hora juntas, como "16 oct 2026 · 14:05". */
    fun fechaHora(milis: Long): String = "${fecha(milis)} · ${hora(milis)}"

    /** Devuelve el instante en que empieza el día de [milis] en la zona horaria del teléfono. */
    fun inicioDelDia(milis: Long): Long =
        Instant.ofEpochMilli(milis).atZone(zona()).toLocalDate().atStartOfDay(zona()).toInstant().toEpochMilli()

    /** Indica si [milis] cae en un día anterior al día de [ahora]. */
    fun esAnteriorAHoy(milis: Long, ahora: Long): Boolean = milis < inicioDelDia(ahora)

    /** Convierte una fecha de calendario en el instante en que empieza ese día. */
    fun milisDeFecha(anio: Int, mes: Int, dia: Int): Long =
        LocalDate.of(anio, mes, dia).atStartOfDay(zona()).toInstant().toEpochMilli()

    /** Describe el tiempo transcurrido, por ejemplo "hace 5 min", "hace 3 h" o "hace 2 d". */
    fun hace(milis: Long, ahora: Long): String {
        val diferencia = (ahora - milis).coerceAtLeast(0)
        return when {
            diferencia < 60_000L -> "hace un momento"
            diferencia < MILIS_HORA -> "hace ${diferencia / 60_000L} min"
            diferencia < MILIS_DIA -> "hace ${diferencia / MILIS_HORA} h"
            else -> "hace ${diferencia / MILIS_DIA} d"
        }
    }

    /** Describe cuánto tiempo lleva algo en un estado, por ejemplo "3 h" o "2 d". */
    fun duracion(milis: Long, ahora: Long): String {
        val diferencia = (ahora - milis).coerceAtLeast(0)
        return when {
            diferencia < MILIS_HORA -> "${(diferencia / 60_000L).coerceAtLeast(1)} min"
            diferencia < MILIS_DIA -> "${diferencia / MILIS_HORA} h"
            else -> "${diferencia / MILIS_DIA} d"
        }
    }

    /** Describe un día como "hoy HH:mm" si es el día actual o como fecha con hora en otro caso. */
    fun fechaHoraRelativa(milis: Long, ahora: Long): String =
        if (inicioDelDia(milis) == inicioDelDia(ahora)) "hoy ${hora(milis)}" else "${fechaCorta(milis)} · ${hora(milis)}"

    /** Cantidad de milisegundos que contiene una hora. */
    const val HORA_EN_MILIS: Long = MILIS_HORA

    /** Cantidad de milisegundos que contiene un día. */
    const val DIA_EN_MILIS: Long = MILIS_DIA
}
