// Pone `ultimaActividad` a los clientes anteriores a la mejora (S11): el máximo entre el último cambio de sus órdenes
// (`historial.last().fechaHora`, o `creadaEn` si no tiene historial) y la creación de sus solicitudes.
// Sin ese campo un cliente no aparece en "recientes". Solo escribe si el campo falta o es menor; nunca lo baja.
//
// Por defecto NO escribe: imprime lo que haría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/rellenarActividad.mjs             # simulacro
//   node scripts/rellenarActividad.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const aplicar = process.argv.includes('--aplicar');

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

// Misma definición que src/actividad.js (no se importa: ese módulo inicia la app de las funciones).
const numero = (valor) => (typeof valor === 'number' && Number.isFinite(valor) ? valor : undefined);
const actividadDeOrden = (orden) => {
  const historial = Array.isArray(orden.historial) ? orden.historial : [];
  return numero(historial.length > 0 ? historial[historial.length - 1]?.fechaHora : undefined) ?? numero(orden.creadaEn);
};
const actividadDeSolicitud = (solicitud) => numero(solicitud.creadaEn);

const actividad = new Map();
const registrar = (clienteId, fecha) => {
  if (typeof clienteId !== 'string' || fecha === undefined) return;
  actividad.set(clienteId, Math.max(actividad.get(clienteId) ?? -Infinity, fecha));
};
for (const d of (await bd.collection('ordenes').get()).docs) registrar(d.data().clienteId, actividadDeOrden(d.data()));
for (const d of (await bd.collection('solicitudes').get()).docs) registrar(d.data().clienteId, actividadDeSolicitud(d.data()));

const clientes = (await bd.collection('usuarios').where('rol', '==', 'CLIENTE').get()).docs;
const lote = [];
let sinActividad = 0;
let alDia = 0;
for (const d of clientes) {
  const calculada = actividad.get(d.id);
  if (calculada === undefined) { sinActividad += 1; continue; }
  const guardada = d.data().ultimaActividad;
  if (typeof guardada === 'number' && guardada >= calculada) { alDia += 1; continue; }
  lote.push([d.ref, calculada]);
}

console.log(`${aplicar ? 'Escribiendo' : 'Rellenaría'} ultimaActividad en ${lote.length} clientes (${sinActividad} sin órdenes ni solicitudes, ${alDia} ya al día).`);
if (aplicar) {
  for (let i = 0; i < lote.length; i += 400) {
    const escritura = bd.batch();
    for (const [ref, ultimaActividad] of lote.slice(i, i + 400)) escritura.update(ref, { ultimaActividad });
    await escritura.commit();
  }
  console.log('Listo.');
} else {
  console.log('Simulacro: no se escribió nada. Añade --aplicar para escribir.');
}
