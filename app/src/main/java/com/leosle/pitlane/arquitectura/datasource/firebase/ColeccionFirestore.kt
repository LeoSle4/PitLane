package com.leosle.pitlane.arquitectura.datasource.firebase

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.modelo.Usuario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private const val ETIQUETA = "ColeccionFirestore"

/** Máximo de valores que Firestore admite en una consulta `in`. */
private const val MAXIMO_VALORES_EN_LISTA = 30

/** Máximo de documentos que devuelve una búsqueda por término. */
private const val MAXIMO_LIMITE_BUSQUEDA = 100

/**
 * Lote de escrituras activo en la corrutina actual. Mientras exista, las colecciones acumulan sus
 * escrituras aquí en lugar de enviarlas una a una, y se aplican todas juntas o ninguna al cerrar el lote.
 */
internal class LoteEscrituras(val lote: WriteBatch) : AbstractCoroutineContextElement(Clave) {
    companion object Clave : CoroutineContext.Key<LoteEscrituras>
}

/**
 * Las escrituras que haga [bloque] se acumulan en un lote de [bd] y se aplican juntas al terminar; si [bloque] falla
 * no se escribe nada. Dentro de un lote ya abierto se suma a él. Las lecturas dentro del bloque no ven las escrituras
 * aún pendientes del mismo lote.
 */
internal suspend fun <R> enLoteDe(bd: FirebaseFirestore, bloque: suspend () -> R): R {
    if (coroutineContext[LoteEscrituras] != null) return bloque()
    val lote = LoteEscrituras(bd.batch())
    val resultado = withContext(lote) { bloque() }
    lote.lote.commit().esperar()
    return resultado
}

/** Combina [campos] con el documento existente; se suma al lote activo si lo hay. */
internal suspend fun escribir(referencia: DocumentReference, campos: Map<String, Any?>) {
    val lote = coroutineContext[LoteEscrituras]
    if (lote != null) lote.lote.set(referencia, campos, SetOptions.merge())
    else referencia.set(campos, SetOptions.merge()).esperar()
}

/** Elimina el documento; se suma al lote activo si lo hay. */
internal suspend fun borrar(referencia: DocumentReference) {
    val lote = coroutineContext[LoteEscrituras]
    if (lote != null) lote.lote.delete(referencia) else referencia.delete().esperar()
}

/**
 * Colección de Firestore que entrega y recibe el modelo. Solo entrega lo que el usuario de la sesión puede
 * leer, según [alcanceDe]; las reglas de Firestore exigen lo mismo.
 *
 * @param actor usuario de la sesión; al cambiar, las lecturas en curso se rehacen con su nuevo alcance.
 * @param dueno id del usuario dueño cuando [coleccion] es una subcolección suya que solo él lee (su bandeja), o nulo
 *   si se usa [CAMPO_ACCESO].
 * @param delPadre verdadero cuando [coleccion] es una subcolección que lee quien puede leer su documento padre (los
 *   avances de una orden): se lee entera y las reglas deciden por el padre.
 * @param aDocumento convierte el modelo en los campos del documento.
 * @param desdeDocumento arma el modelo con el identificador del documento y sus campos.
 * @param acceso valor del campo [CAMPO_ACCESO] al guardar (una lista o un `FieldValue`), o nulo si no se escribe.
 * @param alGuardar se ejecuta después de escribir, con el documento anterior (nulo si es nuevo) y el nuevo.
 */
internal class ColeccionFirestore<T>(
    private val coleccion: CollectionReference,
    private val actor: StateFlow<Usuario?>,
    private val dueno: String? = null,
    private val delPadre: Boolean = false,
    private val aDocumento: (T) -> Documento,
    private val desdeDocumento: (id: String, campos: Documento) -> T,
    private val acceso: (suspend (T) -> Any?)? = null,
    private val alGuardar: (suspend (anterior: T?, nuevo: T) -> Unit)? = null
) : IColeccion<T> {

    /** Un documento dañado se omite en lugar de tumbar toda la lista. */
    private fun convertir(id: String, campos: Documento?): T? =
        campos?.let { runCatching { desdeDocumento(id, it) }.onFailure { e -> Log.w(ETIQUETA, "Documento $id inválido", e) }.getOrNull() }

    private fun consultaDe(alcance: Alcance): Query? = when (alcance) {
        Alcance.Nada -> null
        Alcance.Todo -> coleccion
        is Alcance.Contiene -> coleccion.whereArrayContains(alcance.campo, alcance.valor)
        is Alcance.Igual -> coleccion.whereEqualTo(alcance.campo, alcance.valor)
    }

    private fun instantaneas(consulta: Query): Flow<List<T>> = callbackFlow {
        val registro = consulta.addSnapshotListener { instantanea, error ->
            if (error != null) {
                Log.w(ETIQUETA, "No se pudo leer ${coleccion.path}", error)
                close()
                return@addSnapshotListener
            }
            trySend(instantanea?.documents.orEmpty().mapNotNull { convertir(it.id, it.data) })
        }
        awaitClose { registro.remove() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarTodos(): Flow<List<T>> =
        actor.map { alcanceDe(it, dueno, delPadre) }.distinctUntilChanged().flatMapLatest { alcance ->
            consultaDe(alcance)?.let { instantaneas(it) } ?: flowOf(emptyList())
        }

    /** Reutiliza el alcance del usuario y le suma [restriccion]; se rehace si cambia la sesión. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observarRestringido(restriccion: (Query) -> Query): Flow<List<T>> =
        actor.map { alcanceDe(it, dueno, delPadre) }.distinctUntilChanged().flatMapLatest { alcance ->
            consultaDe(alcance)?.let { instantaneas(restriccion(it)) } ?: flowOf(emptyList())
        }

    override fun observarDonde(campo: String, valor: String): Flow<List<T>> =
        observarRestringido { it.whereEqualTo(campo, valor) }

    /** El orden de la consulta se conserva; [observarRestringido] suma `orderBy` y `limit` al alcance del usuario. */
    override fun observarRecientes(campo: String, limite: Int): Flow<List<T>> {
        require(limite in 1..MAXIMO_LIMITE_BUSQUEDA) { "El límite debe estar entre 1 y $MAXIMO_LIMITE_BUSQUEDA" }
        return observarRestringido { it.orderBy(campo, Query.Direction.DESCENDING).limit(limite.toLong()) }
    }

    override fun observarEnLista(campo: String, valores: List<String>): Flow<List<T>> {
        require(valores.size in 1..MAXIMO_VALORES_EN_LISTA) { "Firestore admite de 1 a $MAXIMO_VALORES_EN_LISTA valores en una consulta in" }
        return observarRestringido { it.whereIn(campo, valores) }
    }

    override suspend fun listar(): List<T> {
        val consulta = consultaDe(alcanceDe(actor.value, dueno, delPadre)) ?: return emptyList()
        return consulta.get().esperar().documents.mapNotNull { convertir(it.id, it.data) }
    }

    override suspend fun listarDonde(campo: String, valor: String): List<T> {
        val consulta = consultaDe(alcanceDe(actor.value, dueno, delPadre))?.whereEqualTo(campo, valor) ?: return emptyList()
        return consulta.get().esperar().documents.mapNotNull { convertir(it.id, it.data) }
    }

    /**
     * Suma `array-contains` al alcance del usuario. Firestore admite un solo `array-contains` por consulta y el
     * alcance por [CAMPO_ACCESO] ya usa uno, así que esta búsqueda solo la hace quien lee la colección completa
     * (el Jefe); para los demás devuelve una lista vacía. Usa el índice automático de un solo campo.
     */
    override suspend fun buscarPorTermino(campo: String, termino: String, limite: Int): List<T> {
        require(limite in 1..MAXIMO_LIMITE_BUSQUEDA) { "El límite debe estar entre 1 y $MAXIMO_LIMITE_BUSQUEDA" }
        val consulta = when (val alcance = alcanceDe(actor.value, dueno, delPadre)) {
            Alcance.Todo, is Alcance.Igual -> consultaDe(alcance)
            Alcance.Nada, is Alcance.Contiene -> null
        }?.whereArrayContains(campo, termino)?.limit(limite.toLong()) ?: return emptyList()
        return consulta.get().esperar().documents.mapNotNull { convertir(it.id, it.data) }
    }

    /** Un documento que no existe o que el usuario no puede leer se emite como nulo, sin cerrar el flujo con error. */
    override fun observar(id: String): Flow<T?> = callbackFlow {
        val registro = coleccion.document(id).addSnapshotListener { instantanea, error ->
            if (error != null) {
                Log.w(ETIQUETA, "No se pudo leer ${coleccion.path}/$id", error)
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) trySend(null) else close()
                return@addSnapshotListener
            }
            trySend(instantanea?.takeIf { it.exists() }?.let { convertir(it.id, it.data) })
        }
        awaitClose { registro.remove() }
    }

    /** Un documento al que el usuario no tiene acceso se trata como inexistente, sin revelar que existe. */
    override suspend fun obtener(id: String): T? = try {
        val documento = coleccion.document(id).get().esperar()
        convertir(documento.id, documento.data)
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
    }

    override suspend fun guardar(id: String, valor: T) {
        val anterior = if (alGuardar != null) obtener(id) else null
        val campos = aDocumento(valor).toMutableMap()
        acceso?.let { campos[CAMPO_ACCESO] = it(valor) }
        escribir(coleccion.document(id), campos)
        alGuardar?.invoke(anterior, valor)
    }

    override suspend fun eliminar(id: String) = borrar(coleccion.document(id))

    override fun nuevoId(): String = coleccion.document().id
}
