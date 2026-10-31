package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.repositorio.IClienteRepository
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.util.Busqueda
import com.leosle.pitlane.util.Textos
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.modelo.FichaCliente
import com.leosle.pitlane.modelo.ResultadoBusqueda
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.VehiculoConHistorial
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Gestión de clientes del Jefe de Taller sobre las fuentes de usuarios y documentos. */
class ClienteRepository(
    private val usuarios: IUsuariosDataSource,
    private val documentos: IDocumentosDataSource
) : IClienteRepository {

    /** Emite los usuarios con rol Cliente ordenados por apellido y nombre. */
    override fun observarClientes(): Flow<List<Usuario>> =
        combine(usuarios.observarUsuarios(), usuarios.usuarioActual) { lista, actor ->
            if (!actor.esJefe()) emptyList()
            else lista.filter { it.rol == Rol.CLIENTE }.sortedWith(compareBy({ it.apellidos }, { it.nombres }))
        }

    /** Une cliente, vehículos y órdenes para emitir la ficha completa. */
    override fun observarFichaCliente(clienteId: String): Flow<FichaCliente?> =
        combine(
            usuarios.observarUsuarios(),
            documentos.vehiculos.observarDonde("clienteId", clienteId),
            documentos.ordenes.observarDonde("clienteId", clienteId),
            usuarios.usuarioActual
        ) { listaUsuarios, listaVehiculos, listaOrdenes, actor ->
            if (!actor.esJefe()) return@combine null
            val cliente = listaUsuarios.firstOrNull { it.id == clienteId } ?: return@combine null
            val historiales = listaVehiculos.filter { it.clienteId == clienteId }.map { vehiculo ->
                VehiculoConHistorial(
                    vehiculo,
                    listaOrdenes.filter { it.vehiculoId == vehiculo.id }.sortedByDescending { it.creadaEn }
                )
            }
            FichaCliente(cliente, historiales)
        }

    override fun observarClientesRecientes(limite: Int): Flow<List<ResultadoBusqueda>> =
        combine(
            usuarios.observarUsuarios(),
            documentos.vehiculos.observarTodos(),
            documentos.ordenes.observarTodos(),
            documentos.solicitudes.observarTodos(),
            usuarios.usuarioActual
        ) { listaUsuarios, listaVehiculos, listaOrdenes, listaSolicitudes, actor ->
            if (!actor.esJefe()) return@combine emptyList()
            val ultimaActividad = HashMap<String, Long>()
            fun registrar(clienteId: String, fecha: Long) {
                ultimaActividad[clienteId] = maxOf(ultimaActividad[clienteId] ?: Long.MIN_VALUE, fecha)
            }
            listaOrdenes.forEach { registrar(it.clienteId, it.fechaUltimoCambio) }
            listaSolicitudes.forEach { registrar(it.clienteId, it.creadaEn) }
            listaUsuarios.filter { it.rol == Rol.CLIENTE && it.id in ultimaActividad }
                .sortedByDescending { ultimaActividad.getValue(it.id) }
                .take(limite)
                .map { cliente ->
                    ResultadoBusqueda(cliente, emptyList(), listaVehiculos.filter { it.clienteId == cliente.id }.map { it.placa })
                }
        }

    /**
     * Busca por los términos indexados en vez de recorrer a todos los clientes y vehículos: el costo depende de las
     * coincidencias (hasta [MAXIMO_COINCIDENCIAS] por rama), no de cuántos clientes hay. Exige al menos 3 caracteres
     * y que quien consulta sea el Jefe.
     *
     * - Nombre y DNI: se consulta por la palabra más larga (de 3 o más caracteres) y luego se exige que cada palabra
     *   de la consulta aparezca en el nombre completo o en el DNI. Los nombres coinciden por el COMIENZO de cada
     *   palabra ("mend" encuentra a Mendoza, "ndoza" no); el DNI coincide en cualquier posición.
     * - Placa: la consulta sin espacios ni guion se busca en cualquier posición de la placa.
     *
     * Los documentos anteriores al relleno de términos (`rellenarBusqueda.mjs`) no se encuentran. El resultado va
     * ordenado por nombre completo (antes salía en el orden, sin especificar, de la lista de usuarios);
     * `placasCoincidentes` son las placas que contienen la consulta y `placas` todas las del cliente.
     */
    override suspend fun buscarClientes(consulta: String): List<ResultadoBusqueda> {
        val texto = Busqueda.normalizar(consulta)
        val placaBuscada = texto.replace(" ", "")
        if (placaBuscada.length < LARGO_MINIMO_BUSQUEDA || !usuarios.usuarioActual.value.esJefe()) return emptyList()

        val encontrados = LinkedHashMap<String, Usuario>()
        val palabras = texto.split(' ')
        palabras.filter { it.length >= LARGO_MINIMO_BUSQUEDA }.maxByOrNull { it.length }?.let { palabra ->
            usuarios.buscarClientesPorTermino(palabra.take(LARGO_MAXIMO_TERMINO), MAXIMO_COINCIDENCIAS)
                .filter { it.rol == Rol.CLIENTE && it.contieneTodas(palabras) }
                .forEach { encontrados[it.id] = it }
        }
        // Los términos los escribe quien registra el vehículo; se comprueba contra la placa real, como se hace con el nombre
        documentos.vehiculos.buscarPorTermino("terminosPlaca", placaBuscada, MAXIMO_COINCIDENCIAS)
            .filter { Busqueda.normalizar(it.placa).replace(" ", "").contains(placaBuscada) }
            .map { it.clienteId }.distinct().filter { it !in encontrados }
            .mapNotNull { usuarios.obtenerUsuario(it) }.filter { it.rol == Rol.CLIENTE }
            .forEach { encontrados[it.id] = it }

        return encontrados.values.sortedBy { Busqueda.normalizar(it.nombreCompleto) }.map { cliente ->
            val todas = documentos.vehiculos.listarDonde("clienteId", cliente.id).map { it.placa }
            val coincidentes = todas.filter { Busqueda.normalizar(it).replace(" ", "").contains(placaBuscada) }
            ResultadoBusqueda(cliente, coincidentes, todas)
        }
    }

    private fun Usuario.contieneTodas(palabras: List<String>): Boolean {
        val nombre = Busqueda.normalizar(nombreCompleto)
        val documento = Busqueda.normalizar(dni)
        return palabras.all { nombre.contains(it) || documento.contains(it) }
    }

    /** Crea un cliente sin cuenta; rechaza DNI o correo repetidos. */
    override suspend fun registrarCliente(
        nombres: String,
        apellidos: String,
        dni: String,
        telefono: String,
        correo: String
    ): Resultado<Usuario> {
        val existentes = usuarios.listarUsuarios()
        existentes.firstOrNull { it.dni == dni }?.let {
            return Resultado.Error("Ya existe un cliente con ese DNI", it.id)
        }
        if (correo.isNotBlank() && existentes.any { it.correo.equals(correo.trim(), ignoreCase = true) }) {
            return Resultado.Error("Ya existe un usuario con ese correo")
        }
        val cliente = Usuario(
            id = usuarios.nuevoId("u-cli"),
            nombres = Textos.nombrePropio(nombres),
            apellidos = Textos.nombrePropio(apellidos),
            dni = dni,
            telefono = telefono,
            correo = correo.trim(),
            contrasena = "",
            rol = Rol.CLIENTE,
            tieneCuenta = false
        )
        usuarios.guardarUsuario(cliente)
        return Resultado.Exito(cliente)
    }

    /** Guarda nombres, apellidos y teléfono del cliente. */
    override suspend fun editarCliente(
        clienteId: String,
        nombres: String,
        apellidos: String,
        telefono: String
    ): Resultado<Usuario> {
        val cliente = usuarios.obtenerUsuario(clienteId) ?: return Resultado.Error("No se encontró el cliente")
        val actualizado = cliente.copy(nombres = Textos.nombrePropio(nombres), apellidos = Textos.nombrePropio(apellidos), telefono = telefono)
        usuarios.guardarUsuario(actualizado)
        return Resultado.Exito(actualizado)
    }

    /** Elimina al cliente y sus vehículos si no tiene cuenta ni órdenes registradas. */
    override suspend fun eliminarCliente(clienteId: String): Resultado<Unit> {
        val cliente = usuarios.obtenerUsuario(clienteId) ?: return Resultado.Error("No se encontró el cliente")
        if (cliente.tieneCuenta) return Resultado.Error("Este cliente tiene cuenta en la app y solo él puede eliminarla")
        if (documentos.ordenes.listarDonde("clienteId", clienteId).isNotEmpty()) {
            return Resultado.Error("No se puede eliminar un cliente con historial de órdenes")
        }
        documentos.enLote {
            documentos.solicitudes.listarDonde("clienteId", clienteId).forEach { documentos.solicitudes.eliminar(it.id) }
            documentos.vehiculos.listarDonde("clienteId", clienteId).forEach { documentos.vehiculos.eliminar(it.id) }
        }
        usuarios.eliminarUsuario(clienteId)
        return Resultado.Exito(Unit)
    }
}

private const val LARGO_MINIMO_BUSQUEDA = 3
private const val LARGO_MAXIMO_TERMINO = 20
private const val MAXIMO_COINCIDENCIAS = 30
