package com.leosle.pitlane.vista.comun

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import androidx.annotation.LayoutRes

/**
 * Adaptador genérico para `ListView`: infla [diseno] para cada fila y llama a [enlazar] para llenarla.
 * La lista se reemplaza con [actualizar], que refresca la pantalla.
 */
class AdaptadorLista<T>(
    private val contexto: Context,
    @LayoutRes private val diseno: Int,
    private val enlazar: (vista: View, elemento: T) -> Unit
) : BaseAdapter() {

    private var elementos: List<T> = emptyList()

    /** Reemplaza los elementos mostrados y vuelve a dibujar la lista. */
    fun actualizar(nuevos: List<T>) {
        elementos = nuevos
        notifyDataSetChanged()
    }

    override fun getCount(): Int = elementos.size

    override fun getItem(posicion: Int): T = elementos[posicion]

    override fun getItemId(posicion: Int): Long = posicion.toLong()

    override fun getView(posicion: Int, vistaReciclada: View?, padre: ViewGroup): View {
        val vista = vistaReciclada ?: LayoutInflater.from(contexto).inflate(diseno, padre, false).also {
            (it as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        enlazar(vista, elementos[posicion])
        return vista
    }
}

/** Llena un contenedor vertical con una fila por elemento; sirve para listas cortas dentro de una pantalla con scroll. */
fun <T> LinearLayout.poblar(elementos: List<T>, @LayoutRes diseno: Int, enlazar: (vista: View, elemento: T) -> Unit) {
    removeAllViews()
    val inflador = LayoutInflater.from(context)
    elementos.forEach { elemento ->
        val fila = inflador.inflate(diseno, this, false)
        enlazar(fila, elemento)
        addView(fila)
    }
}
