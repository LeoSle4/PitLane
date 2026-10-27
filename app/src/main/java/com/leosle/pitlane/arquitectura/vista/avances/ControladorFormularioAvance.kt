package com.leosle.pitlane.arquitectura.vista.avances

import android.content.Context
import android.view.View
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.TecIncludeFormAvanceBinding
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.Opciones

/**
 * Lógica compartida del formulario de avance (tipo, descripción y zona de fotos) usado al registrar y al editar.
 * Lee el tipo elegido, valida los campos mostrando el error bajo cada uno y avisa cuando algo cambia.
 */
class ControladorFormularioAvance(
    private val contexto: Context,
    private val formulario: TecIncludeFormAvanceBinding,
    private val alCambiar: () -> Unit = {}
) {

    /** Conecta la validación al salir del campo y los avisos de cambio del tipo y de la descripción. */
    fun iniciar() {
        Campos.validarAlSalir(formulario.etDescripcionAvance, formulario.txtErrorDescripcionAvance) { reglaDescripcion(it) }
        Campos.alCambiarTexto(formulario.etDescripcionAvance) { alCambiar() }
        formulario.rgTipoAvance.setOnCheckedChangeListener { _, _ ->
            formulario.txtErrorTipoAvance.visibility = View.GONE
            alCambiar()
        }
    }

    /** Devuelve el tipo de avance marcado o nulo si no se eligió ninguno. */
    fun tipo(): TipoAvance? = when (Opciones.indiceMarcado(formulario.rgTipoAvance)) {
        0 -> TipoAvance.HALLAZGO
        1 -> TipoAvance.TRABAJO_REALIZADO
        else -> null
    }

    /** Devuelve la descripción escrita sin espacios en los extremos. */
    fun descripcion(): String = Campos.texto(formulario.etDescripcionAvance)

    /** Marca el tipo y escribe la descripción indicados. */
    fun establecer(tipo: TipoAvance, descripcion: String) {
        Opciones.marcar(formulario.rgTipoAvance, if (tipo == TipoAvance.HALLAZGO) 0 else 1)
        formulario.etDescripcionAvance.setText(descripcion)
    }

    /** Comprueba tipo y descripción, muestra los errores y devuelve verdadero si ambos son válidos. */
    fun validar(): Boolean {
        val tipoValido = tipo() != null
        formulario.txtErrorTipoAvance.text = contexto.getString(R.string.tec_error_tipo)
        formulario.txtErrorTipoAvance.visibility = if (tipoValido) View.GONE else View.VISIBLE
        val descripcionValida = Campos.validar(formulario.etDescripcionAvance, formulario.txtErrorDescripcionAvance) { reglaDescripcion(it) }
        return tipoValido && descripcionValida
    }

    /** Regla de la descripción: obligatoria y de al menos 10 caracteres. */
    private fun reglaDescripcion(valor: String): String? =
        Validador.longitudMinima(valor, 10, contexto.getString(R.string.tec_error_descripcion))
}
