package com.leosle.pitlane.arquitectura.vista.comun

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.ItemPestanaBinding

/** Descripción de una pestaña: título, íconos (normal y seleccionado) y la pantalla raíz que abre. */
data class ItemPestana(
    val titulo: String,
    @DrawableRes val icono: Int,
    @DrawableRes val iconoLleno: Int,
    val crearFragmento: () -> Fragment
)

/**
 * Barra de navegación inferior con apariencia de vidrio translúcido. Se llena con [configurar]; la pestaña
 * seleccionada cambia a ícono relleno con un indicador suave, y cada pestaña puede mostrar una insignia numérica.
 */
class BarraNavegacionVidrio @JvmOverloads constructor(
    contexto: Context,
    atributos: AttributeSet? = null
) : LinearLayout(contexto, atributos) {

    private var items: List<ItemPestana> = emptyList()
    private val vistas = mutableListOf<ItemPestanaBinding>()
    private var alSeleccionar: (Int) -> Unit = {}

    /** Índice de la pestaña seleccionada, o -1 si todavía no hay ninguna. */
    var indiceSeleccionado: Int = -1
        private set

    init {
        orientation = HORIZONTAL
        setBackgroundResource(R.drawable.fondo_vidrio)
    }

    /** Crea una vista por cada elemento de [pestanas] y avisa con [alSeleccionar] cuando el usuario toca una. */
    fun configurar(pestanas: List<ItemPestana>, alSeleccionar: (Int) -> Unit) {
        removeAllViews()
        vistas.clear()
        items = pestanas
        this.alSeleccionar = alSeleccionar
        indiceSeleccionado = -1
        val inflador = LayoutInflater.from(context)
        pestanas.forEachIndexed { indice, pestana ->
            val vista = ItemPestanaBinding.inflate(inflador, this, false)
            vista.txtTituloPestana.text = pestana.titulo
            vista.root.contentDescription = pestana.titulo
            vista.root.setOnClickListener { alSeleccionar(indice) }
            vistas.add(vista)
            addView(vista.root)
        }
    }

    /** Marca visualmente la pestaña del [indice] indicado. */
    fun marcar(indice: Int) {
        if (indice !in vistas.indices) return
        indiceSeleccionado = indice
        vistas.forEachIndexed { posicion, vista ->
            val activa = posicion == indice
            val color = ContextCompat.getColor(
                context,
                if (activa) R.color.sobre_contenedor_primario else R.color.texto_secundario
            )
            vista.imgIconoPestana.setImageResource(if (activa) items[posicion].iconoLleno else items[posicion].icono)
            vista.imgIconoPestana.setColorFilter(color)
            vista.txtTituloPestana.setTextColor(color)
            vista.flIndicadorPestana.setBackgroundResource(if (activa) R.drawable.fondo_pestana_activa else 0)
        }
    }

    /** Muestra [cantidad] como insignia en la pestaña indicada; con cero la oculta y con más de 9 muestra "9+". */
    fun establecerInsignia(indice: Int, cantidad: Int) {
        val vista = vistas.getOrNull(indice) ?: return
        vista.txtInsigniaPestana.visibility = if (cantidad > 0) View.VISIBLE else View.GONE
        vista.txtInsigniaPestana.text = if (cantidad > 9) "9+" else cantidad.toString()
    }
}
