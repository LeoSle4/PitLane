# Reglas de seguridad de Firebase

Reglas de Firestore y de Storage de PitLane y sus pruebas. El diseño completo está en `odd/tasks/firebase-datasources.md`
(puntos S8 a S12).

## Cómo funcionan

- Cada cuenta lleva los reclamos `usuarioId` (id de su documento en `usuarios`) y `rol`. Los pone el servidor.
- Los documentos de `usuarios`, `vehiculos`, `solicitudes`, `ordenes` y `avances` tienen un campo `acceso` con los
  `usuarioId` que pueden leerlos, además del Jefe. La app consulta con `acceso array-contains <usuarioId>`.
- `notificaciones` se filtran por `usuarioId`.
- En `ordenes`, el Jefe solo cambia el estado para anular una orden Ingresado o entregar una orden Lista cuya conformidad ya estaba guardada (RN-14); la conformidad no se borra ni se cambia, y la orden solo acepta los campos que define.
- Las fotos van en `fotos/{clienteId}/{archivo}`; las ve el Jefe, el cliente dueño y los técnicos que tienen una orden de
  ese cliente.

## Probar las reglas en el emulador

Requiere Node, Java y `firebase-tools`. Las pruebas (57 de reglas) usan un proyecto de demostración y no tocan el proyecto real.

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

### Rellenar las instantáneas de los documentos existentes

Las órdenes y solicitudes guardan una instantánea del cliente, del vehículo y (solo las órdenes) del técnico, tomada el
día del ingreso. No se propaga: si después cambia el teléfono de un cliente o los datos de un vehículo, la orden
conserva lo que era cierto ese día. Los documentos creados antes de esta mejora no la tienen; el script la completa leyendo
`usuarios` y `vehiculos`, sin sobrescribir las que ya existen. Por defecto solo muestra lo que cambiaría:

```bash
cd firebase/functions
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
node scripts/rellenarInstantaneas.mjs             # simulacro: no escribe
node scripts/rellenarInstantaneas.mjs --aplicar   # escribe en el proyecto
```

Despliega las reglas nuevas después del relleno o antes, da igual: las reglas no exigen las instantáneas al actualizar,
solo al crear.

### Tokens de dispositivo

El token de notificaciones vive en `usuarios/{usuarioId}/tokensDispositivo/{token}`, con `{token, expiraEn}`: un
documento por dispositivo y el propio token como id. Así un usuario con dos teléfonos no pisa el token del otro y volver a
guardar el mismo token solo actualiza su documento. El dueño los escribe y borra; nadie los lee desde la app, ni el Jefe
(los lee el servidor). Ninguna Cloud Function lee hoy esta colección.

`expiraEn` es una fecha (`Timestamp`) que la app renueva a 60 días cada vez que abre; las reglas aceptan hasta 90. Para que
Firestore borre solo los tokens de teléfonos que ya no se usan, activa una vez la política TTL (la ejecutas tú, no la hace
ningún script):

```bash
gcloud firestore fields ttls update expiraEn --collection-group=tokensDispositivo --enable-ttl --project=<id-del-proyecto>
```

Antes el token estaba en la raíz, en `tokensDispositivo/{usuarioId}`. La app registra el token en la ruta nueva cada vez que
se abre, y las reglas ya deniegan la ruta antigua, así que esos documentos quedan huérfanos. Cuando los dispositivos hayan
abierto la app nueva, bórralos (simulacro por defecto):

```bash
cd firebase/functions
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
node scripts/limpiarTokensAntiguos.mjs             # simulacro: no borra
node scripts/limpiarTokensAntiguos.mjs --aplicar   # borra en el proyecto
```

### Placa única (`placas/{placa}`)

La unicidad de la placa la garantiza la colección `placas`: el id de cada documento es la placa (`ABC-123`, en mayúsculas)
y sus campos son `{vehiculoId, clienteId}`. Firestore solo crea un documento si su id está libre, así que dos registros
simultáneos no pueden quedarse con la misma placa. Nadie la lee desde la app, ni el Jefe, y no se actualiza.

Regla del lote: el vehículo y su reserva se escriben en el MISMO lote y las reglas lo comprueban en ambos sentidos con
`existsAfter`/`getAfter`. Crear un vehículo exige su reserva (placa en mayúsculas, `vehiculoId` igual al id del vehículo);
crear la reserva exige el vehículo con esa placa y ese dueño. Eliminar un vehículo exige borrar su reserva en el mismo
lote; ocultarlo (`activo = false`) la conserva. Un vehículo anterior a las reservas se elimina igual (la app intenta borrar
una reserva inexistente y las reglas lo aceptan). Si la app pierde la carrera muestra "Esta placa ya está registrada".

Orden de despliegue (importa: tras las reglas, una app antigua ya no puede registrar vehículos):

1. Publica la app nueva.
2. Ejecuta el relleno (simulacro primero; revisa los conflictos de placa repetida, que el script omite).
3. Despliega las reglas al final.

```bash
cd firebase/functions
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
node scripts/rellenarPlacas.mjs             # simulacro: no escribe
node scripts/rellenarPlacas.mjs --aplicar   # escribe en el proyecto
```

### Términos de búsqueda (`terminos`, `terminosPlaca`)

Firestore no busca texto, así que la búsqueda de clientes usa campos con términos y consultas `array-contains`:

- `usuarios/{id}.terminos` (solo clientes): prefijos de al menos 3 letras de cada palabra de nombres y apellidos
  (minúsculas, sin tildes, palabra cortada a 20 caracteres) y todas las subcadenas de al menos 3 caracteres del DNI.
  Sin repetidos, ordenados y como mucho 120.
- `vehiculos/{id}.terminosPlaca`: la placa en minúsculas y sin guion, con todas sus subcadenas de al menos 3 caracteres
  (10 para una placa de 6).

Los calculan `Busqueda.kt` (app) y `functions/src/busqueda.js` (servidor, usado por `registrarCliente`); las dos
versiones deben dar las mismas listas y sus pruebas comparten los mismos ejemplos. Las reglas aceptan los campos como
opcionales (`terminos` hasta 120 elementos, `terminosPlaca` hasta 12), también al editar un documento anterior, que
gana el campo al guardarse. La búsqueda pasa de "contiene" a "prefijo de palabra" para nombres; el DNI y la placa
siguen admitiendo "contiene".

La app (`ClienteRepository.buscarClientes`) ya no recorre todos los usuarios y vehículos: consulta
`usuarios` con `terminos array-contains <palabra más larga>` y `vehiculos` con `terminosPlaca array-contains <placa>`,
cada una con `limit(30)`, y luego carga solo las placas de los clientes encontrados (`vehiculos` por `clienteId`). Así
el costo depende de las coincidencias y no de cuántos clientes hay. Cada consulta usa un único `array-contains` sobre un
campo, que cubre el índice automático de un solo campo: no hace falta ningún índice compuesto ni cambiar
`firestore.indexes.json`. Las reglas no cambian (el Jefe lee todo; al cliente y al técnico se les deniega, y las pruebas
de reglas lo comprueban). Los resultados salen ordenados por nombre completo.

Cambio de comportamiento: los nombres coinciden por el COMIENZO de cada palabra ("mend" encuentra a Mendoza, "ndoza" ya
no); el DNI y la placa se siguen encontrando en cualquier posición. Los clientes y vehículos que todavía no tienen
`terminos`/`terminosPlaca` NO aparecen en la búsqueda hasta que se ejecute el relleno de abajo.

DESPLIEGUE: ejecuta el relleno ANTES de publicar la app que use la búsqueda por términos, porque esa búsqueda solo
encuentra los documentos que ya llevan los términos. Es seco por defecto, nunca sobrescribe un campo existente y no toca
a técnicos ni al Jefe.

```bash
cd firebase/functions
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
node scripts/rellenarBusqueda.mjs             # simulacro: no escribe
node scripts/rellenarBusqueda.mjs --aplicar   # escribe en el proyecto
```

## Índices compuestos

Las consultas dirigidas de la app (`acceso` contiene el usuario y un campo igual a un valor) necesitan un índice
compuesto en producción: `ordenes` y `solicitudes` por `acceso` + `vehiculoId`, y `avances` por `acceso` + `ordenId`.
Los paneles y la ficha del cliente añaden otros cuatro: `ordenes` por `acceso` + `estado` (panel del técnico, `in`),
y `ordenes`, `vehiculos` y `solicitudes` por `acceso` + `clienteId` (consultas del cliente). Las consultas del Jefe
(`estado in [...]` o `clienteId ==` sin `acceso`) usan un solo campo y no necesitan índice compuesto.
Están en `firestore.indexes.json`. El emulador no los exige, pero Firestore real rechaza la consulta sin ellos.
Los despliegas tú, una vez, con `firebase login` hecho:

```bash
cd firebase
firebase deploy --only firestore:indexes
```

La construcción puede tardar unos minutos; hasta que termine, esas consultas fallan con `FAILED_PRECONDITION`.
Despliega los índices antes de publicar la app que usa las consultas.

## Desplegar las reglas

Solo después de que las pruebas pasen. Con `firebase login` hecho:

```bash
cd firebase
firebase deploy --only firestore:rules,storage
firebase deploy --only functions
```

Las reglas de Storage consultan Firestore (`firestore.get`). Al desplegar, Firebase puede pedir permiso para darle ese
acceso al servicio de Storage; acéptalo.
