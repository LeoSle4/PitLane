// Rellena los términos de búsqueda (S8) de los documentos anteriores a la mejora:
//   - `terminos` en los `usuarios` con rol CLIENTE que no lo tienen.
//   - `terminosPlaca` en los `vehiculos` que no lo tienen.
// Nunca sobrescribe un campo existente y no toca a técnicos ni al Jefe.
//
// Por defecto NO escribe: imprime lo que haría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/rellenarBusqueda.mjs             # simulacro
//   node scripts/rellenarBusqueda.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { terminosDePersona, terminosDePlaca } from '../src/busqueda.js';

const aplicar = process.argv.includes('--aplicar');

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

async function rellenar(coleccion, filtro, campo, calcular) {
  const documentos = (await bd.collection(coleccion).get()).docs.filter((d) => filtro(d.data()));
  let porEscribir = 0;
  let yaTenian = 0;
  let lote = bd.batch();
  let enLote = 0;
  for (const d of documentos) {
    if (campo in d.data()) {
      yaTenian += 1;
      continue;
    }
    porEscribir += 1;
    console.log(`${aplicar ? 'Escribe' : 'Escribiría'} ${coleccion}/${d.id}.${campo}`);
    if (!aplicar) continue;
    lote.update(d.ref, { [campo]: calcular(d.data()) });
    enLote += 1;
    if (enLote === 400) {
      await lote.commit();
      lote = bd.batch();
      enLote = 0;
    }
  }
  if (aplicar && enLote > 0) await lote.commit();
  return { porEscribir, yaTenian };
}

const clientes = await rellenar('usuarios', (u) => u.rol === 'CLIENTE', 'terminos',
  (u) => terminosDePersona(u.nombres ?? '', u.apellidos ?? '', u.dni ?? ''));
const vehiculos = await rellenar('vehiculos', () => true, 'terminosPlaca', (v) => terminosDePlaca(v.placa ?? ''));

const verbo = aplicar ? 'escritos' : 'por escribir';
console.log(
  `${aplicar ? 'Listo' : 'Simulacro'}: clientes ${clientes.porEscribir} ${verbo} (${clientes.yaTenian} ya tenían), ` +
  `vehículos ${vehiculos.porEscribir} ${verbo} (${vehiculos.yaTenian} ya tenían).${aplicar ? '' : ' Añade --aplicar para escribir.'}`
);
