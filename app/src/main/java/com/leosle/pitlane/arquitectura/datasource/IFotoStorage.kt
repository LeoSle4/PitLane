package com.leosle.pitlane.arquitectura.datasource

import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.util.Resultado
import java.io.File

/** Almacén de fotos: valida formato y tamaño y las guarda de forma permanente. */
interface IFotoStorage {

    /**
     * Valida cada foto (JPG o PNG de hasta 5 MB) y devuelve las fotos ya almacenadas de forma permanente.
     * [propietarioId] es el cliente dueño de la orden o solicitud; determina quién puede ver las fotos.
     */
    suspend fun subir(propietarioId: String, fotos: List<Foto>): Resultado<List<Foto>>

    /** Devuelve un archivo del teléfono con la foto listo para mostrar, o nulo si no está disponible. */
    suspend fun archivo(foto: Foto): File?

    /** Elimina del almacén las fotos indicadas. */
    suspend fun eliminar(fotos: List<Foto>)
}
