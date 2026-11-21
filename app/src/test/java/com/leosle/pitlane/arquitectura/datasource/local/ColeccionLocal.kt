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
 * @param terminos términos de búsqueda de un documento (el modelo no tiene el campo); lo usa [buscarPorTermino].
 * @param actividad valor del campo de orden de un documento (el modelo no lo tiene), o nulo si no lo tiene; lo usa [observarRecientes].
 * @param pertenece indica si un documento de [flujo] es de esta colección; simula una subcolección (como la bandeja de
 *   un usuario) sobre una lista común: solo se leen, guardan y borran los que pertenecen.
 * @param alGuardar se ejecuta después de cada escritura con el documento anterior (nulo si es nuevo) y el nuevo.
 */
internal class ColeccionLocal<T>(
    private val flujo: MutableStateFlow<List<T>>,
    private val idDe: (T) -> String,
    private val generarId: () -> String,
    private val terminos: (T) -> List<String> = { emptyList() },
    private val actividad: (T) -> Long? = { null },
    private val pertenece: ((T) -> Boolean)? = null,
    private val alGuardar: (anterior: T?, nuevo: T) -> Unit = { _, _ -> }
) : IColeccion<T> {

    /** Los documentos de esta colección, en vivo. */
    private val vista: Flow<List<T>> = pertenece?.let { prueba -> flujo.map { it.filter(prueba) } } ?: flujo

    /** Los documentos actuales de esta colección. */
    private val actuales: List<T> get() = pertenece?.let { flujo.value.filter(it) } ?: flujo.value

    override fun observarTodos(): Flow<List<T>> = vista

    override suspend fun listar(): List<T> = actuales

    /** Compara el campo [campo] (leído por reflexión) con [valor]; la memoria no filtra por usuario, igual que [listar]. */
    override suspend fun listarDonde(campo: String, valor: String): List<T> = actuales.filter { coincide(it, campo) { v -> v == valor } }

    override fun observarDonde(campo: String, valor: String): Flow<List<T>> =
        vista.map { lista -> lista.filter { coincide(it, campo) { v -> v == valor } } }

    /** Igual que Firestore: límite de 1 a 100, del mayor al menor y sin los documentos que no tienen el campo. */
    override fun observarRecientes(campo: String, limite: Int): Flow<List<T>> {
        require(limite in 1..100) { "El límite debe estar entre 1 y 100" }
        return vista.map { lista -> lista.filter { actividad(it) != null }.sortedByDescending { actividad(it) }.take(limite) }
    }

    /** Igual que Firestore: de 1 a 30 valores; la memoria no filtra por usuario, igual que [observarTodos]. */
    override fun observarEnLista(campo: String, valores: List<String>): Flow<List<T>> {
        require(valores.size in 1..30) { "Firestore admite de 1 a 30 valores en una consulta in" }
        return vista.map { lista -> lista.filter { coincide(it, campo) { v -> v in valores } } }
    }

    /** Igual que Firestore: límite de 1 a 100; la memoria no filtra por usuario, igual que [listar]. */
    override suspend fun buscarPorTermino(campo: String, termino: String, limite: Int): List<T> {
        require(limite in 1..100) { "El límite debe estar entre 1 y 100" }
        return actuales.filter { termino in terminos(it) }.take(limite)
    }

    private fun coincide(item: T, campo: String, prueba: (String) -> Boolean): Boolean {
        val atributo = item!!.javaClass.getDeclaredField(campo).apply { isAccessible = true }
        return prueba(atributo.get(item).toString())
    }

    override fun observar(id: String): Flow<T?> = vista.map { lista -> lista.firstOrNull { idDe(it) == id } }

    override suspend fun obtener(id: String): T? = actuales.firstOrNull { idDe(it) == id }

    /** Reemplaza el documento con ese identificador o lo agrega al final, y avisa a [alGuardar]. */
    override suspend fun guardar(id: String, valor: T) {
        require(pertenece?.invoke(valor) != false) { "El documento $id no pertenece a esta colección" }
        val anterior = obtener(id)
        flujo.update { lista ->
            if (anterior != null) lista.map { if (idDe(it) == id && pertenece?.invoke(it) != false) valor else it } else lista + valor
        }
        alGuardar(anterior, valor)
    }

    override suspend fun eliminar(id: String) {
        flujo.update { lista -> lista.filterNot { idDe(it) == id && pertenece?.invoke(it) != false } }
    }

    override fun nuevoId(): String = generarId()
}
