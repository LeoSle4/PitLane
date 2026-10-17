package com.leosle.pitlane.repositorio

import com.leosle.pitlane.modelo.TecnicoConCarga
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow

/** Gestión de las cuentas de técnicos por parte del Jefe de Taller. */
interface ITecnicoRepository {

    /** Emite todos los técnicos, activos e inactivos, con su cantidad de órdenes en curso. */
    fun observarTecnicos(): Flow<List<TecnicoConCarga>>

    /** Crea la cuenta de un técnico con estado activo. */
    suspend fun registrarTecnico(
        nombres: String,
        apellidos: String,
        dni: String,
        telefono: String,
        correo: String,
        contrasena: String
    ): Resultado<Usuario>

    /** Cambia nombres, apellidos y teléfono del técnico. */
    suspend fun editarTecnico(tecnicoId: String, nombres: String, apellidos: String, telefono: String): Resultado<Usuario>

    /** Desactiva al técnico si no tiene órdenes en curso. */
    suspend fun desactivarTecnico(tecnicoId: String): Resultado<Unit>

    /** Vuelve a activar a un técnico inactivo. */
    suspend fun reactivarTecnico(tecnicoId: String): Resultado<Unit>
}
