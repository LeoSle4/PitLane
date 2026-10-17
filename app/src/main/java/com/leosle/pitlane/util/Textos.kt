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
