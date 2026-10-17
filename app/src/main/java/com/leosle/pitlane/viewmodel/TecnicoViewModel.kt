package com.leosle.pitlane.viewmodel

import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.modelo.TecnicoConCarga
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.repositorio.ITecnicoRepository
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** Gestión de técnicos por el Jefe de Taller. */
class TecnicoViewModel(
    private val repositorio: ITecnicoRepository = Dependencias.repositorioTecnico
) : ViewModelBase() {

    /** Todos los técnicos con su cantidad de órdenes en curso. */
    val tecnicos: StateFlow<List<TecnicoConCarga>> by lazy { repositorio.observarTecnicos().comoEstado(emptyList()) }

    /** Solo los técnicos activos, usados para asignar órdenes. */
    val tecnicosActivos: StateFlow<List<TecnicoConCarga>> by lazy {
        repositorio.observarTecnicos().map { lista -> lista.filter { it.tecnico.activo } }.comoEstado(emptyList())
    }

    /** Crea la cuenta de un técnico. */
    fun registrarTecnico(
        nombres: String,
        apellidos: String,
        dni: String,
        telefono: String,
        correo: String,
        contrasena: String,
        alTerminar: (Resultado<Usuario>) -> Unit
    ) = ejecutar({ repositorio.registrarTecnico(nombres, apellidos, dni, telefono, correo, contrasena) }, alTerminar)

    /** Guarda los datos editables del técnico. */
    fun editarTecnico(
        tecnicoId: String,
        nombres: String,
        apellidos: String,
        telefono: String,
        alTerminar: (Resultado<Usuario>) -> Unit
    ) = ejecutar({ repositorio.editarTecnico(tecnicoId, nombres, apellidos, telefono) }, alTerminar)

    /** Desactiva al técnico si no tiene órdenes en curso. */
    fun desactivarTecnico(tecnicoId: String, alTerminar: (Resultado<Unit>) -> Unit) =
        ejecutar({ repositorio.desactivarTecnico(tecnicoId) }, alTerminar)

    /** Reactiva a un técnico inactivo. */
    fun reactivarTecnico(tecnicoId: String, alTerminar: (Resultado<Unit>) -> Unit) =
        ejecutar({ repositorio.reactivarTecnico(tecnicoId) }, alTerminar)
}
