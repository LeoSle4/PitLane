package com.leosle.pitlane.arquitectura.datasource.local

import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.modelo.Orden
import kotlinx.coroutines.flow.update

/**
 * Reproduce en memoria lo que haría el servidor cuando se escribe una orden: crear la notificación
 * para el cliente con cuenta cuando la orden se registra, cambia de estado o se reenvía el aviso de listo.
 */
internal class FuncionesLocales(private val bd: BaseDatosLocal) {

    /** Compara la orden anterior con la nueva y, si corresponde, crea la notificación del cliente. */
    fun alEscribirOrden(anterior: Orden?, nueva: Orden) {
        val cliente = bd.usuarios.value.firstOrNull { it.id == nueva.clienteId } ?: return
        if (!cliente.tieneCuenta) return
        val placa = bd.vehiculos.value.firstOrNull { it.id == nueva.vehiculoId }?.placa.orEmpty()
        val aviso = when {
            anterior == null -> "Vehículo ingresado" to "Tu vehículo $placa fue ingresado. Orden ${nueva.numero}"
            anterior.estado != nueva.estado -> avisoPorEstado(nueva, placa)
            nueva.estado == EstadoOrden.LISTO && nueva.avisosEnviados > anterior.avisosEnviados ->
                "Vehículo listo" to "Tu vehículo $placa está listo para recoger"
            else -> null
        } ?: return
        val notificacion = Notificacion(
            id = bd.nuevoId("not"),
            usuarioId = cliente.id,
            titulo = aviso.first,
            mensaje = aviso.second,
            fechaHora = bd.ahora(),
            leida = false,
            ordenId = nueva.id,
            esIngreso = anterior == null
        )
        bd.notificaciones.update { it + notificacion }
    }

    /** Devuelve título y mensaje del aviso que corresponde al nuevo estado de la orden. */
    private fun avisoPorEstado(orden: Orden, placa: String): Pair<String, String>? = when (orden.estado) {
        EstadoOrden.EN_REPARACION -> "Vehículo en reparación" to "Tu vehículo $placa está en reparación"
        EstadoOrden.LISTO -> "Vehículo listo" to "Tu vehículo $placa está listo para recoger"
        EstadoOrden.ANULADA -> "Orden anulada" to "Tu orden ${orden.numero} fue anulada"
        EstadoOrden.INGRESADO, EstadoOrden.ENTREGADO -> null
    }
}
