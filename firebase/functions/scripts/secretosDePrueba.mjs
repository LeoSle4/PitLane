// El emulador de Functions lee los secretos de .secret.local. Para las pruebas basta con valores de mentira:
// en el emulador las funciones no envían correos. El archivo no se versiona.
import { existsSync, writeFileSync } from 'node:fs';

const archivo = new URL('../.secret.local', import.meta.url);
if (!existsSync(archivo)) {
  writeFileSync(archivo, 'CORREO_REMITENTE=pruebas@example.com\nCORREO_CLAVE_APP=clave-de-prueba\n');
}
