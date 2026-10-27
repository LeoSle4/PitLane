package com.leosle.pitlane.arquitectura.vista.ordenes

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.core.content.ContextCompat
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JoItemOrdenPanelBinding
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.arquitectura.vista.comun.LogosMarca
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.TecnicoConCarga
import com.leosle.pitlane.arquitectura.vista.comun.EstadosVisuales
import com.leosle.pitlane.arquitectura.vista.comun.Imagenes
import com.leosle.pitlane.arquitectura.vista.comun.Opciones
import com.leosle.pitlane.arquitectura.vista.comun.poblar

/** Funciones compartidas por las pantallas de órdenes del Jefe: colores de estado, combustible, galería y técnicos. */
internal object AyudasOrden {

    /** Cantidad máxima de fotos que admite una orden. */
    const val MAXIMO_FOTOS = 6

    /** Mensaje que se muestra al intentar superar el máximo de fotos. */
    const val MENSAJE_MAXIMO_FOTOS = "Máximo 6 fotos"

    /** Pinta [barra] con el color de texto del [estado] para formar la barra lateral de las tarjetas. */
    fun pintarBarraEstado(barra: View, estado: EstadoOrden) {
        barra.setBackgroundColor(ContextCompat.getColor(barra.context, EstadosVisuales.colorEstado(estado)))
    }

    /** Llena una tarjeta de orden con marca, número, placa, vehículo, cliente y técnico; [textoTiempo] se escribe junto al número. */
    fun pintarTarjetaOrden(fila: View, detalle: OrdenDetallada, textoTiempo: String) {
        val celda = JoItemOrdenPanelBinding.bind(fila)
        pintarBarraEstado(celda.viewBarraEstado, detalle.orden.estado)
        LogosMarca.aplicar(celda.imgMarcaOrden, detalle.vehiculo?.marca.orEmpty())
        celda.txtNumeroOrden.text = detalle.orden.numero
        celda.txtTiempoEstado.text = textoTiempo
        celda.txtPlacaOrden.text = detalle.vehiculo?.placa.orEmpty()
        celda.txtVehiculoOrden.text = detalle.vehiculo?.descripcion.orEmpty()
        celda.txtClienteOrden.text = detalle.cliente?.nombreCompleto ?: "Cliente sin datos"
        celda.txtTecnicoOrden.text = detalle.tecnico?.nombreCompleto ?: "Sin técnico"
        // La tarjeta completa abre el detalle: el toque lo recibe la lista, no la tarjeta
        celda.mcvOrden.isClickable = false
    }

    /** Marca en el grupo la opción que corresponde a [nivel]; el orden de los chips es el de [NivelCombustible]. */
    fun marcarCombustible(grupo: RadioGroup, nivel: NivelCombustible) {
        Opciones.marcar(grupo, nivel.ordinal)
    }

    /** Devuelve el nivel de combustible marcado en el grupo, o nulo si no se eligió ninguno. */
    fun combustibleElegido(grupo: RadioGroup): NivelCombustible? =
        NivelCombustible.values().getOrNull(Opciones.indiceMarcado(grupo))

    /** Dibuja una miniatura por foto en [contenedor] y avisa con [alTocar] la posición de la foto tocada. */
    fun llenarGaleria(contenedor: LinearLayout, fotos: List<Foto>, alTocar: (Int) -> Unit) {
        contenedor.poblar(fotos, R.layout.jo_item_foto) { vista, foto ->
            Imagenes.cargarFoto(vista as ImageView, foto, 256)
            vista.setOnClickListener { alTocar(fotos.indexOf(foto)) }
        }
    }

    /** Escribe el técnico con su carga actual, por ejemplo "Luis Pérez · 2 órdenes en curso". */
    fun textoTecnico(tecnico: TecnicoConCarga): String {
        val cantidad = tecnico.ordenesEnCurso
        val carga = if (cantidad == 1) "1 orden en curso" else "$cantidad órdenes en curso"
        return "${tecnico.tecnico.nombreCompleto} · $carga"
    }
}
