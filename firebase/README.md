# Reglas de seguridad de Firebase

Reglas de Firestore y de Storage de PitLane y sus pruebas. El diseño completo está en `odd/tasks/firebase-datasources.md`
(puntos S8 a S12).

## Cómo funcionan

- Cada cuenta lleva los reclamos `usuarioId` (id de su documento en `usuarios`) y `rol`. Los pone el servidor.
- Los documentos de `usuarios`, `vehiculos`, `solicitudes`, `ordenes` y `avances` tienen un campo `acceso` con los
  `usuarioId` que pueden leerlos, además del Jefe. La app consulta con `acceso array-contains <usuarioId>`.
- `notificaciones` se filtran por `usuarioId`.
- Las fotos van en `fotos/{clienteId}/{archivo}`; las ve el Jefe, el cliente dueño y los técnicos que tienen una orden de
  ese cliente.

## Probar las reglas en el emulador

Requiere Node, Java y `firebase-tools`. Las pruebas (53 de reglas) usan un proyecto de demostración y no tocan el proyecto real.

```bash
cd firebase
npm install
npm test
```

## Funciones del servidor (`functions/`)

Node 22, región `southamerica-west1`. Son las operaciones que la app no puede hacer sola:

| Función | Quién la llama | Qué hace |
| --- | --- | --- |
| `registrarCliente` | sin sesión | Crea la cuenta del cliente y une el registro previo del Jefe si el DNI ya existía |
| `crearTecnico` | Jefe | Crea la cuenta de un técnico sin cerrar la sesión del Jefe |
| `desactivarCuenta` | Jefe, o el cliente sobre su propia cuenta | Inhabilita la cuenta y revoca sus sesiones |
| `reactivarCuenta` | Jefe | Vuelve a habilitar la cuenta |
| `verificarPlaca` | cualquier sesión | Dice si una placa ya existe; solo el Jefe recibe el dueño |
| `notificarOrden` | automática | Crea el aviso del cliente cuando se registra una orden o cambia su estado |
| `solicitarCodigo` | sin sesión | Envía por correo un código de 6 dígitos para restablecer la contraseña |
| `restablecerContrasena` | sin sesión | Cambia la contraseña si el código es correcto y sigue vigente |

```bash
cd firebase/functions
npm install
npm test      # emuladores de Auth, Firestore y Functions; 39 pruebas
```

Una cuenta desactivada ya no puede iniciar sesión ni renovar su sesión, pero un token ya emitido sigue valiendo
hasta una hora (lo que dura un token de Firebase).

### Correo para restablecer la contraseña

Los códigos los envía una cuenta de Gmail del taller. Se configura una sola vez:

1. En la cuenta de Gmail, activa la **verificación en dos pasos** (Cuenta de Google, Seguridad).
2. Crea una **contraseña de aplicación** en https://myaccount.google.com/apppasswords con el nombre `PitLane` y copia
   las 16 letras que muestra.
3. Guarda el correo y esa contraseña como secretos de Firebase; nunca van al repositorio:

```bash
cd firebase
firebase functions:secrets:set CORREO_REMITENTE    # pega el correo de Gmail
firebase functions:secrets:set CORREO_CLAVE_APP    # pega la contraseña de aplicación
firebase deploy --only functions
```

El código vence a los 10 minutos, admite 5 intentos y no se puede pedir otro antes de un minuto. En el emulador no se
envían correos: el código aparece en el registro de la función.

### Crear el primer Jefe

Nadie puede crear al Jefe desde la app, así que se siembra una sola vez. Necesitas una clave de cuenta de servicio
(consola de Firebase, Configuración del proyecto, Cuentas de servicio). Guárdala **fuera del repositorio**.

```bash
cd firebase/functions
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
read -s JEFE_CONTRASENA && export JEFE_CONTRASENA
JEFE_CORREO=... JEFE_NOMBRES=... JEFE_APELLIDOS=... JEFE_DNI=... JEFE_TELEFONO=... node scripts/sembrarJefe.mjs
```

## Desplegar las reglas

Solo después de que las pruebas pasen. Con `firebase login` hecho:

```bash
cd firebase
firebase deploy --only firestore:rules,storage
firebase deploy --only functions
```

Las reglas de Storage consultan Firestore (`firestore.get`). Al desplegar, Firebase puede pedir permiso para darle ese
acceso al servicio de Storage; acéptalo.
