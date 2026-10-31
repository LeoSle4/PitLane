package com.leosle.pitlane.arquitectura.datasource.firebase

import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val USUARIOS = "usuarios"
private const val ETIQUETA = "UsuariosDataSource"

/** Tiempo máximo que se espera el perfil de una sesión guardada antes de pedir que se inicie de nuevo. */
private const val TIEMPO_RESTAURAR_SESION_MS = 5_000L

/** Reclamo de la sesión con el identificador del documento del usuario; lo escriben las funciones del servidor. */
private const val RECLAMO_USUARIO_ID = "usuarioId"

/**
 * Cuentas en Firebase Authentication y perfiles en la colección `usuarios` de Firestore. Crear técnicos,
 * registrar clientes y activar o desactivar cuentas lo hacen las funciones del servidor, porque el cliente
 * no puede crear cuentas sin cerrar la sesión del Jefe ni cambiar el estado de otra cuenta.
 */
class UsuariosDataSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val bd: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val funciones: FirebaseFunctions = FirebaseFunctions.getInstance(REGION_FUNCIONES),
    private val alcance: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : IUsuariosDataSource {

    private val sesion = MutableStateFlow<Usuario?>(null)
    private var escuchaPerfil: ListenerRegistration? = null

    override val usuarioActual: StateFlow<Usuario?> = sesion

    /** Cada usuario lee su propio perfil y los de quienes comparten una orden con él; el Jefe lee todos. */
    private val perfiles = ColeccionFirestore(
        bd.collection(USUARIOS), sesion,
        aDocumento = Usuario::aDocumento,
        desdeDocumento = { id, campos -> campos.aUsuario(id) },
        acceso = { FieldValue.arrayUnion(it.id) }
    )

    init {
        auth.addAuthStateListener { alCambiarSesion(it.currentUser) }
    }

    /** Sigue el perfil de la cuenta con sesión abierta; al cerrar sesión o reabrir la app lo restablece. */
    private fun alCambiarSesion(cuenta: FirebaseUser?) {
        escuchaPerfil?.remove()
        escuchaPerfil = null
        if (cuenta == null) {
            sesion.value = null
            return
        }
        alcance.launch {
            val id = runCatching { idDeUsuario(cuenta) }.getOrNull() ?: return@launch
            if (auth.currentUser?.uid != cuenta.uid) return@launch
            escuchaPerfil = bd.collection(USUARIOS).document(id).addSnapshotListener { documento, error ->
                if (error != null) {
                    Log.w(ETIQUETA, "No se pudo leer el perfil", error)
                    return@addSnapshotListener
                }
                val usuario = documento?.data?.let { runCatching { it.aUsuario(documento.id) }.getOrNull() }
                if (usuario != null && !usuario.activo) auth.signOut()
                sesion.value = usuario?.takeIf { it.activo }
            }
        }
    }

    private suspend fun idDeUsuario(cuenta: FirebaseUser): String? =
        cuenta.getIdToken(false).esperar().claims[RECLAMO_USUARIO_ID] as? String

    /** Sin sesión guardada responde de inmediato; con ella espera su perfil unos segundos como máximo. */
    override suspend fun restaurarSesion(): Usuario? {
        if (auth.currentUser == null) return null
        return withTimeoutOrNull(TIEMPO_RESTAURAR_SESION_MS) { sesion.filterNotNull().first() }
    }

    override fun observarUsuarios(): Flow<List<Usuario>> = perfiles.observarTodos()

    override suspend fun listarUsuarios(): List<Usuario> = perfiles.listar()

    override suspend fun obtenerUsuario(id: String): Usuario? = perfiles.obtener(id)

    override suspend fun guardarUsuario(usuario: Usuario) = perfiles.guardar(usuario.id, usuario)

    override suspend fun eliminarUsuario(id: String) = perfiles.eliminar(id)

    override fun nuevoId(prefijo: String): String = "$prefijo-${bd.collection(USUARIOS).document().id}"

    /** Abre la sesión y comprueba que el perfil exista y siga activo; si no, la cierra de inmediato. */
    override suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Usuario> = intentar {
        val cuenta = auth.signInWithEmailAndPassword(correo.trim(), contrasena).esperar().user
            ?: return@intentar Resultado.Error(MENSAJE_CREDENCIALES)
        val usuario = idDeUsuario(cuenta)?.let { obtenerUsuario(it) }
        if (usuario == null) {
            auth.signOut()
            return@intentar Resultado.Error(MENSAJE_CREDENCIALES)
        }
        if (!usuario.activo) {
            auth.signOut()
            return@intentar Resultado.Error(MENSAJE_DESACTIVADA)
        }
        sesion.value = usuario
        Resultado.Exito(usuario)
    }

    override fun cerrarSesion() {
        auth.signOut()
    }

    /** El servidor genera el código, lo guarda cifrado y lo envía por correo; responde igual exista o no la cuenta. */
    override suspend fun solicitarCodigoDeRestablecimiento(correo: String): Resultado<Unit> =
        llamarFuncion("solicitarCodigo", mapOf("correo" to correo.trim())).comoUnidad()

    override suspend fun restablecerContrasena(correo: String, codigo: String, contrasena: String): Resultado<Unit> =
        llamarFuncion(
            "restablecerContrasena",
            mapOf("correo" to correo.trim(), "codigo" to codigo.trim(), "contrasena" to contrasena)
        ).comoUnidad()

    /** El servidor crea la cuenta y une el registro previo del Jefe si el DNI ya existía; luego se abre la sesión. */
    override suspend fun registrarCuentaCliente(datos: DatosRegistroCliente): Resultado<Usuario> =
        llamarFuncion("registrarCliente", datos.comoCampos()).let { respuesta ->
            if (respuesta is Resultado.Error) respuesta else iniciarSesion(datos.correo, datos.contrasena)
        }

    override suspend fun crearCuentaTecnico(
        nombres: String,
        apellidos: String,
        dni: String,
        telefono: String,
        correo: String,
        contrasena: String
    ): Resultado<Usuario> {
        val campos = DatosRegistroCliente(nombres, apellidos, dni, telefono, correo, contrasena).comoCampos()
        return when (val respuesta = llamarFuncion("crearTecnico", campos)) {
            is Resultado.Error -> respuesta
            is Resultado.Exito -> intentar {
                obtenerUsuario(respuesta.valor)?.let { Resultado.Exito(it) } ?: Resultado.Error(MENSAJE_GENERICO)
            }
        }
    }

    /**
     * Con [contrasena] es un cliente que elimina su propia cuenta: se reautentica para comprobarla, el servidor
     * aplica las reglas del taller y la sesión se cierra. Sin ella, el Jefe desactiva la cuenta de otra persona.
     */
    override suspend fun desactivarCuenta(usuarioId: String, contrasena: String?): Resultado<Unit> {
        val cuenta = auth.currentUser
        if (contrasena != null) {
            val correo = cuenta?.email ?: return Resultado.Error(MENSAJE_CREDENCIALES)
            val credencial = EmailAuthProvider.getCredential(correo, contrasena)
            val reautenticado = intentar<Unit> {
                cuenta.reauthenticate(credencial).esperar()
                Resultado.Exito(Unit)
            }
            if (reautenticado is Resultado.Error) return Resultado.Error("Contraseña incorrecta")
        }
        val respuesta = llamarFuncion("desactivarCuenta", mapOf("usuarioId" to usuarioId))
        if (respuesta is Resultado.Exito && contrasena != null) auth.signOut()
        return respuesta.comoUnidad()
    }

    override suspend fun reactivarCuenta(usuarioId: String): Resultado<Unit> =
        llamarFuncion("reactivarCuenta", mapOf("usuarioId" to usuarioId)).comoUnidad()

    /** Llama a una función del servidor; devuelve el identificador que ella responda, o el mensaje de su rechazo. */
    private suspend fun llamarFuncion(nombre: String, datos: Map<String, Any?>): Resultado<String> = intentar {
        val respuesta = funciones.getHttpsCallable(nombre).call(datos).esperar().data as? Map<*, *>
        Resultado.Exito(respuesta?.get("id") as? String ?: "")
    }

    private fun Resultado<String>.comoUnidad(): Resultado<Unit> = when (this) {
        is Resultado.Exito -> Resultado.Exito(Unit)
        is Resultado.Error -> this
    }

    private fun DatosRegistroCliente.comoCampos(): Map<String, Any?> = mapOf(
        "nombres" to nombres.trim(),
        "apellidos" to apellidos.trim(),
        "dni" to dni,
        "telefono" to telefono,
        "correo" to correo.trim(),
        "contrasena" to contrasena
    )

    /** Ejecuta [bloque] y convierte cualquier falla de Firebase en un mensaje, sin tragarse la cancelación. */
    private suspend fun <T> intentar(bloque: suspend () -> Resultado<T>): Resultado<T> = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(ETIQUETA, "Operación rechazada", e)
        Resultado.Error(mensajeDe(e))
    }
}
