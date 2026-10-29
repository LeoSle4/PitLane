// Crea la cuenta del primer Jefe de Taller (nadie puede crearla desde la app). Se ejecuta una sola vez:
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   read -s JEFE_CONTRASENA && export JEFE_CONTRASENA
//   JEFE_CORREO=... JEFE_NOMBRES=... JEFE_APELLIDOS=... JEFE_DNI=... JEFE_TELEFONO=... node scripts/sembrarJefe.mjs
//
// Con FIREBASE_AUTH_EMULATOR_HOST y FIRESTORE_EMULATOR_HOST definidos escribe en los emuladores.
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { normalizarCuenta, validarCuenta } from '../src/validacion.js';

const cuenta = normalizarCuenta({
  nombres: process.env.JEFE_NOMBRES, apellidos: process.env.JEFE_APELLIDOS, dni: process.env.JEFE_DNI,
  telefono: process.env.JEFE_TELEFONO, correo: process.env.JEFE_CORREO, contrasena: process.env.JEFE_CONTRASENA,
});
const error = validarCuenta(cuenta);
if (error) {
  console.error(`Datos inválidos: ${error}`);
  process.exit(1);
}

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const autenticacion = getAuth();
const bd = getFirestore();

const existente = await autenticacion.getUserByEmail(cuenta.correo).catch(() => null);
const registro = existente ?? await autenticacion.createUser({
  email: cuenta.correo, password: cuenta.contrasena, displayName: `${cuenta.nombres} ${cuenta.apellidos}`,
});
const id = existente?.customClaims?.usuarioId ?? `u-jefe-${bd.collection('usuarios').doc().id}`;
await autenticacion.setCustomUserClaims(registro.uid, { usuarioId: id, rol: 'JEFE' });
await bd.collection('usuarios').doc(id).set({
  nombres: cuenta.nombres, apellidos: cuenta.apellidos, dni: cuenta.dni, telefono: cuenta.telefono,
  correo: cuenta.correo, rol: 'JEFE', activo: true, tieneCuenta: true, authUid: registro.uid,
  acceso: FieldValue.arrayUnion(id),
}, { merge: true });
console.log(`Jefe listo: ${cuenta.correo} (usuarioId ${id})`);
