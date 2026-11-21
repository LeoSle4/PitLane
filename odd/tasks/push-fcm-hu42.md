Objective: cerrar HU-42 criterio 2 con push real por FCM y alinear todos los diagramas y documentos.
Branch: feat/modelo-datos-nosql · Strategy: ask-on-risk · Runner: gradle testDebugUnitTest, npm test (firebase, firebase/functions)

## Specs
- S1: "Dado ... el teléfono muestra la notificación con la app en segundo plano o cerrada" (HU-42 criterio 2): la función `notificarOrden` envía un push FCM a los tokens de `usuarios/{clienteId}/tokensDispositivo`.
- S2: La app registra el token real de FCM (no un UUID) en `tokensDispositivo/{token}` con `expiraEn` (reglas existentes: claves `token`, `expiraEn`) y reacciona a `onNewToken`.
- S3: Un token que FCM rechaza por inválido o no registrado se borra de `tokensDispositivo`.
- S4: La notificación del sistema con la app en primer plano no se duplica; tocarla abre la orden (extras `orden_id`, `notificacion_id`, `es_ingreso`).
- S5: Todos los diagramas (componentes, despliegue, modelo lógico/físico, ER, guías de exposición, README, modelo-de-datos-firestore) dejan de decir que PitLane no usa FCM y muestran Firebase Cloud Messaging.

## Tasks
- T1 (S1,S3): función push en `firebase/functions/src` + tests — route: delegated (parallel unit A)
- T2 (S2,S4): app con firebase-messaging, servicio y token real + tests — route: delegated (parallel unit A, same writer as T1)
- T3 (S5): diagramas y documentos — route: delegated (parallel unit B)

## Log
- L1 (2026-11-21): "mientras se actualicen todos los diagramas hagamoslo" (implementar FCM para cerrar HU-42 criterio 2 y actualizar todos los diagramas).
