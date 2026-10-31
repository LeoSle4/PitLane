package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.modelo.AvanceDetallado
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.SolicitudDetallada
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.Vehiculo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Usuario liviano armado con la instantánea de una orden o solicitud; solo trae lo que la instantánea guarda. */
private fun usuarioLiviano(id: String, nombres: String, apellidos: String, rol: Rol, dni: String = "", telefono: String = "") =
    Usuario(id, nombres, apellidos, dni, telefono, correo = "", contrasena = "", rol = rol)

/** Verdadero si la orden guarda la instantánea de cliente, vehículo y técnico. */
private fun Orden.tieneInstantanea() = cliente != null && vehiculo != null && tecnico != null

/** Arma la orden detallada solo con la instantánea; la solicitud queda nula y los autores salen de las firmas del historial. */
private fun ordenDesdeInstantanea(orden: Orden): OrdenDetallada {
    val cliente = orden.cliente!!
    val vehiculo = orden.vehiculo!!
    val tecnico = orden.tecnico!!
    fun autor(usuarioId: String, nombre: String?): Usuario {
        val rol = if (usuarioId == orden.clienteId) Rol.CLIENTE else if (usuarioId == orden.tecnicoId) Rol.TECNICO else Rol.JEFE
        return usuarioLiviano(usuarioId, nombre.orEmpty(), "", rol)
    }
    return OrdenDetallada(
        orden = orden,
        cliente = usuarioLiviano(orden.clienteId, cliente.nombres, cliente.apellidos, Rol.CLIENTE, cliente.dni, cliente.telefono),
        vehiculo = Vehiculo(orden.vehiculoId, orden.clienteId, vehiculo.placa, vehiculo.marca, vehiculo.modelo, vehiculo.anio, vehiculo.color),
        tecnico = usuarioLiviano(orden.tecnicoId, tecnico.nombres, tecnico.apellidos, Rol.TECNICO),
        solicitud = null,
        conformidadRegistradaPor = orden.conformidad?.let { autor(it.usuarioId, it.usuarioNombre) },
        entregadaPor = orden.historial.lastOrNull { it.estado == EstadoOrden.ENTREGADO }?.let { autor(it.usuarioId, it.usuarioNombre) }
    )
}

/** Arma la solicitud detallada solo con la instantánea de cliente y vehículo. */
private fun solicitudDesdeInstantanea(solicitud: Solicitud): SolicitudDetallada {
    val cliente = solicitud.cliente!!
    val vehiculo = solicitud.vehiculo!!
    return SolicitudDetallada(
        solicitud = solicitud,
        cliente = usuarioLiviano(solicitud.clienteId, cliente.nombres, cliente.apellidos, Rol.CLIENTE, cliente.dni, cliente.telefono),
        vehiculo = Vehiculo(solicitud.vehiculoId, solicitud.clienteId, vehiculo.placa, vehiculo.marca, vehiculo.modelo, vehiculo.anio, vehiculo.color)
    )
}

/**
 * Emite cada orden con sus datos relacionados. Si todas las órdenes traen la instantánea se arma solo con ellas, sin
 * escuchar `usuarios`, `vehiculos` ni `solicitudes`; si alguna es anterior al relleno se cruzan las colecciones completas.
 * La solicitud de origen no viene en las listas: el detalle la obtiene con [ordenDetallada].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun ordenesDetalladas(usuarios: IUsuariosDataSource, documentos: IDocumentosDataSource): Flow<List<OrdenDetallada>> =
    documentos.ordenes.observarTodos().flatMapLatest { lista ->
        if (lista.all { it.tieneInstantanea() }) flowOf(lista.map(::ordenDesdeInstantanea))
        else ordenesCruzandoColecciones(lista, usuarios, documentos)
    }

/** Cruce completo de órdenes con usuarios, vehículos y solicitudes, para documentos anteriores a la instantánea. */
private fun ordenesCruzandoColecciones(
    listaOrdenes: List<Orden>,
    usuarios: IUsuariosDataSource,
    documentos: IDocumentosDataSource
): Flow<List<OrdenDetallada>> =
    combine(
        usuarios.observarUsuarios(),
        documentos.vehiculos.observarTodos(),
        documentos.solicitudes.observarTodos()
    ) { listaUsuarios, listaVehiculos, listaSolicitudes ->
        listaOrdenes.map { orden ->
            OrdenDetallada(
                orden = orden,
                cliente = listaUsuarios.firstOrNull { it.id == orden.clienteId },
                vehiculo = listaVehiculos.firstOrNull { it.id == orden.vehiculoId },
                tecnico = listaUsuarios.firstOrNull { it.id == orden.tecnicoId },
                solicitud = listaSolicitudes.firstOrNull { it.id == orden.solicitudId },
                conformidadRegistradaPor = orden.conformidad?.let { conformidad -> listaUsuarios.firstOrNull { it.id == conformidad.usuarioId } },
                entregadaPor = orden.historial.lastOrNull { it.estado == EstadoOrden.ENTREGADO }
                    ?.let { cambio -> listaUsuarios.firstOrNull { it.id == cambio.usuarioId } }
            )
        }
    }

/**
 * Detalle de una orden: escucha ese único documento de la colección de órdenes y resuelve cliente, vehículo, técnico, solicitud y autores
 * con un `get` de un solo documento cada uno, sin escuchar las demás colecciones completas.
 */
internal fun ordenDetallada(ordenId: String, usuarios: IUsuariosDataSource, documentos: IDocumentosDataSource): Flow<OrdenDetallada?> =
    documentos.ordenes.observar(ordenId)
        .distinctUntilChanged()
        .map { orden ->
            orden?.let {
                OrdenDetallada(
                    orden = it,
                    cliente = usuarios.obtenerUsuario(it.clienteId),
                    vehiculo = documentos.vehiculos.obtener(it.vehiculoId),
                    tecnico = usuarios.obtenerUsuario(it.tecnicoId),
                    solicitud = it.solicitudId?.let { id -> documentos.solicitudes.obtener(id) },
                    conformidadRegistradaPor = it.conformidad?.let { c -> usuarios.obtenerUsuario(c.usuarioId) },
                    entregadaPor = it.historial.lastOrNull { c -> c.estado == EstadoOrden.ENTREGADO }
                        ?.let { c -> usuarios.obtenerUsuario(c.usuarioId) }
                )
            }
        }

/**
 * Emite cada solicitud con su cliente y vehículo. Con la instantánea en todas las solicitudes no escucha `usuarios` ni
 * `vehiculos`; si alguna es anterior al relleno cruza las colecciones completas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun solicitudesDetalladas(usuarios: IUsuariosDataSource, documentos: IDocumentosDataSource): Flow<List<SolicitudDetallada>> =
    documentos.solicitudes.observarTodos().flatMapLatest { lista ->
        if (lista.all { it.cliente != null && it.vehiculo != null }) flowOf(lista.map(::solicitudDesdeInstantanea))
        else combine(usuarios.observarUsuarios(), documentos.vehiculos.observarTodos()) { listaUsuarios, listaVehiculos ->
            lista.map { solicitud ->
                SolicitudDetallada(
                    solicitud = solicitud,
                    cliente = listaUsuarios.firstOrNull { it.id == solicitud.clienteId },
                    vehiculo = listaVehiculos.firstOrNull { it.id == solicitud.vehiculoId }
                )
            }
        }
    }

/** Combina avances y usuarios para emitir cada avance con el técnico que lo registró. */
internal fun avancesDetallados(usuarios: IUsuariosDataSource, documentos: IDocumentosDataSource): Flow<List<AvanceDetallado>> =
    combine(documentos.avances.observarTodos(), usuarios.observarUsuarios()) { listaAvances, listaUsuarios ->
        listaAvances.map { avance ->
            AvanceDetallado(avance, listaUsuarios.firstOrNull { it.id == avance.tecnicoId })
        }
    }
