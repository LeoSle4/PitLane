package com.leosle.pitlane.arquitectura.datasource.local

import com.leosle.pitlane.arquitectura.datasource.IColeccion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Colección de documentos guardada en memoria sobre un [MutableStateFlow].
 *
 * @param idDe obtiene el identificador de un documento.
 * @param generarId crea un identificador nuevo.
 * @param alGuardar se ejecuta después de cada escritura con el documento anterior (nulo si es nuevo) y el nuevo.
 */
internal class ColeccionLocal<T>(
    private val flujo: MutableStateFlow<List<T>>,
    private val idDe: (T) -> String,
    private val generarId: () -> String,
    private val alGuardar: (anterior: T?, nuevo: T) -> Unit = { _, _ -> }
) : IColeccion<T> {

    override fun observarTodos(): Flow<List<T>> = flujo

    override suspend fun listar(): List<T> = flujo.value

    /** Compara el campo [campo] (leído por reflexión) con [valor]; la memoria no filtra por usuario, igual que [listar]. */
    override suspend fun listarDonde(campo: String, valor: String): List<T> = flujo.value.filter { coincide(it, campo) { v -> v == valor } }

    override fun observarDonde(campo: String, valor: String): Flow<List<T>> =
        flujo.map { lista -> lista.filter { coincide(it, campo) { v -> v == valor } } }

    /** Igual que Firestore: de 1 a 30 valores; la memoria no filtra por usuario, igual que [observarTodos]. */
    override fun observarEnLista(campo: String, valores: List<String>): Flow<List<T>> {
        require(valores.size in 1..30) { "Firestore admite de 1 a 30 valores en una consulta in" }
        return flujo.map { lista -> lista.filter { coincide(it, campo) { v -> v in valores } } }
    }

    private fun coincide(item: T, campo: String, prueba: (String) -> Boolean): Boolean {
        val atributo = item!!.javaClass.getDeclaredField(campo).apply { isAccessible = true }
        return prueba(atributo.get(item).toString())
    }

    override fun observar(id: String): Flow<T?> = flujo.map { lista -> lista.firstOrNull { idDe(it) == id } }

    override suspend fun obtener(id: String): T? = flujo.value.firstOrNull { idDe(it) == id }

    /** Reemplaza el documento con ese identificador o lo agrega al final, y avisa a [alGuardar]. */
    override suspend fun guardar(id: String, valor: T) {
        val anterior = obtener(id)
        flujo.update { lista ->
            if (anterior != null) lista.map { if (idDe(it) == id) valor else it } else lista + valor
        }
        alGuardar(anterior, valor)
    }

    override suspend fun eliminar(id: String) {
        flujo.update { lista -> lista.filterNot { idDe(it) == id } }
    }

    override fun nuevoId(): String = generarId()
}
