package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.modelo.DatosOrden
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.modelo.ResultadoIngreso
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow

/** Órdenes de ingreso y sus cambios de estado. */
interface IOrdenRepository {

    /** Emite una orden con su cliente, vehículo, técnico y solicitud de origen. */
    fun observarOrden(ordenId: String, usuarioId: String): Flow<OrdenDetallada?>

    /** Emite todas las órdenes de un cliente, de la más reciente a la más antigua. */
    fun observarOrdenesDeCliente(clienteId: String): Flow<List<OrdenDetallada>>

    /** Emite las órdenes Ingresado, En reparación y Listo para recoger de todo el taller. */
    fun observarOrdenesActivas(): Flow<List<OrdenDetallada>>

    /** Emite las órdenes Ingresado o En reparación asignadas a un técnico. */
    fun observarOrdenesDeTecnico(tecnicoId: String): Flow<List<OrdenDetallada>>

    /** Emite todas las órdenes de un vehículo, de la más reciente a la más antigua. */
    fun observarOrdenesDeVehiculo(vehiculoId: String): Flow<List<OrdenDetallada>>

    /** Crea una orden Ingresado, marca atendida la solicitud de origen y avisa al cliente si tiene cuenta. */
    suspend fun registrarOrden(datos: DatosOrden, jefeId: String): Resultado<ResultadoIngreso>

    /** Corrige los datos de una orden Ingresado o En reparación y registra quién la editó. */
    suspend fun editarOrden(
        ordenId: String,
        falla: String,
        kilometraje: Int,
        combustible: NivelCombustible,
        observaciones: String,
        tecnicoId: String,
        usuarioId: String
    ): Resultado<Orden>

    /** Anula una orden Ingresado con un motivo obligatorio y avisa al cliente. */
    suspend fun anularOrden(ordenId: String, motivo: String, usuarioId: String): Resultado<Orden>

    /** Agrega fotos del estado exterior a una orden Ingresado, hasta un máximo de 6. */
    suspend fun agregarFotosOrden(ordenId: String, fotos: List<Foto>, usuarioId: String): Resultado<Orden>

    /** Quita una foto de una orden Ingresado; la orden debe conservar al menos una. */
    suspend fun eliminarFotoOrden(ordenId: String, fotoId: String, usuarioId: String): Resultado<Orden>

    /** Pasa la orden de Ingresado a En reparación y avisa al cliente. */
    suspend fun iniciarReparacion(ordenId: String, tecnicoId: String): Resultado<Orden>

    /** Pasa la orden a Listo para recoger si tiene un trabajo realizado, y avisa al cliente. */
    suspend fun marcarListo(ordenId: String, tecnicoId: String): Resultado<Orden>

    /** Busca entre las órdenes Listo para recoger por placa, nombre o DNI del cliente o número de orden; vacío devuelve todas. */
    suspend fun buscarOrdenesListas(consulta: String): List<OrdenDetallada>

    /** Registra que el cliente comprobó el resultado y quedó conforme, con fecha, hora y usuario; solo una vez y solo por el Jefe. */
    suspend fun registrarConformidad(ordenId: String, usuarioId: String): Resultado<Orden>

    /** Agrega a una orden Listo para recoger la observación del cliente y lo acordado, sin cambiar su estado. */
    suspend fun registrarObservacionCliente(ordenId: String, observacion: String, acuerdo: String, usuarioId: String): Resultado<Orden>

    /** Registra la entrega del vehículo al cliente: solo una orden Listo para recoger con la conformidad registrada pasa a Entregado, y solo por el Jefe. */
    suspend fun marcarEntregada(ordenId: String, usuarioId: String): Resultado<Orden>

    /** Reenvía el aviso de vehículo listo, como máximo una vez cada 24 horas. */
    suspend fun reenviarAviso(ordenId: String, usuarioId: String): Resultado<Orden>
}
