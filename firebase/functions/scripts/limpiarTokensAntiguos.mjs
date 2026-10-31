// Borra los documentos antiguos de la colección raíz `tokensDispositivo/{usuarioId}` (S2).
// El token ahora vive en `usuarios/{usuarioId}/tokensDispositivo/{token}`, un documento por dispositivo. La app lo
// vuelve a registrar sola cada vez que se abre, así que los documentos de la raíz quedan huérfanos y nadie los lee.
// Ejecútalo cuando los dispositivos ya hayan abierto la app nueva.
//
// Por defecto NO borra: imprime lo que eliminaría. Para aplicar los cambios añade --aplicar.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
//   node scripts/limpiarTokensAntiguos.mjs             # simulacro
//   node scripts/limpiarTokensAntiguos.mjs --aplicar   # borra
//
// Con FIRESTORE_EMULATOR_HOST definido actúa sobre el emulador.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const aplicar = process.argv.includes('--aplicar');

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? 'pitlane-ad951' });
const bd = getFirestore();

let total = 0;
for (const d of (await bd.collection('tokensDispositivo').get()).docs) {
  total += 1;
  console.log(`${aplicar ? 'Borra' : 'Borraría'} tokensDispositivo/${d.id}`);
  if (aplicar) await d.ref.delete();
}
console.log(aplicar ? `Listo: ${total} documentos borrados.` : `Simulacro: ${total} documentos se borrarían. Añade --aplicar para borrar.`);
