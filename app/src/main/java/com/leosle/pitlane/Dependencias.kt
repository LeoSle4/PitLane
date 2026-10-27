package com.leosle.pitlane

import android.content.Context
import com.leosle.pitlane.arquitectura.datasource.IDocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.IFotoStorage
import com.leosle.pitlane.arquitectura.datasource.IUsuariosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.FotoStorage
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.arquitectura.repositorio.impl.AuthRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.AvanceRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.ClienteRepository
import com.leosle.pitlane.arquitectura.repositorio.IAuthRepository
import com.leosle.pitlane.arquitectura.repositorio.IAvanceRepository
import com.leosle.pitlane.arquitectura.repositorio.IClienteRepository
import com.leosle.pitlane.arquitectura.repositorio.INotificacionRepository
import com.leosle.pitlane.arquitectura.repositorio.IOrdenRepository
import com.leosle.pitlane.arquitectura.repositorio.IPerfilRepository
import com.leosle.pitlane.arquitectura.repositorio.ISolicitudRepository
import com.leosle.pitlane.arquitectura.repositorio.ITecnicoRepository
import com.leosle.pitlane.arquitectura.repositorio.IVehiculoRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.NotificacionRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.OrdenRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.PerfilRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.SolicitudRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.TecnicoRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.VehiculoRepository
import com.leosle.pitlane.util.RelojSistema
import java.io.File

/**
 * Raíz de composición: único lugar donde se decide qué implementación de cada fuente de datos usa la
 * aplicación y cómo se arman los repositorios sobre ellas. Los ViewModels piden aquí sus repositorios.
 */
object Dependencias {

    private var carpetaFotos: File? = null

    /** Prepara la carpeta permanente de fotos; se llama una vez al arrancar la aplicación. */
    fun inicializar(contexto: Context) {
        carpetaFotos = File(contexto.filesDir, "fotos")
    }

    private val baseDatos by lazy { BaseDatosLocal() }

    private val usuarios: IUsuariosDataSource by lazy { UsuariosDataSource(baseDatos) }
    private val documentos: IDocumentosDataSource by lazy { DocumentosDataSource(baseDatos) }
    private val fotos: IFotoStorage by lazy {
        FotoStorage(checkNotNull(carpetaFotos) { "Dependencias.inicializar no se llamó al arrancar" })
    }

    val repositorioAuth: IAuthRepository by lazy { AuthRepository(usuarios) }
    val repositorioPerfil: IPerfilRepository by lazy { PerfilRepository(usuarios) }
    val repositorioCliente: IClienteRepository by lazy { ClienteRepository(usuarios, documentos) }
    val repositorioTecnico: ITecnicoRepository by lazy { TecnicoRepository(usuarios, documentos) }
    val repositorioVehiculo: IVehiculoRepository by lazy { VehiculoRepository(usuarios, documentos) }
    val repositorioSolicitud: ISolicitudRepository by lazy { SolicitudRepository(usuarios, documentos, fotos, RelojSistema) }
    val repositorioOrden: IOrdenRepository by lazy { OrdenRepository(usuarios, documentos, fotos, RelojSistema) }
    val repositorioAvance: IAvanceRepository by lazy { AvanceRepository(usuarios, documentos, fotos, RelojSistema) }
    val repositorioNotificacion: INotificacionRepository by lazy { NotificacionRepository(usuarios, documentos) }
}
