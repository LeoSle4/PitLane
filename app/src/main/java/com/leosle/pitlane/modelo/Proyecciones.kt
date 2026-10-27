package com.leosle.pitlane.modelo

/** Vehículo junto con su orden más reciente que no fue anulada, usada para mostrar su estado en el taller. */
data class VehiculoConEstado(
    val vehiculo: Vehiculo,
    val ordenActiva: Orden?
)

/** Técnico con la cantidad de órdenes que tiene en curso (Ingresado o En reparación). */
data class TecnicoConCarga(
    val tecnico: Usuario,
    val ordenesEnCurso: Int
)

/** Vehículo con todas sus órdenes, de la más reciente a la más antigua. */
data class VehiculoConHistorial(
    val vehiculo: Vehiculo,
    val ordenes: List<Orden>
)

/** Datos completos de un cliente: persona, vehículos e historial de órdenes por vehículo. */
data class FichaCliente(
    val cliente: Usuario,
    val vehiculos: List<VehiculoConHistorial>
)

/** Coincidencia de la búsqueda de clientes con las placas que coincidieron con el texto escrito. */
data class ResultadoBusqueda(
    val cliente: Usuario,
    val placasCoincidentes: List<String>
)

/** Solicitud con el cliente y el vehículo ya resueltos para mostrarla sin consultas adicionales. */
data class SolicitudDetallada(
    val solicitud: Solicitud,
    val cliente: Usuario?,
    val vehiculo: Vehiculo?
)

/** Orden con su cliente, vehículo, técnico y solicitud de origen ya resueltos. */
data class OrdenDetallada(
    val orden: Orden,
    val cliente: Usuario?,
    val vehiculo: Vehiculo?,
    val tecnico: Usuario?,
    val solicitud: Solicitud?,
    val conformidadRegistradaPor: Usuario? = null,
    val entregadaPor: Usuario? = null
)

/** Avance con el técnico que lo registró. */
data class AvanceDetallado(
    val avance: Avance,
    val tecnico: Usuario?
)

/** Resultado de registrar una orden: la orden creada y si el cliente recibió el aviso en su teléfono. */
data class ResultadoIngreso(
    val orden: Orden,
    val clienteNotificado: Boolean
)
