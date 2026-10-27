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

    /** Devuelve el documento con ese identificador, o nulo si no existe. */
    suspend fun obtener(id: String): T?

    /** Guarda el documento: reemplaza el existente o lo agrega. */
    suspend fun guardar(id: String, valor: T)

    /** Elimina el documento con ese identificador. */
    suspend fun eliminar(id: String)

    /** Genera un identificador nuevo para un documento de esta colección. */
    fun nuevoId(): String
}

/** Fuente de datos de las colecciones de la base de documentos. */
interface IDocumentosDataSource {
    val vehiculos: IColeccion<Vehiculo>
    val solicitudes: IColeccion<Solicitud>
    val ordenes: IColeccion<Orden>
    val avances: IColeccion<Avance>
    val notificaciones: IColeccion<Notificacion>

    /** Entrega el siguiente número correlativo de orden con el formato ORD-0001 de forma transaccional. */
    suspend fun siguienteNumeroOrden(): String

    /** Ejecuta [bloque] de modo que todas las escrituras que haga se apliquen juntas o ninguna. */
    suspend fun <R> enLote(bloque: suspend () -> R): R

    /** Guarda el token con el que el dispositivo del usuario recibe los avisos. */
    suspend fun guardarTokenDispositivo(usuarioId: String, token: String)
}
