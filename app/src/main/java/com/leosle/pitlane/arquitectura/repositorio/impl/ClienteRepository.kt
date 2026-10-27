package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.repositorio.IClienteRepository
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
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
            documentos.vehiculos.observarTodos(),
            documentos.ordenes.observarTodos(),
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

    /** Compara el texto con DNI, nombre completo y placas; exige al menos 3 caracteres. */
    override suspend fun buscarClientes(consulta: String): List<ResultadoBusqueda> {
        val texto = consulta.trim().lowercase()
        if (texto.length < 3 || !usuarios.usuarioActual.value.esJefe()) return emptyList()
        val textoPlaca = texto.replace("-", "")
        val vehiculos = documentos.vehiculos.listar()
        return usuarios.listarUsuarios().filter { it.rol == Rol.CLIENTE }.mapNotNull { cliente ->
            val placas = vehiculos
                .filter { it.clienteId == cliente.id && it.placa.replace("-", "").lowercase().contains(textoPlaca) }
                .map { it.placa }
            val coincidePersona = cliente.dni.contains(texto) || cliente.nombreCompleto.lowercase().contains(texto)
            if (coincidePersona || placas.isNotEmpty()) ResultadoBusqueda(cliente, placas) else null
        }
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
        if (documentos.ordenes.listar().any { it.clienteId == clienteId }) {
            return Resultado.Error("No se puede eliminar un cliente con historial de órdenes")
        }
        documentos.enLote {
            documentos.solicitudes.listar().filter { it.clienteId == clienteId }.forEach { documentos.solicitudes.eliminar(it.id) }
            documentos.vehiculos.listar().filter { it.clienteId == clienteId }.forEach { documentos.vehiculos.eliminar(it.id) }
        }
        usuarios.eliminarUsuario(clienteId)
        return Resultado.Exito(Unit)
    }
}
