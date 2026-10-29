package com.leosle.pitlane.arquitectura.vista.comun

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.leosle.pitlane.Dependencias
import com.leosle.pitlane.R
import com.leosle.pitlane.modelo.Foto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.WeakHashMap

/** Carga fotos en un `ImageView` desde un archivo del teléfono, un recurso de ejemplo o el almacén de fotos. */
object Imagenes {

    private const val PREFIJO_RECURSO = "recurso:"

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Foto que cada vista está esperando, para no pintar una foto vieja cuando la vista ya se reutilizó. */
    private val esperadas = WeakHashMap<ImageView, String>()

    /**
     * Muestra [foto] en [vista]; si no es un archivo del teléfono la pide al almacén sin bloquear la pantalla,
     * y mientras llega o si no se puede leer, muestra un ícono de imagen.
     */
    fun cargarFoto(vista: ImageView, foto: Foto, ladoMaximo: Int = 1024) {
        esperadas.remove(vista)
        if (foto.ruta.startsWith(PREFIJO_RECURSO)) {
            val nombre = foto.ruta.removePrefix(PREFIJO_RECURSO)
            val recurso = vista.resources.getIdentifier(nombre, "drawable", vista.context.packageName)
            if (recurso != 0) vista.setImageResource(recurso) else mostrarMarcador(vista)
            return
        }
        val mapa = decodificarReducida(foto.ruta, ladoMaximo)
        if (mapa != null) {
            vista.setImageBitmap(mapa)
            return
        }
        mostrarMarcador(vista)
        cargarDesdeAlmacen(vista, foto, ladoMaximo)
    }

    private fun cargarDesdeAlmacen(vista: ImageView, foto: Foto, ladoMaximo: Int) {
        esperadas[vista] = foto.id
        alcance.launch {
            val archivo = Dependencias.almacenFotos.archivo(foto)
            if (esperadas[vista] != foto.id) return@launch
            esperadas.remove(vista)
            archivo?.let { decodificarReducida(it.absolutePath, ladoMaximo) }?.let { vista.setImageBitmap(it) }
        }
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
