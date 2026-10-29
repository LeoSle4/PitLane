package com.leosle.pitlane.arquitectura.datasource.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.PlacaRegistrada
import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.Vehiculo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private const val CONTADORES = "contadores"
private const val CONTADOR_ORDENES = "ordenes"
private const val CAMPO_ULTIMO = "ultimo"
private const val TOKENS = "tokensDispositivo"

/** Número de orden con el formato ORD-0001 a partir de su correlativo. */
internal fun numeroDeOrden(correlativo: Long): String = "ORD-%04d".format(correlativo)

/**
 * Colecciones de documentos en Firestore. Cada colección solo entrega lo que el usuario de [actor] puede leer
 * (ver [alcanceDe]). Los avisos al cliente cuando cambia una orden los crea la función del servidor `notificarOrden`.
 */
class DocumentosDataSource(
    private val actor: StateFlow<Usuario?>,
    private val bd: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val funciones: FirebaseFunctions = FirebaseFunctions.getInstance(REGION_FUNCIONES)
) : IDocumentosDataSource {

    private val usuariosBd = bd.collection("usuarios")
    private val vehiculosBd = bd.collection("vehiculos")
    private val ordenesBd = bd.collection("ordenes")
    private val avancesBd = bd.collection("avances")

    override val vehiculos: IColeccion<Vehiculo> = ColeccionFirestore(
        vehiculosBd, actor,
        aDocumento = Vehiculo::aDocumento,
        desdeDocumento = { id, campos -> campos.aVehiculo(id) },
        acceso = { FieldValue.arrayUnion(it.clienteId) }
    )

    override val solicitudes: IColeccion<Solicitud> = ColeccionFirestore(
        bd.collection("solicitudes"), actor,
        aDocumento = Solicitud::aDocumento,
        desdeDocumento = { id, campos -> campos.aSolicitud(id) },
        acceso = { FieldValue.arrayUnion(it.clienteId) }
    )

    private val ordenesBase = ColeccionFirestore(
        ordenesBd, actor,
        aDocumento = Orden::aDocumento,
        desdeDocumento = { id, campos -> campos.aOrden(id) },
        acceso = { listOf(it.clienteId, it.tecnicoId) },
        alGuardar = ::alGuardarOrden
    )

    /** La orden y los permisos que arrastra (vehículo, perfiles y avances) se escriben juntos o ninguno. */
    override val ordenes: IColeccion<Orden> = object : IColeccion<Orden> by ordenesBase {
        override suspend fun guardar(id: String, valor: Orden) = enLote { ordenesBase.guardar(id, valor) }
    }

    /** Un avance lo pueden leer quienes pueden leer su orden. */
    override val avances: IColeccion<Avance> = ColeccionFirestore(
        avancesBd, actor,
        aDocumento = Avance::aDocumento,
        desdeDocumento = { id, campos -> campos.aAvance(id) },
        acceso = { accesoDeLaOrden(it.ordenId) }
    )

    /** Los avisos son personales: cada usuario, incluido el Jefe, lee solo los suyos. */
    override val notificaciones: IColeccion<Notificacion> = ColeccionFirestore(
        bd.collection("notificaciones"), actor,
        campoPropio = "usuarioId",
        aDocumento = Notificacion::aDocumento,
        desdeDocumento = { id, campos -> campos.aNotificacion(id) }
    )

    private suspend fun accesoDeLaOrden(ordenId: String): List<String> {
        val campo = ordenesBd.document(ordenId).get().esperar().get(CAMPO_ACCESO)
        return (campo as? List<*>).orEmpty().filterIsInstance<String>()
    }

    /**
     * Al crear una orden o cambiar su técnico, vehículo o cliente, agrega al técnico en los permisos del vehículo
     * y del cliente, y al cliente en los del técnico. Si cambió el técnico, se los quita al anterior cuando ya no
     * le queda otra orden con ese cliente o vehículo, y actualiza los permisos de los avances de la orden.
     */
    private suspend fun alGuardarOrden(anterior: Orden?, nueva: Orden) {
        val mismosParticipantes = anterior != null && anterior.tecnicoId == nueva.tecnicoId &&
            anterior.clienteId == nueva.clienteId && anterior.vehiculoId == nueva.vehiculoId
        if (mismosParticipantes) return
        escribir(vehiculosBd.document(nueva.vehiculoId), mapOf(CAMPO_ACCESO to FieldValue.arrayUnion(nueva.tecnicoId)))
        escribir(usuariosBd.document(nueva.clienteId), mapOf(CAMPO_ACCESO to FieldValue.arrayUnion(nueva.tecnicoId)))
        escribir(usuariosBd.document(nueva.tecnicoId), mapOf(CAMPO_ACCESO to FieldValue.arrayUnion(nueva.clienteId)))
        if (anterior != null && anterior.tecnicoId != nueva.tecnicoId) retirarAccesoDelTecnico(anterior.tecnicoId, nueva)
    }

    private suspend fun retirarAccesoDelTecnico(tecnicoAnterior: String, nueva: Orden) {
        val ordenesDelTecnico = ordenesBd.whereEqualTo("tecnicoId", tecnicoAnterior)
        suspend fun sigueConOtraOrden(campo: String, valor: String): Boolean =
            ordenesDelTecnico.whereEqualTo(campo, valor).get().esperar().documents.any { it.id != nueva.id }

        if (!sigueConOtraOrden("clienteId", nueva.clienteId)) {
            escribir(usuariosBd.document(nueva.clienteId), mapOf(CAMPO_ACCESO to FieldValue.arrayRemove(tecnicoAnterior)))
            escribir(usuariosBd.document(tecnicoAnterior), mapOf(CAMPO_ACCESO to FieldValue.arrayRemove(nueva.clienteId)))
        }
        if (!sigueConOtraOrden("vehiculoId", nueva.vehiculoId)) {
            escribir(vehiculosBd.document(nueva.vehiculoId), mapOf(CAMPO_ACCESO to FieldValue.arrayRemove(tecnicoAnterior)))
        }
        val permisos = listOf(nueva.clienteId, nueva.tecnicoId)
        avancesBd.whereEqualTo("ordenId", nueva.id).get().esperar().documents.forEach {
            escribir(it.reference, mapOf(CAMPO_ACCESO to permisos))
        }
    }

    /**
     * Pregunta al servidor, porque un cliente solo puede leer sus propios vehículos. El servidor responde
     * `existe` y, solo si quien pregunta es el Jefe, el `clienteId` del dueño.
     */
    override suspend fun buscarPlaca(placa: String): PlacaRegistrada? {
        val respuesta = funciones.getHttpsCallable("verificarPlaca").call(mapOf("placa" to placa)).esperar().data as? Map<*, *>
        checkNotNull(respuesta) { "Respuesta inválida de verificarPlaca" }
        if (respuesta["existe"] != true) return null
        return PlacaRegistrada(respuesta["clienteId"] as? String)
    }

    /** Incrementa el contador en una transacción, así dos órdenes simultáneas nunca reciben el mismo número. */
    override suspend fun siguienteNumeroOrden(): String {
        val contador = bd.collection(CONTADORES).document(CONTADOR_ORDENES)
        val correlativo = bd.runTransaction { transaccion ->
            val siguiente = (transaccion.get(contador).getLong(CAMPO_ULTIMO) ?: 0L) + 1
            transaccion.set(contador, mapOf(CAMPO_ULTIMO to siguiente))
            siguiente
        }.esperar()
        return numeroDeOrden(correlativo)
    }

    /**
     * Las escrituras que haga [bloque] se acumulan en un lote y se aplican juntas al terminar; si [bloque] falla
     * no se escribe nada. Las lecturas dentro del bloque no ven las escrituras aún pendientes del mismo lote.
     */
    override suspend fun <R> enLote(bloque: suspend () -> R): R {
        if (coroutineContext[LoteEscrituras] != null) return bloque()
        val lote = LoteEscrituras(bd.batch())
        val resultado = withContext(lote) { bloque() }
        lote.lote.commit().esperar()
        return resultado
    }

    override suspend fun guardarTokenDispositivo(usuarioId: String, token: String) {
        bd.collection(TOKENS).document(usuarioId).set(mapOf("token" to token), SetOptions.merge()).esperar()
    }
}
