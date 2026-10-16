# Guía de diseño y prototipo en Figma
## App Servicio Eléctrico Freddy

---

## 1. Nombre y título

| | |
|---|---|
| **Nombre de la app** | **Servicio Eléctrico Freddy** (el mismo nombre de la empresa) |
| **Título formal del proyecto** | **Aplicación móvil para la recepción y seguimiento de reparaciones de vehículos en Servicio Eléctrico Freddy** |

El título es corto y nombra los dos procesos modelados: la **recepción** (Proceso 1) y el **seguimiento de la reparación** (Proceso 2).

---
#1.1 link Figma
https://www.figma.com/design/ovUIx6bM5Jym2brqw6CY7x/Proyecto-app-moviles?node-id=8-2&t=v2vOMQfL8FvT8JvY-1

## 2. Sistema de diseño

### 2.1 Paleta de colores

Todos los pares de texto y fondo cumplen el contraste **WCAG AA** (mínimo 4.5:1), verificado.

#### Modo claro — rojos suaves y blanco

| Token | Color | Uso | Contraste |
|---|---|---|---|
| `primary` | `#C8403A` | Botones principales, FAB, ícono activo | Blanco sobre él: 4.9:1 |
| `onPrimary` | `#FFFFFF` | Texto sobre botones rojos | — |
| `primaryContainer` | `#FDECEB` | Fondos suaves: chips, tarjetas destacadas | — |
| `onPrimaryContainer` | `#B3342E` | Texto sobre fondos rojos suaves y botones de eliminar | 5.3:1 |
| `accent` | `#E8817B` | Detalles decorativos e ilustraciones (no para texto) | — |
| `background` | `#FFFFFF` | Fondo de pantallas | — |
| `surface` | `#FFF8F7` | Tarjetas y modales (blanco cálido) | — |
| `surfaceVariant` | `#F1EEEE` | Campos de formulario y campos bloqueados | — |
| `outline` | `#EADBDA` | Bordes de campos y tarjetas | — |
| `scrim` | `#000000` al 40 % | Fondo oscurecido detrás de los modales | — |
| `textPrimary` | `#1C1717` | Títulos y texto principal | 17.7:1 |
| `textSecondary` | `#6E6363` | Texto de apoyo, fechas, ayudas | 5.8:1 |

#### Modo oscuro — negro con rojos suaves

| Token | Color | Uso | Contraste |
|---|---|---|---|
| `primary` | `#FF8A82` | Botones principales, ícono activo | Negro sobre él: 9.2:1 |
| `onPrimary` | `#000000` | Texto sobre botones rojos | — |
| `primaryContainer` | `#3A1716` | Chips y tarjetas destacadas | — |
| `onPrimaryContainer` | `#FFB4AE` | Texto sobre fondos rojos oscuros | 9.4:1 |
| `background` | `#000000` | Fondo de pantallas (negro puro) | — |
| `surface` | `#121010` | Tarjetas y modales | — |
| `surfaceVariant` | `#252020` | Campos de formulario | — |
| `outline` | `#3A3333` | Bordes | — |
| `scrim` | `#000000` al 60 % | Fondo detrás de los modales | — |
| `textPrimary` | `#F5F2F2` | Texto principal | 18.9:1 |
| `textSecondary` | `#A39A9A` | Texto de apoyo | 7.7:1 |

#### Colores de estado de la orden

El rojo de la marca **no** se usa para estados, así nada parece una alerta. Cada estado lleva color **e** ícono, para no depender solo del color.

| Estado | Fondo claro / texto | Fondo oscuro / texto | Ícono (Phosphor) |
|---|---|---|---|
| Ingresado | `#F1EEEE` / `#5F5656` | `#252020` / `#C8C0C0` | `clipboard-text` |
| En reparación | `#FFF4DC` / `#8A5A00` | `#2E2410` / `#F2C46B` | `wrench` |
| Listo para recoger | `#E3F4EA` / `#1E7A4F` | `#0F2A1E` / `#6FD3A0` | `check-circle` |
| Anulada | `#FDECEB` / `#B3342E` | `#3A1716` / `#FFB4AE` | `x-circle` |

### 2.2 Tipografía

**Fuente: Plus Jakarta Sans** (Google Fonts, gratuita): moderna, geométrica y muy legible en pantallas pequeñas.

- **En Figma:** está disponible directamente en el selector de fuentes.
- **En Android (XML):** atributo `fontFamily` → *More Fonts…* → "Plus Jakarta Sans" → *Create downloadable font*.
- **Placas y números de orden:** activar números tabulares (`tnum`) para que queden alineados.

| Estilo | Tamaño | Peso | Uso |
|---|---|---|---|
| Display | 28 | Bold | Saludo de inicio, estado grande del vehículo |
| Title | 20 | SemiBold | Títulos de pantalla y de modal |
| Subtitle | 16 | SemiBold | Títulos de tarjeta |
| Body | 14 | Regular | Texto general |
| Label | 12 | Medium | Chips, botones pequeños, etiquetas de campo |
| Caption | 11 | Regular | Fechas, horas, notas |

### 2.3 Íconos

**Librería: Phosphor Icons** (gratuita, licencia MIT). Su trazo fino y redondeado se ve minimalista y moderno, a diferencia de los íconos clásicos de Material.

- **Estados del ícono:** peso **Regular** en estado normal y **Fill** cuando está seleccionado.
- **En Figma:** plugin oficial "Phosphor Icons".
- **En Android:** descargar el SVG desde phosphoricons.com e importarlo con *File → New → Vector Asset → Local file (SVG)*.

| Uso | Ícono | Uso | Ícono |
|---|---|---|---|
| Mis vehículos | `car-profile` | Panel del Jefe | `squares-four` |
| Solicitudes | `clipboard-text` | Clientes | `address-book` |
| Órdenes / seguimiento | `path` | Técnicos | `users-three` |
| Notificaciones | `bell` | Hallazgo | `magnifying-glass` |
| Perfil | `user-circle` | Trabajo realizado | `wrench` |
| Agregar | `plus` | Foto | `camera` |
| Editar | `pencil-simple` | Eliminar | `trash` |
| Más opciones | `dots-three-vertical` | Cerrar modal | `x` |
| Advertencia | `warning-circle` | Taller eléctrico | `lightning` |

### 2.4 Animaciones y logo animado

| Recurso | Herramienta en Android | Cómo se simula en Figma | Dónde se usa |
|---|---|---|---|
| **Logo animado** | `AnimatedVectorDrawable` + `androidx.core:core-splashscreen` | Smart Animate entre dos frames del logo | Pantalla de inicio |
| **Animaciones de estado** | **Lottie** (`LottieAnimationView` en XML) | Plugin "LottieFiles for Figma" | Carga, lista vacía, orden registrada, vehículo listo |
| **Modales** | `BottomSheetDialogFragment` / `MaterialAlertDialog` | Open overlay con animación *Move in* | Editar, eliminar, confirmar |
| **Transiciones** | `MaterialContainerTransform` | Smart Animate | De una tarjeta a su detalle |

**Logo:** un rayo (taller eléctrico) dentro de un pin de ubicación (seguimiento), con el texto "Servicio Eléctrico Freddy". Rojo `#C8403A` sobre blanco, o blanco sobre rojo para el ícono de la app.

**Animaciones Lottie** (gratuitas en lottiefiles.com, recoloreadas con la paleta):

| Momento | Animación |
|---|---|
| Cargando | Rayo que pulsa |
| Lista vacía | Auto con una lupa |
| Orden registrada (HU-29) | Check que se dibuja |
| Vehículo listo (HU-46) | Check con destellos (una sola vez) |
| Sin conexión | Enchufe desconectado |

Las animaciones duran entre 300 y 1 200 ms y se desactivan si el usuario activó *Quitar animaciones* en la accesibilidad de Android.

### 2.5 Estilo Liquid Glass (vidrio translúcido)

**Sí se puede en Kotlin con XML:**

| Opción | Librería | Ventaja | Limitación |
|---|---|---|---|
| **Recomendada** | **BlurView** (`com.github.Dimezis:BlurView`, vía JitPack) | Madura, compatible con layouts XML | Desenfoque tipo vidrio, sin refracción |
| Avanzada | **Liquid-Glass-Android** (`com.github.QWEA0:liquidglass`, vía JitPack) | Refracción y reflejos reales en XML | El efecto completo requiere Android 13+; proyecto pequeño |

**En Figma:** rectángulo con relleno `surface` al 70 %, efecto *Background blur* de 20, borde interior de 1 px en blanco al 30 % (claro) o al 12 % (oscuro) y radio de 20.

| ✅ Usar vidrio en | ❌ No usar vidrio en |
|---|---|
| Barra de navegación inferior | Formularios |
| Barra superior al desplazarse | Modales de edición y diálogos |
| Tarjeta de estado sobre la foto del vehículo | Textos largos |
| Botón flotante sobre fotos | Listas extensas |

### 2.6 Espaciado, bordes y sombras

| Elemento | Valor |
|---|---|
| Escala de espaciado | 4 · 8 · 12 · 16 · 24 · 32 |
| Margen lateral de pantalla | 16 |
| Radio de campos y botones | 14 |
| Radio de tarjetas | 20 |
| Radio de hoja modal | 28 (solo esquinas superiores) |
| Radio de diálogo modal | 28 |
| Altura de botón principal | 52 |
| Área táctil mínima | 48 × 48 |
| Sombras | Ninguna en modo oscuro; muy suaves en modo claro |

---

## 3. Reglas de prototipado en Figma

### 3.1 Regla principal

> **Cada historia de usuario se prototipa.** Cada HU tiene su propio frame (o modal) con el código de su vista, todos sus elementos, sus estados y su interacción conectada. Así el prototipo demuestra el funcionamiento completo de la app.

### 3.2 Qué se prototipa como pantalla y qué como modal

| Tipo de acción | Cómo se prototipa | Componente | Ejemplos |
|---|---|---|---|
| **Listar, ver detalle, buscar** | **Pantalla** completa | Frame de 412 × 917 | Mis vehículos, Detalle de orden |
| **Registrar** (crear algo nuevo) | **Pantalla** con formulario | Frame de 412 × 917 | Registrar vehículo, Registrar orden |
| **Editar** | **Hoja modal** que sube desde abajo, con los datos ya llenados | Componente `Modal/Hoja` | Editar vehículo, Editar avance |
| **Eliminar, cancelar, anular, desactivar** | **Diálogo modal** centrado de confirmación | Componente `Modal/Diálogo` | Eliminar vehículo, Anular orden |
| **Cambiar estado** (iniciar reparación, marcar listo) | **Diálogo modal** de confirmación | Componente `Modal/Diálogo` | Iniciar reparación |
| **Elegir foto, técnico o fecha** | **Hoja modal** de selección | Componente `Modal/Selector` | Tomar foto o elegir de la galería |
| **Ver una foto** | **Visor** a pantalla completa | Componente `Modal/Visor` | Ampliar foto de un hallazgo |
| **Confirmar que algo se guardó** | **Snackbar** temporal abajo | Componente `Snackbar` | "Vehículo actualizado" |
| **Aviso push** | **Notificación** simulada arriba | Componente `Notificación` | "Tu vehículo está listo" |

**Excepción:** "Editar orden de ingreso" (HU-27) tiene muchos campos, así que se prototipa como **modal de pantalla completa**: sube desde abajo, tiene una "X" para cerrar y un botón "Guardar" arriba.

### 3.3 Anatomía de los modales

**Hoja modal de edición (`Modal/Hoja`)**
1. Fondo oscurecido (`scrim`) sobre la pantalla de origen.
2. Hoja `surface` con radio 28 arriba, que ocupa hasta el 90 % del alto.
3. Agarradera gris de 32 × 4 arriba al centro.
4. Título ("Editar vehículo") y botón `x` para cerrar.
5. Campos ya llenados con los datos actuales; los campos que no se pueden editar (placa, DNI, correo) aparecen **bloqueados**: fondo `surfaceVariant` y candado `lock-simple`.
6. Botón principal "Guardar cambios" al final, deshabilitado hasta que haya un cambio.

**Diálogo modal de confirmación (`Modal/Diálogo`)**
1. Fondo oscurecido (`scrim`).
2. Tarjeta `surface` centrada, radio 28, ancho de 312.
3. Ícono arriba: `trash` (eliminar), `warning-circle` (anular o desactivar) o `wrench` / `check-circle` (cambio de estado).
4. Título como pregunta: "¿Eliminar este vehículo?".
5. Texto con la consecuencia: "Se quitará de tu lista. Esta acción no se puede deshacer".
6. Dos botones: "Cancelar" (texto) y la acción ("Eliminar", con relleno `#B3342E`).
7. **Variante de error** cuando la acción no se puede hacer (por ejemplo, vehículo en el taller): solo ícono, mensaje y el botón "Entendido".

### 3.4 Biblioteca de componentes (página "Componentes")

Cada componente se crea con **variantes** para no dibujar dos veces lo mismo:

| Componente | Variantes |
|---|---|
| Botón | Primario · Secundario · Texto · Peligro × Normal · Presionado · Deshabilitado · Cargando |
| Campo de texto | Vacío · Con foco · Lleno · Error · Bloqueado |
| Chip de estado | Ingresado · En reparación · Listo · Anulada · Pendiente · Atendida · Cancelada |
| Tarjeta | Vehículo · Orden · Solicitud · Avance · Técnico · Cliente |
| Barra inferior (vidrio) | Cliente · Jefe · Técnico × pestaña activa |
| Modal | Hoja · Diálogo · Diálogo de error · Selector · Visor · Pantalla completa |
| Estados de lista | Vacío (Lottie) · Cargando · Sin conexión |
| Snackbar | Éxito · Error |
| Notificación push | Ingreso · En reparación · Listo · Anulada |

**Variables de color en Figma:** crear una colección "Tema" con dos modos, **Claro** y **Oscuro**, y aplicar los tokens de la sección 2.1. Así se cambia todo el prototipo de modo con un clic.

### 3.5 Interacciones de Figma

| Acción del usuario | Interacción en Figma | Configuración |
|---|---|---|
| Ir a otra pantalla | *On tap → Navigate to* | Smart Animate, 300 ms, ease out |
| Abrir hoja modal | *On tap → Open overlay* | Posición: abajo al centro · Animación: Move in desde abajo, 300 ms · ✅ Cerrar al tocar fuera · ✅ Fondo oscurecido |
| Abrir diálogo modal | *On tap → Open overlay* | Posición: centro · Animación: Dissolve, 200 ms · ❌ Cerrar al tocar fuera (obliga a elegir) · ✅ Fondo oscurecido |
| Cerrar modal (`x` o "Cancelar") | *On tap → Close overlay* | — |
| Guardar en un modal | *On tap → Close overlay* + *Navigate to* la variante de pantalla con Snackbar | Muestra el dato actualizado |
| Confirmar eliminación | *On tap → Close overlay* + *Navigate to* la variante de lista sin el elemento | Snackbar "Vehículo eliminado" |
| Volver | *On tap → Back* | — |
| Cambiar de pestaña | Componente interactivo (*Change to*) | Ícono Regular → Fill |
| Notificación que llega | *After delay 2 000 ms → Open overlay* arriba | Simula el push |

### 3.6 Organización del archivo de Figma

| Página | Contenido |
|---|---|
| 0 · Portada | Nombre, título formal, versión |
| 1 · Sistema de diseño | Colores, tipografía, íconos, espaciado |
| 2 · Componentes | Biblioteca con variantes |
| 3 · Acceso | HU-T01 a HU-T03, HU-01 |
| 4 · Cliente | HU-02 a HU-13, HU-30, HU-31, HU-44 a HU-48 |
| 5 · Jefe de Taller | HU-T04 a HU-T07, HU-14 a HU-29, HU-41, HU-43 |
| 6 · Técnico | HU-32 a HU-40 |
| 7 · Flujos | Recorridos completos con puntos de inicio |

**Nombre de cada frame:** `V-XX · Nombre de la vista / Estado`. Ejemplos:
- `V-06 · Mis vehículos / Con datos`
- `V-06 · Mis vehículos / Vacío`
- `V-07 · Editar vehículo (modal)`
- `V-08 · Eliminar vehículo (diálogo) / Error en taller`

### 3.7 Flujos para la demostración (puntos de inicio)

| Flujo | Recorrido |
|---|---|
| **1 · Cliente nuevo** | Splash → Iniciar sesión → Crear cuenta → Mis vehículos (vacío) → Registrar vehículo → Registrar solicitud → Mis solicitudes |
| **2 · Recepción (Jefe)** | Panel → Solicitudes pendientes → Detalle → Registrar orden de ingreso → Fotos → Elegir técnico → Confirmación con Lottie |
| **3 · Reparación (Técnico)** | Mis órdenes asignadas → Detalle → Iniciar reparación (diálogo) → Registrar avance con fotos → Marcar como listo (diálogo) |
| **4 · Seguimiento (Cliente)** | Llega la notificación "Tu vehículo está listo" → Estado de la orden → Línea de tiempo → Hallazgos → Visor de foto |
| **5 · Editar y eliminar** | Mis vehículos → ⋮ → Editar (hoja modal) → Guardar → Snackbar → ⋮ → Eliminar (diálogo) → Lista actualizada |

---

## 4. Prototipo de cada historia de usuario

**Tipos:** 🖥️ Pantalla · 📄 Hoja modal · 💬 Diálogo modal · 🔲 Modal de pantalla completa · 🔔 Notificación · ✅ Pantalla de confirmación

### Módulo transversal — Acceso y usuarios

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-T01 Iniciar sesión | V-T01 | 🖥️ | Splash | Logo, campo correo, campo contraseña con ícono `eye`, botón "Ingresar" (deshabilitado si hay campos vacíos), enlaces "¿Olvidaste tu contraseña?" y "Crear cuenta", mensaje de error | Inicio según rol: Mis vehículos · Panel · Mis órdenes asignadas |
| HU-T02 Cerrar sesión | V-T02 | 💬 | Perfil → "Cerrar sesión" | Ícono `sign-out`, pregunta, botones "Cancelar" y "Cerrar sesión" | Iniciar sesión |
| HU-T03 Recuperar contraseña | V-T03 | 🖥️ | Iniciar sesión → enlace | Campo correo, botón "Enviar enlace", estado de éxito "Revisa tu correo" | Iniciar sesión |
| HU-T04 Registrar técnico | V-T04 | 🖥️ | Técnicos → botón `plus` | Campos nombres, apellidos, DNI, teléfono, correo, contraseña inicial; botón "Registrar"; errores por campo | Técnicos + Snackbar "Técnico registrado" |
| HU-T05 Listar técnicos | V-T05 | 🖥️ | Pestaña Técnicos | Tarjetas con nombre, teléfono, chip Activo/Inactivo y órdenes en curso; menú `dots-three-vertical`; estado vacío; botón `plus` | — |
| HU-T06 Editar técnico | V-T06 | 📄 | Tarjeta de técnico → ⋮ → "Editar" | Campos llenados; DNI y correo bloqueados; botón "Guardar cambios" | Cierra el modal + Snackbar |
| HU-T07 Desactivar técnico | V-T07 | 💬 | Tarjeta de técnico → ⋮ → "Desactivar" | Ícono `warning-circle`, pregunta, botón "Desactivar"; **variante de error** "Reasigna sus órdenes en curso"; **variante** "Reactivar" | La tarjeta cambia a chip Inactivo |

### Proceso 1 — Recepción del vehículo

**Actividad 1.1 — Proporcionar datos personales y del vehículo**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-01 Registrar cuenta de cliente | V-01 | 🖥️ | Iniciar sesión → "Crear cuenta" | Campos nombres, apellidos, DNI, teléfono, correo, contraseña y confirmación; casilla de términos; botón "Crear cuenta"; errores | Mis vehículos (vacío) |
| HU-02 Ver mi perfil | V-02 | 🖥️ | Pestaña Perfil | Avatar con iniciales, datos personales, botones "Editar perfil", "Eliminar cuenta" y "Cerrar sesión", selector de modo claro/oscuro | — |
| HU-03 Editar mi perfil | V-03 | 📄 | Perfil → "Editar perfil" | Nombres, apellidos y teléfono llenados; DNI y correo bloqueados; botón "Guardar cambios" | Cierra + Snackbar "Perfil actualizado" |
| HU-04 Eliminar mi cuenta | V-04 | 💬 | Perfil → "Eliminar cuenta" | Ícono `trash`, advertencia, campo contraseña, botón "Eliminar cuenta"; **variante de error** "Tienes un vehículo en el taller" | Iniciar sesión |
| HU-05 Registrar vehículo | V-05 | 🖥️ | Mis vehículos → botón `plus` | Campos placa (mayúsculas automáticas), marca, modelo, año, color; botón "Registrar"; error "Placa ya registrada" | Mis vehículos + Snackbar |
| HU-06 Listar mis vehículos | V-06 | 🖥️ | Pestaña Vehículos (inicio) | Saludo, tarjetas con placa, marca, modelo y chip de estado; menú ⋮; botón `plus`; barra inferior de vidrio; **estado vacío** con Lottie | Tocar tarjeta con orden → Estado (V-46) |
| HU-07 Editar vehículo | V-07 | 📄 | Tarjeta de vehículo → ⋮ → "Editar" | Marca, modelo, año y color llenados; placa bloqueada; botón "Guardar cambios" | Cierra + Snackbar "Vehículo actualizado" |
| HU-08 Eliminar vehículo | V-08 | 💬 | Tarjeta de vehículo → ⋮ → "Eliminar" | Ícono `trash`, "¿Eliminar este vehículo?", botón "Eliminar"; **variante de error** "No puedes eliminar un vehículo que está en el taller" | Lista sin el vehículo + Snackbar |

**Actividad 1.2 — Describir falla del vehículo**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-09 Registrar solicitud de atención | V-09 | 🖥️ | Solicitudes → `plus`, o tarjeta de vehículo → "Solicitar atención" | Selector de vehículo, chips de síntomas (no arranca, batería, luces, tablero, otro), campo de descripción, fecha de llegada (abre selector), zona de fotos, botón "Enviar solicitud" | Mis solicitudes + Snackbar "Solicitud enviada" |
| HU-10 Adjuntar fotos a la solicitud | V-10 | 📄 | Solicitud → "+ Agregar foto" | Opciones "Tomar foto" (`camera`) y "Elegir de la galería" (`image`); miniaturas con `x`; aviso "Máximo 3 fotos" | La foto aparece en la solicitud |
| HU-11 Listar mis solicitudes | V-11 | 🖥️ | Pestaña Solicitudes | Tarjetas con placa, fecha y chip (Pendiente, Atendida, Cancelada); menú ⋮ solo en las pendientes; estado vacío | Atendida → abre la orden |
| HU-12 Editar solicitud pendiente | V-12 | 📄 | Solicitud pendiente → ⋮ → "Editar" | Síntomas, descripción y fecha llenados; vehículo bloqueado; botón "Guardar cambios" | Cierra + Snackbar |
| HU-13 Cancelar solicitud pendiente | V-13 | 💬 | Solicitud pendiente → ⋮ → "Cancelar solicitud" | Ícono `x-circle`, "¿Cancelar esta solicitud?", botón "Cancelar solicitud" | La tarjeta cambia a chip Cancelada |

**Actividad 1.3 — Buscar historial del cliente**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-14 Listar solicitudes pendientes | V-14 | 🖥️ + 📄 | Pestaña Solicitudes (Jefe) | Lista ordenada por fecha de llegada; al tocar una, **hoja modal de detalle** con descripción, fotos y botón "Registrar ingreso"; estado vacío | "Registrar ingreso" → V-23 llenado |
| HU-15 Buscar cliente | V-15 | 🖥️ | Pestaña Clientes | Barra de búsqueda (DNI, nombre o placa), resultados en tiempo real, estado "No se encontró el cliente" con botón "Registrar cliente" | Resultado → V-16 |
| HU-16 Ver detalle del cliente | V-16 | 🖥️ | Resultado de búsqueda | Datos del cliente, insignia "Tiene la app", lista de vehículos con historial de órdenes desplegable; botones "Editar cliente", "Agregar vehículo" y "Registrar ingreso" | — |

**Actividad 1.4 — Anotar datos del cliente y vehículo**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-17 Registrar cliente | V-17 | 🖥️ | Búsqueda sin resultados → "Registrar cliente" | Campos nombres, apellidos, DNI, teléfono y correo opcional; error "Ya existe un cliente con ese DNI" | Detalle del cliente (V-16) |
| HU-18 Editar cliente | V-18 | 📄 | Detalle del cliente → "Editar cliente" | Nombres, apellidos y teléfono llenados; DNI bloqueado; "Guardar cambios" | Cierra + Snackbar |
| HU-19 Eliminar cliente | V-19 | 💬 | Detalle del cliente → ⋮ → "Eliminar" | Ícono `trash`, botón "Eliminar"; **2 variantes de error**: "tiene historial de órdenes" y "tiene cuenta en la app" | Búsqueda + Snackbar |
| HU-20 Registrar vehículo de un cliente | V-20 | 🖥️ | Detalle del cliente → "Agregar vehículo" | Placa, marca, modelo, año, color; error "Placa registrada a nombre de…" | Detalle del cliente |
| HU-21 Editar vehículo de un cliente | V-21 | 📄 | Vehículo en detalle → ⋮ → "Editar" | Campos llenados, placa bloqueada | Cierra + Snackbar |
| HU-22 Eliminar vehículo de un cliente | V-22 | 💬 | Vehículo en detalle → ⋮ → "Eliminar" | Ícono `trash`; **variante de error** "tiene historial de órdenes" | Detalle actualizado |

**Actividad 1.5 — Llenar ficha de ingreso**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-23 Registrar orden de ingreso | V-23 | 🖥️ | Detalle de solicitud o de cliente → "Registrar ingreso" | Formulario por secciones en tarjetas: **Cliente** · **Vehículo** · **Falla** · **Estado** (kilometraje, combustible con chips Reserva, 1/4, 1/2, 3/4, Lleno, observaciones) · **Fotos** · **Técnico** (abre una hoja modal con los técnicos y sus órdenes en curso); botón "Registrar ingreso" | Confirmación (V-29) |
| HU-24 Adjuntar fotos del estado exterior | V-24 | 📄 | Orden de ingreso → celda "+ Agregar" | Selector cámara o galería; cuadrícula de 3 columnas; contador "3 / 6"; error "Agrega al menos una foto" | Las fotos aparecen en la cuadrícula |
| HU-25 Eliminar foto del estado exterior | V-25 | 💬 | Foto → visor → `trash` | "¿Eliminar esta foto?"; **variante de error** "La orden debe tener al menos una foto" | La cuadrícula sin la foto |
| HU-26 Ver detalle de la orden | V-26 | 🖥️ | Panel → tarjeta de orden | Encabezado con número y chip de estado; secciones de datos; galería de fotos; historial de estados; botones "Editar" y "Anular" (solo en Ingresado) | — |
| HU-27 Editar orden de ingreso | V-27 | 🔲 | Detalle de orden → "Editar" | Modal de pantalla completa con `x` y "Guardar"; falla, kilometraje, combustible, observaciones y técnico llenados | Cierra + Snackbar "Orden actualizada" |
| HU-28 Anular orden de ingreso | V-28 | 💬 | Detalle de orden → "Anular" | Ícono `warning-circle`, campo "Motivo" obligatorio, botón "Anular orden" deshabilitado hasta escribir el motivo | Detalle con chip Anulada |

**Actividad 1.6 — Indicar al cliente que se le llamará con novedades**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-29 Notificar ingreso al cliente | V-29 | ✅ + 🔔 | Al pulsar "Registrar ingreso" | Lottie del check, número de orden grande, texto "Cliente notificado"; **variante** "El cliente no tiene la app: entrégale el número ORD-XXXX"; botones "Ver orden" e "Ir al panel"; en el flujo del cliente, la notificación "Tu vehículo fue ingresado" | Panel o detalle de la orden |

**Actividad 1.7 — Recibir indicaciones del Jefe de Taller**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-30 Ver comprobante de ingreso | V-30 | 🖥️ | Notificación de ingreso o Estado de la orden → "Ver comprobante" | Estilo de ticket con borde punteado: número de orden, fecha y hora, vehículo, falla, kilometraje, combustible, observaciones, fotos y técnico | — |
| HU-31 Listar mis órdenes | V-31 | 🖥️ | Pestaña Órdenes | Secciones "En curso" y "Anteriores"; tarjetas con número, placa, fecha y chip; estado vacío | Tarjeta → V-46 |

### Proceso 2 — Reparación del vehículo

**Actividad 2.1 — Revisar ficha de ingreso del vehículo**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-32 Listar mis órdenes asignadas | V-32 | 🖥️ | Inicio del técnico | Tarjetas con barra lateral del color del estado, número, placa, falla resumida y fecha; primero "En reparación", luego "Ingresado"; estado vacío | Tarjeta → V-33 |
| HU-33 Ver detalle de orden asignada | V-33 | 🖥️ | Tarjeta de orden asignada | Falla, fotos de la solicitud y del ingreso, datos del vehículo, órdenes anteriores del vehículo; botón principal que cambia según el estado | — |
| HU-34 Iniciar reparación | V-34 | 💬 | Detalle → "Iniciar reparación" | Ícono `wrench`, "¿Iniciar la reparación?", texto "El cliente será notificado", botón "Iniciar" | Detalle con chip En reparación y botón "Registrar avance" |

**Actividad 2.2 — Registrar hallazgos y trabajos realizados**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-35 Registrar avance | V-35 | 🖥️ | Detalle → "Registrar avance" | Selector de tipo con dos opciones (Hallazgo `magnifying-glass` / Trabajo realizado `wrench`), descripción, zona de fotos, botón "Registrar"; error "Primero inicia la reparación" | Lista de avances + Snackbar |
| HU-36 Adjuntar fotos al avance | V-36 | 📄 | Avance → "+ Agregar foto" | Selector cámara o galería, miniaturas con `x`, aviso "Máximo 4 fotos" | Fotos en el avance |
| HU-37 Listar avances de la orden | V-37 | 🖥️ | Detalle → pestaña "Avances" | Línea de avances con ícono por tipo, descripción, miniaturas, fecha; menú ⋮ solo en los propios; estado vacío | — |
| HU-38 Editar avance | V-38 | 📄 | Avance → ⋮ → "Editar" | Tipo, descripción y fotos llenados; botón "Guardar cambios" | Cierra + Snackbar |
| HU-39 Eliminar avance | V-39 | 💬 | Avance → ⋮ → "Eliminar" | Ícono `trash`, "¿Eliminar este avance? El cliente dejará de verlo", botón "Eliminar" | Lista sin el avance |

**Actividad 2.3 — Informar al Jefe que el vehículo está listo**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-40 Marcar orden como lista | V-40 | 💬 | Detalle → "Marcar como listo" | Ícono `check-circle`, "¿El vehículo está listo?", botón "Confirmar"; **variante de error** "Registra el trabajo realizado antes de marcar como listo" | Mis órdenes asignadas sin esa orden |
| HU-41 Ver panel de órdenes | V-41 | 🖥️ | Inicio del Jefe | Pestañas segmentadas (Ingresado · En reparación · Listo) con contador; tarjetas con número, placa, cliente, técnico y tiempo en el estado; barra superior de vidrio | Tarjeta → V-26 |

**Actividad 2.4 — Llamar al cliente para avisar que está listo**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-42 Notificar que el vehículo está listo | V-42 | 🔔 | Automático tras HU-40 | Notificación con el logo, título "Tu vehículo está listo", placa y hora | Tocar → V-46 |
| HU-43 Reenviar aviso de vehículo listo | V-43 | 🖥️ (variante de V-26) | Detalle de orden lista → "Reenviar aviso" | Botón "Reenviar aviso" con ícono `bell`; texto "Último aviso: hoy 10:30 · 1 enviado"; **variante deshabilitada** "Disponible en 18 h" | Snackbar "Aviso reenviado" |

**Actividad 2.5 — Recibir aviso de vehículo listo**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-44 Abrir la orden desde la notificación | V-44 | 🔔 → 🖥️ | Notificación push | Interacción: tocar la notificación lleva al Estado de la orden (V-46); variante que pasa antes por Iniciar sesión | V-46 |
| HU-45 Ver bandeja de notificaciones | V-45 | 🖥️ | Pestaña Avisos (`bell` con contador) | Lista con título, mensaje y fecha; las no leídas con punto rojo y fondo `primaryContainer`; estado vacío | Tocar → orden relacionada |

**Actividad 2.6 — Llamar al taller para consultar el avance**

| HU | Vista | Tipo | Se abre desde | Elementos a prototipar | Al confirmar |
|---|---|---|---|---|---|
| HU-46 Ver estado actual de mi vehículo | V-46 | 🖥️ | Mis órdenes, Mis vehículos o la notificación | Foto del vehículo de fondo con **tarjeta de vidrio** encima: estado grande, ícono, hora del último cambio y técnico; botones "Ver línea de tiempo", "Ver hallazgos" y "Ver comprobante"; **variante "Listo"** con Lottie de check con destellos | — |
| HU-47 Ver línea de tiempo de la orden | V-47 | 🖥️ | Estado → "Ver línea de tiempo" | Línea vertical: puntos verdes (cumplido, con fecha), rojo con pulso (actual), gris (pendiente); **variante "Anulada"** con el motivo | — |
| HU-48 Ver hallazgos y trabajos | V-48 | 🖥️ + visor | Estado → "Ver hallazgos" | Tarjetas con ícono por tipo, descripción, galería horizontal y fecha; sin opciones de edición; al tocar una foto se abre el **visor** a pantalla completa; estado vacío "El técnico aún no registra avances" | — |

---

## 5. Navegación por rol

| Rol | Pestañas de la barra inferior (vidrio) | Pantalla de inicio |
|---|---|---|
| **Cliente** | Vehículos (`car-profile`) · Solicitudes (`clipboard-text`) · Órdenes (`path`) · Avisos (`bell`) · Perfil (`user-circle`) | V-06 Mis vehículos |
| **Jefe de Taller** | Panel (`squares-four`) · Solicitudes (`clipboard-text`) · Clientes (`address-book`) · Técnicos (`users-three`) · Perfil (`user-circle`) | V-41 Panel de órdenes |
| **Técnico** | Mis órdenes (`wrench`) · Perfil (`user-circle`) | V-32 Mis órdenes asignadas |

---

## 6. Lista de verificación del prototipo

- [ ] Las 55 HU tienen su frame o modal, con el código V-XX en el nombre.
- [ ] Cada **editar** se abre como hoja modal (HU-27 como modal de pantalla completa).
- [ ] Cada **eliminar, cancelar, anular, desactivar** y cada **cambio de estado** se abre como diálogo modal.
- [ ] Cada diálogo que puede fallar tiene su **variante de error**.
- [ ] Cada lista tiene su **estado vacío**.
- [ ] Cada guardado muestra un **Snackbar** de confirmación.
- [ ] Los campos que no se pueden editar aparecen **bloqueados** con candado.
- [ ] Están los 5 flujos de demostración con su punto de inicio.
- [ ] Todo el prototipo funciona en **modo claro y oscuro** (variables de Figma).
- [ ] El vidrio solo se usa en barras, en la tarjeta de estado y en el botón flotante.
