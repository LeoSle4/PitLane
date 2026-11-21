package com.leosle.pitlane.arquitectura.datasource.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.PlacaRegistrada
import com.leosle.pitlane.arquitectura.datasource.PlacaYaRegistradaException
import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.Vehiculo
import java.util.Date
import kotlinx.coroutines.flow.StateFlow

private const val CONTADORES = "contadores"
private const val CONTADOR_ORDENES = "ordenes"
private const val CAMPO_ULTIMO = "ultimo"
private const val USUARIOS = "usuarios"
private const val TOKENS = "tokensDispositivo"
private const val PLACAS = "placas"
private const val NUMEROS_ORDEN = "numerosOrden"
private const val NOTIFICACIONES = "notificaciones"
private const val ORDENES = "ordenes"
private const val AVANCES = "avances"

/**
 * Bandeja de notificaciones del usuario: una subcolección dentro de su perfil. Conserva el nombre `notificaciones`
 * porque la política TTL de `expiraEn` es por grupo de colecciones y así la misma política la alcanza.
 */
internal fun rutaDeNotificaciones(usuarioId: String): String = "$USUARIOS/$usuarioId/$NOTIFICACIONES"

/**
 * Avances de una orden: una subcolección dentro de la orden. Las reglas autorizan desde la orden padre, así que el
 * avance no copia su `acceso` ni guarda su `ordenId`.
 */
internal fun rutaDeAvances(ordenId: String): String = "$ORDENES/$ordenId/$AVANCES"

/**
 * Ruta y datos de la reserva de la placa de un vehículo: el id del documento ES la placa, así que Firestore solo
 * lo crea si está libre. Las reglas exigen que se escriba en el mismo lote que el vehículo.
 */
internal fun rutaYDatosDeReservaDePlaca(vehiculo: Vehiculo): Pair<String, Map<String, String>> =
    "$PLACAS/${vehiculo.placa}" to mapOf("vehiculoId" to vehiculo.id, "clienteId" to vehiculo.clienteId)

/**
 * Ruta y datos de la reserva del número de una orden: el id del documento ES el número, así que Firestore solo lo
 * crea si está libre. Las reglas exigen que se escriba en el mismo lote que la orden; el contador solo genera números.
 */
internal fun rutaYDatosDeReservaDeNumero(numero: String, ordenId: String): Pair<String, Map<String, String>> =
    "$NUMEROS_ORDEN/$numero" to mapOf("ordenId" to ordenId)

/** Número de orden con el formato ORD-0001 a partir de su correlativo. */
internal fun numeroDeOrden(correlativo: Long): String = "ORD-%04d".format(correlativo)

/**
 * Ruta y datos del token de un dispositivo: un documento por token dentro del usuario, para que dos teléfonos
 * del mismo usuario no se pisen y volver a guardar el mismo token actualice el mismo documento.
 */
/** Cuánto vive un token sin que el dispositivo vuelva a abrir la app: la política TTL de Firestore lo borra al vencer. */
internal const val VIGENCIA_TOKEN_DISPOSITIVO_MS = 60L * 24 * 60 * 60 * 1000

internal fun rutaYDatosDeTokenDispositivo(usuarioId: String, token: String, ahora: Long): Pair<String, Map<String, Any>> =
    "$USUARIOS/$usuarioId/$TOKENS/$token" to mapOf(
        "token" to token,
        "expiraEn" to Timestamp(Date(ahora + VIGENCIA_TOKEN_DISPOSITIVO_MS))
    )

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
    private val ordenesBd = bd.collection(ORDENES)

    private val vehiculosBase = ColeccionFirestore(
        vehiculosBd, actor,
        aDocumento = Vehiculo::aDocumento,
        desdeDocumento = { id, campos -> campos.aVehiculo(id) },
        acceso = { FieldValue.arrayUnion(it.clienteId) },
        alGuardar = ::reservarPlaca
    )

    /** El vehículo y la reserva de su placa se escriben (y se borran) juntos o ninguno. */
    override val vehiculos: IColeccion<Vehiculo> = object : IColeccion<Vehiculo> by vehiculosBase {
        override suspend fun guardar(id: String, valor: Vehiculo) {
            try {
                enLote { vehiculosBase.guardar(id, valor) }
            } catch (e: FirebaseFirestoreException) {
                // Si la placa ya estaba reservada, las reglas rechazan el lote completo y el vehículo no se creó
                val perdioLaPlaca = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS
                if (perdioLaPlaca && vehiculosBase.obtener(id) == null) throw PlacaYaRegistradaException()
                throw e
            }
        }

        override suspend fun eliminar(id: String) = enLote {
            // Un vehículo anterior a las reservas no tiene ninguna; las reglas aceptan borrar una reserva inexistente
            vehiculosBase.obtener(id)?.let { borrar(bd.document(rutaYDatosDeReservaDePlaca(it).first)) }
            vehiculosBase.eliminar(id)
        }
    }

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

    /** La orden y los permisos que arrastra (vehículo y perfiles) se escriben juntos o ninguno. */
    override val ordenes: IColeccion<Orden> = object : IColeccion<Orden> by ordenesBase {
        override suspend fun guardar(id: String, valor: Orden) = enLote { ordenesBase.guardar(id, valor) }
    }

    /** Los avances de una orden los leen quienes pueden leer la orden: las reglas lo deciden por la orden padre. */
    override fun avancesDe(ordenId: String): IColeccion<Avance> = ColeccionFirestore(
        bd.collection(rutaDeAvances(ordenId)), actor,
        delPadre = true,
        aDocumento = Avance::aDocumento,
        desdeDocumento = { id, campos -> campos.aAvance(id, ordenId) }
    )

    /** Los avisos son personales: cada usuario, incluido el Jefe, lee solo su propia bandeja. */
    override fun notificacionesDe(usuarioId: String): IColeccion<Notificacion> = ColeccionFirestore(
        bd.collection(rutaDeNotificaciones(usuarioId)), actor,
        dueno = usuarioId,
        aDocumento = Notificacion::aDocumento,
        desdeDocumento = { id, campos -> campos.aNotificacion(id) }
    )

    /** Al crear un vehículo reserva su placa en `placas/{placa}`; al editarlo la placa no cambia y la reserva sigue. */
    private suspend fun reservarPlaca(anterior: Vehiculo?, nuevo: Vehiculo) {
        if (anterior != null) return
        val (ruta, datos) = rutaYDatosDeReservaDePlaca(nuevo)
        escribir(bd.document(ruta), datos)
    }

    /**
     * Al crear una orden reserva su número en `numerosOrden/{numero}`. Al crearla o cambiar su técnico, vehículo o cliente, agrega al técnico en los permisos del vehículo
     * y del cliente, y al cliente en los del técnico. Si cambió el técnico, se los quita al anterior cuando ya no
     * le queda otra orden con ese cliente o vehículo. Los avances no se tocan: las reglas los autorizan por su orden.
     */
    private suspend fun alGuardarOrden(anterior: Orden?, nueva: Orden) {
        if (anterior == null) {
            val (ruta, datos) = rutaYDatosDeReservaDeNumero(nueva.numero, nueva.id)
            escribir(bd.document(ruta), datos)
        }
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
    override suspend fun <R> enLote(bloque: suspend () -> R): R = enLoteDe(bd, bloque)

    override suspend fun guardarTokenDispositivo(usuarioId: String, token: String) {
        val (ruta, datos) = rutaYDatosDeTokenDispositivo(usuarioId, token, System.currentTimeMillis())
        bd.document(ruta).set(datos, SetOptions.merge()).esperar()
    }
}
