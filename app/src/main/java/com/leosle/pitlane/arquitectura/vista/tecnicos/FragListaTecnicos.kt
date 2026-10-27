package com.leosle.pitlane.arquitectura.vista.tecnicos

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.leosle.pitlane.R
import com.leosle.pitlane.arquitectura.vista.comun.MenuAcciones
import com.leosle.pitlane.databinding.JcFragListaTecnicosBinding
import com.leosle.pitlane.databinding.JcItemTecnicoBinding
import com.leosle.pitlane.modelo.TecnicoConCarga
import com.leosle.pitlane.arquitectura.viewmodel.TecnicoViewModel
import com.leosle.pitlane.arquitectura.vista.comun.AdaptadorLista
import com.leosle.pitlane.arquitectura.vista.comun.DialogoModal
import com.leosle.pitlane.arquitectura.vista.comun.EstadoVacio
import com.leosle.pitlane.arquitectura.vista.comun.EstadosVisuales
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado

/**
 * Pestaña de técnicos: lista cada técnico con su teléfono, estado y órdenes en curso, y ofrece registrar,
 * editar, desactivar y reactivar. La lista se redibuja sola cada vez que el flujo de técnicos cambia.
 */
class FragListaTecnicos : FragmentoVinculado<JcFragListaTecnicosBinding>(JcFragListaTecnicosBinding::inflate) {

    override val mostrarBarraInferior: Boolean = true

    private val modelo: TecnicoViewModel by viewModels()
    private lateinit var adaptador: AdaptadorLista<TecnicoConCarga>

    /** Prepara la lista con su estado vacío, conecta los botones y empieza a observar a los técnicos. */
    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        adaptador = AdaptadorLista(requireContext(), R.layout.jc_item_tecnico) { fila, item -> enlazarFila(fila, item) }
        vinculo.lvTecnicos.adapter = adaptador
        vinculo.lvTecnicos.emptyView = vinculo.includeVacio.root
        vinculo.lvTecnicos.setOnItemClickListener { _, _, posicion, _ -> abrirEdicion(adaptador.getItem(posicion)) }

        EstadoVacio.configurar(
            vinculo.includeVacio,
            "Aún no hay técnicos registrados",
            "Registra a tu equipo para poder asignarle órdenes de ingreso.",
            "Registrar técnico"
        ) { abrirRegistro() }
        vinculo.btnAgregarTecnico.setOnClickListener { abrirRegistro() }

        recolectar(modelo.tecnicos) { lista ->
            adaptador.actualizar(lista)
            vinculo.txtResumenTecnicos.text = resumen(lista)
        }
    }

    /** Escribe el total de técnicos y cuántos están activos. */
    private fun resumen(lista: List<TecnicoConCarga>): String {
        val activos = lista.count { it.tecnico.activo }
        val total = lista.size
        return if (total == 0) "Sin técnicos" else "$total ${if (total == 1) "técnico" else "técnicos"} · $activos ${if (activos == 1) "activo" else "activos"}"
    }

    /** Llena una fila con avatar, nombre, teléfono, chip de estado, órdenes en curso y menú de opciones. */
    private fun enlazarFila(fila: View, item: TecnicoConCarga) {
        val tarjeta = JcItemTecnicoBinding.bind(fila)
        val tecnico = item.tecnico
        tarjeta.txtInicialesTecnico.text = tecnico.iniciales
        tarjeta.txtNombreTecnico.text = tecnico.nombreCompleto
        tarjeta.txtTelefonoTecnico.text = tecnico.telefono
        EstadosVisuales.estadoCuenta(tarjeta.txtChipEstadoTecnico, tecnico.activo)
        tarjeta.txtOrdenesEnCurso.text = when (item.ordenesEnCurso) {
            0 -> "Sin órdenes en curso"
            1 -> "1 orden en curso"
            else -> "${item.ordenesEnCurso} órdenes en curso"
        }
        tarjeta.btnMenuTecnico.setOnClickListener { mostrarMenu(it, item) }
    }

    /** Despliega el menú de la fila con Editar y, según el estado, Desactivar o Reactivar. */
    private fun mostrarMenu(ancla: View, item: TecnicoConCarga) {
        val activo = item.tecnico.activo
        MenuAcciones.mostrar(
            ancla,
            listOf(
                MenuAcciones.Opcion(R.drawable.ic_pencil_simple, "Editar") { abrirEdicion(item) },
                MenuAcciones.Opcion(
                    if (activo) R.drawable.ic_x_circle else R.drawable.ic_check_circle,
                    if (activo) "Desactivar" else "Reactivar",
                    peligro = activo
                ) { if (activo) confirmarDesactivar(item) else confirmarReactivar(item) }
            )
        )
    }

    /** Navega a la pantalla de registro de técnico. */
    private fun abrirRegistro() {
        navegador.irA(FragRegistrarTecnico())
    }

    /** Abre la hoja de edición con los datos actuales del técnico. */
    private fun abrirEdicion(item: TecnicoConCarga) {
        HojaEditarTecnico.mostrar(parentFragmentManager, item.tecnico)
    }

    /** Pide confirmar la desactivación; si el técnico tiene órdenes en curso muestra el diálogo de error. */
    private fun confirmarDesactivar(item: TecnicoConCarga) {
        val tecnico = item.tecnico
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_warning_circle,
            "¿Desactivar técnico?",
            "${tecnico.nombreCompleto} ya no podrá iniciar sesión. Su historial de órdenes se conserva.",
            "Desactivar",
            peligro = true
        ) {
            modelo.desactivarTecnico(tecnico.id) { resultado ->
                resultado
                    .alExito { mostrarMensaje("Técnico desactivado") }
                    .alError { DialogoModal.error(requireContext(), it.mensaje, "No se puede desactivar") }
            }
        }
    }

    /** Pide confirmar la reactivación de un técnico inactivo y la aplica. */
    private fun confirmarReactivar(item: TecnicoConCarga) {
        val tecnico = item.tecnico
        DialogoModal.confirmar(
            requireContext(),
            R.drawable.ic_check_circle,
            "¿Reactivar técnico?",
            "${tecnico.nombreCompleto} volverá a poder iniciar sesión y recibir órdenes.",
            "Reactivar"
        ) {
            modelo.reactivarTecnico(tecnico.id) { resultado ->
                resultado
                    .alExito { mostrarMensaje("Técnico reactivado") }
                    .alError { DialogoModal.error(requireContext(), it.mensaje, "No se pudo reactivar") }
            }
        }
    }
}
