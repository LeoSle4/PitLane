package com.leosle.pitlane.arquitectura.vista.comun

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.DlgVisorFotoBinding
import com.leosle.pitlane.databinding.ItemVisorFotoBinding
import com.leosle.pitlane.modelo.Foto

/**
 * Visor de fotos a pantalla completa con deslizamiento lateral; se cierra con la X, tocando la foto o con Atrás. Si se entrega [alSolicitarEliminar]
 * muestra el botón de eliminar; el visor se cierra y la confirmación la gestiona quien lo abrió.
 */
class VisorFoto : DialogFragment() {

    private var fotos: List<Foto> = emptyList()
    private var indiceInicial = 0
    private var alSolicitarEliminar: ((Foto) -> Unit)? = null
    private var vinculoInterno: DlgVisorFotoBinding? = null

    override fun getTheme(): Int = R.style.Tema_Visor

    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = DlgVisorFotoBinding.inflate(inflater, contenedor, false)
        vinculoInterno = vinculo
        if (fotos.isEmpty()) {
            dismissAllowingStateLoss()
            return vinculo.root
        }
        ajustarABarrasDelSistema(vinculo)
        vinculo.vpFotos.adapter = AdaptadorFotos(fotos) { dismiss() }
        vinculo.vpFotos.setCurrentItem(indiceInicial, false)
        actualizarContador(vinculo, indiceInicial)
        vinculo.vpFotos.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(posicion: Int) = actualizarContador(vinculo, posicion)
        })
        vinculo.btnCerrarVisor.setOnClickListener { dismiss() }
        if (alSolicitarEliminar != null) {
            vinculo.btnEliminarFotoVisor.visibility = View.VISIBLE
            vinculo.btnEliminarFotoVisor.setOnClickListener {
                val foto = fotos[vinculo.vpFotos.currentItem]
                val retorno = alSolicitarEliminar
                dismiss()
                retorno?.invoke(foto)
            }
        }
        return vinculo.root
    }

    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Baja los botones y sube el contador lo necesario para que no queden bajo las barras del sistema. */
    private fun ajustarABarrasDelSistema(vinculo: DlgVisorFotoBinding) {
        val margen = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(vinculo.root) { _, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            listOf(vinculo.btnCerrarVisor, vinculo.btnEliminarFotoVisor).forEach { boton ->
                boton.updateLayoutParams<FrameLayout.LayoutParams> { topMargin = barras.top + margen }
            }
            vinculo.txtContadorVisor.updateLayoutParams<FrameLayout.LayoutParams> { bottomMargin = barras.bottom + 2 * margen }
            insets
        }
        ViewCompat.requestApplyInsets(vinculo.root)
    }

    /** Escribe "n / total" con la posición de la foto visible. */
    private fun actualizarContador(vinculo: DlgVisorFotoBinding, posicion: Int) {
        vinculo.txtContadorVisor.text = "${posicion + 1} / ${fotos.size}"
    }

    /** Adaptador del paginador: una página por foto. */
    private class AdaptadorFotos(private val fotos: List<Foto>, private val alTocar: () -> Unit) : RecyclerView.Adapter<AdaptadorFotos.Pagina>() {

        class Pagina(val vinculo: ItemVisorFotoBinding) : RecyclerView.ViewHolder(vinculo.root)

        override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): Pagina =
            Pagina(ItemVisorFotoBinding.inflate(LayoutInflater.from(padre.context), padre, false))

        override fun onBindViewHolder(pagina: Pagina, posicion: Int) {
            Imagenes.cargarFoto(pagina.vinculo.imgFotoCompleta, fotos[posicion], 2048)
            pagina.vinculo.imgFotoCompleta.setOnClickListener { alTocar() }
        }

        override fun getItemCount(): Int = fotos.size
    }

    companion object {
        /** Abre el visor en la foto [indice] de [fotos]; con [alSolicitarEliminar] muestra el botón de eliminar. */
        fun mostrar(
            administrador: FragmentManager,
            fotos: List<Foto>,
            indice: Int = 0,
            alSolicitarEliminar: ((Foto) -> Unit)? = null
        ) {
            VisorFoto().apply {
                this.fotos = fotos
                this.indiceInicial = indice.coerceIn(0, (fotos.size - 1).coerceAtLeast(0))
                this.alSolicitarEliminar = alSolicitarEliminar
            }.show(administrador, "visor_foto")
        }
    }
}
