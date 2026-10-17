package com.leosle.pitlane.vista.perfil

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Modos de apariencia que el usuario puede elegir. */
enum class ModoTema(val modoNoche: Int) {
    CLARO(AppCompatDelegate.MODE_NIGHT_NO),
    OSCURO(AppCompatDelegate.MODE_NIGHT_YES),
    SISTEMA(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
}

/** Guarda en el teléfono el modo de apariencia elegido y lo aplica a toda la aplicación. */
object PreferenciasTema {

    private const val ARCHIVO = "preferencias_apariencia"
    private const val CLAVE_MODO = "modo_tema"

    /** Lee el modo guardado; si el usuario nunca eligió uno devuelve [ModoTema.SISTEMA]. */
    fun leer(contexto: Context): ModoTema {
        val nombre = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).getString(CLAVE_MODO, null)
        return ModoTema.values().firstOrNull { it.name == nombre } ?: ModoTema.SISTEMA
    }

    /** Aplica el modo guardado sin cambiar nada más. */
    fun aplicarGuardado(contexto: Context) {
        AppCompatDelegate.setDefaultNightMode(leer(contexto).modoNoche)
    }

    /** Guarda el modo elegido y lo aplica de inmediato. */
    fun guardarYAplicar(contexto: Context, modo: ModoTema) {
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit().putString(CLAVE_MODO, modo.name).apply()
        AppCompatDelegate.setDefaultNightMode(modo.modoNoche)
    }
}
