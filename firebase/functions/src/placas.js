import { onCall } from 'firebase-functions/https';
import { bd, HttpsError, sesion } from './comun.js';

/**
 * Busca una placa entre todos los vehículos, porque un cliente solo puede leer los suyos. Responde `existe` y,
 * solo si quien pregunta es el Jefe, el `clienteId` del dueño.
 */
export const verificarPlaca = onCall(async (request) => {
  const quien = sesion(request);
  const placa = typeof request.data?.placa === 'string' ? request.data.placa.trim() : '';
  if (!placa) throw new HttpsError('invalid-argument', 'Ingresa la placa');
  const resultado = await bd.collection('vehiculos').where('placa', '==', placa).limit(1).get();
  if (resultado.empty) return { existe: false };
  return quien.rol === 'JEFE'
    ? { existe: true, clienteId: resultado.docs[0].data().clienteId }
    : { existe: true };
});
