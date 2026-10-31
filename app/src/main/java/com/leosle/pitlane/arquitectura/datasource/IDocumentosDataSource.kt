package com.leosle.pitlane.arquitectura.datasource

import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.Vehiculo
import kotlinx.coroutines.flow.Flow

/** Colección de documentos de un mismo tipo. */
interface IColeccion<T> {

    /** Emite todos los documentos cada vez que la colección cambia. */
    fun observarTodos(): Flow<List<T>>

    /** Devuelve los documentos actuales. */
    suspend fun listar(): List<T>

    /**
     * Devuelve los documentos que el usuario puede leer y cuyo campo [campo] vale [valor]; equivale a filtrar
     * [listar] por ese campo, pero la base solo entrega los que coinciden.
     */
    suspend fun listarDonde(campo: String, valor: String): List<T>

    /**
     * Emite los documentos que el usuario puede leer y cuyo campo [campo] vale [valor], cada vez que cambian; es la
     * versión en vivo de [listarDonde] y la base solo entrega los que coinciden.
     */
    fun observarDonde(campo: String, valor: String): Flow<List<T>>

    /**
     * Devuelve como máximo [limite] (de 1 a 100) documentos que el usuario puede leer y cuyo campo de lista [campo]
     * contiene [termino] (`array-contains`); la base solo entrega los que coinciden, así que el costo depende de las
     * coincidencias y no del tamaño de la colección.
     */
    suspend fun buscarPorTermino(campo: String, termino: String, limite: Int): List<T>

    /**
     * Emite los documentos que el usuario puede leer y cuyo campo [campo] vale alguno de [valores] (`in`); Firestore
     * admite como máximo 30 valores, y la lista no puede estar vacía.
     */
    fun observarEnLista(campo: String, valores: List<String>): Flow<List<T>>

    /** Emite el documento con ese identificador cada vez que cambia; nulo si no existe o el usuario no puede leerlo. */
    fun observar(id: String): Flow<T?>

    /** Devuelve el documento con ese identificador, o nulo si no existe. */
    suspend fun obtener(id: String): T?

    /** Guarda el documento: reemplaza el existente o lo agrega. */
    suspend fun guardar(id: String, valor: T)

    /** Elimina el documento con ese identificador. */
    suspend fun eliminar(id: String)

    /** Genera un identificador nuevo para un documento de esta colección. */
    fun nuevoId(): String
}

/** Vehículo ya registrado con una placa. [clienteId] es nulo cuando quien consulta no puede conocer al dueño. */
data class PlacaRegistrada(val clienteId: String?)

/** Otro vehículo ganó la placa: la reserva `placas/{placa}` ya existía cuando se intentó registrar este. */
class PlacaYaRegistradaException : Exception("La placa ya está registrada")

/** Fuente de datos de las colecciones de la base de documentos. */
interface IDocumentosDataSource {
    val vehiculos: IColeccion<Vehiculo>
    val solicitudes: IColeccion<Solicitud>
    val ordenes: IColeccion<Orden>
    val avances: IColeccion<Avance>
    val notificaciones: IColeccion<Notificacion>

    /** Busca entre todos los vehículos, sin importar de quién sean, el que tenga esa placa; nulo si no existe. */
    suspend fun buscarPlaca(placa: String): PlacaRegistrada?

    /** Entrega el siguiente número correlativo de orden con el formato ORD-0001 de forma transaccional. */
    suspend fun siguienteNumeroOrden(): String

    /** Ejecuta [bloque] de modo que todas las escrituras que haga se apliquen juntas o ninguna. */
    suspend fun <R> enLote(bloque: suspend () -> R): R

    /** Guarda el token con el que el dispositivo del usuario recibe los avisos. */
    suspend fun guardarTokenDispositivo(usuarioId: String, token: String)
}
