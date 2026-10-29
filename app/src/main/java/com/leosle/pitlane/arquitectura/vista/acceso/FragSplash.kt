package com.leosle.pitlane.arquitectura.vista.acceso

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.leosle.pitlane.databinding.AccFragSplashBinding
import com.leosle.pitlane.arquitectura.vista.comun.FragmentoVinculado
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Presentación inicial: reproduce el logo animado y, pasado un instante, abre el inicio de sesión
 * o entra directamente a la aplicación si hay una sesión guardada, esperando a que se restablezca.
 */
class FragSplash : FragmentoVinculado<AccFragSplashBinding>(AccFragSplashBinding::inflate) {

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        (vinculo.imgLogoSplash.drawable as? Animatable)?.start()
        viewLifecycleOwner.lifecycleScope.launch {
            delay(DURACION_PRESENTACION_MS)
            sesionViewModel.restaurarSesion { usuario ->
                if (view == null) return@restaurarSesion
                if (usuario != null) navegador.abrirInicio() else navegador.irA(FragLogin(), apilar = false)
            }
        }
    }

    private companion object {
        const val DURACION_PRESENTACION_MS = 1_600L
    }
}
