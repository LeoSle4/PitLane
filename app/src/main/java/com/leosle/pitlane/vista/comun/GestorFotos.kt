package com.leosle.pitlane.vista.comun

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.leosle.pitlane.databinding.IncludeZonaFotosBinding
import com.leosle.pitlane.databinding.ItemMiniaturaFotoBinding
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.util.Validador
import java.io.File
import java.util.UUID

/**
 * Administra las fotos adjuntas de un formulario: abre la cámara o la galería, valida formato y tamaño,
 * limita la cantidad, dibuja las miniaturas con su botón de quitar y abre el visor al tocarlas.
 *
 * Debe crearse al construir el fragmento (como propiedad) porque registra los lanzadores de resultados.
 *
 * @param maximo cantidad máxima de fotos permitida.
 * @param mensajeMaximo mensaje que se muestra al intentar superar el máximo.
 * @param alCambiar se llama cada vez que cambia la lista de fotos.
 */
class GestorFotos(
    private val fragmento: Fragment,
    private val maximo: Int,
    private val mensajeMaximo: String,
    private val alCambiar: (List<Foto>) -> Unit = {}
) {
    private val lista = mutableListOf<Foto>()
    private var zona: IncludeZonaFotosBinding? = null
    private var editable = true

    private val lanzadorGaleria = fragmento.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) incorporarDesdeUri(uri)
    }

    private var archivoCamara: File? = null

    private val lanzadorCamara = fragmento.registerForActivityResult(ActivityResultContracts.TakePicture()) { tomada ->
        val archivo = archivoCamara
        archivoCamara = null
        if (archivo != null) incorporarDesdeCamara(archivo, tomada)
    }

    /** Fotos adjuntas en este momento, en el orden en que se agregaron. */
    val fotos: List<Foto> get() = lista.toList()

    /** Conecta el gestor con la zona de fotos del diseño; con [editable] en falso solo permite ver las fotos. */
    fun vincular(zonaFotos: IncludeZonaFotosBinding, editable: Boolean = true) {
        zona = zonaFotos
        this.editable = editable
        zonaFotos.btnAgregarFoto.visibility = if (editable) View.VISIBLE else View.GONE
        zonaFotos.btnAgregarFoto.setOnClickListener { solicitarFoto() }
        dibujar()
    }

    /** Reemplaza las fotos adjuntas por [nuevas] y redibuja las miniaturas. */
    fun establecer(nuevas: List<Foto>) {
        lista.clear()
        lista.addAll(nuevas)
        dibujar()
    }

    /** Abre la hoja con "Tomar foto" y "Elegir de la galería", salvo que ya se alcanzó el máximo. */
    fun solicitarFoto() {
        if (lista.size >= maximo) {
            avisar(mensajeMaximo)
            return
        }
        HojaSelectorFoto.mostrar(fragmento.parentFragmentManager) { origen ->
            when (origen) {
                OrigenFoto.CAMARA -> abrirCamara()
                OrigenFoto.GALERIA -> lanzadorGaleria.launch("image/*")
            }
        }
    }

    /** Copia la imagen elegida en la galería a la carpeta de la app, valida formato y tamaño, y la agrega. */
    private fun incorporarDesdeUri(uri: Uri) {
        val contexto = fragmento.requireContext()
        val tipo = contexto.contentResolver.getType(uri)
        val extension = if (tipo == "image/png") "png" else "jpg"
        val destino = nuevoArchivo(extension)
        contexto.contentResolver.openInputStream(uri)?.use { entrada ->
            destino.outputStream().use { salida -> entrada.copyTo(salida) }
        }
        val error = Validador.foto(tipo, destino.length())
        if (error != null) {
            destino.delete()
            avisar(error)
            return
        }
        agregar(destino)
    }

    /** Pide a la cámara que guarde la foto a tamaño completo en un archivo de la carpeta de la app. */
    private fun abrirCamara() {
        val contexto = fragmento.requireContext()
        val destino = nuevoArchivo("jpg")
        archivoCamara = destino
        lanzadorCamara.launch(FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", destino))
    }

    /** Si la cámara guardó la foto valida su tamaño y la agrega; si se canceló descarta el archivo vacío. */
    private fun incorporarDesdeCamara(archivo: File, tomada: Boolean) {
        if (!tomada || archivo.length() == 0L) {
            archivo.delete()
            return
        }
        val error = Validador.foto("image/jpeg", archivo.length())
        if (error != null) {
            archivo.delete()
            avisar(error)
            return
        }
        agregar(archivo)
    }

    /** Crea la referencia a un archivo nuevo dentro de la carpeta de fotos de la app. */
    private fun nuevoArchivo(extension: String): File {
        val carpeta = File(fragmento.requireContext().cacheDir, "fotos_pendientes").apply { mkdirs() }
        return File(carpeta, "${UUID.randomUUID()}.$extension")
    }

    /** Registra el archivo como foto nueva, redibuja y avisa del cambio. */
    private fun agregar(archivo: File) {
        if (lista.size >= maximo) {
            archivo.delete()
            avisar(mensajeMaximo)
            return
        }
        lista.add(Foto(UUID.randomUUID().toString(), archivo.absolutePath, System.currentTimeMillis()))
        dibujar()
    }

    /** Quita una foto de la lista, redibuja y avisa del cambio. */
    private fun quitar(foto: Foto) {
        lista.removeAll { it.id == foto.id }
        dibujar()
    }

    /** Redibuja las miniaturas antes del botón de agregar, actualiza el contador y notifica el cambio. */
    private fun dibujar() {
        val zonaActual = zona ?: return
        val contenedor = zonaActual.llMiniaturas
        while (contenedor.childCount > 1) contenedor.removeViewAt(0)
        val inflador = LayoutInflater.from(contenedor.context)
        lista.forEachIndexed { indice, foto ->
            val miniatura = ItemMiniaturaFotoBinding.inflate(inflador, contenedor, false)
            Imagenes.cargarFoto(miniatura.imgMiniatura, foto, 256)
            miniatura.btnQuitarFoto.visibility = if (editable) View.VISIBLE else View.GONE
            miniatura.btnQuitarFoto.setOnClickListener { quitar(foto) }
            miniatura.imgMiniatura.setOnClickListener {
                VisorFoto.mostrar(fragmento.parentFragmentManager, lista.toList(), indice)
            }
            contenedor.addView(miniatura.root, contenedor.childCount - 1)
        }
        zonaActual.txtContadorFotos.text = "${lista.size} / $maximo"
        alCambiar(lista.toList())
    }

    /** Muestra un mensaje breve con Toast. */
    private fun avisar(mensaje: String) {
        Toast.makeText(fragmento.requireContext().applicationContext, mensaje, Toast.LENGTH_SHORT).show()
    }
}
