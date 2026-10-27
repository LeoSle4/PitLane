package com.leosle.pitlane.arquitectura.repositorio.impl

import com.leosle.pitlane.arquitectura.repositorio.ITecnicoRepository
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.util.Textos
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.TecnicoConCarga
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Gestión de técnicos del Jefe de Taller sobre las fuentes de usuarios y documentos. */
class TecnicoRepository(
    private val usuarios: IUsuariosDataSource,
    private val documentos: IDocumentosDataSource
) : ITecnicoRepository {

    /** Emite cada técnico con el número de órdenes que tiene en curso, ordenados por nombre. */
    override fun observarTecnicos(): Flow<List<TecnicoConCarga>> =
        combine(usuarios.observarUsuarios(), documentos.ordenes.observarTodos(), usuarios.usuarioActual) { listaUsuarios, listaOrdenes, actor ->
            listaUsuarios.filter { it.rol == Rol.TECNICO && actor.esJefe() }
                .sortedBy { it.nombres }
                .map { tecnico ->
                    TecnicoConCarga(tecnico, listaOrdenes.count { it.tecnicoId == tecnico.id && it.enCurso })
                }
        }

    /** Pide a la fuente crear la cuenta del técnico sin cerrar la sesión del Jefe. */
    override suspend fun registrarTecnico(
        nombres: String,
        apellidos: String,
        dni: String,
        telefono: String,
        correo: String,
        contrasena: String
    ): Resultado<Usuario> =
        usuarios.crearCuentaTecnico(Textos.nombrePropio(nombres), Textos.nombrePropio(apellidos), dni, telefono, correo, contrasena)

    /** Guarda nombres, apellidos y teléfono del técnico. */
    override suspend fun editarTecnico(
        tecnicoId: String,
        nombres: String,
        apellidos: String,
        telefono: String
    ): Resultado<Usuario> {
        val tecnico = usuarios.obtenerUsuario(tecnicoId) ?: return Resultado.Error("No se encontró el técnico")
        val actualizado = tecnico.copy(nombres = Textos.nombrePropio(nombres), apellidos = Textos.nombrePropio(apellidos), telefono = telefono)
        usuarios.guardarUsuario(actualizado)
        return Resultado.Exito(actualizado)
    }

    /** Pide a la fuente desactivar al técnico; rechaza si tiene órdenes en curso. */
    override suspend fun desactivarTecnico(tecnicoId: String): Resultado<Unit> =
        usuarios.desactivarCuenta(tecnicoId, null)

    /** Devuelve al técnico al estado activo. */
    override suspend fun reactivarTecnico(tecnicoId: String): Resultado<Unit> =
        usuarios.reactivarCuenta(tecnicoId)
}
