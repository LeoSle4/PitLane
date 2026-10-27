package com.leosle.pitlane.arquitectura.vista.acceso

import com.leosle.pitlane.R
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.Insets
import androidx.fragment.app.FragmentManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.leosle.pitlane.databinding.ActivityAccesoBinding
import com.leosle.pitlane.arquitectura.viewmodel.AuthViewModel
import com.leosle.pitlane.arquitectura.vista.PrincipalActivity
import com.leosle.pitlane.arquitectura.vista.comun.Navegador
import com.leosle.pitlane.arquitectura.vista.notificaciones.MensajesPushService

/**
 * Actividad de entrada: aloja la presentación, el inicio de sesión, el registro y la recuperación de contraseña.
 * Si ya hay una sesión abierta salta directamente a las pestañas del rol.
 */
class AccesoActivity : AppCompatActivity(), Navegador {

    private var barrasSistema: Insets = Insets.NONE
    private var alturaTeclado = 0
    private var contenidoBajoBarras = false

    private lateinit var vinculo: ActivityAccesoBinding
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vinculo = ActivityAccesoBinding.inflate(layoutInflater)
        setContentView(vinculo.root)
        ViewCompat.setOnApplyWindowInsetsListener(vinculo.flRaizAcceso) { _, insets ->
            barrasSistema = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            alturaTeclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            ajustarMargenes()
            insets
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                contenidoBajoBarras = f is ContenidoBajoBarras
                ajustarMargenes()
            }
        }, false)
        if (savedInstanceState == null) irA(FragSplash(), apilar = false)
    }

    /** Deja libre el borde superior e inferior si la pantalla dibuja bajo las barras del sistema; si no, respeta las barras. */
    private fun ajustarMargenes() {
        val arriba = if (contenidoBajoBarras) 0 else barrasSistema.top
        val abajo = if (contenidoBajoBarras) alturaTeclado else maxOf(barrasSistema.bottom, alturaTeclado)
        vinculo.flContenidoAcceso.setPadding(0, arriba, 0, abajo)
    }

    override fun irA(fragmento: Fragment, apilar: Boolean) {
        val transaccion = supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.pantalla_entrar, R.anim.pantalla_salir, R.anim.pantalla_volver_entrar, R.anim.pantalla_volver_salir)
            .replace(vinculo.flContenidoAcceso.id, fragmento)
        if (apilar) transaccion.addToBackStack(null)
        transaccion.commit()
    }

    override fun volver() {
        onBackPressedDispatcher.onBackPressed()
    }

    override fun cambiarPestana(indice: Int) = Unit

    override fun cerrarSesion() = Unit

    override fun abrirOrden(ordenId: String) = Unit

    override fun abrirInicio() {
        if (authViewModel.usuarioActual.value == null) return
        val principal = Intent(this, PrincipalActivity::class.java)
        intent?.getStringExtra(MensajesPushService.EXTRA_ORDEN_ID)?.let { principal.putExtra(MensajesPushService.EXTRA_ORDEN_ID, it) }
        intent?.getStringExtra(MensajesPushService.EXTRA_NOTIFICACION_ID)?.let { principal.putExtra(MensajesPushService.EXTRA_NOTIFICACION_ID, it) }
        principal.putExtra(MensajesPushService.EXTRA_ES_INGRESO, intent?.getBooleanExtra(MensajesPushService.EXTRA_ES_INGRESO, false) == true)
        startActivity(principal)
        finish()
    }
}
