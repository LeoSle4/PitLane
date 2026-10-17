package com.leosle.pitlane.fuente

import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.util.Resultado

/** Almacén de fotos: valida formato y tamaño y las guarda de forma permanente. */
interface IFotoStorage {

    /** Valida cada foto (JPG o PNG de hasta 5 MB) y devuelve las fotos ya almacenadas de forma permanente. */
    suspend fun subir(fotos: List<Foto>): Resultado<List<Foto>>

    /** Elimina del almacén las fotos indicadas. */
    suspend fun eliminar(fotos: List<Foto>)
}
