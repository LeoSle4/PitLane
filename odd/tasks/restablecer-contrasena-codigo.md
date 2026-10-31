Objetivo: restablecer la contraseña con un código de 6 dígitos enviado por correo y cambiarla dentro de la app, en lugar del formulario web de Firebase.
Rama: nueva desde main (el agente no hace commits; la entrega es por script). Pruebas: `./gradlew testDebugUnitTest` (JDK temurin-25) y `cd firebase/functions && npm test` (emulador).

## Specs
- S1: "no podemos hacer que llegue un codigo al correo, y dentro de la app se pueda cambiar la contrasena". El usuario escribe su correo, recibe un código de 6 dígitos y, en la app, escribe el código y la contraseña nueva.
- S2 (decidido con el usuario): el correo lo envía una cuenta de Gmail del taller con contraseña de aplicación, desde Cloud Functions. El remitente y la contraseña de aplicación son secretos de Firebase, nunca van al repositorio.
- S3 (seguridad propuesta y aceptada): el código vence a los 10 minutos, admite 5 intentos, se guarda cifrado (hash) y no se puede pedir otro antes de 60 segundos; la respuesta al pedir el código es la misma exista o no la cuenta. Al cambiar la contraseña se cierran las demás sesiones de esa cuenta.
- S4: la contraseña nueva cumple la regla de la app: mínimo 8 caracteres, con una letra y un número.

## Tasks
- [x] T1 (S1-S4) Funciones `solicitarCodigo` y `restablecerContrasena` (`src/restablecer.js`, `src/codigos.js`, nodemailer 10.3.1, secretos CORREO_REMITENTE y CORREO_CLAVE_APP); en el emulador el código se escribe en el registro en lugar de enviarse. 39 de 39 pruebas de funciones y 52 de 52 de reglas (la nueva: nadie lee `codigosRestablecimiento`). Sin RED observado: se escribió la implementación antes que las pruebas. Sin commit.
- [x] T2 (S1, S4) `FragRecuperar` en dos pasos (correo; código y contraseña nueva con cuenta regresiva para reenviar), `Validador.codigo`, `IUsuariosDataSource.solicitarCodigoDeRestablecimiento` y `restablecerContrasena` (reemplazan al enlace de Firebase). RED observado, 112 pruebas en verde (108 + 4). Instalado en el emulador; sin probar contra Firebase real hasta desplegar y cargar los secretos. Sin commit.
- [x] T3 (S2) Instrucciones en `firebase/README.md` (contraseña de aplicación de Gmail, `firebase functions:secrets:set`, despliegue) y script `scripts/entrega_restablecer.sh`. PENDIENTE del usuario: crear la contraseña de aplicación, guardar los secretos, desplegar y probar.

## Log
- L1 (2026-10-31) Usuario: "quiero que el restablecimienot de la contrasenam lo probe y te envia a un formulario muy simple, no podemos hacer que llegue un codigo al correo, y dentro de la app se pueda cambiar la contrasena".
- L2 Propuesta: código de 6 dígitos enviado por funciones del servidor con Gmail. Usuario: "Si" (tiene o puede crear una cuenta de Gmail del taller).
