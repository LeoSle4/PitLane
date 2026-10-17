package com.leosle.pitlane.repositorio

import kotlinx.coroutines.flow.combine
import com.leosle.pitlane.fuente.IDocumentosDataSource
import com.leosle.pitlane.fuente.IFotoStorage
import com.leosle.pitlane.fuente.IUsuariosDataSource
import com.leosle.pitlane.modelo.CambioEstado
import com.leosle.pitlane.modelo.DatosOrden
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.modelo.ResultadoIngreso
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.util.Reloj
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Órdenes de ingreso y sus cambios de estado. Los avisos al cliente no se crean aquí: los genera la fuente
 * de documentos al escribirse la orden.
 */
class OrdenRepository(
    private val usuarios: IUsuariosDataSource,
    private val documentos: IDocumentosDataSource,
    private val fotos: IFotoStorage,
    private val reloj: Reloj
) : IOrdenRepository {

    private companion object {
        const val MAXIMO_FOTOS = 6
        const val MENSAJE_NO_ASIGNADA = "Esta orden no está asignada a ti"
        const val MENSAJE_SOLO_JEFE = "Solo el Jefe de Taller puede realizar esta acción"
    }

    /** RN-07 y RN-08: el Jefe ve todas las órdenes, el técnico las que tiene asignadas y el cliente las propias. */
    private fun puedeVer(usuario: com.leosle.pitlane.modelo.Usuario?, orden: Orden): Boolean = when (usuario?.rol) {
        Rol.JEFE -> true
        Rol.TECNICO -> orden.tecnicoId == usuario.id
        Rol.CLIENTE -> orden.clienteId == usuario.id
        null -> false
    }

    /** Verdadero si el usuario existe, está activo y es Jefe de Taller (RN-06). */
    private suspend fun esJefe(usuarioId: String): Boolean =
        usuarios.obtenerUsuario(usuarioId)?.let { it.rol == Rol.JEFE && it.activo } == true

    /** Emite la orden cada vez que cambia, solo si el usuario puede verla: el Jefe todas, el técnico las suyas y el cliente las suyas. */
    override fun observarOrden(ordenId: String, usuarioId: String): Flow<OrdenDetallada?> =
        ordenesDetalladas(usuarios, documentos)
            .map { lista ->
                val usuario = usuarios.obtenerUsuario(usuarioId)
                lista.firstOrNull { it.orden.id == ordenId }?.takeIf { puedeVer(usuario, it.orden) }
            }
            .distinctUntilChanged()

    /** Emite las órdenes del cliente de la más reciente a la más antigua. */
    override fun observarOrdenesDeCliente(clienteId: String): Flow<List<OrdenDetallada>> =
        combine(ordenesDetalladas(usuarios, documentos), usuarios.usuarioActual) { lista, actor ->
            if (!actor.esJefeOPropio(clienteId)) emptyList()
            else lista.filter { it.orden.clienteId == clienteId }.sortedByDescending { it.orden.creadaEn }
        }

    /** Emite las órdenes que siguen vivas en el taller, de la más reciente a la más antigua. */
    override fun observarOrdenesActivas(): Flow<List<OrdenDetallada>> =
        combine(ordenesDetalladas(usuarios, documentos), usuarios.usuarioActual) { lista, actor ->
            if (!actor.esJefe()) emptyList()
            else lista.filter { it.orden.enTaller }.sortedByDescending { it.orden.creadaEn }
        }

    /** Emite las órdenes en curso del técnico: primero En reparación, luego Ingresado, de la más antigua a la más reciente. */
    override fun observarOrdenesDeTecnico(tecnicoId: String): Flow<List<OrdenDetallada>> =
        combine(ordenesDetalladas(usuarios, documentos), usuarios.usuarioActual) { lista, actor ->
            if (!actor.esJefeOPropio(tecnicoId)) emptyList()
            else lista.filter { it.orden.tecnicoId == tecnicoId && it.orden.enCurso }
                .sortedWith(
                    compareBy<OrdenDetallada> { if (it.orden.estado == EstadoOrden.EN_REPARACION) 0 else 1 }
                        .thenBy { it.orden.creadaEn }
                )
        }

    /** Emite las órdenes del vehículo de la más reciente a la más antigua; el cliente solo recibe las suyas y el técnico las usa para ver el historial. */
    override fun observarOrdenesDeVehiculo(vehiculoId: String): Flow<List<OrdenDetallada>> =
        combine(ordenesDetalladas(usuarios, documentos), usuarios.usuarioActual) { lista, actor ->
            lista.filter { it.orden.vehiculoId == vehiculoId && (actor.esJefe() || actor?.rol == Rol.TECNICO || (actor != null && it.orden.clienteId == actor.id)) }
                .sortedByDescending { it.orden.creadaEn }
        }

    /** Valida fotos, técnico y vehículo; guarda las fotos, crea la orden Ingresado y marca atendida la solicitud. */
    override suspend fun registrarOrden(datos: DatosOrden, jefeId: String): Resultado<ResultadoIngreso> {
        if (!esJefe(jefeId)) return Resultado.Error(MENSAJE_SOLO_JEFE)
        val cliente = usuarios.obtenerUsuario(datos.clienteId) ?: return Resultado.Error("No se encontró el cliente")
        val vehiculo = documentos.vehiculos.obtener(datos.vehiculoId) ?: return Resultado.Error("No se encontró el vehículo")
        if (vehiculo.clienteId != cliente.id) return Resultado.Error("El vehículo no pertenece a ese cliente")
        if (datos.falla.isBlank()) return Resultado.Error("Describe la falla reportada")
        if (datos.kilometraje < 0) return Resultado.Error("Ingresa un kilometraje válido")
        if (datos.fotos.isEmpty()) return Resultado.Error("Agrega al menos una foto del estado del vehículo")
        if (datos.fotos.size > MAXIMO_FOTOS) return Resultado.Error("Máximo 6 fotos")
        if (documentos.ordenes.listar().any { it.vehiculoId == datos.vehiculoId && it.enCurso }) {
            return Resultado.Error("Este vehículo ya tiene una orden en curso")
        }
        val tecnico = usuarios.obtenerUsuario(datos.tecnicoId)?.takeIf { it.activo && it.rol == Rol.TECNICO }
            ?: return Resultado.Error("Elige un técnico activo")
        val guardadas = when (val subida = fotos.subir(datos.fotos)) {
            is Resultado.Error -> return subida
            is Resultado.Exito -> subida.valor
        }

        val ahora = reloj.ahora()
        val orden = Orden(
            id = documentos.ordenes.nuevoId(),
            numero = documentos.siguienteNumeroOrden(),
            clienteId = cliente.id,
            vehiculoId = vehiculo.id,
            solicitudId = datos.solicitudId,
            falla = datos.falla.trim(),
            kilometraje = datos.kilometraje,
            combustible = datos.combustible,
            observaciones = datos.observaciones.trim(),
            fotos = guardadas,
            tecnicoId = tecnico.id,
            estado = EstadoOrden.INGRESADO,
            historial = listOf(CambioEstado(EstadoOrden.INGRESADO, ahora, jefeId)),
            creadaEn = ahora
        )
        // Con solicitud de origen se atiende esa; sin ella, las pendientes del mismo vehículo (HU-23)
        val solicitudesAtendidas = datos.solicitudId?.let { listOf(it) }
            ?: documentos.solicitudes.listar()
                .filter { it.vehiculoId == datos.vehiculoId && it.estado == EstadoSolicitud.PENDIENTE }
                .map { it.id }
        documentos.enLote {
            documentos.ordenes.guardar(orden.id, orden)
            solicitudesAtendidas.forEach { idSolicitud ->
                documentos.solicitudes.obtener(idSolicitud)?.let { solicitud ->
                    documentos.solicitudes.guardar(
                        idSolicitud,
                        solicitud.copy(estado = EstadoSolicitud.ATENDIDA, ordenId = orden.id)
                    )
                }
            }
        }
        return Resultado.Exito(ResultadoIngreso(orden, cliente.tieneCuenta))
    }

    /** Actualiza los datos editables de una orden Ingresado o En reparación y deja constancia de quién la editó. */
    override suspend fun editarOrden(
        ordenId: String,
        falla: String,
        kilometraje: Int,
        combustible: NivelCombustible,
        observaciones: String,
        tecnicoId: String,
        usuarioId: String
    ): Resultado<Orden> {
        if (!esJefe(usuarioId)) return Resultado.Error(MENSAJE_SOLO_JEFE)
        val orden = documentos.ordenes.obtener(ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (!orden.enCurso) return Resultado.Error("Esta orden ya no se puede editar")
        if (falla.isBlank()) return Resultado.Error("Describe la falla reportada")
        if (kilometraje < 0) return Resultado.Error("Ingresa un kilometraje válido")
        val tecnico = usuarios.obtenerUsuario(tecnicoId)?.takeIf { it.rol == Rol.TECNICO && (it.activo || it.id == orden.tecnicoId) }
            ?: return Resultado.Error("Elige un técnico activo")
        val actualizada = orden.copy(
            falla = falla.trim(),
            kilometraje = kilometraje,
            combustible = combustible,
            observaciones = observaciones.trim(),
            tecnicoId = tecnico.id,
            editadaPor = usuarioId,
            editadaEn = reloj.ahora()
        )
        documentos.ordenes.guardar(ordenId, actualizada)
        return Resultado.Exito(actualizada)
    }

    /** Anula una orden Ingresado con su motivo y registra el cambio de estado. */
    override suspend fun anularOrden(ordenId: String, motivo: String, usuarioId: String): Resultado<Orden> {
        if (!esJefe(usuarioId)) return Resultado.Error(MENSAJE_SOLO_JEFE)
        val orden = documentos.ordenes.obtener(ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.estado != EstadoOrden.INGRESADO) return Resultado.Error("Solo se puede anular una orden en estado Ingresado")
        if (motivo.isBlank()) return Resultado.Error("Escribe el motivo de la anulación")
        val anulada = orden.copy(
            estado = EstadoOrden.ANULADA,
            motivoAnulacion = motivo.trim(),
            historial = orden.historial + CambioEstado(EstadoOrden.ANULADA, reloj.ahora(), usuarioId, motivo.trim())
        )
        documentos.ordenes.guardar(ordenId, anulada)
        return Resultado.Exito(anulada)
    }

    /** Guarda las fotos nuevas y las suma a una orden Ingresado respetando el máximo de 6. */
    override suspend fun agregarFotosOrden(ordenId: String, fotos: List<Foto>, usuarioId: String): Resultado<Orden> {
        if (!esJefe(usuarioId)) return Resultado.Error(MENSAJE_SOLO_JEFE)
        val orden = documentos.ordenes.obtener(ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.estado != EstadoOrden.INGRESADO) return Resultado.Error("Ya no se pueden agregar fotos a esta orden")
        if (orden.fotos.size + fotos.size > MAXIMO_FOTOS) return Resultado.Error("Máximo 6 fotos")
        val guardadas = when (val subida = this.fotos.subir(fotos)) {
            is Resultado.Error -> return subida
            is Resultado.Exito -> subida.valor
        }
        val actualizada = orden.copy(fotos = orden.fotos + guardadas)
        documentos.ordenes.guardar(ordenId, actualizada)
        return Resultado.Exito(actualizada)
    }

    /** Quita una foto de una orden Ingresado sin dejarla sin evidencia. */
    override suspend fun eliminarFotoOrden(ordenId: String, fotoId: String, usuarioId: String): Resultado<Orden> {
        if (!esJefe(usuarioId)) return Resultado.Error(MENSAJE_SOLO_JEFE)
        val orden = documentos.ordenes.obtener(ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.estado != EstadoOrden.INGRESADO) return Resultado.Error("Las fotos de esta orden están protegidas como evidencia")
        if (orden.fotos.size <= 1) return Resultado.Error("La orden debe tener al menos una foto")
        fotos.eliminar(orden.fotos.filter { it.id == fotoId })
        val actualizada = orden.copy(fotos = orden.fotos.filterNot { it.id == fotoId })
        documentos.ordenes.guardar(ordenId, actualizada)
        return Resultado.Exito(actualizada)
    }

    /** Cambia la orden a En reparación si pertenece al técnico y está Ingresado. */
    override suspend fun iniciarReparacion(ordenId: String, tecnicoId: String): Resultado<Orden> {
        val orden = documentos.ordenes.obtener(ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.tecnicoId != tecnicoId) return Resultado.Error(MENSAJE_NO_ASIGNADA)
        if (orden.estado != EstadoOrden.INGRESADO) return Resultado.Error("La reparación ya fue iniciada")
        val enReparacion = orden.copy(
            estado = EstadoOrden.EN_REPARACION,
            historial = orden.historial + CambioEstado(EstadoOrden.EN_REPARACION, reloj.ahora(), tecnicoId)
        )
        documentos.ordenes.guardar(ordenId, enReparacion)
        return Resultado.Exito(enReparacion)
    }

    /** Cambia la orden a Listo para recoger si tiene un trabajo realizado y registra el primer aviso. */
    override suspend fun marcarListo(ordenId: String, tecnicoId: String): Resultado<Orden> {
        val orden = documentos.ordenes.obtener(ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.tecnicoId != tecnicoId) return Resultado.Error(MENSAJE_NO_ASIGNADA)
        if (orden.estado != EstadoOrden.EN_REPARACION) return Resultado.Error("Primero inicia la reparación")
        val tieneTrabajo = documentos.avances.listar().any { it.ordenId == ordenId && it.tipo == TipoAvance.TRABAJO_REALIZADO }
        if (!tieneTrabajo) return Resultado.Error("Registra el trabajo realizado antes de marcar como listo")
        val ahora = reloj.ahora()
        val lista = orden.copy(
            estado = EstadoOrden.LISTO,
            historial = orden.historial + CambioEstado(EstadoOrden.LISTO, ahora, tecnicoId),
            avisosEnviados = 1,
            ultimoAviso = ahora
        )
        documentos.ordenes.guardar(ordenId, lista)
        return Resultado.Exito(lista)
    }

    /** Pasa la orden de Listo para recoger a Entregado y deja el cambio en el historial; no envía aviso. */
    override suspend fun marcarEntregada(ordenId: String, usuarioId: String): Resultado<Orden> {
        if (!esJefe(usuarioId)) return Resultado.Error(MENSAJE_SOLO_JEFE)
        val orden = documentos.ordenes.obtener(ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.estado != EstadoOrden.LISTO) return Resultado.Error("Solo se puede entregar una orden lista para recoger")
        val entregada = orden.copy(
            estado = EstadoOrden.ENTREGADO,
            historial = orden.historial + CambioEstado(EstadoOrden.ENTREGADO, reloj.ahora(), usuarioId)
        )
        documentos.ordenes.guardar(ordenId, entregada)
        return Resultado.Exito(entregada)
    }

    /** Registra un nuevo aviso de vehículo listo si pasaron al menos 24 horas del último. */
    override suspend fun reenviarAviso(ordenId: String, usuarioId: String): Resultado<Orden> {
        if (!esJefe(usuarioId)) return Resultado.Error(MENSAJE_SOLO_JEFE)
        val orden = documentos.ordenes.obtener(ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.estado != EstadoOrden.LISTO) return Resultado.Error("Solo se puede reenviar el aviso de una orden lista")
        val ahora = reloj.ahora()
        val faltan = orden.horasParaReenviar(ahora)
        if (faltan > 0) return Resultado.Error("Disponible en $faltan h")
        val reenviada = orden.copy(avisosEnviados = orden.avisosEnviados + 1, ultimoAviso = ahora)
        documentos.ordenes.guardar(ordenId, reenviada)
        return Resultado.Exito(reenviada)
    }
}
