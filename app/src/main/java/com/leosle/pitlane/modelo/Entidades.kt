package com.leosle.pitlane.modelo

/**
 * Persona registrada en el sistema. Un cliente creado por el Jefe de Taller no tiene cuenta de acceso
 * ([tieneCuenta] en falso) hasta que se registra con el mismo DNI.
 */
data class Usuario(
    val id: String,
    val nombres: String,
    val apellidos: String,
    val dni: String,
    val telefono: String,
    val correo: String,
    val contrasena: String,
    val rol: Rol,
    val activo: Boolean = true,
    val tieneCuenta: Boolean = true
) {
    /** Nombres y apellidos unidos en un solo texto. */
    val nombreCompleto: String get() = "$nombres $apellidos".trim()

    /** Primeras letras del nombre y del apellido, usadas en el avatar. */
    val iniciales: String
        get() = (nombres.firstOrNull()?.toString().orEmpty() + apellidos.firstOrNull()?.toString().orEmpty()).uppercase()
}

/** Vehículo registrado a nombre de un cliente; [activo] en falso significa oculto de la lista del cliente. */
data class Vehiculo(
    val id: String,
    val clienteId: String,
    val placa: String,
    val marca: String,
    val modelo: String,
    val anio: Int,
    val color: String,
    val activo: Boolean = true
) {
    /** Marca, modelo y año en una sola línea. */
    val descripcion: String get() = "$marca $modelo $anio"
}

/**
 * Foto asociada a una solicitud, orden o avance. [ruta] puede ser la dirección de un archivo o un recurso
 * de ejemplo con el formato `recurso:nombre_del_drawable`.
 */
data class Foto(
    val id: String,
    val ruta: String,
    val fechaHora: Long
)

/** Aviso de falla enviado por el cliente antes de llevar su vehículo al taller. */
data class Solicitud(
    val id: String,
    val clienteId: String,
    val vehiculoId: String,
    val descripcion: String,
    val sintomas: List<String>,
    val fechaLlegada: Long,
    val fotos: List<Foto>,
    val estado: EstadoSolicitud,
    val creadaEn: Long,
    val ordenId: String? = null
)

/** Registro de un cambio de estado de la orden con su fecha, hora y autor. */
data class CambioEstado(
    val estado: EstadoOrden,
    val fechaHora: Long,
    val usuarioId: String,
    val motivo: String? = null
)

/** Orden de ingreso: documento digital que acompaña al vehículo durante toda la reparación. */
data class Orden(
    val id: String,
    val numero: String,
    val clienteId: String,
    val vehiculoId: String,
    val solicitudId: String?,
    val falla: String,
    val kilometraje: Int,
    val combustible: NivelCombustible,
    val observaciones: String,
    val fotos: List<Foto>,
    val tecnicoId: String,
    val estado: EstadoOrden,
    val historial: List<CambioEstado>,
    val creadaEn: Long,
    val motivoAnulacion: String? = null,
    val avisosEnviados: Int = 0,
    val ultimoAviso: Long? = null,
    val editadaPor: String? = null,
    val editadaEn: Long? = null
) {
    /** Verdadero mientras el técnico todavía trabaja en la orden (Ingresado o En reparación). */
    val enCurso: Boolean get() = estado == EstadoOrden.INGRESADO || estado == EstadoOrden.EN_REPARACION

    /** Verdadero mientras el vehículo permanece físicamente en el taller (en curso o listo para recoger). */
    val enTaller: Boolean get() = enCurso || estado == EstadoOrden.LISTO

    /** Fecha y hora del último cambio de estado registrado. */
    val fechaUltimoCambio: Long get() = historial.lastOrNull()?.fechaHora ?: creadaEn

    /** Fecha y hora en que la orden entró al estado indicado, o nulo si aún no llegó a él. */
    fun fechaDeEstado(estadoBuscado: EstadoOrden): Long? =
        historial.firstOrNull { it.estado == estadoBuscado }?.fechaHora

    /** Horas que faltan para poder reenviar el aviso de vehículo listo; cero si ya se puede reenviar. */
    fun horasParaReenviar(ahora: Long): Int {
        val ultimo = ultimoAviso ?: return 0
        val horaEnMilis = 3_600_000L
        val restante = 24 * horaEnMilis - (ahora - ultimo)
        return if (restante <= 0) 0 else ((restante + horaEnMilis - 1) / horaEnMilis).toInt()
    }
}

/** Hallazgo o trabajo realizado que el técnico documenta dentro de una orden. */
data class Avance(
    val id: String,
    val ordenId: String,
    val tecnicoId: String,
    val tipo: TipoAvance,
    val descripcion: String,
    val fotos: List<Foto>,
    val fechaHora: Long
)

/** Aviso dirigido a un usuario; [ordenId] indica la orden que se abre al tocarlo. */
data class Notificacion(
    val id: String,
    val usuarioId: String,
    val titulo: String,
    val mensaje: String,
    val fechaHora: Long,
    val leida: Boolean = false,
    val ordenId: String? = null,
    /** Indica que el aviso corresponde al ingreso del vehículo y debe abrir el comprobante. */
    val esIngreso: Boolean = false
)
