import { onDocumentWritten } from 'firebase-functions/firestore';
import { avisoDeOrden } from './avisos.js';
import { bd, Timestamp } from './comun.js';

const ALREADY_EXISTS = 6;
/** Las notificaciones vencen a los 90 días; la política TTL de Firestore las borra al pasar `expiraEn`. */
const VIGENCIA_NOTIFICACION_MS = 90 * 24 * 60 * 60 * 1000;

/**
 * Crea la notificación del cliente cuando se registra una orden o cambia su estado. Solo avisa a clientes con
 * cuenta. El id de la notificación sale del evento, así un reintento del mismo evento no la duplica.
 */
export const notificarOrden = onDocumentWritten('ordenes/{ordenId}', async (event) => {
  const despues = event.data?.after;
  if (!despues?.exists) return;
  const nueva = despues.data();
  const anterior = event.data.before.exists ? event.data.before.data() : null;

  const cliente = await bd.collection('usuarios').doc(nueva.clienteId).get();
  if (!cliente.exists || !cliente.data().tieneCuenta) return;
  const vehiculo = await bd.collection('vehiculos').doc(nueva.vehiculoId).get();
  const aviso = avisoDeOrden(anterior, nueva, vehiculo.data()?.placa ?? '');
  if (!aviso) return;

  const ahora = Date.now();
  try {
    await bd.collection('notificaciones').doc(`n-${event.id}`).create({
      usuarioId: nueva.clienteId,
      titulo: aviso.titulo,
      mensaje: aviso.mensaje,
      fechaHora: ahora,
      expiraEn: Timestamp.fromMillis(ahora + VIGENCIA_NOTIFICACION_MS),
      leida: false,
      ordenId: event.params.ordenId,
      esIngreso: !anterior,
    });
  } catch (error) {
    if (error.code !== ALREADY_EXISTS) throw error;
  }
});
