package com.leosle.pitlane.arquitectura.datasource.local

import com.leosle.pitlane.arquitectura.datasource.IColeccion
import com.leosle.pitlane.util.Busqueda
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.PlacaRegistrada
import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.Vehiculo
import kotlinx.coroutines.flow.update

/** Colecciones de documentos en memoria; al guardar una orden se ejecutan las funciones del servidor simuladas. */
class DocumentosDataSource internal constructor(
    private val bd: BaseDatosLocal,
    private val funciones: FuncionesLocales
) : IDocumentosDataSource {

    constructor(bd: BaseDatosLocal) : this(bd, FuncionesLocales(bd))

    override val vehiculos: IColeccion<Vehiculo> =
        ColeccionLocal(bd.vehiculos, Vehiculo::id, { bd.nuevoId("v") }, { Busqueda.terminosDePlaca(it.placa) })

    override val solicitudes: IColeccion<Solicitud> =
        ColeccionLocal(bd.solicitudes, Solicitud::id, { bd.nuevoId("s") }, actividad = { it.creadaEn })

    override val ordenes: IColeccion<Orden> =
        ColeccionLocal(bd.ordenes, Orden::id, { bd.nuevoId("o") }, actividad = { it.fechaUltimoCambio }) { anterior, nueva ->
            funciones.alEscribirOrden(anterior, nueva)
        }

    /** Simula la subcolección `ordenes/{ordenId}/avances` sobre la lista común: su orden es `ordenId`. */
    override fun avancesDe(ordenId: String): IColeccion<Avance> =
        ColeccionLocal(bd.avances, Avance::id, { bd.nuevoId("a") }, pertenece = { it.ordenId == ordenId })

    /** Simula la subcolección `usuarios/{usuarioId}/notificaciones` sobre la lista común: su dueño es `usuarioId`. */
    override fun notificacionesDe(usuarioId: String): IColeccion<Notificacion> =
        ColeccionLocal(bd.notificaciones, Notificacion::id, { bd.nuevoId("not") }, pertenece = { it.usuarioId == usuarioId })

    override suspend fun buscarPlaca(placa: String): PlacaRegistrada? =
        bd.vehiculos.value.firstOrNull { it.placa == placa }?.let { PlacaRegistrada(it.clienteId) }

    override suspend fun siguienteNumeroOrden(): String = bd.siguienteNumeroOrden()

    /** En memoria las escrituras son inmediatas, así que el bloque se ejecuta tal cual. */
    override suspend fun <R> enLote(bloque: suspend () -> R): R = bloque()

    override suspend fun guardarTokenDispositivo(usuarioId: String, token: String) {
        bd.tokensDispositivo.update { it + (usuarioId to (it[usuarioId].orEmpty() + token)) }
    }
}
