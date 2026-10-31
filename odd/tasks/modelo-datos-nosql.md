# Modelo de datos NoSQL: instantáneas, subcolecciones y unicidad

Objetivo: aplicar a Firestore las mejoras de modelado acordadas (instantánea en la orden, tokens por dispositivo, consultas dirigidas, placa única).
Rama: `feat/modelo-datos-nosql`. Entrega: `ask-on-risk`. Pruebas de reglas: `cd firebase && npm test`; de funciones: `cd firebase/functions && npm test`; de la app: `./gradlew --offline testDebugUnitTest` (línea base verde).

## Specs

- S0 (hecho) "Los 4 desarrolla": reglas de `ordenes` con transiciones del Jefe (anular desde Ingresado, entregar desde Lista con conformidad ya guardada), conformidad inmutable y esquema cerrado en la actualización. Commit `7826db2`.
- S1 Instantánea en la orden y la solicitud. "Guarda también un objeto embebido con los datos mínimos que siempre vas a mostrar en una lista, por ejemplo: vehiculo: { placa: \"ABC-123\", marca: \"Toyota\", modelo: \"Hilux\" }. Si el usuario quiere ver los detalles ... recién ahí consultas la colección vehiculos."
  - S1a Datos: `ordenes` y `solicitudes` guardan `cliente {nombres, apellidos, dni, telefono}`, `vehiculo {placa, marca, modelo, anio, color}` y solo en `ordenes` también `tecnico {nombres, apellidos}`. Se escribe al crear y, en la orden, `tecnico` se actualiza al reasignar. Es una instantánea del ingreso: un cambio posterior de nombre o teléfono NO se propaga (decisión: la ficha conserva lo cierto el día del ingreso). Reglas: los campos nuevos entran en las claves permitidas; el Jefe y el cliente solo los escriben al crear. Script de relleno para documentos existentes (no se ejecuta contra el proyecto real sin autorización).
  - S1b Lectura: las listas de órdenes y solicitudes se arman con la instantánea y dejan de escuchar las colecciones completas de `usuarios`, `vehiculos` y `solicitudes`. El detalle de una orden resuelve cliente, vehículo, técnico y solicitud por `get` de un solo documento.
- S2 `tokensDispositivo` como subcolección: `usuarios/{usuarioId}/tokensDispositivo/{token}`, un documento por dispositivo, para que un usuario con dos teléfonos no pise el token. Reglas, fuente de datos, pruebas y migración del documento antiguo.
- S3 Consultas dirigidas en lugar de `listar()` completo: avances de una orden, órdenes y solicitudes de un vehículo. Con `whereEqualTo` más el alcance del usuario; índices en `firestore.indexes.json` si hacen falta.
- S4 Placa única con colección `placas/{placa}`: "La unicidad de la placa no está garantizada ... check-then-create". El vehículo y su reserva se crean en el mismo lote; las reglas exigen que no exista y que se cree con el vehículo; se libera al borrar el vehículo. Script de relleno para vehículos existentes.
- S5 Tras cada tarea, una evaluación como ingeniero de bases de datos NoSQL (lecturas y costo, consistencia y atomicidad, seguridad, migración y reversión, riesgos que quedan), registrada en el Log. Al final se actualizan `docs/04-arquitectura/modelo-de-datos-firestore.md` y el diagrama.

## Tasks

- [x] T0 S0 reglas del Jefe. inline. commit 7826db2
- [x] T1 S1a instantánea: datos, escritura, reglas, relleno. route: delegada (contexto). commit ver git log (feat(modelo): instantánea...). RED: 6 pruebas Android y 10 de reglas; GREEN: 65 de reglas y Gradle verde
- [ ] T2 S1b listas desde la instantánea; detalle por get. route: delegada (contexto)
- [ ] T3 S2 tokens por dispositivo. route: delegada (contexto)
- [ ] T4 S3 consultas dirigidas. route: delegada (contexto)
- [ ] T5 S4 placa única. route: delegada (contexto)
- [ ] T6 S5 documentación y diagrama finales. route: inline

## Log

L1 (2026-10-31, usuario): "Desarrolla todos los punto, pero cada que termines evaluao como ingeneiro de base dedatos no relacional, correctamente"
L2 (2026-10-31, usuario, observaciones pegadas sobre el diagrama): normalización excesiva con lecturas en cadena, falta de desnormalización, subcolecciones para `tokensDispositivo` y `avances`, y contador de órdenes "peligroso". Evaluado: se acepta la instantánea y los tokens por dispositivo; los avances se resuelven con consulta dirigida; el contador distribuido se descarta (solo el Jefe crea órdenes y una transacción basta).
L3 (2026-10-31, orquestador): la ruta delegada se elige por el respaldo de contexto de la sesión (la sesión supera con creces los ~150k tokens); las tareas comparten archivos, así que se ejecutan una tras otra.

L4 (T1, verificación independiente): 1 bloqueo reproducido y corregido: `editarOrden` reescribía `tecnico` y las reglas lo rechazaban si `tecnicoId` no cambiaba (órdenes anteriores al relleno o técnico renombrado). Ahora la instantánea solo cambia al reasignar. Se agregaron pruebas en las dos capas y un mensaje claro en `registrarSolicitud` cuando el cliente o el vehículo no existen.
L5 (T1, evaluación como ingeniero NoSQL):
- Lecturas y costo: la instantánea añade unos 300 bytes por documento y no cuesta lecturas extra al escribir (el repositorio ya leía cliente, vehículo y técnico). El ahorro de lecturas NO existe todavía: llega con T2, cuando las listas dejen de escuchar `usuarios`, `vehiculos` y `solicitudes` completos.
- Consistencia: es una instantánea del ingreso, por diseño no se propaga (la ficha conserva lo cierto ese día). `cliente` y `vehiculo` son inmutables por regla; `tecnico` cambia solo junto con `tecnicoId`. Riesgo asumido: un cambio de teléfono o nombre no se refleja en órdenes ya creadas.
- Seguridad: hallazgo propio, la instantánea de la solicitud la escribe el cliente y la regla solo validaba su forma, así que podía falsear placa o DNI ante el Jefe. Corregido: al crear, debe coincidir con `usuarios/{uid}` y con el vehículo (`get` en la regla, 2 lecturas por solicitud nueva, volumen bajo). En `ordenes` no se compara porque solo escribe el Jefe.
- Atomicidad: sin cambio; el número de orden y la orden siguen sin transacción conjunta (punto 8 de la evaluación).
- Migración y reversión: los lectores toleran documentos sin instantánea (null). El relleno es seco por defecto y nunca pisa una instantánea existente. Reversión: ignorar los campos. ORDEN DE DESPLIEGUE: primero publicar la app nueva, luego ejecutar el relleno, y al final desplegar las reglas; si las reglas van antes, la app antigua no podrá crear órdenes ni solicitudes porque ahora exigen la instantánea.
- Riesgos que quedan: las reglas de `ordenes` comprueban la forma de la instantánea pero no su coincidencia con los perfiles; el relleno usa `''` o `0` si falta un dato del perfil; una solicitud puede apuntar al vehículo de otro cliente (la regla no comprueba la propiedad del vehículo).
