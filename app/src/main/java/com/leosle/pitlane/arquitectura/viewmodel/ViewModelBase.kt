package com.leosle.pitlane.arquitectura.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Base de los ViewModels: convierte flujos de los repositorios en [StateFlow] y ejecuta operaciones con callback. */
abstract class ViewModelBase : ViewModel() {

    private val estadosPorClave = HashMap<String, StateFlow<*>>()

    /** Convierte el flujo en un [StateFlow] que arranca al haber observadores y conserva el último valor. */
    protected fun <T> Flow<T>.comoEstado(inicial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inicial)

    /** Entrega siempre el mismo [StateFlow] para una misma [clave], creándolo con [crear] la primera vez. */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> estadoPorClave(clave: String, inicial: T, crear: () -> Flow<T>): StateFlow<T> =
        estadosPorClave.getOrPut(clave) { crear().comoEstado(inicial) } as StateFlow<T>

    /** Ejecuta la operación suspendida y entrega su resultado a [alTerminar] en el hilo principal. */
    protected fun <T> ejecutar(operacion: suspend () -> Resultado<T>, alTerminar: (Resultado<T>) -> Unit) {
        viewModelScope.launch { alTerminar(protegido(operacion)) }
    }
}

/** Mensaje que se muestra cuando una operación falla por una causa técnica, como la red o los permisos. */
internal const val MENSAJE_FALLA_TECNICA = "No se pudo completar la operación. Revisa tu conexión e inténtalo de nuevo"

/** Ejecuta la operación y convierte cualquier falla inesperada en un error con mensaje, sin tragarse la cancelación. */
internal suspend fun <T> protegido(operacion: suspend () -> Resultado<T>): Resultado<T> = try {
    operacion()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Resultado.Error(MENSAJE_FALLA_TECNICA)
}
