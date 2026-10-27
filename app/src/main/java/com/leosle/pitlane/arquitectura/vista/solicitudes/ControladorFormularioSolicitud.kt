package com.leosle.pitlane.arquitectura.vista.solicitudes

import android.app.DatePickerDialog
import android.widget.CheckBox
import com.leosle.pitlane.databinding.CliIncludeFormularioSolicitudBinding
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.vista.comun.AyudasVista
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.GestorFotos
import java.util.Calendar

/**
 * Maneja los campos comunes del formulario de solicitud (síntomas, descripción, fecha y fotos). Lee y escribe sus
 * valores, abre el selector de fecha sin permitir días pasados, valida al salir y al enviar, y avisa con [alCambiar]
 * cada vez que el usuario modifica algún dato.
 */
internal class ControladorFormularioSolicitud(
    private val formulario: CliIncludeFormularioSolicitudBinding,
    private val gestorFotos: GestorFotos,
    private val alCambiar: () -> Unit
) {

    private val casillas: List<Pair<CheckBox, String>> = listOf(
        formulario.chkNoArranca to AyudasVista.SINTOMA_NO_ARRANCA,
        formulario.chkBateria to AyudasVista.SINTOMA_BATERIA,
        formulario.chkLuces to AyudasVista.SINTOMA_LUCES,
        formulario.chkTablero to AyudasVista.SINTOMA_TABLERO,
        formulario.chkOtro to AyudasVista.SINTOMA_OTRO
    )

    /** Fecha de llegada elegida como inicio de día en milisegundos, o nulo si todavía no se eligió. */
    var fechaLlegada: Long? = null
        private set

    /** Síntomas marcados, en el orden en que aparecen en pantalla. */
    val sintomasMarcados: List<String>
        get() = casillas.filter { it.first.isChecked }.map { it.second }

    /** Descripción de la falla sin espacios en los extremos. */
    val descripcion: String
        get() = Campos.texto(formulario.etDescripcionFalla)

    /** Fotos adjuntas en este momento. */
    val fotos: List<Foto>
        get() = gestorFotos.fotos

    init {
        gestorFotos.vincular(formulario.incZonaFotos)
        casillas.forEach { (casilla, _) -> casilla.setOnCheckedChangeListener { _, _ -> alCambiar() } }
        Campos.validarAlSalir(formulario.etDescripcionFalla, formulario.txtErrorDescripcion) { reglaDescripcion(it) }
        Campos.alCambiarTexto(formulario.etDescripcionFalla) { alCambiar() }
        formulario.etFechaLlegada.setOnClickListener { abrirSelectorFecha() }
    }

    /** Escribe en el formulario los valores indicados, usado para rellenar la edición de una solicitud. */
    fun establecer(sintomas: List<String>, descripcion: String, fecha: Long, fotos: List<Foto>) {
        casillas.forEach { (casilla, texto) -> casilla.isChecked = texto in sintomas }
        formulario.etDescripcionFalla.setText(descripcion)
        escribirFecha(fecha)
        gestorFotos.establecer(fotos)
    }

    /** Valida la descripción y la fecha mostrando cada error bajo su campo; devuelve verdadero si ambas son válidas. */
    fun validar(): Boolean {
        val descripcionValida = Campos.validar(formulario.etDescripcionFalla, formulario.txtErrorDescripcion) { reglaDescripcion(it) }
        val mensajeFecha = reglaFecha()
        Campos.mostrarError(formulario.etFechaLlegada, formulario.txtErrorFecha, mensajeFecha)
        return descripcionValida && mensajeFecha == null
    }

    /** Valida solo la descripción de la falla mostrando su error; devuelve verdadero si es válida. */
    fun validarDescripcion(): Boolean =
        Campos.validar(formulario.etDescripcionFalla, formulario.txtErrorDescripcion) { reglaDescripcion(it) }

    /** Valida solo la fecha de llegada mostrando su error; devuelve verdadero si es válida. */
    fun validarFecha(): Boolean {
        val mensaje = reglaFecha()
        Campos.mostrarError(formulario.etFechaLlegada, formulario.txtErrorFecha, mensaje)
        return mensaje == null
    }

    /** Exige al menos 10 caracteres en la descripción de la falla. */
    private fun reglaDescripcion(valor: String): String? =
        Validador.longitudMinima(valor, 10, "Describe la falla con al menos 10 caracteres")

    /** Devuelve el error de la fecha: obligatoria y no anterior a hoy; nulo si es válida. */
    private fun reglaFecha(): String? {
        val fecha = fechaLlegada ?: return "Elige la fecha estimada de llegada"
        return if (Fechas.esAnteriorAHoy(fecha, System.currentTimeMillis())) {
            "La fecha de llegada no puede ser anterior a hoy"
        } else {
            null
        }
    }

    /** Abre el calendario partiendo de la fecha elegida (o de hoy) y sin permitir días anteriores a hoy. */
    private fun abrirSelectorFecha() {
        val hoy = Fechas.inicioDelDia(System.currentTimeMillis())
        val base = Calendar.getInstance().apply { timeInMillis = maxOf(fechaLlegada ?: hoy, hoy) }
        val dialogo = DatePickerDialog(
            formulario.root.context,
            { _, anio, mes, dia ->
                escribirFecha(Fechas.milisDeFecha(anio, mes + 1, dia))
                Campos.mostrarError(formulario.etFechaLlegada, formulario.txtErrorFecha, null)
                alCambiar()
            },
            base.get(Calendar.YEAR),
            base.get(Calendar.MONTH),
            base.get(Calendar.DAY_OF_MONTH)
        )
        dialogo.datePicker.minDate = hoy
        dialogo.show()
    }

    /** Guarda la fecha y la escribe en el campo con formato legible. */
    private fun escribirFecha(fecha: Long) {
        fechaLlegada = fecha
        formulario.etFechaLlegada.setText(Fechas.fecha(fecha))
    }
}
