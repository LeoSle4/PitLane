package com.leosle.pitlane.modelo

/** Datos que un cliente escribe al crear su cuenta. */
data class DatosRegistroCliente(
    val nombres: String,
    val apellidos: String,
    val dni: String,
    val telefono: String,
    val correo: String,
    val contrasena: String
)

/** Datos que el Jefe de Taller completa para registrar una orden de ingreso. */
data class DatosOrden(
    val clienteId: String,
    val vehiculoId: String,
    val solicitudId: String?,
    val falla: String,
    val kilometraje: Int,
    val combustible: NivelCombustible,
    val observaciones: String,
    val fotos: List<Foto>,
    val tecnicoId: String
)
