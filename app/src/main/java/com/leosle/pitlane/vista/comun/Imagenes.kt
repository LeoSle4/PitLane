package com.leosle.pitlane.vista.comun

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.leosle.pitlane.R
import com.leosle.pitlane.modelo.Foto
import java.io.File

/** Carga fotos en un `ImageView` desde un archivo del teléfono o desde un recurso de ejemplo. */
object Imagenes {

    private const val PREFIJO_RECURSO = "recurso:"

    /** Muestra [foto] en [vista]; si no se puede leer, muestra un ícono de imagen. */
    fun cargarFoto(vista: ImageView, foto: Foto, ladoMaximo: Int = 1024) {
        if (foto.ruta.startsWith(PREFIJO_RECURSO)) {
            val nombre = foto.ruta.removePrefix(PREFIJO_RECURSO)
            val recurso = vista.resources.getIdentifier(nombre, "drawable", vista.context.packageName)
            if (recurso != 0) vista.setImageResource(recurso) else mostrarMarcador(vista)
            return
        }
        val mapa = decodificarReducida(foto.ruta, ladoMaximo)
        if (mapa != null) vista.setImageBitmap(mapa) else mostrarMarcador(vista)
    }

    /** Coloca un ícono neutro cuando la imagen no está disponible. */
    private fun mostrarMarcador(vista: ImageView) {
        vista.setImageResource(R.drawable.ic_image)
    }

    /** Lee el archivo reduciendo su tamaño para que el lado mayor no supere [ladoMaximo] píxeles. */
    private fun decodificarReducida(ruta: String, ladoMaximo: Int): Bitmap? {
        val archivo = File(ruta)
        if (!archivo.exists()) return null
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, medidas)
        var reduccion = 1
        while (medidas.outWidth / reduccion > ladoMaximo || medidas.outHeight / reduccion > ladoMaximo) reduccion *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = reduccion }
        return BitmapFactory.decodeFile(ruta, opciones)
    }
}
