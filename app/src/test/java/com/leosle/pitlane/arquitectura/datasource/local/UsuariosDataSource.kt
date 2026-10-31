package com.leosle.pitlane.arquitectura.datasource.local

import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.util.Textos
import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Cuentas y sesión en memoria, con las funciones de cuenta que en la nube ejecuta el servidor. */
class UsuariosDataSource(private val bd: BaseDatosLocal) : IUsuariosDataSource {

    override val usuarioActual: StateFlow<Usuario?> = bd.usuarioActual

    override suspend fun restaurarSesion(): Usuario? = bd.usuarioActual.value

    override fun observarUsuarios(): Flow<List<Usuario>> = bd.usuarios

    override suspend fun listarUsuarios(): List<Usuario> = bd.usuarios.value

    override suspend fun obtenerUsuario(id: String): Usuario? = bd.usuarios.value.firstOrNull { it.id == id }

    /** Reemplaza al usuario con el mismo identificador o lo agrega; si es el usuario activo, actualiza la sesión. */
    override suspend fun guardarUsuario(usuario: Usuario) {
        val existe = bd.usuarios.value.any { it.id == usuario.id }
        bd.usuarios.update { lista -> if (existe) lista.map { if (it.id == usuario.id) usuario else it } else lista + usuario }
        if (bd.usuarioActual.value?.id == usuario.id) bd.usuarioActual.value = usuario
    }

    override suspend fun eliminarUsuario(id: String) {
        bd.usuarios.update { lista -> lista.filterNot { it.id == id } }
    }

    override fun nuevoId(prefijo: String): String = bd.nuevoId(prefijo)

    /** Busca una cuenta con ese correo y contraseña; rechaza las cuentas desactivadas. */
    override suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Usuario> {
        val usuario = bd.usuarios.value.firstOrNull {
            it.tieneCuenta && it.correo.equals(correo.trim(), ignoreCase = true) && it.contrasena == contrasena
        } ?: return Resultado.Error("Correo o contraseña incorrectos")
        if (!usuario.activo) return Resultado.Error("Tu cuenta está desactivada. Comunícate con el taller")
        bd.usuarioActual.value = usuario
        return Resultado.Exito(usuario)
    }

    override fun cerrarSesion() {
        bd.usuarioActual.value = null
    }

    /** Último código "enviado"; en memoria no hay correo, así que las pruebas lo leen de aquí. */
    var ultimoCodigoEnviado: String? = null
        private set
    private val codigos = HashMap<String, String>()

    /** Genera el código solo si la cuenta existe, pero responde igual en ambos casos. */
    override suspend fun solicitarCodigoDeRestablecimiento(correo: String): Resultado<Unit> {
        val clave = correo.trim().lowercase()
        if (bd.usuarios.value.any { it.tieneCuenta && it.correo.equals(clave, ignoreCase = true) }) {
            val codigo = (0..5).map { (0..9).random() }.joinToString("")
            codigos[clave] = codigo
            ultimoCodigoEnviado = codigo
        }
        return Resultado.Exito(Unit)
    }

    override suspend fun restablecerContrasena(correo: String, codigo: String, contrasena: String): Resultado<Unit> {
        val clave = correo.trim().lowercase()
        if (codigos[clave] != codigo.trim()) return Resultado.Error("El código no es correcto")
        val usuario = bd.usuarios.value.first { it.correo.equals(clave, ignoreCase = true) }
        guardarUsuario(usuario.copy(contrasena = contrasena))
        codigos.remove(clave)
        return Resultado.Exito(Unit)
    }

    /**
     * Crea la cuenta del cliente. Si el Jefe ya lo había registrado con el mismo DNI y sin cuenta, completa
     * ese registro para conservar sus vehículos e historial; en otro caso crea uno nuevo.
     */
    override suspend fun registrarCuentaCliente(datos: DatosRegistroCliente): Resultado<Usuario> {
        val correo = datos.correo.trim()
        val usuarios = bd.usuarios.value
        val previo = usuarios.firstOrNull { it.dni == datos.dni }
        if (previo != null && (previo.tieneCuenta || previo.rol != Rol.CLIENTE)) {
            return Resultado.Error("Ya existe un usuario con ese DNI")
        }
        if (usuarios.any { it.id != previo?.id && it.correo.isNotBlank() && it.correo.equals(correo, ignoreCase = true) }) {
            return Resultado.Error("Este correo ya está registrado")
        }
        val cliente = Usuario(
            id = previo?.id ?: bd.nuevoId("u-cli"),
            nombres = datos.nombres.trim(),
            apellidos = datos.apellidos.trim(),
            dni = datos.dni,
            telefono = datos.telefono,
            correo = correo,
            contrasena = datos.contrasena,
            rol = Rol.CLIENTE,
            activo = true,
            tieneCuenta = true
        )
        guardarUsuario(cliente)
        bd.usuarioActual.value = cliente
        return Resultado.Exito(cliente)
    }

    /** Crea el técnico sin tocar la sesión del Jefe; rechaza DNI o correo repetidos. */
    override suspend fun crearCuentaTecnico(
        nombres: String,
        apellidos: String,
        dni: String,
        telefono: String,
        correo: String,
        contrasena: String
    ): Resultado<Usuario> {
        val usuarios = bd.usuarios.value
        if (usuarios.any { it.dni == dni }) return Resultado.Error("Ya existe un usuario con ese DNI")
        if (usuarios.any { it.correo.isNotBlank() && it.correo.equals(correo.trim(), ignoreCase = true) }) {
            return Resultado.Error("Ya existe un usuario con ese correo")
        }
        val tecnico = Usuario(
            id = bd.nuevoId("u-tec"),
            nombres = nombres.trim(),
            apellidos = apellidos.trim(),
            dni = dni,
            telefono = telefono,
            correo = correo.trim(),
            contrasena = contrasena,
            rol = Rol.TECNICO
        )
        guardarUsuario(tecnico)
        return Resultado.Exito(tecnico)
    }

    override suspend fun desactivarCuenta(usuarioId: String, contrasena: String?): Resultado<Unit> {
        val usuario = obtenerUsuario(usuarioId) ?: return Resultado.Error("No se encontró el usuario")
        if (contrasena != null) {
            if (bd.ordenes.value.any { it.clienteId == usuarioId && it.enTaller }) {
                return Resultado.Error("Tienes un vehículo en el taller. Podrás eliminar tu cuenta cuando se entregue")
            }
            if (usuario.contrasena != contrasena) return Resultado.Error("Contraseña incorrecta")
        }
        if (usuario.rol == Rol.TECNICO && bd.ordenes.value.any { it.tecnicoId == usuarioId && it.enCurso }) {
            return Resultado.Error("Reasigna sus órdenes en curso antes de desactivarlo")
        }
        guardarUsuario(usuario.copy(activo = false))
        if (contrasena != null) bd.usuarioActual.value = null
        return Resultado.Exito(Unit)
    }

    override suspend fun reactivarCuenta(usuarioId: String): Resultado<Unit> {
        val usuario = obtenerUsuario(usuarioId) ?: return Resultado.Error("No se encontró el usuario")
        guardarUsuario(usuario.copy(activo = true))
        return Resultado.Exito(Unit)
    }
}
