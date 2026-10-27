package com.leosle.pitlane.arquitectura.vista.ordenes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.JoDlgEdicionOrdenBinding
import com.leosle.pitlane.modelo.OrdenDetallada
import com.leosle.pitlane.modelo.TecnicoConCarga
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.viewmodel.AuthViewModel
import com.leosle.pitlane.arquitectura.viewmodel.OrdenViewModel
import com.leosle.pitlane.arquitectura.viewmodel.TecnicoViewModel
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.Combos
import com.leosle.pitlane.arquitectura.vista.comun.DialogoModal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Edición de una orden en una pantalla modal completa que sube desde abajo: la X cierra, "Guardar" confirma.
 * Los campos llegan llenados con los datos de la orden y "Guardar" solo se habilita cuando algo cambió.
 */
class PantallaEdicionOrden : DialogFragment() {

    private var vinculoInterno: JoDlgEdicionOrdenBinding? = null
    private val modelo: OrdenViewModel by viewModels()
    private val tecnicoModelo: TecnicoViewModel by viewModels()
    private val sesionModelo: AuthViewModel by activityViewModels()

    private var original: OrdenDetallada? = null
    private var tecnicos: List<TecnicoConCarga> = emptyList()
    private var tecnicosMostrados: List<TecnicoConCarga> = emptyList()
    private var tecnicoElegidoId: String? = null
    private var cargada = false

    override fun getTheme(): Int = R.style.Tema_JoEdicionOrden

    override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        val vinculo = JoDlgEdicionOrdenBinding.inflate(inflater, contenedor, false)
        vinculoInterno = vinculo
        // El contenido deja espacio para las barras del sistema y sube con el teclado
        ViewCompat.setOnApplyWindowInsetsListener(vinculo.llRaizEdicion) { raiz, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
            raiz.setPadding(0, barras.top, 0, maxOf(barras.bottom, teclado.bottom))
            insets
        }
        return vinculo.root
    }

    override fun onStart() {
        super.onStart()
        // La ventana ocupa toda la pantalla y se reduce al abrir el teclado
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        val vinculo = vinculoInterno ?: return

        vinculo.btnCerrarEdicion.setOnClickListener { dismiss() }
        vinculo.btnGuardarEdicion.setOnClickListener { guardar() }

        // Validación al salir de cada campo y detección de cambios
        Campos.validarAlSalir(vinculo.etFallaEdicion, vinculo.txtErrorFallaEdicion, ::validarFalla)
        Campos.validarAlSalir(vinculo.etKilometrajeEdicion, vinculo.txtErrorKilometrajeEdicion, Validador::kilometraje)
        Campos.alCambiarTexto(vinculo.etFallaEdicion, vinculo.etKilometrajeEdicion, vinculo.etObservacionesEdicion) { actualizarBoton() }
        vinculo.incCombustibleEdicion.rgCombustible.setOnCheckedChangeListener { _, _ -> actualizarBoton() }
        vinculo.spnTecnicoEdicion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(padre: AdapterView<*>?, vista: View?, posicion: Int, id: Long) {
                tecnicoElegidoId = tecnicosMostrados.getOrNull(posicion)?.tecnico?.id ?: tecnicoElegidoId
                actualizarBoton()
            }

            override fun onNothingSelected(padre: AdapterView<*>?) = Unit
        }

        // Los datos de la orden y la lista de técnicos llegan por separado; ambos llenan sus campos al recibirse
        val ordenId = requireArguments().getString(CLAVE_ORDEN).orEmpty()
        recolectar(modelo.orden(ordenId, sesionModelo.usuarioActual.value?.id.orEmpty())) { detalle ->
            if (detalle != null && !cargada) {
                original = detalle
                llenarCampos(detalle)
            }
        }
        recolectar(tecnicoModelo.tecnicosActivos) { lista ->
            tecnicos = lista
            actualizarComboTecnicos()
        }
    }

    override fun onDestroyView() {
        vinculoInterno = null
        super.onDestroyView()
    }

    /** Observa [flujo] mientras la pantalla esté visible y llama a [accion] con cada valor emitido. */
    private fun <T> recolectar(flujo: Flow<T>, accion: (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flujo.collect { accion(it) }
            }
        }
    }

    /** Escribe en los campos los datos actuales de la orden, una sola vez. */
    private fun llenarCampos(detalle: OrdenDetallada) {
        val vinculo = vinculoInterno ?: return
        val orden = detalle.orden
        cargada = true
        vinculo.etOrdenBloqueada.setText("${orden.numero} · ${detalle.vehiculo?.placa.orEmpty()} ${detalle.vehiculo?.descripcion.orEmpty()}".trim())
        Campos.bloquear(vinculo.etOrdenBloqueada)
        vinculo.etFallaEdicion.setText(orden.falla)
        vinculo.etKilometrajeEdicion.setText(orden.kilometraje.toString())
        AyudasOrden.marcarCombustible(vinculo.incCombustibleEdicion.rgCombustible, orden.combustible)
        vinculo.etObservacionesEdicion.setText(orden.observaciones)
        tecnicoElegidoId = orden.tecnicoId
        actualizarComboTecnicos()
        actualizarBoton()
    }

    /** Llena el combo con los técnicos activos y, si el actual ya no lo es, lo agrega para no perderlo; marca el elegido. */
    private fun actualizarComboTecnicos() {
        val vinculo = vinculoInterno ?: return
        val actual = original?.tecnico
        val lista = if (actual != null && tecnicos.none { it.tecnico.id == actual.id }) {
            listOf(TecnicoConCarga(actual, 0)) + tecnicos
        } else {
            tecnicos
        }
        tecnicosMostrados = lista
        Combos.configurar(vinculo.spnTecnicoEdicion, lista.map(AyudasOrden::textoTecnico))
        val indice = lista.indexOfFirst { it.tecnico.id == tecnicoElegidoId }
        if (indice >= 0) vinculo.spnTecnicoEdicion.setSelection(indice)
    }

    /** Exige que la falla no esté vacía. */
    private fun validarFalla(valor: String): String? = Validador.obligatorio(valor, "Describe la falla reportada")

    /** Habilita "Guardar" solo cuando algún campo difiere de los datos originales de la orden. */
    private fun actualizarBoton() {
        val vinculo = vinculoInterno ?: return
        vinculo.btnGuardarEdicion.isEnabled = cargada && hayCambios()
    }

    /** Indica si falla, kilometraje, combustible, observaciones o técnico son distintos de los originales. */
    private fun hayCambios(): Boolean {
        val vinculo = vinculoInterno ?: return false
        val orden = original?.orden ?: return false
        return Campos.texto(vinculo.etFallaEdicion) != orden.falla.trim() ||
            Campos.texto(vinculo.etKilometrajeEdicion) != orden.kilometraje.toString() ||
            AyudasOrden.combustibleElegido(vinculo.incCombustibleEdicion.rgCombustible) != orden.combustible ||
            Campos.texto(vinculo.etObservacionesEdicion) != orden.observaciones.trim() ||
            tecnicoElegidoId != orden.tecnicoId
    }

    /** Valida los campos y guarda los cambios; al terminar cierra la pantalla y avisa con un Toast, o muestra el error. */
    private fun guardar() {
        val vinculo = vinculoInterno ?: return
        val orden = original?.orden ?: return
        val combustible = AyudasOrden.combustibleElegido(vinculo.incCombustibleEdicion.rgCombustible) ?: orden.combustible
        val tecnicoId = tecnicoElegidoId ?: orden.tecnicoId
        val fallaValida = Campos.validar(vinculo.etFallaEdicion, vinculo.txtErrorFallaEdicion, ::validarFalla)
        val kilometrajeValido = Campos.validar(vinculo.etKilometrajeEdicion, vinculo.txtErrorKilometrajeEdicion, Validador::kilometraje)
        if (!fallaValida || !kilometrajeValido) return

        val contexto = requireContext()
        val usuarioId = sesionModelo.usuarioActual.value?.id.orEmpty()
        vinculo.btnGuardarEdicion.isEnabled = false
        modelo.editarOrden(
            orden.id,
            Campos.texto(vinculo.etFallaEdicion),
            Campos.texto(vinculo.etKilometrajeEdicion).toInt(),
            combustible,
            Campos.texto(vinculo.etObservacionesEdicion),
            tecnicoId,
            usuarioId
        ) { resultado ->
            resultado
                .alExito {
                    Toast.makeText(contexto.applicationContext, "Orden actualizada", Toast.LENGTH_SHORT).show()
                    if (isAdded) dismiss()
                }
                .alError {
                    if (isAdded) {
                        actualizarBoton()
                        DialogoModal.error(contexto, it.mensaje)
                    }
                }
        }
    }

    companion object {
        private const val CLAVE_ORDEN = "ordenId"

        /** Abre la pantalla de edición de la orden indicada. */
        fun mostrar(administrador: FragmentManager, ordenId: String) {
            PantallaEdicionOrden().apply {
                arguments = Bundle().apply { putString(CLAVE_ORDEN, ordenId) }
            }.show(administrador, "pantalla_edicion_orden")
        }
    }
}
