// Pone `expiraEn` a las notificaciones anteriores a la mejora (S9): su `fechaHora` más 90 días.
// Sin ese campo la política TTL de Firestore no las borraría nunca.
// Solo escribe las que no lo tienen; nunca sobrescribe un `expiraEn` existente.
// Recorre el grupo de colecciones `notificaciones`: las bandejas `usuarios/{id}/notificaciones` y, si quedara alguna
// sin mover, la colección de primer nivel `notificaciones`.
//
// Por defecto NO escribe: imprime lo que haría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/rellenarExpiracion.mjs             # simulacro
//   node scripts/rellenarExpiracion.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';

const aplicar = process.argv.includes('--aplicar');
const NOVENTA_DIAS_MS = 90 * 24 * 60 * 60 * 1000;

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

const pendientes = (await bd.collectionGroup('notificaciones').get()).docs.filter((d) => !('expiraEn' in d.data()));
let omitidas = 0;
const lote = [];
for (const d of pendientes) {
  const fecha = d.data().fechaHora;
  if (typeof fecha !== 'number') {
    omitidas += 1;
    console.warn(`  ${d.ref.path}: fechaHora inválida (${JSON.stringify(fecha)}); se omite`);
    continue;
  }
  lote.push([d.ref, Timestamp.fromMillis(fecha + NOVENTA_DIAS_MS)]);
}

console.log(`${aplicar ? 'Escribiendo' : 'Rellenaría'} expiraEn en ${lote.length} notificaciones (${omitidas} omitidas, ${pendientes.length - lote.length - omitidas} ya lo tenían).`);
if (aplicar) {
  for (let i = 0; i < lote.length; i += 400) {
    const escritura = bd.batch();
    for (const [ref, expiraEn] of lote.slice(i, i + 400)) escritura.update(ref, { expiraEn });
    await escritura.commit();
  }
  console.log('Listo.');
} else {
  console.log('Simulacro: no se escribió nada. Añade --aplicar para escribir.');
}
