# Reglas de seguridad de Firebase

Reglas de Firestore y de Storage de PitLane y sus pruebas. El diseño completo está en `odd/tasks/firebase-datasources.md`
(puntos S8 a S12).

## Cómo funcionan

- Cada cuenta lleva los reclamos `usuarioId` (id de su documento en `usuarios`) y `rol`. Los pone el servidor.
- Los documentos de `usuarios`, `vehiculos`, `solicitudes` y `ordenes` tienen un campo `acceso` con los
  `usuarioId` que pueden leerlos, además del Jefe. La app consulta con `acceso array-contains <usuarioId>`.
- Los avances viven dentro de su orden, `ordenes/{ordenId}/avances/{id}`: las reglas autorizan desde la orden padre (la
  lee quien puede leer la orden; escribe el técnico asignado a ella), sin copiar su `acceso`.
- Las notificaciones viven en la bandeja de su dueño, `usuarios/{usuarioId}/notificaciones/{id}`: la ruta dice de quién
  son y solo él las lee (tampoco el Jefe), sin filtrar por `usuarioId`.
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

#### Versión de esquema

`ordenes` y `solicitudes` (los dos que cambiaron de forma con las instantáneas) llevan `esquema: 2`. Un documento SIN el
campo es de la versión 1 (anterior a las instantáneas). La versión nunca miente: la app y las reglas solo la escriben en
documentos con la forma de la versión 2, así que editar un documento de la versión 1 no lo promueve; el relleno le da las
instantáneas y `esquema: 2` en la misma actualización, y solo si queda con todas (si falta el cliente, el vehículo o el
técnico referenciado, sigue en la versión 1). Las reglas exigen `esquema == 2` al crear, y el Jefe no puede cambiar ni quitar
el `esquema` de una orden ni ponérselo a una sin las tres instantáneas. La app no se apoya en la versión para leer (sigue
comprobando si hay instantánea); sirve a los scripts de migración y al diagnóstico.

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

### DNI único (`dnis/{dni}`)

Igual que la placa: el id de cada documento de `dnis` es el DNI (8 dígitos) y su único campo es `{usuarioId}`. Firestore
solo crea un documento si su id está libre, así que dos registros simultáneos no pueden quedarse con el mismo DNI. Nadie
la lee desde la app, ni el Jefe, y no se actualiza (el DNI de un perfil no cambia).

- App (el Jefe registra un cliente sin cuenta): el perfil y su reserva van en el MISMO lote; las reglas exigen ambos
  sentidos con `existsAfter`/`getAfter` (perfil con DNI de 8 dígitos y reserva con su `usuarioId`; reserva con un perfil
  de ese DNI). Eliminar el cliente borra su reserva en el mismo lote; un perfil anterior a las reservas se elimina igual
  (la app intenta borrar una reserva inexistente y las reglas lo aceptan). Si la app pierde la carrera muestra
  "Ya existe un cliente con ese DNI".
- Funciones: `registrarCliente` y `crearTecnico` escriben el perfil y la reserva (con `create`) en una transacción; si
  el DNI ya está reservado por otro usuario no escriben nada, eliminan la cuenta recién creada y responden
  "Ya existe un usuario con ese DNI". Al unir el registro previo del Jefe, la reserva de ese perfil se conserva (o se
  crea si era anterior a las reservas).

Orden de despliegue (importa: tras las reglas, una app antigua ya no puede registrar clientes sin cuenta):

1. Despliega las funciones (desde aquí cada cuenta nueva ya lleva su reserva).
2. Publica la app nueva.
3. Ejecuta el relleno (simulacro primero; revisa los DNI repetidos que lista con sus `usuarios/{id}` y que el script omite).
4. Despliega las reglas al final.

```bash
cd firebase/functions
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
node scripts/rellenarDnis.mjs             # simulacro: no escribe
node scripts/rellenarDnis.mjs --aplicar   # escribe en el proyecto
```

### Número de orden único (`numerosOrden/{numero}`)

Firestore no tiene restricciones de unicidad: lo único que no se repite es el id de un documento. Por eso cada orden
lleva una reserva `numerosOrden/{numero}` = `{ ordenId }`, escrita en el mismo lote que la orden (igual que `placas` y
`dnis`). Las reglas exigen las dos partes en la creación (la orden con su reserva y la reserva con su orden), y nadie
lee, cambia ni borra la reserva. El contador `contadores/ordenes` solo genera el siguiente número; no garantiza nada.

Orden de despliegue: publicar la app nueva, ejecutar el relleno de las órdenes existentes (simulacro primero; lista los
números repetidos y los omite) y desplegar las reglas al final. Con las reglas nuevas, una app vieja no puede crear órdenes.

```bash
cd firebase/functions
node scripts/rellenarNumerosOrden.mjs             # simulacro: no escribe
node scripts/rellenarNumerosOrden.mjs --aplicar   # escribe en el proyecto
```

### Notificaciones en la bandeja del usuario (`usuarios/{usuarioId}/notificaciones/{id}`)

Cada notificación vive dentro del perfil de su dueño. La ruta dice de quién es: las reglas dejan leer la bandeja y
marcar `leida` → `true` (ningún otro campo) solo a ese usuario, y nadie crea ni borra desde la app. `notificarOrden`
escribe en la bandeja del cliente con el mismo id idempotente `n-{id del evento}`. El documento conserva el campo
`usuarioId`, igual a la ruta: la app arma el aviso con él y al marcarlo como leído vuelve a escribir el documento
completo con `merge`, así que un documento sin ese campo haría que la escritura lo agregara y las reglas la rechazaran;
además el traslado copia los documentos tal cual. Las reglas no lo miran: manda la ruta. La colección de primer nivel
`notificaciones` ya no tiene reglas.

La subcolección se sigue llamando `notificaciones` a propósito: la política TTL de `expiraEn` es por grupo de colecciones
(`--collection-group=notificaciones`), así que la misma política alcanza a todas las bandejas sin tocarla (ver
"Vencimiento automático").

Para mover las notificaciones anteriores (simulacro por defecto): copia cada `notificaciones/{id}` a
`usuarios/{usuarioId}/notificaciones/{id}` (mismo id y campos) y borra el original en el mismo lote. Omite e informa las
que no tienen un `usuarioId` válido o cuyo usuario no existe; esas quedan donde estaban. Se puede volver a ejecutar: si
la copia ya existe, solo borra el original.

Orden de despliegue (importa: la app nueva lee la bandeja, que las reglas anteriores no autorizan):

1. Despliega las funciones (desde aquí cada notificación nueva va a la bandeja; nada nuevo llega a la colección antigua).
2. Despliega las reglas (autorizan la bandeja; una app antigua deja de ver la colección antigua).
3. Ejecuta el traslado (simulacro primero; revisa las omitidas). Va después de las funciones para que no quede ninguna
   notificación sin mover.
4. Publica la app nueva (lee la bandeja y ya encuentra las notificaciones movidas). Si se publicara antes de las reglas,
   Firestore le negaría la lectura de la bandeja y la vería vacía.

```bash
cd firebase/functions
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
node scripts/moverNotificaciones.mjs             # simulacro: no escribe
node scripts/moverNotificaciones.mjs --aplicar   # escribe en el proyecto
```

### Avances en la subcolección de su orden (`ordenes/{ordenId}/avances/{id}`)

Cada avance vive dentro de su orden y la ruta dice de cuál es. Las reglas hacen un `get` de la orden padre: leen el
avance el Jefe y los `usuarioId` del `acceso` de la orden (su cliente y su técnico); lo crean, editan (`tipo`,
`descripcion`, `fotos`) y borran solo el técnico asignado a la orden y solo los suyos (`tecnicoId`). Leer los avances
de una orden es leer su subcolección, sin filtros ni índice compuesto.

El documento ya no lleva `acceso` ni `ordenId`:

- `acceso`: era una copia del de la orden y la app la reescribía en cada avance al reasignar el técnico. Con el `get`
  del padre basta con el `acceso` de la orden, así que reasignar ya no toca los avances. Solo haría falta para una
  consulta de grupo de colecciones (`collectionGroup('avances')`), y ninguna pantalla lista avances de varias órdenes.
- `ordenId`: lo da la ruta. A diferencia de `usuarioId` en la bandeja, la app ya no lo escribe (arma el avance con el
  id de la orden de la ruta), así que reescribir el avance con `merge` no lo agrega y las reglas lo rechazan si llega.

La colección de primer nivel `avances` ya no tiene reglas.

Para mover los avances anteriores (simulacro por defecto): copia cada `avances/{id}` a
`ordenes/{ordenId}/avances/{id}` (mismo id, sin `ordenId` ni `acceso`) y borra el original en el mismo lote. Omite e
informa los que no tienen un `ordenId` válido o cuya orden no existe; esos quedan donde estaban. Se puede volver a
ejecutar: si la copia ya existe, solo borra el original.

Orden de despliegue (importa: las reglas anteriores no tienen coincidencia para la subcolección y las nuevas ya no
autorizan la colección de primer nivel):

1. Despliega las reglas (autorizan la subcolección; una app antigua deja de leer y escribir avances, y su Jefe no puede
   reasignar el técnico de una orden, porque esa app reescribe el `acceso` de los avances en el mismo lote).
2. Ejecuta el traslado (simulacro primero; revisa los omitidos). Desde el paso 1 nadie escribe en la colección de
   primer nivel, así que no queda ningún avance sin mover.
3. Publica la app nueva cuanto antes (lee y escribe en la subcolección y ya encuentra los avances movidos). Si se
   publicara antes de las reglas, Firestore le negaría los avances y la orden se vería sin ellos.
4. Despliega los índices: el de `avances` por `acceso` + `ordenId` ya no existe en `firestore.indexes.json`
   (`firebase deploy --only firestore:indexes` pregunta si borrar el que sobra).

Las funciones del servidor no leen ni escriben avances, así que no hay que redesplegarlas para este cambio.

```bash
cd firebase/functions
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/fuera/del/repo/clave-de-servicio.json
node scripts/moverAvances.mjs             # simulacro: no escribe
node scripts/moverAvances.mjs --aplicar   # escribe en el proyecto
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

### Historial de la orden: solo crece

Las reglas tratan `ordenes/{id}.historial` como un registro al que solo se agrega: si el estado no cambia, el historial
se reenvía idéntico; si cambia, se agrega exactamente una entrada al final, firmada por quien hace el cambio
(`usuarioId == usuarioId del token`) y con el estado nuevo. La orden nace con una sola entrada `INGRESADO` firmada por
el Jefe que la crea. La fecha de cada entrada la pone el dispositivo (Firestore no admite `serverTimestamp` dentro de un
arreglo), por eso las reglas no la comparan con la anterior.

Antes de desplegar estas reglas hay que completar las entradas antiguas a las que les falta `motivo` o
`usuarioNombre`: el mapeador de la app reenvía esas claves en `null`, y para Firestore una clave ausente no es igual a
una en `null`, así que la orden quedaría bloqueada.

```bash
cd functions
node scripts/rellenarHistorial.mjs             # simulacro: no escribe
node scripts/rellenarHistorial.mjs --aplicar   # escribe en el proyecto
```

## Índices compuestos

Las consultas dirigidas de la app (`acceso` contiene el usuario y un campo igual a un valor) necesitan un índice
compuesto en producción: `ordenes` y `solicitudes` por `acceso` + `vehiculoId`. Los avances de una orden son su
subcolección y se leen enteros, sin índice compuesto.
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

### Vencimiento automático de notificaciones y códigos

`notificaciones` (90 días) y `codigosRestablecimiento` (un día después de vencer el código) llevan `expiraEn`, una fecha
(`Timestamp`) que pone el servidor. Para que Firestore los borre solo, activa una vez las políticas TTL (las ejecutas tú;
ningún script lo hace):

```bash
gcloud firestore fields ttls update expiraEn --collection-group=notificaciones --enable-ttl --project=<id-del-proyecto>
gcloud firestore fields ttls update expiraEn --collection-group=codigosRestablecimiento --enable-ttl --project=<id-del-proyecto>
```

La política de `notificaciones` es por grupo de colecciones, así que alcanza a cada bandeja
`usuarios/{usuarioId}/notificaciones` (y a la colección antigua de primer nivel mientras quede algo en ella).

Firestore borra al pasar `expiraEn`, normalmente en el día siguiente y a veces más tarde. Las notificaciones anteriores a esta
mejora no tienen el campo y no vencerían nunca: ponles la fecha con (simulacro por defecto; recorre el grupo
`notificaciones`, bandejas y colección antigua):

```bash
cd firebase/functions
node scripts/rellenarExpiracion.mjs             # simulacro
node scripts/rellenarExpiracion.mjs --aplicar   # escribe
```

`fechaHora`, `creadaEn` y demás fechas de negocio siguen siendo números (milisegundos); solo los campos de vencimiento son
`Timestamp`.
