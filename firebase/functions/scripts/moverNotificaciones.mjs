// Mueve las notificaciones anteriores a la mejora (S2) de la colección de primer nivel `notificaciones/{id}` a la
// bandeja de su dueño, `usuarios/{usuarioId}/notificaciones/{id}`, con el mismo id y los mismos campos.
// Cada copia y el borrado de su original van en el mismo lote: o se mueve o queda donde estaba.
// Omite (e informa) las que no tienen un `usuarioId` válido o cuyo usuario no existe; esas quedan en su lugar.
// Nunca sobrescribe una notificación que ya esté en la bandeja: si existe, solo borra el original repetido.
//
// Por defecto NO escribe: imprime lo que haría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/moverNotificaciones.mjs             # simulacro
//   node scripts/moverNotificaciones.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const aplicar = process.argv.includes('--aplicar');
const TAMANO_LOTE = 200; // dos escrituras por notificación; Firestore admite 500 por lote

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

const originales = (await bd.collection('notificaciones').get()).docs;

let omitidas = 0;
let repetidas = 0;
const movimientos = [];
for (const d of originales) {
  const usuarioId = d.data().usuarioId;
  if (typeof usuarioId !== 'string' || usuarioId === '' || usuarioId.includes('/')) {
    omitidas += 1;
    console.warn(`  notificaciones/${d.id}: usuarioId inválido (${JSON.stringify(usuarioId)}); se omite`);
    continue;
  }
  const usuario = bd.collection('usuarios').doc(usuarioId);
  if (!(await usuario.get()).exists) {
    omitidas += 1;
    console.warn(`  notificaciones/${d.id}: el usuario ${usuarioId} no existe; se omite`);
    continue;
  }
  const destino = usuario.collection('notificaciones').doc(d.id);
  const yaMovida = (await destino.get()).exists;
  if (yaMovida) repetidas += 1;
  console.log(`${aplicar ? 'Mueve' : 'Movería'} notificaciones/${d.id} -> ${destino.path}${yaMovida ? ' (ya estaba; solo borra el original)' : ''}`);
  movimientos.push({ origen: d.ref, destino, datos: d.data(), yaMovida });
}

if (aplicar) {
  for (let i = 0; i < movimientos.length; i += TAMANO_LOTE) {
    const lote = bd.batch();
    for (const { origen, destino, datos, yaMovida } of movimientos.slice(i, i + TAMANO_LOTE)) {
      if (!yaMovida) lote.create(destino, datos);
      lote.delete(origen);
    }
    await lote.commit();
  }
}
console.log(
  `${aplicar ? 'Listo' : 'Simulacro'}: ${movimientos.length} notificaciones ${aplicar ? 'movidas' : 'por mover'} ` +
  `(${repetidas} ya estaban en la bandeja), ${omitidas} omitidas (revísalas a mano).${aplicar ? '' : ' Añade --aplicar para escribir.'}`
);
