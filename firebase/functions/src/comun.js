import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/https';
import { setGlobalOptions } from 'firebase-functions/options';

/** Región de las funciones; la misma que Firestore. La app la usa en `REGION_FUNCIONES`. */
export const REGION = 'southamerica-west1';

// Cada módulo importa este archivo antes de declarar sus funciones, así que esto se ejecuta primero.
setGlobalOptions({ region: REGION, maxInstances: 5 });
initializeApp();

export const bd = getFirestore();
export const autenticacion = getAuth();
export { FieldValue, HttpsError };

/** Devuelve el usuarioId y el rol de quien llama, o rechaza si no tiene sesión con los reclamos del servidor. */
export function sesion(request) {
  const token = request.auth?.token;
  if (!token || typeof token.usuarioId !== 'string' || typeof token.rol !== 'string') {
    throw new HttpsError('unauthenticated', 'Inicia sesión para continuar');
  }
  return { usuarioId: token.usuarioId, rol: token.rol };
}

/** Igual que [sesion], pero solo admite al Jefe de Taller. */
export function exigirJefe(request) {
  const datos = sesion(request);
  if (datos.rol !== 'JEFE') throw new HttpsError('permission-denied', 'Solo el Jefe de Taller puede hacer esto');
  return datos;
}
