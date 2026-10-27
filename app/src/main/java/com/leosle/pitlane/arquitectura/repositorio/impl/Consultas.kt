package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.modelo.AvanceDetallado
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.modelo.SolicitudDetallada
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Combina órdenes, usuarios, vehículos y solicitudes para emitir cada orden con sus datos relacionados. */
internal fun ordenesDetalladas(usuarios: IUsuariosDataSource, documentos: IDocumentosDataSource): Flow<List<OrdenDetallada>> =
    combine(
        documentos.ordenes.observarTodos(),
        usuarios.observarUsuarios(),
        documentos.vehiculos.observarTodos(),
        documentos.solicitudes.observarTodos()
    ) { listaOrdenes, listaUsuarios, listaVehiculos, listaSolicitudes ->
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

/** Combina solicitudes, usuarios y vehículos para emitir cada solicitud con su cliente y vehículo. */
internal fun solicitudesDetalladas(usuarios: IUsuariosDataSource, documentos: IDocumentosDataSource): Flow<List<SolicitudDetallada>> =
    combine(
        documentos.solicitudes.observarTodos(),
        usuarios.observarUsuarios(),
        documentos.vehiculos.observarTodos()
    ) { listaSolicitudes, listaUsuarios, listaVehiculos ->
        listaSolicitudes.map { solicitud ->
            SolicitudDetallada(
                solicitud = solicitud,
                cliente = listaUsuarios.firstOrNull { it.id == solicitud.clienteId },
                vehiculo = listaVehiculos.firstOrNull { it.id == solicitud.vehiculoId }
            )
        }
    }

/** Combina avances y usuarios para emitir cada avance con el técnico que lo registró. */
internal fun avancesDetallados(usuarios: IUsuariosDataSource, documentos: IDocumentosDataSource): Flow<List<AvanceDetallado>> =
    combine(documentos.avances.observarTodos(), usuarios.observarUsuarios()) { listaAvances, listaUsuarios ->
        listaAvances.map { avance ->
            AvanceDetallado(avance, listaUsuarios.firstOrNull { it.id == avance.tecnicoId })
        }
    }
