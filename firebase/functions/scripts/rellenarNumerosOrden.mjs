// Crea la reserva `numerosOrden/{numero}` = {ordenId} de las órdenes anteriores a la mejora (T6 de
// odd/tasks/firestore-anidamiento-y-unicidad.md). El id del documento es el número tal como está guardado en la orden.
// Solo crea las que faltan; nunca sobrescribe una reserva existente.
// Si dos órdenes comparten número NO elige una ganadora: imprime el conflicto con sus ids y las omite todas.
//
// Por defecto NO escribe: imprime lo que haría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/rellenarNumerosOrden.mjs             # simulacro
//   node scripts/rellenarNumerosOrden.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const aplicar = process.argv.includes('--aplicar');

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

const ordenes = (await bd.collection('ordenes').get()).docs;

const porNumero = new Map();
for (const d of ordenes) {
  const numero = d.data().numero;
  if (typeof numero !== 'string' || numero === '' || numero.includes('/')) {
    console.warn(`  ordenes/${d.id}: número inválido (${JSON.stringify(numero)}); se omite`);
    continue;
  }
  porNumero.set(numero, [...(porNumero.get(numero) ?? []), d]);
}

let creadas = 0;
let existentes = 0;
let conflictos = 0;
for (const [numero, docs] of porNumero) {
  if (docs.length > 1) {
    conflictos += 1;
    console.warn(`CONFLICTO número ${numero}: ${docs.map((d) => `ordenes/${d.id} (${d.data().estado})`).join(', ')}; se omiten todas`);
    continue;
  }
  const [d] = docs;
  const reserva = bd.collection('numerosOrden').doc(numero);
  const actual = await reserva.get();
  if (actual.exists) {
    existentes += 1;
    if (actual.data().ordenId !== d.id) {
      console.warn(`  numerosOrden/${numero} apunta a ${actual.data().ordenId} y no a ordenes/${d.id}; revísalo a mano`);
    }
    continue;
  }
  creadas += 1;
  console.log(`${aplicar ? 'Crea' : 'Crearía'} numerosOrden/${numero} -> ordenes/${d.id}`);
  if (aplicar) await reserva.create({ ordenId: d.id });
}
console.log(
  `${aplicar ? 'Listo' : 'Simulacro'}: ${creadas} reservas ${aplicar ? 'creadas' : 'por crear'}, ${existentes} ya existían, ` +
  `${conflictos} números en conflicto (resuélvelos a mano).${aplicar ? '' : ' Añade --aplicar para escribir.'}`
);
