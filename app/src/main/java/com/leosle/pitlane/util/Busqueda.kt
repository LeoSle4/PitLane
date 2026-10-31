package com.leosle.pitlane.util

import java.text.Normalizer

/**
 * Términos de búsqueda que se guardan en los documentos de Firestore, que no tiene búsqueda de texto: la consulta
 * es `array-contains <término>`. Tiene un gemelo en JavaScript, `firebase/functions/src/busqueda.js`; los dos deben
 * producir exactamente las mismas listas (las pruebas de ambos lados usan los mismos ejemplos).
 */
object Busqueda {

    private const val LARGO_MINIMO = 3
    private const val LARGO_MAXIMO_PALABRA = 20
    private const val MAXIMO_TERMINOS_PERSONA = 120

    private val noAlfanumerico = Regex("[^\\p{L}\\p{N}]+")
    private val marcas = Regex("\\p{M}+")

    /**
     * Minúsculas, sin tildes y solo letras y dígitos: todo lo demás se vuelve un único espacio y los extremos se
     * recortan. "María-José" queda "maria jose".
     */
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
            .replace(marcas, "")
            .replace(noAlfanumerico, " ")
            .trim()

    /**
     * Términos de un cliente: prefijos de al menos 3 letras de cada palabra de "nombres apellidos" (palabra cortada a
     * 20 caracteres) y todas las subcadenas de al menos 3 caracteres del DNI. Sin repetidos, ordenados y como mucho
     * 120; si hubiera más se conservan los más cortos y, a igual largo, los primeros alfabéticamente.
     */
    fun terminosDePersona(nombres: String, apellidos: String, dni: String): List<String> {
        val prefijos = normalizar("$nombres $apellidos").split(' ').filter { it.isNotEmpty() }
            .flatMap { palabra -> prefijosDe(palabra.take(LARGO_MAXIMO_PALABRA)) }
        val terminos = (prefijos + subcadenasDe(normalizar(dni).replace(" ", ""))).toSet()
        val elegidos = if (terminos.size <= MAXIMO_TERMINOS_PERSONA) terminos
        else terminos.sortedWith(compareBy<String>({ it.length }, { it })).take(MAXIMO_TERMINOS_PERSONA)
        return elegidos.sorted()
    }

    /** Términos de un vehículo: la placa sin guion y en minúsculas y todas sus subcadenas de al menos 3 caracteres. */
    fun terminosDePlaca(placa: String): List<String> =
        subcadenasDe(normalizar(placa).replace(" ", "")).toSet().sorted()

    private fun prefijosDe(palabra: String): List<String> =
        (LARGO_MINIMO..palabra.length).map { palabra.substring(0, it) }

    private fun subcadenasDe(texto: String): List<String> =
        (LARGO_MINIMO..texto.length).flatMap { largo -> (0..texto.length - largo).map { texto.substring(it, it + largo) } }
}
