package com.leosle.pitlane.arquitectura.datasource.firebase

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
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
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private const val ETIQUETA = "ColeccionFirestore"

/**
 * Lote de escrituras activo en la corrutina actual. Mientras exista, las colecciones acumulan sus
 * escrituras aquí en lugar de enviarlas una a una, y se aplican todas juntas o ninguna al cerrar el lote.
 */
internal class LoteEscrituras(val lote: WriteBatch) : AbstractCoroutineContextElement(Clave) {
    companion object Clave : CoroutineContext.Key<LoteEscrituras>
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
 * @param campoPropio campo que debe valer el id del usuario para todos los roles, o nulo si se usa [CAMPO_ACCESO].
 * @param aDocumento convierte el modelo en los campos del documento.
 * @param desdeDocumento arma el modelo con el identificador del documento y sus campos.
 * @param acceso valor del campo [CAMPO_ACCESO] al guardar (una lista o un `FieldValue`), o nulo si no se escribe.
 * @param alGuardar se ejecuta después de escribir, con el documento anterior (nulo si es nuevo) y el nuevo.
 */
internal class ColeccionFirestore<T>(
    private val coleccion: CollectionReference,
    private val actor: StateFlow<Usuario?>,
    private val campoPropio: String? = null,
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
        actor.map { alcanceDe(it, campoPropio) }.distinctUntilChanged().flatMapLatest { alcance ->
            consultaDe(alcance)?.let { instantaneas(it) } ?: flowOf(emptyList())
        }

    override suspend fun listar(): List<T> {
        val consulta = consultaDe(alcanceDe(actor.value, campoPropio)) ?: return emptyList()
        return consulta.get().esperar().documents.mapNotNull { convertir(it.id, it.data) }
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
