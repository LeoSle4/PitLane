package com.leosle.pitlane.arquitectura.datasource.firebase

import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.CambioEstado
import com.leosle.pitlane.modelo.Conformidad
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.modelo.ObservacionCliente
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.Vehiculo

/**
 * Conversión entre el modelo y los documentos de Firestore. El identificador es el del documento y no se
 * repite entre los campos. La contraseña del [Usuario] nunca se escribe: la guarda Firebase Authentication.
 */
internal typealias Documento = Map<String, Any?>

private fun Documento.texto(campo: String): String = this[campo] as? String ?: ""

private fun Documento.textoONulo(campo: String): String? = this[campo] as? String

private fun Documento.largo(campo: String): Long = (this[campo] as? Number)?.toLong() ?: 0L

private fun Documento.largoONulo(campo: String): Long? = (this[campo] as? Number)?.toLong()

private fun Documento.entero(campo: String): Int = (this[campo] as? Number)?.toInt() ?: 0

private fun Documento.booleano(campo: String, porDefecto: Boolean): Boolean = this[campo] as? Boolean ?: porDefecto

@Suppress("UNCHECKED_CAST")
private fun Documento.documentos(campo: String): List<Documento> = this[campo] as? List<Documento> ?: emptyList()

@Suppress("UNCHECKED_CAST")
private fun Documento.subdocumento(campo: String): Documento? = this[campo] as? Documento

@Suppress("UNCHECKED_CAST")
private fun Documento.textos(campo: String): List<String> = this[campo] as? List<String> ?: emptyList()

/** Un estado desconocido o ausente indica datos dañados, así que falla en lugar de adivinar un valor. */
private inline fun <reified E : Enum<E>> Documento.enumeracion(campo: String): E =
    enumValueOf<E>(requireNotNull(this[campo] as? String) { "Falta el campo $campo" })

internal fun Usuario.aDocumento(): Documento = mapOf(
    "nombres" to nombres,
    "apellidos" to apellidos,
    "dni" to dni,
    "telefono" to telefono,
    "correo" to correo,
    "rol" to rol.name,
    "activo" to activo,
    "tieneCuenta" to tieneCuenta
)

internal fun Documento.aUsuario(id: String): Usuario = Usuario(
    id = id,
    nombres = texto("nombres"),
    apellidos = texto("apellidos"),
    dni = texto("dni"),
    telefono = texto("telefono"),
    correo = texto("correo"),
    contrasena = "",
    rol = enumeracion<Rol>("rol"),
    activo = booleano("activo", true),
    tieneCuenta = booleano("tieneCuenta", true)
)

internal fun Vehiculo.aDocumento(): Documento = mapOf(
    "clienteId" to clienteId,
    "placa" to placa,
    "marca" to marca,
    "modelo" to modelo,
    "anio" to anio,
    "color" to color,
    "activo" to activo
)

internal fun Documento.aVehiculo(id: String): Vehiculo = Vehiculo(
    id = id,
    clienteId = texto("clienteId"),
    placa = texto("placa"),
    marca = texto("marca"),
    modelo = texto("modelo"),
    anio = entero("anio"),
    color = texto("color"),
    activo = booleano("activo", true)
)

private fun Foto.aDocumento(): Documento = mapOf("id" to id, "ruta" to ruta, "fechaHora" to fechaHora)

private fun Documento.aFoto(): Foto = Foto(id = texto("id"), ruta = texto("ruta"), fechaHora = largo("fechaHora"))

internal fun Solicitud.aDocumento(): Documento = mapOf(
    "clienteId" to clienteId,
    "vehiculoId" to vehiculoId,
    "descripcion" to descripcion,
    "sintomas" to sintomas,
    "fechaLlegada" to fechaLlegada,
    "fotos" to fotos.map { it.aDocumento() },
    "estado" to estado.name,
    "creadaEn" to creadaEn,
    "ordenId" to ordenId
)

internal fun Documento.aSolicitud(id: String): Solicitud = Solicitud(
    id = id,
    clienteId = texto("clienteId"),
    vehiculoId = texto("vehiculoId"),
    descripcion = texto("descripcion"),
    sintomas = textos("sintomas"),
    fechaLlegada = largo("fechaLlegada"),
    fotos = documentos("fotos").map { it.aFoto() },
    estado = enumeracion<EstadoSolicitud>("estado"),
    creadaEn = largo("creadaEn"),
    ordenId = textoONulo("ordenId")
)

private fun CambioEstado.aDocumento(): Documento = mapOf(
    "estado" to estado.name, "fechaHora" to fechaHora, "usuarioId" to usuarioId, "motivo" to motivo, "usuarioNombre" to usuarioNombre
)

private fun Documento.aCambioEstado(): CambioEstado = CambioEstado(
    estado = enumeracion<EstadoOrden>("estado"),
    fechaHora = largo("fechaHora"),
    usuarioId = texto("usuarioId"),
    motivo = textoONulo("motivo"),
    usuarioNombre = textoONulo("usuarioNombre")
)

private fun Conformidad.aDocumento(): Documento =
    mapOf("fechaHora" to fechaHora, "usuarioId" to usuarioId, "usuarioNombre" to usuarioNombre)

private fun Documento.aConformidad(): Conformidad =
    Conformidad(fechaHora = largo("fechaHora"), usuarioId = texto("usuarioId"), usuarioNombre = textoONulo("usuarioNombre"))

private fun ObservacionCliente.aDocumento(): Documento =
    mapOf("texto" to texto, "acuerdo" to acuerdo, "fechaHora" to fechaHora, "usuarioId" to usuarioId)

private fun Documento.aObservacionCliente(): ObservacionCliente = ObservacionCliente(
    texto = texto("texto"),
    acuerdo = texto("acuerdo"),
    fechaHora = largo("fechaHora"),
    usuarioId = texto("usuarioId")
)

internal fun Orden.aDocumento(): Documento = mapOf(
    "numero" to numero,
    "clienteId" to clienteId,
    "vehiculoId" to vehiculoId,
    "solicitudId" to solicitudId,
    "falla" to falla,
    "kilometraje" to kilometraje,
    "combustible" to combustible.name,
    "observaciones" to observaciones,
    "fotos" to fotos.map { it.aDocumento() },
    "tecnicoId" to tecnicoId,
    "estado" to estado.name,
    "historial" to historial.map { it.aDocumento() },
    "creadaEn" to creadaEn,
    "motivoAnulacion" to motivoAnulacion,
    "avisosEnviados" to avisosEnviados,
    "ultimoAviso" to ultimoAviso,
    "editadaPor" to editadaPor,
    "editadaEn" to editadaEn,
    "conformidad" to conformidad?.aDocumento(),
    "observacionesCliente" to observacionesCliente.map { it.aDocumento() }
)

internal fun Documento.aOrden(id: String): Orden = Orden(
    id = id,
    numero = texto("numero"),
    clienteId = texto("clienteId"),
    vehiculoId = texto("vehiculoId"),
    solicitudId = textoONulo("solicitudId"),
    falla = texto("falla"),
    kilometraje = entero("kilometraje"),
    combustible = enumeracion<NivelCombustible>("combustible"),
    observaciones = texto("observaciones"),
    fotos = documentos("fotos").map { it.aFoto() },
    tecnicoId = texto("tecnicoId"),
    estado = enumeracion<EstadoOrden>("estado"),
    historial = documentos("historial").map { it.aCambioEstado() },
    creadaEn = largo("creadaEn"),
    motivoAnulacion = textoONulo("motivoAnulacion"),
    avisosEnviados = entero("avisosEnviados"),
    ultimoAviso = largoONulo("ultimoAviso"),
    editadaPor = textoONulo("editadaPor"),
    editadaEn = largoONulo("editadaEn"),
    conformidad = subdocumento("conformidad")?.aConformidad(),
    observacionesCliente = documentos("observacionesCliente").map { it.aObservacionCliente() }
)

internal fun Avance.aDocumento(): Documento = mapOf(
    "ordenId" to ordenId,
    "tecnicoId" to tecnicoId,
    "tipo" to tipo.name,
    "descripcion" to descripcion,
    "fotos" to fotos.map { it.aDocumento() },
    "fechaHora" to fechaHora
)

internal fun Documento.aAvance(id: String): Avance = Avance(
    id = id,
    ordenId = texto("ordenId"),
    tecnicoId = texto("tecnicoId"),
    tipo = enumeracion<TipoAvance>("tipo"),
    descripcion = texto("descripcion"),
    fotos = documentos("fotos").map { it.aFoto() },
    fechaHora = largo("fechaHora")
)

internal fun Notificacion.aDocumento(): Documento = mapOf(
    "usuarioId" to usuarioId,
    "titulo" to titulo,
    "mensaje" to mensaje,
    "fechaHora" to fechaHora,
    "leida" to leida,
    "ordenId" to ordenId,
    "esIngreso" to esIngreso
)

internal fun Documento.aNotificacion(id: String): Notificacion = Notificacion(
    id = id,
    usuarioId = texto("usuarioId"),
    titulo = texto("titulo"),
    mensaje = texto("mensaje"),
    fechaHora = largo("fechaHora"),
    leida = booleano("leida", false),
    ordenId = textoONulo("ordenId"),
    esIngreso = booleano("esIngreso", false)
)
