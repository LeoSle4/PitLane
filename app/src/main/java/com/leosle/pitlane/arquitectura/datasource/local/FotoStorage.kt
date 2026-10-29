package com.leosle.pitlane.arquitectura.datasource.local

import com.leosle.pitlane.arquitectura.datasource.IFotoStorage
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.util.Resultado
import com.leosle.pitlane.util.Validador
import java.io.File

/**
 * Almacén de fotos en una carpeta del teléfono. Las fotos de ejemplo (`recurso:`) y las que ya están en la
 * carpeta se devuelven sin cambios; las demás se validan y se copian con el nombre de su identificador.
 */
class FotoStorage(private val carpeta: File) : IFotoStorage {

    private companion object {
        const val PREFIJO_RECURSO = "recurso:"
    }

    override suspend fun subir(propietarioId: String, fotos: List<Foto>): Resultado<List<Foto>> {
        carpeta.mkdirs()
        val guardadas = mutableListOf<Foto>()
        for (foto in fotos) {
            if (foto.ruta.startsWith(PREFIJO_RECURSO) || File(foto.ruta).parentFile == carpeta) {
                guardadas.add(foto)
                continue
            }
            val origen = File(foto.ruta)
            if (!origen.exists()) return Resultado.Error("No se encontró una de las fotos")
            val extension = origen.extension.lowercase()
            val error = Validador.foto(Validador.tipoMimeDeImagen(extension), origen.length())
            if (error != null) return Resultado.Error(error)
            val destino = File(carpeta, "${foto.id}.$extension")
            origen.copyTo(destino, overwrite = true)
            guardadas.add(foto.copy(ruta = destino.absolutePath))
        }
        return Resultado.Exito(guardadas)
    }

    override suspend fun eliminar(fotos: List<Foto>) {
        fotos.filter { !it.ruta.startsWith(PREFIJO_RECURSO) && File(it.ruta).parentFile == carpeta }
            .forEach { File(it.ruta).delete() }
    }

    override suspend fun archivo(foto: Foto): File? =
        if (foto.ruta.startsWith(PREFIJO_RECURSO)) null else File(foto.ruta).takeIf { it.exists() }
}
