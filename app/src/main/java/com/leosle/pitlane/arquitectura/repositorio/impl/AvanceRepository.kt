package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.repositorio.IAvanceRepository
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.IFotoStorage
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.AvanceDetallado
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.util.Reloj
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Hallazgos y trabajos realizados sobre las fuentes de documentos, usuarios y fotos. */
class AvanceRepository(
    private val usuarios: IUsuariosDataSource,
    private val documentos: IDocumentosDataSource,
    private val fotos: IFotoStorage,
    private val reloj: Reloj
) : IAvanceRepository {

    private companion object {
        const val MAXIMO_FOTOS = 4
        const val MINIMO_DESCRIPCION = 10
    }

    /** Emite los avances de la orden del más antiguo al más reciente; un cliente solo recibe los de sus propias órdenes (RN-08). */
    override fun observarAvances(ordenId: String, usuarioId: String): Flow<List<AvanceDetallado>> =
        avancesDetallados(usuarios, documentos).map { lista ->
            val usuario = usuarios.obtenerUsuario(usuarioId)
            val ajena = usuario?.rol == Rol.CLIENTE && documentos.ordenes.obtener(ordenId)?.clienteId != usuario.id
            if (usuario == null || ajena) emptyList() else lista.filter { it.avance.ordenId == ordenId }.sortedBy { it.avance.fechaHora }
        }

    /** Valida estado de la orden, técnico, descripción y fotos; guarda el avance con la hora actual. */
    override suspend fun registrarAvance(
        ordenId: String,
        tecnicoId: String,
        tipo: TipoAvance,
        descripcion: String,
        fotos: List<Foto>
    ): Resultado<Avance> {
        val orden = documentos.ordenes.obtener(ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.tecnicoId != tecnicoId) return Resultado.Error("Esta orden no está asignada a ti")
        estadoNoPermiteAvances(orden)?.let { return Resultado.Error(it) }
        validar(descripcion, fotos)?.let { return Resultado.Error(it) }
        val guardadas = when (val subida = this.fotos.subir(orden.clienteId, fotos)) {
            is Resultado.Error -> return subida
            is Resultado.Exito -> subida.valor
        }
        val avance = Avance(documentos.avances.nuevoId(), ordenId, tecnicoId, tipo, descripcion.trim(), guardadas, reloj.ahora())
        documentos.avances.guardar(avance.id, avance)
        return Resultado.Exito(avance)
    }

    /** Actualiza un avance propio mientras la orden siga En reparación y elimina las fotos que ya no se usan. */
    override suspend fun editarAvance(
        avanceId: String,
        tecnicoId: String,
        tipo: TipoAvance,
        descripcion: String,
        fotos: List<Foto>
    ): Resultado<Avance> {
        val avance = documentos.avances.obtener(avanceId) ?: return Resultado.Error("No se encontró el avance")
        if (avance.tecnicoId != tecnicoId) return Resultado.Error("Solo puedes editar tus propios avances")
        val orden = documentos.ordenes.obtener(avance.ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.estado != EstadoOrden.EN_REPARACION) return Resultado.Error("La orden ya no admite cambios en sus avances")
        validar(descripcion, fotos)?.let { return Resultado.Error(it) }
        val guardadas = when (val subida = this.fotos.subir(orden.clienteId, fotos)) {
            is Resultado.Error -> return subida
            is Resultado.Exito -> subida.valor
        }
        val idsNuevos = guardadas.map { it.id }.toSet()
        this.fotos.eliminar(avance.fotos.filter { it.id !in idsNuevos })
        val actualizado = avance.copy(tipo = tipo, descripcion = descripcion.trim(), fotos = guardadas)
        documentos.avances.guardar(avanceId, actualizado)
        return Resultado.Exito(actualizado)
    }

    /** Elimina un avance propio con sus fotos mientras la orden siga En reparación. */
    override suspend fun eliminarAvance(avanceId: String, tecnicoId: String): Resultado<Unit> {
        val avance = documentos.avances.obtener(avanceId) ?: return Resultado.Error("No se encontró el avance")
        if (avance.tecnicoId != tecnicoId) return Resultado.Error("Solo puedes eliminar tus propios avances")
        val orden = documentos.ordenes.obtener(avance.ordenId) ?: return Resultado.Error("No se encontró la orden")
        if (orden.estado != EstadoOrden.EN_REPARACION) return Resultado.Error("La orden ya no admite cambios en sus avances")
        fotos.eliminar(avance.fotos)
        documentos.avances.eliminar(avanceId)
        return Resultado.Exito(Unit)
    }

    /** Devuelve el motivo por el que la orden no acepta avances, o nulo si está En reparación. */
    private fun estadoNoPermiteAvances(orden: Orden): String? = when (orden.estado) {
        EstadoOrden.EN_REPARACION -> null
        EstadoOrden.INGRESADO -> "Primero inicia la reparación"
        else -> "Esta orden ya no admite avances"
    }

    /** Comprueba la descripción mínima y el máximo de fotos; devuelve el primer error. */
    private fun validar(descripcion: String, fotos: List<Foto>): String? = when {
        descripcion.trim().length < MINIMO_DESCRIPCION -> "La descripción debe tener al menos 10 caracteres"
        fotos.size > MAXIMO_FOTOS -> "Máximo 4 fotos por avance"
        else -> null
    }
}
