// Completa las entradas del historial de las órdenes anteriores a la firma con `motivo: null` y `usuarioNombre: null`
// cuando les faltan. Las reglas exigen que la app reenvíe el historial ya guardado idéntico, y el mapeador escribe
// siempre esas claves: sin este relleno, una orden con entradas incompletas no se podría editar ni avanzar.
// Ejecutarlo ANTES de desplegar las reglas. No cambia valores existentes ni el orden de las entradas.
//
// Por defecto NO escribe: imprime lo que haría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/rellenarHistorial.mjs             # simulacro
//   node scripts/rellenarHistorial.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { normalizarHistorial } from '../src/historial.js';

const aplicar = process.argv.includes('--aplicar');

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

const ordenes = (await bd.collection('ordenes').get()).docs;
const lote = [];
for (const d of ordenes) {
  const { historial, cambio } = normalizarHistorial(d.data().historial);
  if (cambio) lote.push([d.ref, historial]);
}

console.log(`${aplicar ? 'Completando' : 'Completaría'} el historial de ${lote.length} de ${ordenes.length} órdenes.`);
if (aplicar) {
  for (let i = 0; i < lote.length; i += 400) {
    const escritura = bd.batch();
    for (const [ref, historial] of lote.slice(i, i + 400)) escritura.update(ref, { historial });
    await escritura.commit();
  }
  console.log('Listo.');
} else {
  console.log('Simulacro: no se escribió nada. Añade --aplicar para escribir.');
}
