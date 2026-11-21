package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.AvanceDetallado
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow

/** Hallazgos y trabajos realizados que el técnico documenta en una orden. */
interface IAvanceRepository {

    /** Emite los avances de una orden en orden cronológico. */
    fun observarAvances(ordenId: String, usuarioId: String): Flow<List<AvanceDetallado>>

    /** Registra un avance en una orden En reparación asignada al técnico. */
    suspend fun registrarAvance(
        ordenId: String,
        tecnicoId: String,
        tipo: TipoAvance,
        descripcion: String,
        fotos: List<Foto>
    ): Resultado<Avance>

    /** Edita un avance propio de la orden [ordenId] mientras siga En reparación. */
    suspend fun editarAvance(
        ordenId: String,
        avanceId: String,
        tecnicoId: String,
        tipo: TipoAvance,
        descripcion: String,
        fotos: List<Foto>
    ): Resultado<Avance>

    /** Elimina un avance propio de la orden [ordenId], con sus fotos, mientras siga En reparación. */
    suspend fun eliminarAvance(ordenId: String, avanceId: String, tecnicoId: String): Resultado<Unit>
}
