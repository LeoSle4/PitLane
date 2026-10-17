package com.leosle.pitlane.repositorio

import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow

/** Consulta y actualización de los datos personales del usuario con sesión. */
interface IPerfilRepository {

    /** Emite los datos del usuario cada vez que cambian. */
    fun observarPerfil(usuarioId: String): Flow<Usuario?>

    /** Cambia nombres, apellidos y teléfono del usuario. */
    suspend fun actualizarPerfil(usuarioId: String, nombres: String, apellidos: String, telefono: String): Resultado<Usuario>

    /** Desactiva la cuenta si la contraseña es correcta y el cliente no tiene vehículos en el taller. */
    suspend fun eliminarCuenta(usuarioId: String, contrasena: String): Resultado<Unit>
}
