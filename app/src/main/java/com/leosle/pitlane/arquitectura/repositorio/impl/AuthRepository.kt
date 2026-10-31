package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.repositorio.IAuthRepository
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.util.Textos
import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow

/** Sesión y registro de cuentas sobre la fuente de usuarios. */
class AuthRepository(private val usuarios: IUsuariosDataSource) : IAuthRepository {

    override val usuarioActual: StateFlow<Usuario?> = usuarios.usuarioActual

    override suspend fun restaurarSesion(): Usuario? = usuarios.restaurarSesion()

    override suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Usuario> =
        usuarios.iniciarSesion(correo, contrasena)

    override suspend fun registrarCliente(datos: DatosRegistroCliente): Resultado<Usuario> =
        usuarios.registrarCuentaCliente(
            datos.copy(nombres = Textos.nombrePropio(datos.nombres), apellidos = Textos.nombrePropio(datos.apellidos))
        )

    override suspend fun solicitarCodigoDeRestablecimiento(correo: String): Resultado<Unit> =
        usuarios.solicitarCodigoDeRestablecimiento(correo)

    override suspend fun restablecerContrasena(correo: String, codigo: String, contrasena: String): Resultado<Unit> =
        usuarios.restablecerContrasena(correo, codigo, contrasena)

    override fun cerrarSesion() = usuarios.cerrarSesion()
}
