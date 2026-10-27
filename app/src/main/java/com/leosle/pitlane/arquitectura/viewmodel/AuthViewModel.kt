package com.leosle.pitlane.arquitectura.viewmodel

import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.arquitectura.repositorio.IAuthRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow

/** Estado de la sesión y operaciones de acceso: ingresar, registrarse, recuperar contraseña y salir. */
class AuthViewModel(
    private val repositorio: IAuthRepository = Dependencias.repositorioAuth
) : ViewModelBase() {

    /** Usuario con la sesión abierta, o nulo si no hay sesión. */
    val usuarioActual: StateFlow<Usuario?> = repositorio.usuarioActual

    /** Abre la sesión con correo y contraseña. */
    fun iniciarSesion(correo: String, contrasena: String, alTerminar: (Resultado<Usuario>) -> Unit) =
        ejecutar({ repositorio.iniciarSesion(correo, contrasena) }, alTerminar)

    /** Crea la cuenta del cliente y abre su sesión. */
    fun registrarCliente(datos: DatosRegistroCliente, alTerminar: (Resultado<Usuario>) -> Unit) =
        ejecutar({ repositorio.registrarCliente(datos) }, alTerminar)

    /** Solicita el enlace de restablecimiento de contraseña. */
    fun recuperarContrasena(correo: String, alTerminar: (Resultado<Unit>) -> Unit) =
        ejecutar({ repositorio.recuperarContrasena(correo) }, alTerminar)

    /** Cierra la sesión actual. */
    fun cerrarSesion() = repositorio.cerrarSesion()
}
