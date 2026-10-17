package com.leosle.pitlane.viewmodel

import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.AvanceDetallado
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.repositorio.IAvanceRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow

/** Hallazgos y trabajos realizados de una orden. */
class AvanceViewModel(
    private val repositorio: IAvanceRepository = Dependencias.repositorioAvance
) : ViewModelBase() {

    /** Avances de la orden en orden cronológico. */
    fun avances(ordenId: String, usuarioId: String): StateFlow<List<AvanceDetallado>> =
        estadoPorClave("avances:$ordenId:$usuarioId", emptyList()) { repositorio.observarAvances(ordenId, usuarioId) }

    /** Registra un avance en la orden. */
    fun registrarAvance(
        ordenId: String,
        tecnicoId: String,
        tipo: TipoAvance,
        descripcion: String,
        fotos: List<Foto>,
        alTerminar: (Resultado<Avance>) -> Unit
    ) = ejecutar({ repositorio.registrarAvance(ordenId, tecnicoId, tipo, descripcion, fotos) }, alTerminar)

    /** Guarda los cambios de un avance propio. */
    fun editarAvance(
        avanceId: String,
        tecnicoId: String,
        tipo: TipoAvance,
        descripcion: String,
        fotos: List<Foto>,
        alTerminar: (Resultado<Avance>) -> Unit
    ) = ejecutar({ repositorio.editarAvance(avanceId, tecnicoId, tipo, descripcion, fotos) }, alTerminar)

    /** Elimina un avance propio. */
    fun eliminarAvance(avanceId: String, tecnicoId: String, alTerminar: (Resultado<Unit>) -> Unit) =
        ejecutar({ repositorio.eliminarAvance(avanceId, tecnicoId) }, alTerminar)
}
