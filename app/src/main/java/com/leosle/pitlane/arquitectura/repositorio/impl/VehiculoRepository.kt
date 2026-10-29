package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.repositorio.IVehiculoRepository
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.util.Textos
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.modelo.VehiculoConEstado
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.util.Validador
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Gestión de vehículos sobre las fuentes de documentos y de usuarios. */
class VehiculoRepository(
    private val usuarios: IUsuariosDataSource,
    private val documentos: IDocumentosDataSource
) : IVehiculoRepository {

    /** Emite los vehículos visibles del cliente junto con su orden más reciente que no esté anulada ni entregada. */
    override fun observarVehiculosDeCliente(clienteId: String): Flow<List<VehiculoConEstado>> =
        combine(documentos.vehiculos.observarTodos(), documentos.ordenes.observarTodos(), usuarios.usuarioActual) { listaVehiculos, listaOrdenes, actor ->
            listaVehiculos.filter { it.clienteId == clienteId && it.activo && actor.esJefeOPropio(clienteId) }.map { vehiculo ->
                val activa = listaOrdenes
                    .filter { it.vehiculoId == vehiculo.id && it.estado != EstadoOrden.ANULADA && it.estado != EstadoOrden.ENTREGADO }
                    .maxByOrNull { it.creadaEn }
                VehiculoConEstado(vehiculo, activa)
            }
        }

    /** Emite el vehículo con ese identificador cada vez que cambia. */
    override fun observarVehiculo(vehiculoId: String): Flow<Vehiculo?> =
        combine(documentos.vehiculos.observarTodos(), usuarios.usuarioActual) { lista, actor ->
            lista.firstOrNull { it.id == vehiculoId }?.takeIf { actor.esJefeOPropio(it.clienteId) }
        }.distinctUntilChanged()

    /** Guarda el vehículo con la placa normalizada; rechaza placas repetidas con el mensaje según quién registra. */
    override suspend fun registrarVehiculo(
        clienteId: String,
        placa: String,
        marca: String,
        modelo: String,
        anio: Int,
        color: String,
        registradoPorJefe: Boolean
    ): Resultado<Vehiculo> {
        val placaNormal = Validador.normalizarPlaca(placa)
        val existente = documentos.buscarPlaca(placaNormal)
        if (existente != null) {
            if (!registradoPorJefe || existente.clienteId == null) {
                return Resultado.Error("Esta placa ya está registrada. Si es tuya, comunícate con el taller")
            }
            val dueno = usuarios.obtenerUsuario(existente.clienteId)?.nombreCompleto.orEmpty()
            return Resultado.Error("Esta placa ya está registrada a nombre de $dueno", existente.clienteId)
        }
        val vehiculo = Vehiculo(
            documentos.vehiculos.nuevoId(), clienteId, placaNormal, Textos.nombrePropio(marca), Textos.conInicialesMayusculas(modelo), anio, Textos.nombrePropio(color)
        )
        documentos.vehiculos.guardar(vehiculo.id, vehiculo)
        return Resultado.Exito(vehiculo)
    }

    /** Cambia marca, modelo, año y color; conserva placa y dueño. */
    override suspend fun editarVehiculo(
        vehiculoId: String,
        marca: String,
        modelo: String,
        anio: Int,
        color: String
    ): Resultado<Vehiculo> {
        val vehiculo = documentos.vehiculos.obtener(vehiculoId) ?: return Resultado.Error("No se encontró el vehículo")
        val actualizado = vehiculo.copy(marca = Textos.nombrePropio(marca), modelo = Textos.conInicialesMayusculas(modelo), anio = anio, color = Textos.nombrePropio(color))
        documentos.vehiculos.guardar(vehiculoId, actualizado)
        return Resultado.Exito(actualizado)
    }

    /**
     * Elimina el vehículo aplicando las reglas de cada rol: nunca mientras esté en el taller; el Jefe no puede
     * borrar uno con historial y el cliente solo lo oculta cuando tiene historial.
     */
    override suspend fun eliminarVehiculo(vehiculoId: String, solicitadoPorJefe: Boolean): Resultado<Unit> {
        val vehiculo = documentos.vehiculos.obtener(vehiculoId) ?: return Resultado.Error("No se encontró el vehículo")
        val ordenes = documentos.ordenes.listar().filter { it.vehiculoId == vehiculoId }
        if (solicitadoPorJefe) {
            if (ordenes.isNotEmpty()) return Resultado.Error("No se puede eliminar un vehículo con historial de órdenes")
        } else if (ordenes.any { it.enTaller }) {
            return Resultado.Error("No puedes eliminar un vehículo que está en el taller")
        }
        documentos.enLote {
            documentos.solicitudes.listar()
                .filter { it.vehiculoId == vehiculoId && it.estado == EstadoSolicitud.PENDIENTE }
                .forEach { documentos.solicitudes.guardar(it.id, it.copy(estado = EstadoSolicitud.CANCELADA)) }
            if (ordenes.isEmpty()) {
                documentos.vehiculos.eliminar(vehiculo.id)
            } else {
                documentos.vehiculos.guardar(vehiculo.id, vehiculo.copy(activo = false))
            }
        }
        return Resultado.Exito(Unit)
    }
}
