// Crea la reserva `placas/{placa}` = {vehiculoId, clienteId} de los vehículos anteriores a la mejora (S4).
// El id del documento es la placa tal como está guardada en el vehículo.
// Solo crea las que faltan; nunca sobrescribe una reserva existente.
// Si dos vehículos comparten placa NO elige un ganador: imprime el conflicto y omite a ambos.
//
// Por defecto NO escribe: imprime lo que haría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/rellenarPlacas.mjs             # simulacro
//   node scripts/rellenarPlacas.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const aplicar = process.argv.includes('--aplicar');

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

const vehiculos = (await bd.collection('vehiculos').get()).docs;

const porPlaca = new Map();
for (const d of vehiculos) {
  const placa = d.data().placa;
  if (typeof placa !== 'string' || placa === '' || placa.includes('/')) {
    console.warn(`  vehiculos/${d.id}: placa inválida (${JSON.stringify(placa)}); se omite`);
    continue;
  }
  porPlaca.set(placa, [...(porPlaca.get(placa) ?? []), d]);
}

let creadas = 0;
let existentes = 0;
let conflictos = 0;
for (const [placa, docs] of porPlaca) {
  if (docs.length > 1) {
    conflictos += 1;
    console.warn(`CONFLICTO placa ${placa}: ${docs.map((d) => `vehiculos/${d.id} (cliente ${d.data().clienteId})`).join(', ')}; se omiten todos`);
    continue;
  }
  const [d] = docs;
  const reserva = bd.collection('placas').doc(placa);
  if ((await reserva.get()).exists) {
    existentes += 1;
    continue;
  }
  creadas += 1;
  console.log(`${aplicar ? 'Crea' : 'Crearía'} placas/${placa} -> vehiculos/${d.id}`);
  if (aplicar) await reserva.create({ vehiculoId: d.id, clienteId: d.data().clienteId });
}
console.log(
  `${aplicar ? 'Listo' : 'Simulacro'}: ${creadas} reservas ${aplicar ? 'creadas' : 'por crear'}, ${existentes} ya existían, ` +
  `${conflictos} placas en conflicto (resuélvelas a mano).${aplicar ? '' : ' Añade --aplicar para escribir.'}`
);
