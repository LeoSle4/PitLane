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

Requiere Node, Java y `firebase-tools`. Las pruebas usan un proyecto de demostración y no tocan el proyecto real.

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

```bash
cd firebase/functions
npm install
npm test      # emuladores de Auth, Firestore y Functions; 25 pruebas
```

Una cuenta desactivada ya no puede iniciar sesión ni renovar su sesión, pero un token ya emitido sigue valiendo
hasta una hora (lo que dura un token de Firebase).

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
