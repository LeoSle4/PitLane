package com.leosle.pitlane.arquitectura.datasource.firebase

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.leosle.pitlane.arquitectura.datasource.IFotoStorage
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.util.Validador
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

private const val ETIQUETA = "FotoStorage"
private const val PREFIJO_RECURSO = "recurso:"

/** Prefijo de la ruta de una foto guardada en Cloud Storage, igual que `recurso:` marca las de ejemplo. */
internal const val PREFIJO_STORAGE = "storage:"

/** Sin conexión, la subida se rinde pronto en lugar de reintentar durante minutos. */
private const val TIEMPO_MAXIMO_SUBIDA_MS = 30_000L

/**
 * Ruta de la foto en Cloud Storage. La carpeta es el cliente dueño: las reglas de Storage permiten ver
 * la foto al Jefe, al propio cliente y a los técnicos que tienen una orden suya.
 */
internal fun rutaEnAlmacen(propietarioId: String, fotoId: String, extension: String): String =
    "fotos/$propietarioId/$fotoId.$extension"

/**
 * Almacén de fotos en Cloud Storage. Las fotos de ejemplo (`recurso:`) y las ya subidas (`storage:`) se devuelven
 * sin cambios; las demás se validan, se suben y pasan a tener la ruta `storage:fotos/{cliente}/{foto}.{ext}`.
 *
 * @param carpetaDescargas carpeta del teléfono donde se guardan las fotos descargadas para mostrarlas.
 */
class FotoStorage(
    private val carpetaDescargas: File,
    private val almacen: FirebaseStorage = FirebaseStorage.getInstance().apply {
        maxUploadRetryTimeMillis = TIEMPO_MAXIMO_SUBIDA_MS
    }
) : IFotoStorage {

    private val descargando = Mutex()

    override suspend fun subir(propietarioId: String, fotos: List<Foto>): Resultado<List<Foto>> {
        val pendientes = fotos.filter { !it.ruta.startsWith(PREFIJO_RECURSO) && !it.ruta.startsWith(PREFIJO_STORAGE) }
        for (foto in pendientes) {
            val origen = File(foto.ruta)
            if (!origen.exists()) return Resultado.Error("No se encontró una de las fotos")
            Validador.foto(Validador.tipoMimeDeImagen(origen.extension), origen.length())?.let { return Resultado.Error(it) }
        }
        val subidas = mutableMapOf<String, Foto>()
        try {
            for (foto in pendientes) {
                val origen = File(foto.ruta)
                val extension = origen.extension.lowercase()
                val ruta = rutaEnAlmacen(propietarioId, foto.id, extension)
                val metadatos = StorageMetadata.Builder().setContentType(Validador.tipoMimeDeImagen(extension)).build()
                almacen.getReference(ruta).putFile(Uri.fromFile(origen), metadatos).esperar()
                subidas[foto.id] = foto.copy(ruta = PREFIJO_STORAGE + ruta)
            }
        } catch (e: CancellationException) {
            eliminar(subidas.values.toList())
            throw e
        } catch (e: Exception) {
            Log.w(ETIQUETA, "No se pudo subir las fotos", e)
            eliminar(subidas.values.toList())
            return Resultado.Error(mensajeDe(e))
        }
        return Resultado.Exito(fotos.map { subidas[it.id] ?: it })
    }

    override suspend fun eliminar(fotos: List<Foto>) {
        for (foto in fotos.filter { it.ruta.startsWith(PREFIJO_STORAGE) }) {
            try {
                almacen.getReference(foto.ruta.removePrefix(PREFIJO_STORAGE)).delete().esperar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageException) {
                if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) Log.w(ETIQUETA, "No se pudo eliminar ${foto.id}", e)
            } catch (e: Exception) {
                Log.w(ETIQUETA, "No se pudo eliminar ${foto.id}", e)
            }
        }
    }

    override suspend fun archivo(foto: Foto): File? = when {
        foto.ruta.startsWith(PREFIJO_RECURSO) -> null
        foto.ruta.startsWith(PREFIJO_STORAGE) -> descargar(foto)
        else -> File(foto.ruta).takeIf { it.exists() }
    }

    /** Descarga la foto una sola vez y la reutiliza; si la descarga falla no deja un archivo a medias. */
    private suspend fun descargar(foto: Foto): File? = descargando.withLock {
        val ruta = foto.ruta.removePrefix(PREFIJO_STORAGE)
        val destino = File(carpetaDescargas, "${foto.id}.${ruta.substringAfterLast('.')}")
        if (destino.exists() && destino.length() > 0) return@withLock destino
        carpetaDescargas.mkdirs()
        try {
            almacen.getReference(ruta).getFile(destino).esperar()
            destino
        } catch (e: CancellationException) {
            destino.delete()
            throw e
        } catch (e: Exception) {
            Log.w(ETIQUETA, "No se pudo descargar ${foto.id}", e)
            destino.delete()
            null
        }
    }
}
