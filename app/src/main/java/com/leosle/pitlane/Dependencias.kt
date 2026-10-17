package com.leosle.pitlane

import android.content.Context
import com.leosle.pitlane.fuente.IDocumentosDataSource
import com.leosle.pitlane.fuente.IFotoStorage
import com.leosle.pitlane.fuente.IUsuariosDataSource
import com.leosle.pitlane.fuente.local.BaseDatosLocal
import com.leosle.pitlane.fuente.local.DocumentosDataSource
import com.leosle.pitlane.fuente.local.FotoStorage
import com.leosle.pitlane.fuente.local.UsuariosDataSource
import com.leosle.pitlane.repositorio.AuthRepository
import com.leosle.pitlane.repositorio.AvanceRepository
import com.leosle.pitlane.repositorio.ClienteRepository
import com.leosle.pitlane.repositorio.IAuthRepository
import com.leosle.pitlane.repositorio.IAvanceRepository
import com.leosle.pitlane.repositorio.IClienteRepository
import com.leosle.pitlane.repositorio.INotificacionRepository
import com.leosle.pitlane.repositorio.IOrdenRepository
import com.leosle.pitlane.repositorio.IPerfilRepository
import com.leosle.pitlane.repositorio.ISolicitudRepository
import com.leosle.pitlane.repositorio.ITecnicoRepository
import com.leosle.pitlane.repositorio.IVehiculoRepository
import com.leosle.pitlane.repositorio.NotificacionRepository
import com.leosle.pitlane.repositorio.OrdenRepository
import com.leosle.pitlane.repositorio.PerfilRepository
import com.leosle.pitlane.repositorio.SolicitudRepository
import com.leosle.pitlane.repositorio.TecnicoRepository
import com.leosle.pitlane.repositorio.VehiculoRepository
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
