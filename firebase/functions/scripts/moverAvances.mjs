// Mueve los avances anteriores a la mejora (S3) de la colección de primer nivel `avances/{id}` a la subcolección de su
// orden, `ordenes/{ordenId}/avances/{id}`, con el mismo id. La copia ya no lleva `ordenId` (lo dice la ruta) ni `acceso`
// (las reglas autorizan desde la orden padre); el resto de los campos va tal cual.
// Cada copia y el borrado de su original van en el mismo lote: o se mueve o queda donde estaba.
// Omite (e informa) los que no tienen un `ordenId` válido o cuya orden no existe; esos quedan en su lugar.
// Nunca sobrescribe un avance que ya esté en su orden: si existe, solo borra el original repetido.
//
// Por defecto NO escribe: imprime lo que haría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/moverAvances.mjs             # simulacro
//   node scripts/moverAvances.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const aplicar = process.argv.includes('--aplicar');
const TAMANO_LOTE = 200; // dos escrituras por avance; Firestore admite 500 por lote

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

const originales = (await bd.collection('avances').get()).docs;

let omitidos = 0;
let repetidos = 0;
const movimientos = [];
for (const d of originales) {
  const { ordenId, acceso, ...datos } = d.data();
  if (typeof ordenId !== 'string' || ordenId === '' || ordenId.includes('/')) {
    omitidos += 1;
    console.warn(`  avances/${d.id}: ordenId inválido (${JSON.stringify(ordenId)}); se omite`);
    continue;
  }
  const orden = bd.collection('ordenes').doc(ordenId);
  if (!(await orden.get()).exists) {
    omitidos += 1;
    console.warn(`  avances/${d.id}: la orden ${ordenId} no existe; se omite`);
    continue;
  }
  const destino = orden.collection('avances').doc(d.id);
  const yaMovido = (await destino.get()).exists;
  if (yaMovido) repetidos += 1;
  console.log(`${aplicar ? 'Mueve' : 'Movería'} avances/${d.id} -> ${destino.path}${yaMovido ? ' (ya estaba; solo borra el original)' : ''}`);
  movimientos.push({ origen: d.ref, destino, datos, yaMovido });
}

if (aplicar) {
  for (let i = 0; i < movimientos.length; i += TAMANO_LOTE) {
    const lote = bd.batch();
    for (const { origen, destino, datos, yaMovido } of movimientos.slice(i, i + TAMANO_LOTE)) {
      if (!yaMovido) lote.create(destino, datos);
      lote.delete(origen);
    }
    await lote.commit();
  }
}
console.log(
  `${aplicar ? 'Listo' : 'Simulacro'}: ${movimientos.length} avances ${aplicar ? 'movidos' : 'por mover'} ` +
  `(${repetidos} ya estaban en su orden), ${omitidos} omitidos (revísalos a mano).${aplicar ? '' : ' Añade --aplicar para escribir.'}`
);
