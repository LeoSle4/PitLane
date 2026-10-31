Objetivo: mejorar las tarjetas de clientes y técnicos, mostrar clientes recientes y dar señal de carga en los formularios.
Rama: la del usuario (el agente no hace commits; la entrega es por script). Pruebas: `./gradlew testDebugUnitTest` con `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`.

## Specs
- S1: "que se mejore esa card, de busqueda". Tarjeta de resultado de clientes: nombre, DNI y celular con espacios, todas las placas como chips (resaltadas las que coinciden con la búsqueda) y si tiene cuenta en la app.
- S2: "seria bueno que aca ya mostrara algunos clientes". Decidido con el usuario: sin búsqueda, la pantalla muestra los 5 clientes con actividad más reciente (órdenes y solicitudes); si no hay ninguno, el estado vacío de siempre.
- S3: "aca tambien mejorar la card, la estructura de informacion". Tarjeta de técnico: celular con espacios y DNI en una línea; estado y carga de trabajo en otra; la carga resaltada si tiene órdenes en curso.
- S4: "agregar un ux, donde diga registrando y salga como una rueda cargando". Botón con estado de carga (texto en gerundio y rueda) en los 18 formularios que hoy solo deshabilitan el botón. No se usan instancias mínimas en Functions (costo fijo mensual).
- S5 (hallazgo): `ClienteViewModel.buscar` no atrapa fallas técnicas; una falla de Firestore cerraría la app.

- S6: "puedes ya eliminar los datos que estaban el local, ya que hora usaremos todo en fyrebase". Las clases de `datasource/local` salen de la app y pasan a las pruebas (son la base de datos falsa de unas 85 pruebas); se borran las fotos de ejemplo y el combo de cuentas de demostración del login; el README describe la app sobre Firebase.

## Tasks
- [x] T1 (S1, S2, S3, S5) Tarjetas, recientes y búsqueda protegida; inline. RED observado (no compilaba sin la API), GREEN con 106 pruebas (101 + 5). Sin verificación visual en dispositivo. Sin commit.
- [x] T2 (S4) `BotonCarga` (texto en gerundio con `Textos.gerundio` y rueda de Material) aplicado en 17 formularios; `DialogoModal` queda fuera porque allí el botón se deshabilita por validación. RED observado, GREEN con 108 pruebas (106 + 2). Sin verificación visual en dispositivo. Sin commit.
- [x] T3 (S6) Datos locales fuera de la app; inline. 108 pruebas en verde; APK sin `foto_demo_*`; verificado en el emulador (tarjeta de técnico con ícono a 16dp). El login sin el combo no se verificó en pantalla para no cerrar la sesión del Jefe. Sin commit.

## Log
- L1 (2026-10-31) Usuario: "que se mejore esa card, de busqueda, seria bueno que aca ya mostrara algunos clientes, los mas concurridos tal vez? o esta bien que se vea vacio? aca tambien mejorar la card, la estructura de informacion, y mejora el ux, por que hay muchas veces como el crear un tecnico, y tambien una cuenta cliente, donde se queda detenida la app, se puede optimizar para que el registro sea mas rapido? o agregar un ux, donde diga registrando y salga como una rueda cargando".
- L2 Usuario: "Sí, avanza con las cuatro" (propuesta con clientes recientes en lugar de más concurridos).
- L3 T1 y T2 hechos. El conteo real de formularios con carga es 17, no 18: el diálogo de confirmación deshabilita su botón por validación, no por carga.
- L4 Usuario: "puedes ya eliminar los datos que estaban el local, ya que hora usaremos todo en fyrebase, ahora acabo de consturir y compilar y emular la aplicacion y no vi los cambios". Causa: el emulador tenía instalada la versión de las 09:59; compilar no instala. Se instaló el APK nuevo y se verificaron en pantalla la tarjeta de técnico, la búsqueda de clientes y la restauración de sesión.
- L5 Usuario: "que sea simetrico las list view son muy anchas, y debe verse mejor, no contenido acumulado". Causa: dentro de un ListView se ignoran los márgenes de la tarjeta; las listas de Clientes y Técnicos no tenían el margen de pantalla que sí tienen las otras 8. Se agregó, se dio más aire a las tarjetas y la carga del técnico pasó a "Sin órdenes / 1 orden / N órdenes" para que no se parta en dos líneas. Verificado en el emulador.
- L6 Usuario: "el telefnono y dni se ven muy juntos en ambos lv". Celular y DNI pasan a ser dos elementos con su ícono (teléfono y credencial) y 20dp entre ellos, en ambas tarjetas. Compila y pasan las 108 pruebas; instalado en el emulador. El login sin el combo de demostración quedó verificado en pantalla.
