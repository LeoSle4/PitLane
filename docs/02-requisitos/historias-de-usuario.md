# Requisitos funcionales en Historias de Usuario
## App de seguimiento de vehículos — Servicio Eléctrico Freddy

**Origen de los requisitos:** actividades en verde de los diagramas BPMN
- Proceso 1: Recepción del vehículo
- Proceso 2: Reparación del vehículo

**Plataforma:** app móvil Android (Kotlin) con base de datos NoSQL en la nube

---

## 1. Convenciones

### 1.1 Formato de cada Historia de Usuario (HU)

Cada HU sigue el formato estándar de tres partes (Connextra) y los criterios INVEST:

> **Como** [rol], **quiero** [acción], **para** [beneficio].

Cada HU contiene:

| Campo | Descripción |
|---|---|
| **ID** | HU-XX (procesos) o HU-TXX (transversales) |
| **Requisito funcional** | La misma necesidad redactada como "El sistema debe…" |
| **Vista** | Pantalla o diálogo del prototipo que implementa la HU (V-XX) |
| **Prioridad** | MoSCoW: Must (debe), Should (debería), Could (podría) |
| **Estimación** | Puntos de historia (escala Fibonacci: 1, 2, 3, 5, 8) |
| **Criterios de aceptación** | En formato Dado / Cuando / Entonces |
| **Datos y reglas** | Campos, validaciones y reglas de negocio que aplican |

### 1.2 Principio de atomicidad

Cada HU representa **una sola acción**. Registrar, listar, ver detalle, editar y eliminar son HU distintas, y cada una tiene su propia vista.

### 1.3 Roles

| Rol | Descripción | Acceso |
|---|---|---|
| **Cliente** | Dueño del vehículo | Se registra solo desde la app |
| **Jefe de Taller** | Administra la recepción y el taller | Cuenta creada al instalar el sistema |
| **Técnico Electricista** | Repara los vehículos | Cuenta creada por el Jefe de Taller |

### 1.4 Estados

**Solicitud de atención:** `Pendiente` → `Atendida` · o bien `Cancelada`

**Orden de ingreso:** `Ingresado` → `En reparación` → `Listo para recoger` → `Entregado` · o bien `Anulada`

---

## 2. Reglas de negocio generales

| ID | Regla |
|---|---|
| RN-01 | El DNI tiene 8 dígitos numéricos y es único por cliente |
| RN-02 | La placa tiene 6 caracteres alfanuméricos (formato ABC-123, guion opcional), es única y se guarda en mayúsculas |
| RN-03 | El teléfono celular tiene 9 dígitos y empieza con 9 |
| RN-04 | La contraseña tiene mínimo 8 caracteres, con al menos una letra y un número |
| RN-05 | El correo electrónico es único por usuario |
| RN-06 | Solo el Jefe de Taller crea, edita y anula órdenes de ingreso |
| RN-07 | Un técnico solo ve las órdenes que tiene asignadas |
| RN-08 | Un cliente solo ve sus propios vehículos, solicitudes, órdenes y notificaciones |
| RN-09 | Las fotos son JPG o PNG, de máximo 5 MB cada una |
| RN-10 | Toda eliminación pide confirmación antes de ejecutarse |
| RN-11 | No se elimina un registro que tenga órdenes asociadas; en ese caso se aplica eliminación lógica (desactivar o anular) |
| RN-12 | Si el Jefe registró a un cliente y luego ese cliente crea su cuenta con el mismo DNI, el sistema une ambos registros |
| RN-13 | Cada cambio de estado de una orden queda registrado con fecha, hora y usuario |

---

## 3. Módulo transversal: acceso y usuarios

> No aparece en el BPMN porque no es una actividad del negocio, pero lo necesitan todos los roles para usar la app.

### HU-T01 — Iniciar sesión

**Como** usuario (Cliente, Jefe de Taller o Técnico), **quiero** iniciar sesión con mi correo y contraseña, **para** acceder a las funciones de mi rol.

- **Requisito funcional:** El sistema debe permitir iniciar sesión con correo y contraseña, y redirigir al usuario a la pantalla principal de su rol.
- **Vista:** V-T01 Inicio de sesión
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** un usuario con cuenta activa, **cuando** ingresa un correo y una contraseña correctos, **entonces** el sistema lo lleva a la pantalla principal de su rol (Cliente: Mis vehículos · Jefe: Panel de órdenes · Técnico: Mis órdenes asignadas).
2. **Dado** un usuario, **cuando** ingresa credenciales incorrectas, **entonces** el sistema muestra "Correo o contraseña incorrectos" y no indica cuál de los dos falló.
3. **Dado** un usuario desactivado, **cuando** intenta iniciar sesión, **entonces** el sistema muestra "Tu cuenta está desactivada. Comunícate con el taller".
4. **Dado** el formulario, **cuando** algún campo está vacío, **entonces** el botón "Ingresar" permanece deshabilitado.

**Datos y reglas:** correo, contraseña (oculta, con opción de mostrar). La sesión se mantiene abierta hasta que el usuario la cierre.

### HU-T02 — Cerrar sesión

**Como** usuario, **quiero** cerrar mi sesión, **para** que nadie más use mi cuenta en el dispositivo.

- **Requisito funcional:** El sistema debe permitir cerrar la sesión activa previa confirmación.
- **Vista:** V-T02 Diálogo de cierre de sesión
- **Prioridad:** Must · **Estimación:** 1

**Criterios de aceptación**
1. **Dado** un usuario con sesión activa, **cuando** pulsa "Cerrar sesión", **entonces** el sistema pide confirmación.
2. **Dado** el diálogo de confirmación, **cuando** confirma, **entonces** el sistema cierra la sesión y muestra la pantalla de inicio de sesión.
3. **Dado** que cerró sesión, **cuando** vuelve a abrir la app, **entonces** el sistema pide iniciar sesión de nuevo.

### HU-T03 — Recuperar contraseña

**Como** usuario, **quiero** recuperar mi contraseña desde mi correo, **para** volver a entrar si la olvido.

- **Requisito funcional:** El sistema debe enviar un enlace de restablecimiento de contraseña al correo registrado.
- **Vista:** V-T03 Recuperar contraseña
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un correo registrado, **cuando** solicita la recuperación, **entonces** el sistema envía un enlace de restablecimiento y muestra "Revisa tu correo".
2. **Dado** un correo no registrado, **cuando** solicita la recuperación, **entonces** el sistema muestra el mismo mensaje, para no revelar qué correos existen.
3. **Dado** un correo con formato inválido, **cuando** pulsa "Enviar", **entonces** el sistema muestra "Ingresa un correo válido".

### HU-T04 — Registrar técnico

**Como** Jefe de Taller, **quiero** registrar la cuenta de un técnico, **para** que pueda ver y actualizar sus órdenes asignadas.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller registrar cuentas de técnicos con nombres, apellidos, DNI, teléfono, correo y contraseña inicial.
- **Vista:** V-T04 Formulario de registro de técnico
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** que el Jefe completa todos los campos válidos, **cuando** pulsa "Registrar", **entonces** el sistema crea la cuenta con rol Técnico y estado Activo, y muestra "Técnico registrado".
2. **Dado** un DNI o correo que ya existe, **cuando** pulsa "Registrar", **entonces** el sistema muestra "Ya existe un usuario con ese DNI/correo".
3. **Dado** un campo inválido, **cuando** sale del campo, **entonces** el sistema muestra el error debajo del campo (RN-01, RN-03, RN-04, RN-05).

**Datos:** nombres, apellidos, DNI, teléfono, correo, contraseña inicial.

### HU-T05 — Listar técnicos

**Como** Jefe de Taller, **quiero** ver la lista de técnicos, **para** administrar al personal del taller.

- **Requisito funcional:** El sistema debe listar a los técnicos con su nombre, teléfono, estado y número de órdenes en curso.
- **Vista:** V-T05 Lista de técnicos
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** que hay técnicos registrados, **cuando** el Jefe abre la lista, **entonces** ve cada técnico con nombre, teléfono, estado (Activo/Inactivo) y órdenes en curso.
2. **Dado** que no hay técnicos, **cuando** abre la lista, **entonces** ve "Aún no hay técnicos registrados" y un botón "Registrar técnico".
3. **Dado** un técnico en la lista, **cuando** lo pulsa, **entonces** el sistema abre la edición de ese técnico.

### HU-T06 — Editar técnico

**Como** Jefe de Taller, **quiero** editar los datos de un técnico, **para** mantener su información actualizada.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller modificar los nombres, apellidos y teléfono de un técnico.
- **Vista:** V-T06 Formulario de edición de técnico
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un técnico, **cuando** el Jefe abre la edición, **entonces** el formulario muestra los datos actuales.
2. **Dado** cambios válidos, **cuando** pulsa "Guardar", **entonces** el sistema actualiza los datos y muestra "Cambios guardados".
3. **Dado** el formulario, **cuando** se muestra, **entonces** el DNI y el correo aparecen sin posibilidad de edición.

### HU-T07 — Desactivar técnico

**Como** Jefe de Taller, **quiero** desactivar a un técnico que ya no trabaja en el taller, **para** que no pueda acceder sin perder el historial de sus órdenes.

- **Requisito funcional:** El sistema debe permitir desactivar la cuenta de un técnico (eliminación lógica) e impedir su inicio de sesión.
- **Vista:** V-T07 Diálogo de desactivación de técnico
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un técnico sin órdenes en curso, **cuando** el Jefe confirma la desactivación, **entonces** el estado cambia a Inactivo y el técnico ya no puede iniciar sesión.
2. **Dado** un técnico con órdenes en curso, **cuando** el Jefe intenta desactivarlo, **entonces** el sistema muestra "Reasigna sus órdenes en curso antes de desactivarlo".
3. **Dado** un técnico inactivo, **cuando** el Jefe pulsa "Reactivar", **entonces** el estado vuelve a Activo.

---

## 4. Proceso 1: Recepción del vehículo

### Actividad 1.1 — Proporcionar datos personales y del vehículo (Cliente)

> **Problema actual:** el cliente repite sus datos en cada visita.
> **Solución:** el cliente se registra una sola vez y registra sus vehículos en la app.

#### HU-01 — Registrar cuenta de cliente

**Como** cliente, **quiero** crear mi cuenta en la app, **para** no tener que dar mis datos en cada visita al taller.

- **Requisito funcional:** El sistema debe permitir que un cliente cree su cuenta con nombres, apellidos, DNI, teléfono, correo y contraseña.
- **Vista:** V-01 Registro de cliente
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** que el cliente completa todos los campos válidos y acepta los términos, **cuando** pulsa "Crear cuenta", **entonces** el sistema crea la cuenta con rol Cliente, inicia sesión y lo lleva a "Mis vehículos".
2. **Dado** un correo ya registrado, **cuando** pulsa "Crear cuenta", **entonces** el sistema muestra "Este correo ya está registrado".
3. **Dado** un DNI que el Jefe ya registró sin cuenta, **cuando** crea su cuenta, **entonces** el sistema une el registro existente con la cuenta nueva y conserva sus vehículos e historial (RN-12).
4. **Dado** que las contraseñas no coinciden, **cuando** pulsa "Crear cuenta", **entonces** el sistema muestra "Las contraseñas no coinciden".

**Datos:** nombres, apellidos, DNI, teléfono, correo, contraseña, confirmar contraseña, aceptación de términos.

#### HU-02 — Ver mi perfil

**Como** cliente, **quiero** ver mis datos personales, **para** comprobar que el taller tiene mi información correcta.

- **Requisito funcional:** El sistema debe mostrar al cliente sus datos personales registrados.
- **Vista:** V-02 Mi perfil
- **Prioridad:** Must · **Estimación:** 1

**Criterios de aceptación**
1. **Dado** un cliente con sesión iniciada, **cuando** abre "Mi perfil", **entonces** ve nombres, apellidos, DNI, teléfono y correo.
2. **Dado** la vista de perfil, **cuando** se muestra, **entonces** incluye los botones "Editar perfil", "Eliminar cuenta" y "Cerrar sesión".

#### HU-03 — Editar mi perfil

**Como** cliente, **quiero** editar mis datos personales, **para** que el taller pueda contactarme si cambio de número.

- **Requisito funcional:** El sistema debe permitir al cliente modificar sus nombres, apellidos y teléfono.
- **Vista:** V-03 Edición de perfil
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** el formulario con los datos actuales, **cuando** el cliente cambia datos válidos y pulsa "Guardar", **entonces** el sistema actualiza el perfil y muestra "Perfil actualizado".
2. **Dado** un teléfono inválido, **cuando** pulsa "Guardar", **entonces** el sistema muestra el error y no guarda (RN-03).
3. **Dado** el formulario, **cuando** se muestra, **entonces** el DNI y el correo no se pueden editar.

#### HU-04 — Eliminar mi cuenta

**Como** cliente, **quiero** eliminar mi cuenta, **para** dejar de usar la app si ya no lo necesito.

- **Requisito funcional:** El sistema debe permitir al cliente eliminar su cuenta previa confirmación con su contraseña.
- **Vista:** V-04 Diálogo de eliminación de cuenta
- **Prioridad:** Could · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un cliente sin órdenes en curso, **cuando** confirma con su contraseña, **entonces** el sistema desactiva su cuenta, cierra la sesión y conserva el historial de órdenes del taller (RN-11).
2. **Dado** un cliente con una orden en curso, **cuando** intenta eliminar su cuenta, **entonces** el sistema muestra "Tienes un vehículo en el taller. Podrás eliminar tu cuenta cuando se entregue".
3. **Dado** una contraseña incorrecta, **cuando** confirma, **entonces** el sistema muestra "Contraseña incorrecta".

#### HU-05 — Registrar vehículo

**Como** cliente, **quiero** registrar mi vehículo en la app, **para** no tener que dictar sus datos cada vez que lo llevo al taller.

- **Requisito funcional:** El sistema debe permitir al cliente registrar un vehículo con placa, marca, modelo, año y color.
- **Vista:** V-05 Formulario de registro de vehículo
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** datos válidos, **cuando** pulsa "Registrar", **entonces** el sistema guarda el vehículo asociado al cliente y vuelve a "Mis vehículos".
2. **Dado** una placa que ya existe, **cuando** pulsa "Registrar", **entonces** el sistema muestra "Esta placa ya está registrada. Si es tuya, comunícate con el taller".
3. **Dado** un año fuera del rango 1950 a año actual + 1, **cuando** sale del campo, **entonces** el sistema muestra "Año no válido".
4. **Dado** la placa ingresada en minúsculas, **cuando** guarda, **entonces** el sistema la convierte a mayúsculas (RN-02).

**Datos:** placa, marca, modelo, año, color.

#### HU-06 — Listar mis vehículos

**Como** cliente, **quiero** ver la lista de mis vehículos, **para** elegir cuál consultar o editar.

- **Requisito funcional:** El sistema debe listar los vehículos del cliente con su placa, marca, modelo y estado actual en el taller.
- **Vista:** V-06 Mis vehículos
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un cliente con vehículos, **cuando** abre "Mis vehículos", **entonces** ve cada uno con placa, marca, modelo y una etiqueta de estado ("Sin orden activa", "Ingresado", "En reparación" o "Listo para recoger").
2. **Dado** un cliente sin vehículos, **cuando** abre la vista, **entonces** ve "Registra tu primer vehículo" y un botón "Agregar vehículo".
3. **Dado** un vehículo con orden activa, **cuando** lo pulsa, **entonces** el sistema abre el estado de esa orden (HU-46).

#### HU-07 — Editar vehículo

**Como** cliente, **quiero** editar los datos de mi vehículo, **para** corregir errores en la información.

- **Requisito funcional:** El sistema debe permitir al cliente modificar la marca, el modelo, el año y el color de su vehículo.
- **Vista:** V-07 Formulario de edición de vehículo
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un vehículo, **cuando** abre la edición, **entonces** el formulario muestra los datos actuales.
2. **Dado** cambios válidos, **cuando** pulsa "Guardar", **entonces** el sistema actualiza el vehículo y muestra "Vehículo actualizado".
3. **Dado** el formulario, **cuando** se muestra, **entonces** la placa no se puede editar.

#### HU-08 — Eliminar vehículo

**Como** cliente, **quiero** eliminar un vehículo que ya no tengo, **para** mantener mi lista ordenada.

- **Requisito funcional:** El sistema debe permitir al cliente eliminar un vehículo propio previa confirmación.
- **Vista:** V-08 Diálogo de eliminación de vehículo
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un vehículo sin órdenes, **cuando** confirma la eliminación, **entonces** el sistema lo elimina y lo quita de la lista.
2. **Dado** un vehículo con órdenes anteriores, **cuando** confirma, **entonces** el sistema lo oculta de su lista y conserva el historial del taller (RN-11).
3. **Dado** un vehículo con una orden en curso, **cuando** intenta eliminarlo, **entonces** el sistema muestra "No puedes eliminar un vehículo que está en el taller".

---

### Actividad 1.2 — Describir falla del vehículo (Cliente)

> **Problema actual:** la falla se explica de palabra y puede anotarse incompleta.
> **Solución:** el cliente reporta la falla por escrito desde la app, antes o al llegar al taller.

#### HU-09 — Registrar solicitud de atención

**Como** cliente, **quiero** reportar la falla de mi vehículo desde la app, **para** que el taller tenga la descripción completa antes de recibirme.

- **Requisito funcional:** El sistema debe permitir al cliente registrar una solicitud de atención con el vehículo, la descripción de la falla y la fecha estimada de llegada.
- **Vista:** V-09 Formulario de solicitud de atención
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** que el cliente elige un vehículo y escribe la falla (mínimo 10 caracteres), **cuando** pulsa "Enviar solicitud", **entonces** el sistema la registra con estado Pendiente y muestra "Solicitud enviada".
2. **Dado** un vehículo que ya tiene una solicitud pendiente u orden en curso, **cuando** intenta enviar otra, **entonces** el sistema muestra "Este vehículo ya tiene una atención en curso".
3. **Dado** un cliente sin vehículos registrados, **cuando** abre el formulario, **entonces** el sistema le pide registrar un vehículo primero.
4. **Dado** una fecha de llegada anterior a hoy, **cuando** la selecciona, **entonces** el sistema no la acepta.

**Datos:** vehículo (lista de sus vehículos), descripción de la falla, síntomas frecuentes (opciones: no arranca, batería se descarga, luces fallan, tablero con fallas, otro), fecha estimada de llegada.

#### HU-10 — Adjuntar fotos a la solicitud

**Como** cliente, **quiero** adjuntar fotos de la falla, **para** mostrar al taller lo que no sé explicar con palabras.

- **Requisito funcional:** El sistema debe permitir adjuntar hasta 3 fotos a una solicitud, desde la cámara o la galería.
- **Vista:** V-10 Selector de fotos de la solicitud
- **Prioridad:** Should · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** el formulario de solicitud, **cuando** pulsa "Agregar foto", **entonces** el sistema ofrece "Tomar foto" o "Elegir de la galería".
2. **Dado** 3 fotos adjuntas, **cuando** intenta agregar otra, **entonces** el sistema muestra "Máximo 3 fotos".
3. **Dado** una foto mayor a 5 MB o con otro formato, **cuando** la selecciona, **entonces** el sistema la rechaza con un mensaje (RN-09).
4. **Dado** una foto adjunta, **cuando** pulsa la "X" sobre la miniatura, **entonces** el sistema la quita de la solicitud.

#### HU-11 — Listar mis solicitudes

**Como** cliente, **quiero** ver mis solicitudes de atención, **para** saber si el taller ya las atendió.

- **Requisito funcional:** El sistema debe listar las solicitudes del cliente con el vehículo, la fecha y el estado.
- **Vista:** V-11 Mis solicitudes
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un cliente con solicitudes, **cuando** abre la vista, **entonces** las ve ordenadas de la más reciente a la más antigua, con placa, fecha y estado (Pendiente, Atendida o Cancelada).
2. **Dado** una solicitud Pendiente, **cuando** la pulsa, **entonces** ve las opciones "Editar" y "Cancelar".
3. **Dado** una solicitud Atendida, **cuando** la pulsa, **entonces** el sistema abre la orden de ingreso que se generó.

#### HU-12 — Editar solicitud pendiente

**Como** cliente, **quiero** editar mi solicitud mientras está pendiente, **para** agregar detalles de la falla que olvidé.

- **Requisito funcional:** El sistema debe permitir editar la descripción, los síntomas y la fecha de llegada de una solicitud solo mientras su estado sea Pendiente.
- **Vista:** V-12 Formulario de edición de solicitud
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** una solicitud Pendiente, **cuando** guarda cambios válidos, **entonces** el sistema la actualiza y muestra "Solicitud actualizada".
2. **Dado** una solicitud Atendida o Cancelada, **cuando** la abre, **entonces** la opción "Editar" no está disponible.
3. **Dado** el formulario, **cuando** se muestra, **entonces** el vehículo no se puede cambiar.

#### HU-13 — Cancelar solicitud pendiente

**Como** cliente, **quiero** cancelar una solicitud, **para** avisar al taller que ya no llevaré mi vehículo.

- **Requisito funcional:** El sistema debe permitir cancelar una solicitud en estado Pendiente previa confirmación.
- **Vista:** V-13 Diálogo de cancelación de solicitud
- **Prioridad:** Should · **Estimación:** 1

**Criterios de aceptación**
1. **Dado** una solicitud Pendiente, **cuando** confirma la cancelación, **entonces** el estado cambia a Cancelada y desaparece de las solicitudes pendientes del Jefe.
2. **Dado** una solicitud Atendida, **cuando** la abre, **entonces** la opción "Cancelar" no está disponible.

---

### Actividad 1.3 — Buscar historial del cliente en cuaderno (Jefe de Taller)

> **Problema actual:** la búsqueda en el cuaderno es manual, demora y hay datos ilegibles o perdidos.
> **Solución:** el Jefe busca al cliente por DNI, nombre o placa y ve su historial al instante.

#### HU-14 — Listar solicitudes pendientes

**Como** Jefe de Taller, **quiero** ver las solicitudes de atención pendientes, **para** saber qué vehículos van a llegar y qué falla traen.

- **Requisito funcional:** El sistema debe listar las solicitudes en estado Pendiente con el cliente, la placa, la falla resumida y la fecha estimada de llegada.
- **Vista:** V-14 Solicitudes pendientes
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** que hay solicitudes pendientes, **cuando** el Jefe abre la vista, **entonces** las ve ordenadas por fecha estimada de llegada.
2. **Dado** una solicitud, **cuando** la pulsa, **entonces** ve la descripción completa, las fotos y el botón "Registrar ingreso" (HU-23).
3. **Dado** que no hay solicitudes pendientes, **cuando** abre la vista, **entonces** ve "No hay solicitudes pendientes".

#### HU-15 — Buscar cliente

**Como** Jefe de Taller, **quiero** buscar un cliente por DNI, nombre o placa, **para** encontrar sus datos sin revisar el cuaderno.

- **Requisito funcional:** El sistema debe buscar clientes por DNI, nombre, apellido o placa de vehículo y mostrar resultados mientras se escribe.
- **Vista:** V-15 Búsqueda de clientes
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** al menos 3 caracteres escritos, **cuando** el Jefe escribe, **entonces** el sistema muestra coincidencias por DNI, nombre, apellido o placa.
2. **Dado** un resultado, **cuando** lo pulsa, **entonces** el sistema abre el detalle del cliente (HU-16).
3. **Dado** que no hay coincidencias, **cuando** termina de escribir, **entonces** el sistema muestra "No se encontró el cliente" y un botón "Registrar cliente" (HU-17).

#### HU-16 — Ver detalle del cliente

**Como** Jefe de Taller, **quiero** ver los datos, vehículos e historial de órdenes de un cliente, **para** conocer sus atenciones anteriores.

- **Requisito funcional:** El sistema debe mostrar los datos del cliente, sus vehículos y el historial de órdenes de cada vehículo.
- **Vista:** V-16 Detalle del cliente
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** un cliente, **cuando** el Jefe abre su detalle, **entonces** ve nombres, DNI, teléfono, correo y si tiene cuenta en la app.
2. **Dado** el detalle, **cuando** se muestra, **entonces** lista sus vehículos y, por cada uno, sus órdenes anteriores con fecha, falla y estado final.
3. **Dado** el detalle, **cuando** se muestra, **entonces** incluye los botones "Editar cliente", "Agregar vehículo" y "Registrar ingreso".

---

### Actividad 1.4 — Anotar datos del cliente y vehículo en cuaderno (Jefe de Taller)

> **Problema actual:** los datos se anotan a mano y se pierden o quedan ilegibles.
> **Solución:** el Jefe registra al cliente sin cuenta (por ejemplo, alguien que no usa la app) y a su vehículo en el sistema.

#### HU-17 — Registrar cliente

**Como** Jefe de Taller, **quiero** registrar a un cliente que no tiene cuenta en la app, **para** atenderlo aunque no use la app.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller registrar un cliente con nombres, apellidos, DNI y teléfono, con correo opcional.
- **Vista:** V-17 Formulario de registro de cliente (Jefe)
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** datos válidos, **cuando** el Jefe pulsa "Registrar", **entonces** el sistema crea el cliente sin cuenta de acceso y abre su detalle.
2. **Dado** un DNI que ya existe, **cuando** pulsa "Registrar", **entonces** el sistema muestra "Ya existe un cliente con ese DNI" y ofrece abrir su detalle.
3. **Dado** que luego ese cliente crea su cuenta con el mismo DNI, **cuando** se registra, **entonces** el sistema une ambos registros (RN-12).

#### HU-18 — Editar cliente

**Como** Jefe de Taller, **quiero** editar los datos de un cliente, **para** corregirlos o actualizarlos.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller modificar los nombres, apellidos y teléfono de un cliente.
- **Vista:** V-18 Formulario de edición de cliente (Jefe)
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un cliente, **cuando** el Jefe guarda cambios válidos, **entonces** el sistema actualiza los datos y muestra "Cliente actualizado".
2. **Dado** el formulario, **cuando** se muestra, **entonces** el DNI no se puede editar.

#### HU-19 — Eliminar cliente

**Como** Jefe de Taller, **quiero** eliminar un cliente registrado por error, **para** mantener la base de clientes limpia.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller eliminar un cliente sin órdenes registradas, previa confirmación.
- **Vista:** V-19 Diálogo de eliminación de cliente
- **Prioridad:** Could · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un cliente sin órdenes, **cuando** el Jefe confirma, **entonces** el sistema elimina al cliente y a sus vehículos.
2. **Dado** un cliente con órdenes, **cuando** intenta eliminarlo, **entonces** el sistema muestra "No se puede eliminar un cliente con historial de órdenes" (RN-11).
3. **Dado** un cliente con cuenta en la app, **cuando** intenta eliminarlo, **entonces** el sistema muestra "Este cliente tiene cuenta en la app y solo él puede eliminarla".

#### HU-20 — Registrar vehículo de un cliente

**Como** Jefe de Taller, **quiero** registrar un vehículo a nombre de un cliente, **para** poder generar su orden de ingreso.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller registrar un vehículo (placa, marca, modelo, año y color) asociado a un cliente.
- **Vista:** V-20 Formulario de registro de vehículo (Jefe)
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un cliente y datos válidos, **cuando** el Jefe pulsa "Registrar", **entonces** el sistema guarda el vehículo y vuelve al detalle del cliente.
2. **Dado** una placa existente, **cuando** pulsa "Registrar", **entonces** el sistema muestra "Esta placa ya está registrada a nombre de [cliente]".

#### HU-21 — Editar vehículo de un cliente

**Como** Jefe de Taller, **quiero** editar los datos de un vehículo, **para** corregir errores de registro.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller modificar la marca, el modelo, el año y el color de un vehículo.
- **Vista:** V-21 Formulario de edición de vehículo (Jefe)
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un vehículo, **cuando** el Jefe guarda cambios válidos, **entonces** el sistema lo actualiza y muestra "Vehículo actualizado".
2. **Dado** el formulario, **cuando** se muestra, **entonces** la placa no se puede editar.

#### HU-22 — Eliminar vehículo de un cliente

**Como** Jefe de Taller, **quiero** eliminar un vehículo registrado por error, **para** evitar datos duplicados.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller eliminar un vehículo sin órdenes, previa confirmación.
- **Vista:** V-22 Diálogo de eliminación de vehículo (Jefe)
- **Prioridad:** Could · **Estimación:** 1

**Criterios de aceptación**
1. **Dado** un vehículo sin órdenes, **cuando** el Jefe confirma, **entonces** el sistema lo elimina.
2. **Dado** un vehículo con órdenes, **cuando** intenta eliminarlo, **entonces** el sistema muestra "No se puede eliminar un vehículo con historial de órdenes" (RN-11).

---

### Actividad 1.5 — Llenar ficha de ingreso (Jefe de Taller)

> **Problema actual:** la ficha es de papel y no tiene fotos, así que no queda evidencia del estado del vehículo.
> **Solución:** orden de ingreso digital, con fotos del estado exterior y técnico asignado.

#### HU-23 — Registrar orden de ingreso

**Como** Jefe de Taller, **quiero** registrar la orden de ingreso del vehículo, **para** que la información quede guardada y el técnico la reciba sin papeles.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller registrar una orden de ingreso con vehículo, falla reportada, kilometraje, nivel de combustible, observaciones del estado exterior y técnico asignado.
- **Vista:** V-23 Formulario de orden de ingreso
- **Prioridad:** Must · **Estimación:** 5

**Criterios de aceptación**
1. **Dado** que el Jefe parte de una solicitud pendiente, **cuando** abre el formulario, **entonces** el cliente, el vehículo y la falla aparecen ya llenados.
2. **Dado** que no hay solicitud, **cuando** abre el formulario, **entonces** elige al cliente y al vehículo con el buscador (HU-15).
3. **Dado** todos los campos obligatorios válidos, **cuando** pulsa "Registrar ingreso", **entonces** el sistema crea la orden con un número correlativo (ej. ORD-0001) y estado Ingresado, y marca la solicitud como Atendida.
4. **Dado** un vehículo con una orden en curso, **cuando** intenta registrar otra, **entonces** el sistema muestra "Este vehículo ya tiene una orden en curso".
5. **Dado** el campo "Técnico asignado", **cuando** lo abre, **entonces** solo aparecen técnicos activos, cada uno con su número de órdenes en curso.

**Datos:** cliente, vehículo, falla reportada, kilometraje (número entero ≥ 0), nivel de combustible (Reserva, 1/4, 1/2, 3/4, Lleno), observaciones del estado exterior, técnico asignado, fecha y hora de ingreso (automática).

#### HU-24 — Adjuntar fotos del estado exterior

**Como** Jefe de Taller, **quiero** tomar fotos del vehículo al recibirlo, **para** tener evidencia de su estado y evitar reclamos.

- **Requisito funcional:** El sistema debe permitir adjuntar entre 1 y 6 fotos del estado exterior a la orden de ingreso.
- **Vista:** V-24 Captura de fotos del estado exterior
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** la orden de ingreso, **cuando** el Jefe pulsa "Agregar foto", **entonces** puede tomar la foto con la cámara o elegirla de la galería.
2. **Dado** una orden sin fotos, **cuando** intenta registrar el ingreso, **entonces** el sistema muestra "Agrega al menos una foto del estado del vehículo".
3. **Dado** 6 fotos adjuntas, **cuando** intenta agregar otra, **entonces** el sistema muestra "Máximo 6 fotos".
4. **Dado** cada foto, **cuando** se guarda, **entonces** queda con fecha y hora.

#### HU-25 — Eliminar foto del estado exterior

**Como** Jefe de Taller, **quiero** eliminar una foto que salió mal, **para** dejar solo las fotos útiles como evidencia.

- **Requisito funcional:** El sistema debe permitir eliminar una foto del estado exterior mientras la orden esté en estado Ingresado.
- **Vista:** V-25 Diálogo de eliminación de foto
- **Prioridad:** Should · **Estimación:** 1

**Criterios de aceptación**
1. **Dado** una orden Ingresado con más de una foto, **cuando** confirma la eliminación, **entonces** el sistema quita la foto.
2. **Dado** una orden con una sola foto, **cuando** intenta eliminarla, **entonces** el sistema muestra "La orden debe tener al menos una foto".
3. **Dado** una orden En reparación o en un estado posterior, **cuando** abre las fotos, **entonces** la opción de eliminar no está disponible, para proteger la evidencia.

#### HU-26 — Ver detalle de la orden de ingreso

**Como** Jefe de Taller, **quiero** ver el detalle de una orden, **para** revisar todos sus datos y su estado.

- **Requisito funcional:** El sistema debe mostrar el detalle de la orden: datos del cliente y del vehículo, falla, kilometraje, combustible, observaciones, fotos, técnico asignado, estado e historial de estados.
- **Vista:** V-26 Detalle de orden (Jefe)
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** una orden, **cuando** el Jefe la abre, **entonces** ve todos sus datos y sus fotos en miniatura.
2. **Dado** una miniatura, **cuando** la pulsa, **entonces** la foto se abre en pantalla completa.
3. **Dado** una orden Ingresado, **cuando** se muestra, **entonces** incluye los botones "Editar" y "Anular".

#### HU-27 — Editar orden de ingreso

**Como** Jefe de Taller, **quiero** editar una orden de ingreso, **para** corregir datos o cambiar al técnico asignado.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller modificar la falla, el kilometraje, el combustible, las observaciones y el técnico asignado de una orden que no esté anulada, en estado Listo para recoger ni Entregada.
- **Vista:** V-27 Formulario de edición de orden
- **Prioridad:** Should · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** una orden Ingresado o En reparación, **cuando** el Jefe guarda cambios válidos, **entonces** el sistema la actualiza y registra quién hizo el cambio y cuándo.
2. **Dado** que cambia el técnico asignado, **cuando** guarda, **entonces** la orden deja de aparecer en la lista del técnico anterior y aparece en la del nuevo.
3. **Dado** una orden Listo para recoger, Entregada o Anulada, **cuando** la abre, **entonces** la opción "Editar" no está disponible.

#### HU-28 — Anular orden de ingreso

**Como** Jefe de Taller, **quiero** anular una orden registrada por error, **para** que no aparezca como trabajo pendiente.

- **Requisito funcional:** El sistema debe permitir anular una orden en estado Ingresado, indicando un motivo obligatorio (eliminación lógica).
- **Vista:** V-28 Diálogo de anulación de orden
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** una orden Ingresado, **cuando** el Jefe escribe el motivo y confirma, **entonces** el estado cambia a Anulada y la orden sale de las listas de trabajo.
2. **Dado** el motivo vacío, **cuando** pulsa "Anular", **entonces** el botón permanece deshabilitado.
3. **Dado** una orden En reparación o en un estado posterior, **cuando** la abre, **entonces** la opción "Anular" no está disponible.
4. **Dado** que la orden se anula, **cuando** se confirma, **entonces** el cliente recibe la notificación "Tu orden ORD-XXXX fue anulada".

---

### Actividad 1.6 — Indicar al cliente que se le llamará con novedades (Jefe de Taller)

> **Problema actual:** el aviso es verbal y el cliente se va sin nada que lo respalde.
> **Solución:** el sistema avisa automáticamente al cliente cuando se registra su ingreso.

#### HU-29 — Notificar ingreso al cliente

**Como** Jefe de Taller, **quiero** que el sistema avise automáticamente al cliente cuando registro su ingreso, **para** no tener que explicarle de palabra cómo seguirá el proceso.

- **Requisito funcional:** El sistema debe enviar una notificación push al cliente cuando se registre su orden de ingreso, con el número de orden y un enlace a su comprobante.
- **Vista:** V-29 Confirmación de ingreso registrado
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** un cliente con cuenta en la app, **cuando** el Jefe registra la orden, **entonces** el cliente recibe "Tu vehículo [placa] fue ingresado. Orden ORD-XXXX".
2. **Dado** la orden registrada, **cuando** termina el registro, **entonces** el Jefe ve una pantalla de confirmación con el número de orden y el texto "Cliente notificado".
3. **Dado** un cliente sin cuenta en la app, **cuando** se registra la orden, **entonces** la confirmación dice "El cliente no tiene la app: entrégale el número de orden ORD-XXXX".

---

### Actividad 1.7 — Recibir indicaciones del Jefe de Taller (Cliente)

> **Problema actual:** el cliente se va sin comprobante y sin forma de consultar el avance.
> **Solución:** comprobante digital de ingreso y lista de órdenes en la app.

#### HU-30 — Ver comprobante de ingreso

**Como** cliente, **quiero** ver el comprobante de ingreso de mi vehículo, **para** tener constancia de en qué estado lo dejé.

- **Requisito funcional:** El sistema debe mostrar al cliente el comprobante de ingreso con número de orden, fecha y hora, vehículo, falla reportada, kilometraje, combustible, observaciones, fotos del estado exterior y técnico asignado.
- **Vista:** V-30 Comprobante de ingreso
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** una orden del cliente, **cuando** abre el comprobante, **entonces** ve todos los datos del ingreso y las fotos del estado exterior.
2. **Dado** la notificación de ingreso (HU-29), **cuando** la pulsa, **entonces** el sistema abre directamente el comprobante.
3. **Dado** una orden de otro cliente, **cuando** intenta acceder, **entonces** el sistema no la muestra (RN-08).

#### HU-31 — Listar mis órdenes

**Como** cliente, **quiero** ver todas mis órdenes, **para** revisar las atenciones actuales y anteriores de mis vehículos.

- **Requisito funcional:** El sistema debe listar las órdenes del cliente con número, placa, fecha de ingreso y estado.
- **Vista:** V-31 Mis órdenes
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un cliente con órdenes, **cuando** abre la vista, **entonces** ve primero las órdenes en curso y luego las anteriores, cada una con número, placa, fecha y estado.
2. **Dado** una orden, **cuando** la pulsa, **entonces** el sistema abre su estado actual (HU-46).
3. **Dado** un cliente sin órdenes, **cuando** abre la vista, **entonces** ve "Aún no tienes órdenes".

---

## 5. Proceso 2: Reparación del vehículo

### Actividad 2.1 — Revisar ficha de ingreso del vehículo (Técnico Electricista)

> **Problema actual:** la ficha en papel puede estar incompleta o ilegible.
> **Solución:** el técnico ve sus órdenes asignadas con todos los datos y fotos.

#### HU-32 — Listar mis órdenes asignadas

**Como** técnico, **quiero** ver las órdenes que me asignaron, **para** saber qué vehículos debo atender.

- **Requisito funcional:** El sistema debe listar al técnico solo sus órdenes asignadas en estado Ingresado o En reparación, con número, placa, vehículo, falla resumida y fecha de ingreso.
- **Vista:** V-32 Mis órdenes asignadas
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un técnico con órdenes, **cuando** abre la vista, **entonces** ve primero las que están En reparación y luego las que están Ingresado, de la más antigua a la más reciente.
2. **Dado** la lista, **cuando** se muestra, **entonces** no aparecen órdenes de otros técnicos (RN-07).
3. **Dado** un técnico sin órdenes, **cuando** abre la vista, **entonces** ve "No tienes órdenes asignadas".

#### HU-33 — Ver detalle de orden asignada

**Como** técnico, **quiero** ver el detalle completo de la orden, **para** conocer la falla y el estado del vehículo antes de empezar.

- **Requisito funcional:** El sistema debe mostrar al técnico la falla reportada, las fotos de la solicitud, los datos del vehículo, el kilometraje, el combustible, las observaciones, las fotos del estado exterior y las órdenes anteriores del vehículo.
- **Vista:** V-33 Detalle de orden (Técnico)
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** una orden asignada, **cuando** el técnico la abre, **entonces** ve todos los datos del ingreso y las fotos.
2. **Dado** el detalle, **cuando** se muestra, **entonces** incluye las órdenes anteriores del mismo vehículo, con su falla y sus hallazgos.
3. **Dado** una orden Ingresado, **cuando** se muestra, **entonces** incluye el botón "Iniciar reparación" (HU-34).

#### HU-34 — Iniciar reparación

**Como** técnico, **quiero** marcar que empecé a reparar el vehículo, **para** que el cliente y el Jefe sepan que ya está en trabajo.

- **Requisito funcional:** El sistema debe permitir al técnico cambiar el estado de su orden de Ingresado a En reparación y notificar el cambio al cliente.
- **Vista:** V-34 Diálogo de inicio de reparación
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** una orden Ingresado, **cuando** el técnico confirma "Iniciar reparación", **entonces** el estado cambia a En reparación y se registra la fecha y la hora (RN-13).
2. **Dado** el cambio de estado, **cuando** se confirma, **entonces** el cliente recibe "Tu vehículo [placa] está en reparación".
3. **Dado** una orden de otro técnico, **cuando** intenta iniciarla, **entonces** el sistema no lo permite.

---

### Actividad 2.2 — Registrar hallazgos y trabajos realizados (Técnico Electricista)

> **Problema actual:** los hallazgos quedan en papel y sin fotos, y el cliente no los ve.
> **Solución:** el técnico registra cada avance con fotos y el cliente lo ve en la app.

#### HU-35 — Registrar avance

**Como** técnico, **quiero** registrar un hallazgo o un trabajo realizado, **para** que quede documentado y el cliente sepa qué se encontró y qué se hizo.

- **Requisito funcional:** El sistema debe permitir al técnico registrar un avance de tipo Hallazgo o Trabajo realizado, con una descripción, en una orden En reparación.
- **Vista:** V-35 Formulario de registro de avance
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** una orden En reparación, **cuando** el técnico elige el tipo, escribe la descripción (mínimo 10 caracteres) y pulsa "Registrar", **entonces** el sistema guarda el avance con fecha, hora y nombre del técnico.
2. **Dado** el avance registrado, **cuando** se guarda, **entonces** el cliente lo ve en su vista de hallazgos (HU-48).
3. **Dado** una orden Ingresado, **cuando** el técnico intenta registrar un avance, **entonces** el sistema muestra "Primero inicia la reparación".

**Datos:** tipo (Hallazgo / Trabajo realizado), descripción.

#### HU-36 — Adjuntar fotos al avance

**Como** técnico, **quiero** adjuntar fotos a un avance, **para** mostrarle al cliente la falla encontrada o el trabajo hecho.

- **Requisito funcional:** El sistema debe permitir adjuntar hasta 4 fotos a cada avance, desde la cámara o la galería.
- **Vista:** V-36 Captura de fotos del avance
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** el formulario de avance, **cuando** el técnico pulsa "Agregar foto", **entonces** puede tomar la foto con la cámara o elegirla de la galería.
2. **Dado** 4 fotos adjuntas, **cuando** intenta agregar otra, **entonces** el sistema muestra "Máximo 4 fotos por avance".
3. **Dado** una foto que no cumple RN-09, **cuando** la selecciona, **entonces** el sistema la rechaza con un mensaje.

#### HU-37 — Listar avances de la orden

**Como** técnico, **quiero** ver todos los avances que registré en una orden, **para** revisar el historial del trabajo.

- **Requisito funcional:** El sistema debe listar los avances de una orden en orden cronológico, con tipo, descripción, fotos, fecha y técnico.
- **Vista:** V-37 Lista de avances de la orden
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** una orden con avances, **cuando** el técnico abre la lista, **entonces** ve cada avance con un ícono según su tipo, la descripción, las miniaturas y la fecha.
2. **Dado** un avance propio, **cuando** lo pulsa, **entonces** ve las opciones "Editar" y "Eliminar".
3. **Dado** una orden sin avances, **cuando** abre la lista, **entonces** ve "Aún no hay avances registrados".

#### HU-38 — Editar avance

**Como** técnico, **quiero** editar un avance, **para** corregir o completar la descripción.

- **Requisito funcional:** El sistema debe permitir al técnico editar el tipo, la descripción y las fotos de un avance propio mientras la orden esté En reparación.
- **Vista:** V-38 Formulario de edición de avance
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un avance propio en una orden En reparación, **cuando** guarda cambios válidos, **entonces** el sistema lo actualiza y muestra "Avance actualizado".
2. **Dado** una orden Listo para recoger, **cuando** abre un avance, **entonces** la opción "Editar" no está disponible.
3. **Dado** un avance de otro técnico, **cuando** lo abre, **entonces** la opción "Editar" no está disponible.

#### HU-39 — Eliminar avance

**Como** técnico, **quiero** eliminar un avance registrado por error, **para** que el cliente no vea información equivocada.

- **Requisito funcional:** El sistema debe permitir al técnico eliminar un avance propio, con sus fotos, mientras la orden esté En reparación, previa confirmación.
- **Vista:** V-39 Diálogo de eliminación de avance
- **Prioridad:** Should · **Estimación:** 1

**Criterios de aceptación**
1. **Dado** un avance propio en una orden En reparación, **cuando** confirma la eliminación, **entonces** el sistema elimina el avance y sus fotos, y deja de mostrarlo al cliente.
2. **Dado** una orden Listo para recoger, **cuando** abre un avance, **entonces** la opción "Eliminar" no está disponible.

---

### Actividad 2.3 — Informar al Jefe que el vehículo está listo (Técnico Electricista)

> **Problema actual:** el técnico tiene que ir a avisar al Jefe y el aviso depende de la memoria.
> **Solución:** el técnico marca la orden como lista y el Jefe lo ve en su panel.

#### HU-40 — Marcar orden como lista para recoger

**Como** técnico, **quiero** marcar que terminé la reparación, **para** que el Jefe y el cliente se enteren sin que yo tenga que avisar.

- **Requisito funcional:** El sistema debe permitir al técnico cambiar el estado de su orden de En reparación a Listo para recoger, siempre que tenga al menos un avance de tipo Trabajo realizado.
- **Vista:** V-40 Diálogo de confirmación de vehículo listo
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** una orden En reparación con al menos un Trabajo realizado, **cuando** el técnico confirma, **entonces** el estado cambia a Listo para recoger y se registra la fecha y la hora (RN-13).
2. **Dado** una orden sin ningún Trabajo realizado, **cuando** intenta marcarla como lista, **entonces** el sistema muestra "Registra el trabajo realizado antes de marcar como listo".
3. **Dado** el cambio de estado, **cuando** se confirma, **entonces** la orden sale de "Mis órdenes asignadas" del técnico.

#### HU-41 — Ver panel de órdenes

**Como** Jefe de Taller, **quiero** ver todas las órdenes agrupadas por estado, **para** saber qué vehículos están listos sin preguntarle a cada técnico.

- **Requisito funcional:** El sistema debe mostrar al Jefe de Taller las órdenes activas agrupadas en Ingresado, En reparación y Listo para recoger, con un contador por estado.
- **Vista:** V-41 Panel de órdenes (Jefe)
- **Prioridad:** Must · **Estimación:** 5

**Criterios de aceptación**
1. **Dado** que hay órdenes activas, **cuando** el Jefe abre el panel, **entonces** ve tres pestañas (Ingresado, En reparación, Listo para recoger), cada una con su contador.
2. **Dado** cada orden del panel, **cuando** se muestra, **entonces** incluye número, placa, cliente, técnico asignado y tiempo en el estado actual.
3. **Dado** una orden, **cuando** la pulsa, **entonces** el sistema abre su detalle (HU-26).
4. **Dado** que un técnico marca una orden como lista, **cuando** el Jefe tiene el panel abierto, **entonces** la orden aparece en "Listo para recoger" sin recargar manualmente.

---

### Actividad 2.4 — Llamar al cliente para avisar que está listo (Jefe de Taller)

> **Problema actual:** hay llamadas sin respuesta y el vehículo listo ocupa espacio en el taller.
> **Solución:** notificación automática al cliente, con la opción de reenviarla.

#### HU-42 — Notificar al cliente que su vehículo está listo

**Como** cliente, **quiero** recibir un aviso automático cuando mi vehículo esté listo, **para** ir a recogerlo sin esperar una llamada.

- **Requisito funcional:** El sistema debe enviar automáticamente una notificación push al cliente cuando su orden cambie a Listo para recoger.
- **Vista:** V-42 Notificación push "Vehículo listo"
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** que una orden cambia a Listo para recoger, **cuando** se confirma el cambio, **entonces** el cliente recibe "Tu vehículo [placa] está listo para recoger".
2. **Dado** que el cliente tiene la app cerrada, **cuando** se envía la notificación, **entonces** la recibe igual en su celular.
3. **Dado** la notificación, **cuando** se envía, **entonces** queda registrada en la orden con su fecha y hora.

#### HU-43 — Reenviar aviso de vehículo listo

**Como** Jefe de Taller, **quiero** reenviar el aviso a un cliente que no ha recogido su vehículo, **para** recordárselo sin llamarlo.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller reenviar la notificación de vehículo listo a una orden en estado Listo para recoger, como máximo una vez cada 24 horas.
- **Vista:** V-43 Reenvío de aviso desde el detalle de orden
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** una orden Listo para recoger, **cuando** el Jefe pulsa "Reenviar aviso", **entonces** el cliente recibe de nuevo la notificación y el sistema muestra "Aviso reenviado".
2. **Dado** un aviso enviado hace menos de 24 horas, **cuando** intenta reenviarlo, **entonces** el botón está deshabilitado y muestra cuándo podrá reenviar.
3. **Dado** el detalle de la orden, **cuando** se muestra, **entonces** indica la fecha del último aviso enviado y cuántos avisos se enviaron.

---

### Actividad 2.5 — Recibir aviso de vehículo listo (Cliente)

> **Problema actual:** el cliente depende de contestar una llamada.
> **Solución:** el cliente recibe la notificación y la puede volver a ver en la app.

#### HU-44 — Abrir la orden desde la notificación

**Como** cliente, **quiero** abrir la orden al tocar la notificación, **para** ver el detalle del trabajo hecho antes de ir a recoger mi vehículo.

- **Requisito funcional:** El sistema debe abrir la vista de estado de la orden cuando el cliente toque una notificación relacionada con ella.
- **Vista:** V-44 Navegación desde la notificación (estado de la orden)
- **Prioridad:** Must · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** una notificación de orden, **cuando** el cliente la toca, **entonces** la app abre el estado de esa orden (HU-46).
2. **Dado** que el cliente no tiene la sesión iniciada, **cuando** toca la notificación, **entonces** la app pide iniciar sesión y después abre la orden.
3. **Dado** la notificación tocada, **cuando** se abre la orden, **entonces** la notificación queda marcada como leída.

#### HU-45 — Ver bandeja de notificaciones

**Como** cliente, **quiero** ver todas mis notificaciones, **para** revisar un aviso que borré de la barra del celular.

- **Requisito funcional:** El sistema debe mostrar al cliente sus notificaciones de la más reciente a la más antigua, distinguiendo las leídas de las no leídas.
- **Vista:** V-45 Bandeja de notificaciones
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** un cliente con notificaciones, **cuando** abre la bandeja, **entonces** ve cada notificación con su título, mensaje y fecha, y las no leídas resaltadas.
2. **Dado** una notificación, **cuando** la pulsa, **entonces** el sistema abre la orden relacionada y la marca como leída.
3. **Dado** notificaciones sin leer, **cuando** el cliente está en cualquier pantalla, **entonces** el ícono de la campana muestra cuántas son.

---

### Actividad 2.6 — Llamar al taller para consultar el avance (Cliente)

> **Problema actual:** el cliente no sabe cómo va su vehículo, llama varias veces y el técnico interrumpe su trabajo.
> **Solución:** el cliente consulta el estado y los hallazgos en la app. Así se **eliminan** las actividades "Consultar avance al técnico", "Informar avance al Jefe" e "Informar avance al cliente".

#### HU-46 — Ver estado actual de mi vehículo

**Como** cliente, **quiero** ver en qué estado está mi vehículo, **para** saber cómo va sin llamar al taller.

- **Requisito funcional:** El sistema debe mostrar al cliente el estado actual de su orden, con la fecha y la hora del último cambio y el técnico asignado.
- **Vista:** V-46 Estado de la orden (Cliente)
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** una orden del cliente, **cuando** la abre, **entonces** ve el número de orden, el vehículo, el estado actual destacado, la fecha del último cambio y el técnico asignado.
2. **Dado** que el estado cambia, **cuando** el cliente tiene la vista abierta, **entonces** el estado se actualiza sin recargar manualmente.
3. **Dado** la vista, **cuando** se muestra, **entonces** incluye accesos a "Ver hallazgos" (HU-48) y "Ver comprobante" (HU-30).

#### HU-47 — Ver línea de tiempo de la orden

**Como** cliente, **quiero** ver la línea de tiempo de mi orden, **para** saber cuándo ocurrió cada etapa.

- **Requisito funcional:** El sistema debe mostrar los estados de la orden en una línea de tiempo, con las etapas cumplidas, la actual y las pendientes, cada una con su fecha y hora.
- **Vista:** V-47 Línea de tiempo de la orden
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** una orden, **cuando** el cliente abre la línea de tiempo, **entonces** ve las etapas Ingresado, En reparación y Listo para recoger.
2. **Dado** cada etapa, **cuando** se muestra, **entonces** se distingue si está cumplida (con fecha y hora), es la actual o está pendiente.
3. **Dado** una orden anulada, **cuando** se muestra, **entonces** la línea de tiempo termina en "Anulada" con el motivo.

#### HU-48 — Ver hallazgos y trabajos realizados

**Como** cliente, **quiero** ver los hallazgos y trabajos registrados por el técnico, con sus fotos, **para** entender qué tenía mi vehículo y qué se le hizo.

- **Requisito funcional:** El sistema debe mostrar al cliente los avances de su orden en orden cronológico, con tipo, descripción, fotos y fecha, sin permitir editarlos.
- **Vista:** V-48 Hallazgos y trabajos (Cliente)
- **Prioridad:** Must · **Estimación:** 3

**Criterios de aceptación**
1. **Dado** una orden con avances, **cuando** el cliente abre la vista, **entonces** ve cada avance con su tipo (Hallazgo o Trabajo realizado), descripción, fotos y fecha.
2. **Dado** una foto, **cuando** la pulsa, **entonces** la foto se abre en pantalla completa.
3. **Dado** una orden sin avances, **cuando** abre la vista, **entonces** ve "El técnico aún no registra avances".
4. **Dado** la vista, **cuando** se muestra, **entonces** no tiene opciones de edición ni de eliminación.

### Actividad 2.7 — Entregar el vehículo al cliente (Jefe de Taller)

> **Contexto:** el cliente recibe el aviso y va al taller a recoger su vehículo (actividad manual, sin HU).
> **Problema actual:** la entrega no queda registrada y la orden nunca se cierra.
> **Solución:** el Jefe registra la entrega en la app y la orden pasa al historial del cliente.

#### HU-49 — Marcar orden como entregada

**Como** Jefe de Taller, **quiero** marcar una orden como entregada cuando el cliente retira su vehículo, **para** cerrar la orden y dejar constancia de la entrega.

- **Requisito funcional:** El sistema debe permitir al Jefe de Taller cambiar una orden en estado Listo para recoger a Entregado, previa confirmación, y registrar el cambio con fecha, hora y usuario (RN-13).
- **Vista:** V-49 Diálogo de confirmación de entrega
- **Prioridad:** Should · **Estimación:** 2

**Criterios de aceptación**
1. **Dado** una orden Listo para recoger, **cuando** el Jefe pulsa "Marcar como entregado" y confirma, **entonces** la orden pasa a Entregado, queda en el historial de estados y el sistema muestra "Vehículo entregado".
2. **Dado** una orden en cualquier otro estado, **cuando** se intenta entregar, **entonces** el sistema muestra "Solo se puede entregar una orden lista para recoger".
3. **Dado** una orden Entregada, **cuando** el cliente abre su vehículo o su cuenta, **entonces** la orden ya no cuenta como "en el taller": el vehículo muestra "Sin orden activa" y puede eliminar su vehículo o su cuenta (HU-04, HU-08).
4. **Dado** una orden Entregada, **cuando** el Jefe abre el panel de órdenes, **entonces** la orden ya no aparece; sigue visible en el historial del cliente y en su línea de tiempo, que cierra con la etapa "Entregado".
5. **Dado** la entrega registrada, **cuando** se guarda, **entonces** no se envía aviso al cliente, porque él mismo retiró el vehículo.

---

## 6. Catálogo de vistas para el prototipo

| Vista | Nombre | Rol | HU |
|---|---|---|---|
| V-T01 | Inicio de sesión | Todos | HU-T01 |
| V-T02 | Diálogo de cierre de sesión | Todos | HU-T02 |
| V-T03 | Recuperar contraseña | Todos | HU-T03 |
| V-T04 | Formulario de registro de técnico | Jefe | HU-T04 |
| V-T05 | Lista de técnicos | Jefe | HU-T05 |
| V-T06 | Formulario de edición de técnico | Jefe | HU-T06 |
| V-T07 | Diálogo de desactivación de técnico | Jefe | HU-T07 |
| V-01 | Registro de cliente | Cliente | HU-01 |
| V-02 | Mi perfil | Cliente | HU-02 |
| V-03 | Edición de perfil | Cliente | HU-03 |
| V-04 | Diálogo de eliminación de cuenta | Cliente | HU-04 |
| V-05 | Formulario de registro de vehículo | Cliente | HU-05 |
| V-06 | Mis vehículos | Cliente | HU-06 |
| V-07 | Formulario de edición de vehículo | Cliente | HU-07 |
| V-08 | Diálogo de eliminación de vehículo | Cliente | HU-08 |
| V-09 | Formulario de solicitud de atención | Cliente | HU-09 |
| V-10 | Selector de fotos de la solicitud | Cliente | HU-10 |
| V-11 | Mis solicitudes | Cliente | HU-11 |
| V-12 | Formulario de edición de solicitud | Cliente | HU-12 |
| V-13 | Diálogo de cancelación de solicitud | Cliente | HU-13 |
| V-14 | Solicitudes pendientes | Jefe | HU-14 |
| V-15 | Búsqueda de clientes | Jefe | HU-15 |
| V-16 | Detalle del cliente | Jefe | HU-16 |
| V-17 | Formulario de registro de cliente | Jefe | HU-17 |
| V-18 | Formulario de edición de cliente | Jefe | HU-18 |
| V-19 | Diálogo de eliminación de cliente | Jefe | HU-19 |
| V-20 | Formulario de registro de vehículo | Jefe | HU-20 |
| V-21 | Formulario de edición de vehículo | Jefe | HU-21 |
| V-22 | Diálogo de eliminación de vehículo | Jefe | HU-22 |
| V-23 | Formulario de orden de ingreso | Jefe | HU-23 |
| V-24 | Captura de fotos del estado exterior | Jefe | HU-24 |
| V-25 | Diálogo de eliminación de foto | Jefe | HU-25 |
| V-26 | Detalle de orden | Jefe | HU-26 |
| V-27 | Formulario de edición de orden | Jefe | HU-27 |
| V-28 | Diálogo de anulación de orden | Jefe | HU-28 |
| V-29 | Confirmación de ingreso registrado | Jefe | HU-29 |
| V-30 | Comprobante de ingreso | Cliente | HU-30 |
| V-31 | Mis órdenes | Cliente | HU-31 |
| V-32 | Mis órdenes asignadas | Técnico | HU-32 |
| V-33 | Detalle de orden | Técnico | HU-33 |
| V-34 | Diálogo de inicio de reparación | Técnico | HU-34 |
| V-35 | Formulario de registro de avance | Técnico | HU-35 |
| V-36 | Captura de fotos del avance | Técnico | HU-36 |
| V-37 | Lista de avances de la orden | Técnico | HU-37 |
| V-38 | Formulario de edición de avance | Técnico | HU-38 |
| V-39 | Diálogo de eliminación de avance | Técnico | HU-39 |
| V-40 | Diálogo de confirmación de vehículo listo | Técnico | HU-40 |
| V-41 | Panel de órdenes | Jefe | HU-41 |
| V-42 | Notificación push "Vehículo listo" | Cliente | HU-42 |
| V-43 | Reenvío de aviso desde el detalle de orden | Jefe | HU-43 |
| V-44 | Navegación desde la notificación | Cliente | HU-44 |
| V-45 | Bandeja de notificaciones | Cliente | HU-45 |
| V-46 | Estado de la orden | Cliente | HU-46 |
| V-47 | Línea de tiempo de la orden | Cliente | HU-47 |
| V-48 | Hallazgos y trabajos | Cliente | HU-48 |
| V-49 | Diálogo de confirmación de entrega | Jefe | HU-49 |

---

## 7. Matriz de trazabilidad (actividad BPMN → HU)

| Proceso | Actividad en verde | Lane | HU |
|---|---|---|---|
| P1 | 1.1 Proporcionar datos personales y del vehículo | Cliente | HU-01 a HU-08 |
| P1 | 1.2 Describir falla del vehículo | Cliente | HU-09 a HU-13 |
| P1 | 1.3 Buscar historial del cliente en cuaderno | Jefe | HU-14 a HU-16 |
| P1 | 1.4 Anotar datos del cliente y vehículo en cuaderno | Jefe | HU-17 a HU-22 |
| P1 | 1.5 Llenar ficha de ingreso | Jefe | HU-23 a HU-28 |
| P1 | 1.6 Indicar al cliente que se le llamará con novedades | Jefe | HU-29 |
| P1 | 1.7 Recibir indicaciones del Jefe de Taller | Cliente | HU-30, HU-31 |
| P2 | 2.1 Revisar ficha de ingreso del vehículo | Técnico | HU-32 a HU-34 |
| P2 | 2.2 Registrar hallazgos y trabajos realizados | Técnico | HU-35 a HU-39 |
| P2 | 2.3 Informar al Jefe que el vehículo está listo | Técnico / Jefe | HU-40, HU-41 |
| P2 | 2.4 Llamar al cliente para avisar que está listo | Sistema / Jefe | HU-42, HU-43 |
| P2 | 2.5 Recibir aviso de vehículo listo | Cliente | HU-44, HU-45 |
| P2 | 2.6 Llamar al taller para consultar el avance | Cliente | HU-46 a HU-48 |
| P2 | 2.7 Entregar el vehículo al cliente | Jefe | HU-49 |
| — | Módulo transversal (no es actividad del BPMN) | Todos | HU-T01 a HU-T07 |

---

## 8. Resumen

| Prioridad | Cantidad | Puntos |
|---|---|---|
| Must | 34 | 90 |
| Should | 19 | 37 |
| Could | 3 | 5 |
| **Total** | **56** | **132** |

**Por rol:** Jefe de Taller 23 · Cliente 21 · Técnico 9 · Todos los roles 3

**MVP sugerido (solo Must):** permite el flujo completo: el cliente se registra y reporta la falla, el Jefe registra el ingreso con fotos, el técnico registra avances y marca "listo", y el cliente ve el estado y recibe el aviso.

---

## 9. Fuera de alcance

Estas funciones pertenecen a procesos que no se modelaron:

| Función | Proceso al que pertenece |
|---|---|
| Elaborar o aprobar cotizaciones | Cotización |
| Asignar técnicos según la disponibilidad o la cola de espera | Asignación de técnico |
| Pagos en línea | Cobro |
| Inventario y registro de repuestos | Gestión de repuestos |
