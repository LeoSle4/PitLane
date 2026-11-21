// Rellena las instantáneas de los documentos anteriores a la mejora (S1a):
//   ordenes:     cliente {nombres, apellidos, dni, telefono}, vehiculo {placa, marca, modelo, anio, color}, tecnico {nombres, apellidos}
//   solicitudes: cliente y vehiculo
// Solo completa lo que falta; nunca sobrescribe una instantánea existente. Es una foto de los datos de hoy.
// Cuando el documento termina con todas las instantáneas que le corresponden, escribe también `esquema: 2` en la misma
// actualización (nunca sobrescribe un `esquema` existente). Un documento que sigue incompleto (porque el cliente, el
// vehículo o el técnico referenciado ya no existe) queda sin `esquema`, es decir, de la versión 1.
//
// Por defecto NO escribe: imprime lo que cambiaría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/rellenarInstantaneas.mjs             # simulacro
//   node scripts/rellenarInstantaneas.mjs --aplicar   # escribe
//
// Con FIRESTORE_EMULATOR_HOST definido escribe en el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const aplicar = process.argv.includes('--aplicar');

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

const resumenCliente = (u) => ({ nombres: u.nombres ?? '', apellidos: u.apellidos ?? '', dni: u.dni ?? '', telefono: u.telefono ?? '' });
const resumenTecnico = (u) => ({ nombres: u.nombres ?? '', apellidos: u.apellidos ?? '' });
const resumenVehiculo = (v) => ({ placa: v.placa ?? '', marca: v.marca ?? '', modelo: v.modelo ?? '', anio: v.anio ?? 0, color: v.color ?? '' });

const datos = async (coleccion) => new Map((await bd.collection(coleccion).get()).docs.map((d) => [d.id, d.data()]));
const usuarios = await datos('usuarios');
const vehiculos = await datos('vehiculos');

/** Cambios que le faltan a un documento; los referenciados que ya no existen se avisan y se omiten. */
function faltantes(id, doc, conTecnico) {
  const cambios = {};
  const pendiente = (campo, origen, tipo, convertir) => {
    if (doc[campo] !== undefined) return;
    if (!origen) console.warn(`  ${id}: no existe el ${tipo} referenciado; se omite ${campo}`);
    else cambios[campo] = convertir(origen);
  };
  pendiente('cliente', usuarios.get(doc.clienteId), 'cliente', resumenCliente);
  pendiente('vehiculo', vehiculos.get(doc.vehiculoId), 'vehículo', resumenVehiculo);
  if (conTecnico) pendiente('tecnico', usuarios.get(doc.tecnicoId), 'técnico', resumenTecnico);
  // La versión nunca miente: solo se escribe si, con estos cambios, el documento tiene todas las instantáneas.
  const requeridas = conTecnico ? ['cliente', 'vehiculo', 'tecnico'] : ['cliente', 'vehiculo'];
  const completo = requeridas.every((campo) => doc[campo] !== undefined || cambios[campo] !== undefined);
  if (completo && doc.esquema === undefined) cambios.esquema = 2;
  return cambios;
}

let total = 0;
for (const [coleccion, conTecnico] of [['ordenes', true], ['solicitudes', false]]) {
  const docs = await bd.collection(coleccion).get();
  for (const d of docs.docs) {
    const cambios = faltantes(`${coleccion}/${d.id}`, d.data(), conTecnico);
    const campos = Object.keys(cambios);
    if (campos.length === 0) continue;
    total += 1;
    console.log(`${aplicar ? 'Rellena' : 'Rellenaría'} ${coleccion}/${d.id}: ${campos.join(', ')}`);
    if (aplicar) await d.ref.update(cambios);
  }
}
console.log(aplicar ? `Listo: ${total} documentos actualizados.` : `Simulacro: ${total} documentos cambiarían. Añade --aplicar para escribir.`);
