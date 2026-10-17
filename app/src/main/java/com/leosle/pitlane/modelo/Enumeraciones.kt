package com.leosle.pitlane.modelo

/** Rol de una persona dentro de la aplicación; determina las pestañas y permisos que recibe. */
enum class Rol(val etiqueta: String) {
    CLIENTE("Cliente"),
    JEFE("Jefe de Taller"),
    TECNICO("Técnico Electricista")
}

/** Etapas por las que pasa una orden de ingreso, desde que el vehículo llega hasta que se entrega al cliente. */
enum class EstadoOrden(val etiqueta: String) {
    INGRESADO("Ingresado"),
    EN_REPARACION("En reparación"),
    LISTO("Listo para recoger"),
    ENTREGADO("Entregado"),
    ANULADA("Anulada")
}

/** Situación de una solicitud de atención enviada por el cliente. */
enum class EstadoSolicitud(val etiqueta: String) {
    PENDIENTE("Pendiente"),
    ATENDIDA("Atendida"),
    CANCELADA("Cancelada")
}

/** Naturaleza de un avance registrado por el técnico sobre una orden. */
enum class TipoAvance(val etiqueta: String) {
    HALLAZGO("Hallazgo"),
    TRABAJO_REALIZADO("Trabajo realizado")
}

/** Nivel de combustible con el que llega el vehículo al taller. */
enum class NivelCombustible(val etiqueta: String) {
    RESERVA("Reserva"),
    UN_CUARTO("1/4"),
    MEDIO("1/2"),
    TRES_CUARTOS("3/4"),
    LLENO("Lleno")
}
