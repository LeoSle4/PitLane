# Cambio de datos locales a Firebase

La app trabaja hoy con datos en memoria. Las pantallas y los ViewModels no conocen de dónde vienen los datos: solo usan las interfaces `I*Repository`. Para pasar a Firebase se escribe una implementación nueva de las tres fuentes de datos y se cambia un solo archivo.

Los diagramas de referencia están en `docs/04-arquitectura/` (`diagrama-componentes` y `diagrama-despliegue`, en `.png`). Se regeneran con `cd docs/04-arquitectura && python3 -I ../../scripts/diagrama-componentes/marcos.py` y `python3 -I ../../scripts/diagrama-despliegue/marcos.py` (requieren Graphviz y `rsvg-convert`).

## Estado actual frente a los diagramas

El diagrama de despliegue muestra la arquitectura **objetivo** con Firebase. Hoy la app funciona sin nube:

| En el diagrama | Hoy en el código |
|---|---|
| Firebase Auth, Firestore, Storage, Functions y Cloud Messaging | Fuentes locales en `fuente/local` sobre `BaseDatosLocal` (memoria) |
| `FirebaseMessagingService` | `MensajesPushService` (`object` con un flujo en proceso) |
| `firestore.rules`, `storage.rules`, `functions/` | No existen; las reglas RN-06, RN-07 y RN-08 se validan en los repositorios y en la vista |
| Sesión y datos persistentes | La sesión y los datos se pierden al cerrar el proceso |

Los nueve ViewModels reciben su repositorio desde `Dependencias` por argumento por defecto; el diagrama de componentes solo dibuja esa flecha hacia `AuthRepository`.

## Punto de cambio

`app/src/main/java/com/leosle/pitlane/Dependencias.kt` es la raíz de composición: crea las tres fuentes de datos y arma los repositorios sobre ellas. Los repositorios (`repositorio/`) tienen las reglas de negocio y no conocen de dónde vienen los datos; **lo único que cambia al pasar a Firebase son las fuentes**:

```kotlin
private val usuarios: IUsuariosDataSource by lazy { UsuariosDataSource(auth, firestore, funciones) }
private val documentos: IDocumentosDataSource by lazy { DocumentosDataSource(firestore) }
private val fotos: IFotoStorage by lazy { FotoStorage(storage) }
```

Hoy esas tres clases están en `fuente/local` y trabajan sobre `BaseDatosLocal`. Las de Firebase irán en `fuente/firebase` con los mismos nombres del diagrama.

## Capas del diagrama de componentes

| Capa | Dónde está hoy | Con Firebase |
|---|---|---|
| Vista (9 módulos) | `vista/acceso`, `perfil`, `clientes`, `tecnicos`, `vehiculos`, `solicitudes`, `ordenes`, `avances`, `notificaciones` | Sin cambios |
| ViewModel (9) | `viewmodel/` | Sin cambios |
| Interfaces `I*Repository` (9) y repositorios | `repositorio/` | Sin cambios |
| Fuentes remotas (3) | Interfaces en `fuente/`, versión local en `fuente/local` | Nuevas clases en `fuente/firebase` |

El diagrama de componentes muestra que la capa Repository usa las tres fuentes. El detalle por repositorio está aquí y en `docs/04-arquitectura/diagrama-dependencias-repositorios.png`: cada uno tiene una fuente principal y, cuando lo necesita, otra más:

| Repositorio | Fuente principal | También usa |
|---|---|---|
| `AuthRepository`, `PerfilRepository` | `UsuariosDataSource` | |
| `ClienteRepository`, `TecnicoRepository` | `UsuariosDataSource` | `DocumentosDataSource` (ficha, historial y carga) |
| `VehiculoRepository` | `DocumentosDataSource` | `UsuariosDataSource` (nombre del dueño) |
| `SolicitudRepository`, `OrdenRepository`, `AvanceRepository` | `DocumentosDataSource` y `FotoStorage` | `UsuariosDataSource` (cliente y técnico de cada registro) |
| `NotificacionRepository` | `DocumentosDataSource` | |

Todos los métodos que escriben son `suspend` y los que observan devuelven `Flow`. Las escuchas de Firestore (`addSnapshotListener`) se convierten en `callbackFlow`.

## Lo que hoy simulan las fuentes locales y pasará a la nube

| Regla | Hoy | En Firebase |
|---|---|---|
| Crear la cuenta de un técnico, unir al cliente por DNI y desactivar cuentas | `UsuariosDataSource` | Funciones invocables (`crearTecnico`, `unirClienteConDni`, `desactivarCuenta`) con Admin SDK. Con el SDK del cliente, crear un usuario cerraría la sesión del Jefe |
| Avisos de ingreso, reparación, listo, reenvío y anulación | `FuncionesLocales`, que se ejecuta al guardar una orden | Disparador de Firestore `notificarOrden`: escribe en `notificaciones` y envía el aviso por Cloud Messaging |
| Número correlativo `ORD-0001` | `DocumentosDataSource.siguienteNumeroOrden` | Transacción de Firestore con un documento contador, desde la app del Jefe (solo el Jefe crea órdenes, RN-06) |
| Escrituras que deben aplicarse juntas | `DocumentosDataSource.enLote` | `WriteBatch` o transacción |
| Reenvío de aviso cada 24 horas como máximo | `OrdenRepository` | `firestore.rules`, comparando `request.time` con `ultimoAviso` |
| Cada rol ve solo lo suyo (RN-06, RN-07, RN-08) | Filtros en los repositorios con el usuario de la sesión (`AccesoPorRol.kt`): el Jefe ve todo, el técnico sus órdenes asignadas y el historial de los vehículos (HU-33), y el cliente solo lo propio; `marcarLeida` solo acepta avisos propios. Equivale al `request.auth` de las reglas | `firestore.rules` y `storage.rules`. Los clientes no podrán leer usuarios ajenos: la información del técnico y del cliente deberá guardarse dentro de cada orden |
| Estado Entregado (HU-49) | `OrdenRepository.marcarEntregada`, solo el Jefe y solo desde Listo, sin aviso | Regla de Firestore que permita pasar a Entregado solo al Jefe y desde Listo; `notificarOrden` no envía aviso en ese cambio |
| Fotos JPG o PNG de hasta 5 MB (RN-09) | `FotoStorage` | `FotoStorage` sube a Cloud Storage y `storage.rules` valida en el servidor |

## Avisos en el teléfono

- `MensajesPushService` (`vista/notificaciones`): escucha los avisos nuevos del usuario con sesión y, si la app no está a la vista, muestra una notificación del sistema que abre la orden al tocarla. Con Firebase se reemplaza por un `FirebaseMessagingService`.
- El token del dispositivo se guarda al abrir la app con `NotificacionRepository.guardarTokenDispositivo`.
- Hoy los avisos solo llegan mientras el proceso de la app esté vivo. Con Firebase llegarán también con la app cerrada.
- Dependencias del SDK que se agregan: `firebase-auth`, `firebase-firestore`, `firebase-storage`, `firebase-functions` y `firebase-messaging`, con el BoM de Firebase.

## Datos de demostración

`BaseDatosLocal` carga cuentas, vehículos, solicitudes, órdenes y avances de ejemplo. La pantalla de inicio de sesión incluye un combo de cuentas de demostración (`FragLogin`); se elimina junto con la base local.
