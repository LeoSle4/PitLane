// Crea la reserva `dnis/{dni}` = {usuarioId} de los usuarios anteriores a la mejora (S1 de
// odd/tasks/firestore-anidamiento-y-unicidad.md). El id del documento es el DNI tal como está guardado en el perfil.
// Solo crea las que faltan; nunca sobrescribe una reserva existente.
// Si dos usuarios comparten DNI NO elige un ganador: imprime el conflicto con sus ids y omite a todos.
//
// Por defecto NO escribe: imprime lo que haría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/rellenarDnis.mjs             # simulacro
//   node scripts/rellenarDnis.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const aplicar = process.argv.includes('--aplicar');

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

const usuarios = (await bd.collection('usuarios').get()).docs;

const porDni = new Map();
for (const d of usuarios) {
  const dni = d.data().dni;
  if (typeof dni !== 'string' || dni === '' || dni.includes('/')) {
    console.warn(`  usuarios/${d.id}: DNI inválido (${JSON.stringify(dni)}); se omite`);
    continue;
  }
  porDni.set(dni, [...(porDni.get(dni) ?? []), d]);
}

let creadas = 0;
let existentes = 0;
let conflictos = 0;
for (const [dni, docs] of porDni) {
  if (docs.length > 1) {
    conflictos += 1;
    console.warn(`CONFLICTO DNI ${dni}: ${docs.map((d) => `usuarios/${d.id} (${d.data().rol})`).join(', ')}; se omiten todos`);
    continue;
  }
  const [d] = docs;
  const reserva = bd.collection('dnis').doc(dni);
  const actual = await reserva.get();
  if (actual.exists) {
    existentes += 1;
    if (actual.data().usuarioId !== d.id) {
      console.warn(`  dnis/${dni} apunta a ${actual.data().usuarioId} y no a usuarios/${d.id}; revísalo a mano`);
    }
    continue;
  }
  creadas += 1;
  console.log(`${aplicar ? 'Crea' : 'Crearía'} dnis/${dni} -> usuarios/${d.id}`);
  if (aplicar) await reserva.create({ usuarioId: d.id });
}
console.log(
  `${aplicar ? 'Listo' : 'Simulacro'}: ${creadas} reservas ${aplicar ? 'creadas' : 'por crear'}, ${existentes} ya existían, ` +
  `${conflictos} DNI en conflicto (resuélvelos a mano).${aplicar ? '' : ' Añade --aplicar para escribir.'}`
);
