# Servicio Eléctrico Freddy — Documentación del proyecto

**Proyecto:** Aplicación móvil para la recepción y seguimiento de reparaciones de vehículos en Servicio Eléctrico Freddy
**Nombre de la app:** Servicio Eléctrico Freddy
**Empresa:** taller eléctrico automotriz en Ate (Lima) · 1 Jefe de Taller y 3 Técnicos Electricistas

Esta carpeta es el contexto para desarrollar la app. Léela en este orden.

## Contenido

| Carpeta | Archivo | Para qué sirve al desarrollar |
|---|---|---|
| `01-procesos/` | `proceso-1-recepcion-del-vehiculo.bpmn` / `.png` | Proceso actual (AS-IS) de recepción. Las tareas en verde son las que automatiza la app |
| | `proceso-2-reparacion-del-vehiculo.bpmn` / `.png` | Proceso actual (AS-IS) de reparación y consulta del avance |
| `02-requisitos/` | `historias-de-usuario.md` | **Fuente principal.** 55 HU con requisito funcional, vista, prioridad MoSCoW, puntos, criterios de aceptación (Gherkin), reglas de negocio RN-01 a RN-13, estados, catálogo de vistas, trazabilidad y fuera de alcance |
| `03-diseno/` | `guia-diseno-y-prototipo.md` | Sistema de diseño (colores claro/oscuro, tipografía, íconos, estados) y qué pantalla, bottom sheet o diálogo corresponde a cada HU |
| `04-arquitectura/` | `diagrama-componentes.puml` / `.png` | Vistas, ViewModels, interfaces y Repositories (MVVM) y su relación con Firebase |
| | `diagrama-despliegue.puml` / `.png` | Celular Android y servicios de Firebase |
| `05-exposicion/` | `exposicion-procesos-y-arquitectura.md` | Explicación de cada diagrama, decisiones de arquitectura y mapa actividad BPMN → HU → Vista → servicio Firebase (sección 9) |
| | `presentacion-servicio-electrico-freddy.pptx` | Presentación del curso |

## Resumen técnico

| Tema | Decisión |
|---|---|
| Plataforma | Android nativo, **Kotlin**, layouts **XML**, Android 8.0 (API 26) o superior |
| Patrón | **MVVM + Repository**: View (Activity/Fragment) → ViewModel (`StateFlow`) → interfaz `I*Repository` → Repository → Firebase SDK |
| Backend | **Firebase**, sin servidor propio: Authentication (correo y contraseña), Cloud Firestore, Cloud Storage (fotos), Cloud Functions (Node.js 20) y Cloud Messaging (notificaciones) |
| Colecciones Firestore | `usuarios` · `vehiculos` · `solicitudes` · `ordenes` · `avances` · `notificaciones` |
| Seguridad | `firestore.rules` y `storage.rules` por rol (RN-06, RN-07, RN-08) |
| Plan Firebase | Blaze (lo exigen Storage y Functions), con alerta de presupuesto de USD 1 |
| Roles | Cliente (se registra solo) · Jefe de Taller (cuenta inicial) · Técnico Electricista (creado por el Jefe) |
| Estados de la orden | `Ingresado` → `En reparación` → `Listo para recoger` · o `Anulada` |
| Estados de la solicitud | `Pendiente` → `Atendida` · o `Cancelada` |

### Módulos (uno por par View/ViewModel/Repository)

Auth · Perfil · Cliente · Técnico · Vehículo · Solicitud · Orden · Avance · Notificación

### Orden de desarrollo sugerido

1. **MVP: solo las 34 HU Must.** Cubren el flujo completo: el cliente se registra y reporta la falla, el Jefe registra el ingreso con fotos, el técnico registra avances y marca "listo", y el cliente ve el estado y recibe el aviso.
2. Después, las 18 HU Should.
3. Al final, las 3 HU Could (HU-04, HU-19 y HU-22).

### Fuera de alcance

Cotización, asignación automática de técnicos, entrega del vehículo, pagos en línea e inventario de repuestos.
