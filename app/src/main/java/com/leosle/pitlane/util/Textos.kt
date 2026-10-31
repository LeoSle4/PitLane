package com.leosle.pitlane.util

/** Utilidades para dejar con buen formato los textos que escribe la persona: nombres, marcas, modelos y colores. */
object Textos {

    private val particulas = setOf("de", "del", "la", "las", "los", "y", "e", "van", "von", "da", "di")

    /**
     * Da formato de nombre propio: quita espacios sobrantes, pone la inicial de cada palabra en mayúscula y el resto en
     * minúscula ("leopoldo CHOque floRES" queda "Leopoldo Choque Flores"). Las partículas como "de" o "del" quedan en
     * minúscula salvo al inicio, y también se capitaliza después de un guion o un apóstrofo.
     */
    fun nombrePropio(texto: String): String =
        palabras(texto).mapIndexed { indice, palabra ->
            val minuscula = palabra.lowercase()
            if (indice > 0 && minuscula in particulas) minuscula else capitalizar(minuscula)
        }.joinToString(" ")

    /**
     * Pone mayúscula inicial solo a las palabras escritas completamente en minúsculas, y deja intactas las demás para no
     * dañar nombres como "CX-5" o "RAV4" ("santa fe" queda "Santa Fe").
     */
    /**
     * Convierte la acción de un botón en lo que está pasando mientras se ejecuta: "Registrar técnico" pasa a
     * "Registrando…". Si el texto no empieza con un verbo en infinitivo devuelve "Procesando…".
     */
    fun gerundio(accion: String): String {
        val verbo = accion.trim().substringBefore(' ').lowercase()
        val terminacion = when {
            verbo.length > 2 && verbo.endsWith("ar") -> "ando"
            verbo.length > 2 && (verbo.endsWith("er") || verbo.endsWith("ir")) -> "iendo"
            else -> return "Procesando…"
        }
        return verbo.dropLast(2).replaceFirstChar { it.uppercase() } + terminacion + "…"
    }

    /** Agrupa un celular de 9 dígitos de tres en tres ("923 239 575"); cualquier otro texto se deja como está. */
    fun celular(texto: String): String {
        val digitos = texto.filterNot { it.isWhitespace() }
        return if (digitos.length == 9 && digitos.all { it.isDigit() }) digitos.chunked(3).joinToString(" ") else texto
    }

    fun conInicialesMayusculas(texto: String): String =
        palabras(texto).joinToString(" ") { palabra ->
            if (palabra == palabra.lowercase()) capitalizar(palabra) else palabra
        }

    private fun palabras(texto: String): List<String> = texto.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun capitalizar(palabra: String): String {
        val resultado = StringBuilder(palabra.length)
        var mayuscula = true
        for (letra in palabra) {
            resultado.append(if (mayuscula) letra.uppercaseChar() else letra)
            mayuscula = letra == '-' || letra == '\'' || letra == '’'
        }
        return resultado.toString()
    }
}
