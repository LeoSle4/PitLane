package com.leosle.pitlane.arquitectura.vista.comun

import android.content.res.ColorStateList
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.CliIncludeCamposVehiculoBinding
import com.leosle.pitlane.databinding.CliItemFotoGaleriaBinding
import com.leosle.pitlane.databinding.CliItemHallazgoBinding
import com.leosle.pitlane.modelo.AvanceDetallado
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.util.Validador
import com.leosle.pitlane.arquitectura.vista.comun.Campos
import com.leosle.pitlane.arquitectura.vista.comun.EstadosVisuales
import com.leosle.pitlane.arquitectura.vista.comun.Imagenes
import com.leosle.pitlane.arquitectura.vista.comun.VisorFoto
import com.leosle.pitlane.arquitectura.vista.comun.poblar

/** Valores y funciones que comparten las pantallas del cliente: síntomas, colores de barras, galerías y validación del vehículo. */
internal object AyudasVista {

    /** Llena una tarjeta de hallazgo o trabajo realizado: tipo, fecha, descripción, galería que abre el visor y técnico que lo registró. */
    fun pintarHallazgo(vista: View, elemento: AvanceDetallado, administrador: FragmentManager) {
        val fila = CliItemHallazgoBinding.bind(vista)
        val avance = elemento.avance
        val icono = if (avance.tipo == TipoAvance.HALLAZGO) R.drawable.ic_magnifying_glass else R.drawable.ic_wrench
        fila.imgIconoTipo.setImageResource(icono)
        fila.txtTipoHallazgo.text = avance.tipo.etiqueta
        fila.txtFechaHallazgo.text = Fechas.fechaHora(avance.fechaHora)
        fila.txtDescripcionHallazgo.text = avance.descripcion
        fila.hsvGaleria.visibility = if (avance.fotos.isEmpty()) View.GONE else View.VISIBLE
        llenarGaleria(fila.llGaleria, avance.fotos, administrador)
        val tecnico = elemento.tecnico
        fila.txtTecnicoHallazgo.visibility = if (tecnico != null) View.VISIBLE else View.GONE
        fila.txtTecnicoHallazgo.text = vista.context.getString(R.string.cli_registrado_por, tecnico?.nombreCompleto.orEmpty())
    }

    /** Cantidad máxima de fotos que admite una solicitud. */
    const val MAXIMO_FOTOS = 3

    /** Mensaje que se muestra al intentar superar el máximo de fotos. */
    const val MENSAJE_MAXIMO_FOTOS = "Máximo 3 fotos"

    /** Síntomas frecuentes, con el mismo texto que se guarda en la solicitud. */
    const val SINTOMA_NO_ARRANCA = "No arranca"
    const val SINTOMA_BATERIA = "Batería se descarga"
    const val SINTOMA_LUCES = "Luces fallan"
    const val SINTOMA_TABLERO = "Tablero con fallas"
    const val SINTOMA_OTRO = "Otro"

    /** Devuelve el color de la barra lateral de una solicitud según su estado. */
    @ColorRes
    fun colorSolicitud(estado: EstadoSolicitud): Int = when (estado) {
        EstadoSolicitud.PENDIENTE -> R.color.estado_proceso_texto
        EstadoSolicitud.ATENDIDA -> R.color.estado_exito_texto
        EstadoSolicitud.CANCELADA -> R.color.estado_error_texto
    }

    /** Devuelve el color de la barra lateral de una orden, o el neutro si no hay orden. */
    @ColorRes
    fun colorOrden(estado: EstadoOrden?): Int =
        if (estado == null) R.color.estado_neutro_texto else EstadosVisuales.colorEstado(estado)

    /** Pinta la barra lateral de una tarjeta con el color indicado sin cambiar su forma. */
    fun pintarBarra(barra: View, @ColorRes color: Int) {
        barra.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(barra.context, color))
    }

    /** Llena una galería horizontal con las miniaturas de [fotos]; al tocar una se abre el visor en esa foto. */
    fun llenarGaleria(galeria: LinearLayout, fotos: List<Foto>, administrador: FragmentManager) {
        galeria.poblar(fotos.withIndex().toList(), R.layout.cli_item_foto_galeria) { vista, elemento ->
            val miniatura = CliItemFotoGaleriaBinding.bind(vista)
            Imagenes.cargarFoto(miniatura.imgFotoGaleria, elemento.value, 384)
            miniatura.imgFotoGaleria.setOnClickListener {
                VisorFoto.mostrar(administrador, fotos, elemento.index)
            }
        }
    }

    /** Conecta la validación al salir de cada campo del vehículo; la placa solo se valida si [conPlaca] es verdadero. */
    fun configurarCamposVehiculo(campos: CliIncludeCamposVehiculoBinding, conPlaca: Boolean) {
        if (conPlaca) {
            Campos.mayusculas(campos.etPlaca)
            Campos.validarAlSalir(campos.etPlaca, campos.txtErrorPlaca) { Validador.placa(it) }
        }
        Campos.validarAlSalir(campos.etMarca, campos.txtErrorMarca) { Validador.obligatorio(it, "Ingresa la marca") }
        Campos.validarAlSalir(campos.etModelo, campos.txtErrorModelo) { Validador.obligatorio(it, "Ingresa el modelo") }
        Campos.validarAlSalir(campos.etAnio, campos.txtErrorAnio) { Validador.anio(it) }
        Campos.validarAlSalir(campos.etColor, campos.txtErrorColor) { Validador.obligatorio(it, "Ingresa el color") }
    }

    /** Valida todos los campos del vehículo, muestra cada error bajo su campo y devuelve verdadero si todo es válido. */
    fun validarCamposVehiculo(campos: CliIncludeCamposVehiculoBinding, conPlaca: Boolean): Boolean {
        val placaValida = !conPlaca || Campos.validar(campos.etPlaca, campos.txtErrorPlaca) { Validador.placa(it) }
        val marcaValida = Campos.validar(campos.etMarca, campos.txtErrorMarca) { Validador.obligatorio(it, "Ingresa la marca") }
        val modeloValido = Campos.validar(campos.etModelo, campos.txtErrorModelo) { Validador.obligatorio(it, "Ingresa el modelo") }
        val anioValido = Campos.validar(campos.etAnio, campos.txtErrorAnio) { Validador.anio(it) }
        val colorValido = Campos.validar(campos.etColor, campos.txtErrorColor) { Validador.obligatorio(it, "Ingresa el color") }
        return placaValida && marcaValida && modeloValido && anioValido && colorValido
    }
}
