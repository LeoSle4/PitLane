package com.leosle.pitlane.arquitectura.datasource.firebase

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Región donde se despliegan las Cloud Functions; la misma que Firestore. */
internal const val REGION_FUNCIONES = "southamerica-west1"

/** Espera el resultado de una tarea de Firebase sin bloquear el hilo; propaga su error o su cancelación. */
internal suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuacion ->
    addOnSuccessListener { continuacion.resume(it) }
    addOnFailureListener { continuacion.resumeWithException(it) }
    addOnCanceledListener { continuacion.cancel() }
}
