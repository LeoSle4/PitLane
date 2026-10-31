package com.leosle.pitlane.arquitectura.viewmodel

import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.modelo.FichaCliente
import com.leosle.pitlane.modelo.ResultadoBusqueda
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.arquitectura.repositorio.IClienteRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/** Gestión de clientes por el Jefe de Taller: búsqueda, ficha, registro, edición y eliminación. */
class ClienteViewModel(
    private val repositorio: IClienteRepository = Dependencias.repositorioCliente
) : ViewModelBase() {

    /** Todos los clientes ordenados por apellido. */
    val clientes: StateFlow<List<Usuario>> by lazy { repositorio.observarClientes().comoEstado(emptyList()) }

    /** Ficha del cliente con vehículos e historial, actualizada cada vez que cambia. */
    fun ficha(clienteId: String): StateFlow<FichaCliente?> =
        estadoPorClave("ficha:$clienteId", null) { repositorio.observarFichaCliente(clienteId) }

    /** Clientes con actividad más reciente, para mostrar antes de escribir una búsqueda. */
    val recientes: StateFlow<List<ResultadoBusqueda>> by lazy { repositorio.observarClientesRecientes().comoEstado(emptyList()) }

    /**
     * Busca clientes por DNI, nombre, apellido o placa y entrega las coincidencias a [alTerminar]; si la búsqueda
     * falla por una causa técnica entrega una lista vacía en lugar de cerrar la aplicación.
     */
    fun buscar(consulta: String, alTerminar: (List<ResultadoBusqueda>) -> Unit) {
        viewModelScope.launch {
            val resultado = protegido { Resultado.Exito(repositorio.buscarClientes(consulta)) }
            alTerminar((resultado as? Resultado.Exito)?.valor.orEmpty())
        }
    }

    /** Registra un cliente sin cuenta de acceso. */
    fun registrarCliente(
        nombres: String,
        apellidos: String,
        dni: String,
        telefono: String,
        correo: String,
        alTerminar: (Resultado<Usuario>) -> Unit
    ) = ejecutar({ repositorio.registrarCliente(nombres, apellidos, dni, telefono, correo) }, alTerminar)

    /** Guarda los datos editables del cliente. */
    fun editarCliente(
        clienteId: String,
        nombres: String,
        apellidos: String,
        telefono: String,
        alTerminar: (Resultado<Usuario>) -> Unit
    ) = ejecutar({ repositorio.editarCliente(clienteId, nombres, apellidos, telefono) }, alTerminar)

    /** Elimina al cliente si las reglas lo permiten. */
    fun eliminarCliente(clienteId: String, alTerminar: (Resultado<Unit>) -> Unit) =
        ejecutar({ repositorio.eliminarCliente(clienteId) }, alTerminar)
}
