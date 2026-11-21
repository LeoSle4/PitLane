import { onDocumentWritten } from 'firebase-functions/firestore';
import { bd } from './comun.js';

/**
 * Última actividad de una orden: su último cambio de estado (`historial.last().fechaHora`) o, si aún no tiene
 * historial, su creación. Es la misma definición que usa la app (`Orden.fechaUltimoCambio`).
 */
export function actividadDeOrden(orden) {
  const historial = Array.isArray(orden.historial) ? orden.historial : [];
  const ultimo = historial.length > 0 ? historial[historial.length - 1]?.fechaHora : undefined;
  return numero(ultimo) ?? numero(orden.creadaEn);
}

/** Última actividad de una solicitud: su creación. */
export const actividadDeSolicitud = (solicitud) => numero(solicitud.creadaEn);

const numero = (valor) => (typeof valor === 'number' && Number.isFinite(valor) ? valor : undefined);

/**
 * Sube `ultimaActividad` del cliente a [fecha] si es mayor que la guardada; nunca la baja, porque los eventos pueden
 * llegar desordenados. Aplica a todos los clientes, tengan cuenta o no. Un usuario inexistente o que no sea cliente
 * se deja intacto. Devuelve si escribió.
 */
export async function registrarActividad(clienteId, fecha) {
  if (typeof clienteId !== 'string' || clienteId === '' || fecha === undefined) return false;
  const referencia = bd.collection('usuarios').doc(clienteId);
  return bd.runTransaction(async (transaccion) => {
    const cliente = await transaccion.get(referencia);
    if (!cliente.exists || cliente.data().rol !== 'CLIENTE') return false;
    const guardada = cliente.data().ultimaActividad;
    if (typeof guardada === 'number' && guardada >= fecha) return false;
    transaccion.update(referencia, { ultimaActividad: fecha });
    return true;
  });
}

/**
 * Mantiene `usuarios/{clienteId}.ultimaActividad` para que la app pida los clientes recientes con una consulta
 * ordenada y acotada. Una orden se registra al crearse y en cada cambio; no se borran.
 */
export const actividadPorOrden = onDocumentWritten('ordenes/{ordenId}', async (event) => {
  const despues = event.data?.after;
  if (!despues?.exists) return;
  const orden = despues.data();
  await registrarActividad(orden.clienteId, actividadDeOrden(orden));
});

/** Una solicitud cuenta al crearse; borrarla (lo puede hacer el Jefe) no baja la actividad del cliente. */
export const actividadPorSolicitud = onDocumentWritten('solicitudes/{solicitudId}', async (event) => {
  const despues = event.data?.after;
  if (!despues?.exists) return;
  const solicitud = despues.data();
  await registrarActividad(solicitud.clienteId, actividadDeSolicitud(solicitud));
});
