package com.leosle.pitlane.repositorio

import kotlinx.coroutines.flow.combine
import com.leosle.pitlane.fuente.IUsuariosDataSource
import com.leosle.pitlane.util.Textos
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Datos personales del usuario sobre la fuente de usuarios. */
class PerfilRepository(private val usuarios: IUsuariosDataSource) : IPerfilRepository {

    /** Emite el usuario con ese identificador cada vez que cambia. */
    override fun observarPerfil(usuarioId: String): Flow<Usuario?> =
        combine(usuarios.observarUsuarios(), usuarios.usuarioActual) { lista, actor ->
            lista.firstOrNull { it.id == usuarioId }?.takeIf { actor.esJefeOPropio(usuarioId) }
        }.distinctUntilChanged()

    /** Guarda los nuevos nombres, apellidos y teléfono manteniendo DNI y correo. */
    override suspend fun actualizarPerfil(
        usuarioId: String,
        nombres: String,
        apellidos: String,
        telefono: String
    ): Resultado<Usuario> {
        val usuario = usuarios.obtenerUsuario(usuarioId) ?: return Resultado.Error("No se encontró el usuario")
        val actualizado = usuario.copy(nombres = Textos.nombrePropio(nombres), apellidos = Textos.nombrePropio(apellidos), telefono = telefono)
        usuarios.guardarUsuario(actualizado)
        return Resultado.Exito(actualizado)
    }

    /** Pide a la fuente desactivar la cuenta con la contraseña de confirmación. */
    override suspend fun eliminarCuenta(usuarioId: String, contrasena: String): Resultado<Unit> =
        usuarios.desactivarCuenta(usuarioId, contrasena)
}
