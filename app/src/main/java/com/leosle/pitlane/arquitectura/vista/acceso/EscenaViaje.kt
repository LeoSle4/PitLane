package com.leosle.pitlane.arquitectura.vista.acceso

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.leosle.pitlane.R
import com.leosle.pitlane.databinding.ViewEscenaViajeBinding
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/**
 * Escena de las pantallas de acceso: un auto que cruza una carretera con un sol o una luna que se mueve en arco
 * por encima, nubes que pasan de día y estrellas que titilan de noche. Pinta su propio cielo, claro de día y oscuro
 * de noche sin importar el tema de la app, y detiene todas las animaciones al salir de la ventana.
 */
class EscenaViaje @JvmOverloads constructor(
    contexto: Context,
    atributos: AttributeSet? = null
) : FrameLayout(contexto, atributos) {

    private val vinculo = ViewEscenaViajeBinding.inflate(LayoutInflater.from(contexto), this)
    private val animadores = mutableListOf<Animator>()
    private var esNoche = false
    private val densidad = resources.displayMetrics.density

    init {
        clipChildren = false
        clipToPadding = false
        configurar(esNocheAhora())
    }

    /** Elige el cielo, el astro, el color del auto y los elementos del fondo según sea de día o de noche. */
    fun configurar(esNoche: Boolean) {
        this.esNoche = esNoche
        if (esNoche) setBackgroundColor(ContextCompat.getColor(context, R.color.franja_noche)) else setBackgroundResource(R.drawable.fondo_cielo_dia)
        vinculo.imgFuji.setImageResource(if (esNoche) R.drawable.acc_fuji_noche else R.drawable.acc_fuji_dia)
        vinculo.imgSolNucleo.visibility = if (esNoche) GONE else VISIBLE
        vinculo.imgSolRayos.visibility = if (esNoche) GONE else VISIBLE
        vinculo.imgLuna.visibility = if (esNoche) VISIBLE else GONE
        listOf(vinculo.imgNubeUno, vinculo.imgNubeDos).forEach { it.visibility = if (esNoche) GONE else VISIBLE }
        listOf(vinculo.imgEstrellaUno, vinculo.imgEstrellaDos, vinculo.imgEstrellaTres).forEach { it.visibility = if (esNoche) VISIBLE else GONE }
        vinculo.lottieAuto.setAnimation(if (esNoche) R.raw.auto_viaje_avena else R.raw.auto_viaje_carbon)
        vinculo.lottieAuto.repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
        vinculo.lottieAuto.playAnimation()
        if (isAttachedToWindow) reiniciarAnimaciones()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reiniciarAnimaciones()
    }

    override fun onDetachedFromWindow() {
        detenerAnimaciones()
        super.onDetachedFromWindow()
    }

    private fun detenerAnimaciones() {
        animadores.forEach { it.cancel() }
        animadores.clear()
    }

    /** Arranca el arco del astro, el giro de los rayos, el paso de las nubes y el titilar de las estrellas. */
    private fun reiniciarAnimaciones() {
        detenerAnimaciones()
        animarAstro()
        if (esNoche) animarEstrellas() else animarNubes()
    }

    private fun repetir(animador: ValueAnimator, modoReversa: Boolean) {
        animador.repeatCount = ValueAnimator.INFINITE
        animador.repeatMode = if (modoReversa) ValueAnimator.REVERSE else ValueAnimator.RESTART
        animador.start()
        animadores += animador
    }

    /** El astro recorre un arco corto sobre el auto; el sol además gira sus rayos y la luna se mece. */
    private fun animarAstro() {
        val amplitudX = 54 * densidad
        val amplitudY = 14 * densidad
        val arco = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16000
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                vinculo.flAstro.translationX = (t - 0.5f) * 2f * amplitudX
                vinculo.flAstro.translationY = -amplitudY * sin(PI * t).toFloat()
            }
        }
        repetir(arco, true)
        if (esNoche) {
            val mecer = ObjectAnimator.ofFloat(vinculo.imgLuna, "rotation", -6f, 6f).apply { duration = 3200 }
            repetir(mecer, true)
        } else {
            val girar = ObjectAnimator.ofFloat(vinculo.imgSolRayos, "rotation", 0f, 360f).apply {
                duration = 24000
                interpolator = LinearInterpolator()
            }
            repetir(girar, false)
            val latir = ObjectAnimator.ofFloat(vinculo.imgSolNucleo, "scaleX", 1f, 1.1f).apply { duration = 2400 }
            val latirY = ObjectAnimator.ofFloat(vinculo.imgSolNucleo, "scaleY", 1f, 1.1f).apply { duration = 2400 }
            repetir(latir, true)
            repetir(latirY, true)
        }
    }

    /** Las nubes cruzan el cielo de derecha a izquierda a distinta velocidad. */
    private fun animarNubes() {
        post {
            val ancho = width.toFloat().coerceAtLeast(1f)
            listOf(vinculo.imgNubeUno to 38000L, vinculo.imgNubeDos to 54000L).forEachIndexed { indice, (nube, duracion) ->
                val recorrido = ObjectAnimator.ofFloat(nube, "translationX", ancho, -nube.width.toFloat() - 8 * densidad).apply {
                    this.duration = duracion
                    interpolator = LinearInterpolator()
                    setCurrentFraction(if (indice == 0) 0.35f else 0.8f)
                }
                repetir(recorrido, false)
            }
        }
    }

    /** Las estrellas cambian de brillo con ritmos distintos. */
    private fun animarEstrellas() {
        listOf(vinculo.imgEstrellaUno to 1400L, vinculo.imgEstrellaDos to 1900L, vinculo.imgEstrellaTres to 1600L).forEach { (estrella, duracion) ->
            val brillo = ObjectAnimator.ofFloat(estrella, "alpha", 0.25f, 1f).apply { this.duration = duracion }
            repetir(brillo, true)
        }
    }

    companion object {
        private const val HORA_AMANECER = 6
        private const val HORA_ATARDECER = 18

        /** Indica si la hora actual del teléfono corresponde a la noche (de 18:00 a 6:00). */
        fun esNocheAhora(): Boolean {
            val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return hora < HORA_AMANECER || hora >= HORA_ATARDECER
        }

        /** Pone íconos claros u oscuros en la barra de estado según el cielo que quedó bajo ella. */
        fun ajustarBarraDeEstado(actividad: FragmentActivity, vista: View, cieloOscuro: Boolean) {
            WindowCompat.getInsetsController(actividad.window, vista).isAppearanceLightStatusBars = !cieloOscuro
        }
    }
}
