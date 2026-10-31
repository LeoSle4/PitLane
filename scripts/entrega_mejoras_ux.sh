#!/usr/bin/env bash
# Registra en la rama feat/conectar-firebase las mejoras de UX (clientes recientes, tarjetas, botón con carga) y la
# salida de los datos locales de la app, en commits Conventional Commits sin atribuciones. Cada commit compila.
# Después sube la rama y abre el pull request hacia main si todavía no existe (si existe, el push lo actualiza).
#
# Uso:
#   bash scripts/entrega_mejoras_ux.sh                         # prueba, crea los commits, sube y abre el PR
#   SIMULAR=1 bash scripts/entrega_mejoras_ux.sh               # solo muestra qué agregaría cada commit
#   SALTAR_PRUEBAS=1 bash scripts/entrega_mejoras_ux.sh        # no corre Gradle antes de empezar
#   VERIFICAR_CADA_COMMIT=1 bash scripts/entrega_mejoras_ux.sh # compila y prueba tras cada commit
#   SIN_PUBLICAR=1 bash scripts/entrega_mejoras_ux.sh          # crea los commits pero no hace push ni PR
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

RAMA="$(git branch --show-current)"
if [ "$RAMA" = "main" ] || [ "$RAMA" = "master" ]; then
  echo "Estás en '$RAMA'. Cambia a feat/conectar-firebase antes de ejecutar este script." >&2
  exit 1
fi
git config user.name >/dev/null || { echo "Configura git user.name antes de continuar." >&2; exit 1; }
git config user.email >/dev/null || { echo "Configura git user.email antes de continuar." >&2; exit 1; }

BASE=app/src/main/java/com/leosle/pitlane
PRUEBAS=app/src/test/java/com/leosle/pitlane
VISTA=$BASE/arquitectura/vista
RES=app/src/main/res

# Rutas explícitas de cada commit. Cada commit incluye solo sus rutas, aunque haya otros archivos preparados.
RECIENTES=(
  $BASE/util/Textos.kt $PRUEBAS/util/TextosTest.kt
  $BASE/modelo/Proyecciones.kt
  $BASE/arquitectura/repositorio/IClienteRepository.kt $BASE/arquitectura/repositorio/impl/ClienteRepository.kt
  $BASE/arquitectura/viewmodel/ClienteViewModel.kt
  $PRUEBAS/arquitectura/repositorio/ClientesRecientesTest.kt $PRUEBAS/arquitectura/repositorio/ReglasDeNegocioTest.kt
)
TARJETAS=(
  $VISTA/clientes/FragBuscarClientes.kt $VISTA/tecnicos/FragListaTecnicos.kt $VISTA/comun/EstadosVisuales.kt
  $RES/layout/jc_item_resultado_cliente.xml $RES/layout/jc_item_tecnico.xml
  $RES/layout/jc_frag_buscar_clientes.xml $RES/layout/jc_frag_lista_tecnicos.xml
  $RES/values/cadenas_jc.xml
  $RES/drawable/ic_phone_pequeno.xml $RES/drawable/ic_identification_card_pequeno.xml
)
CARGA=(
  $VISTA/comun/BotonCarga.kt $RES/values/ids.xml
  $VISTA/acceso/FragRegistro.kt
  $VISTA/avances/FragRegistrarAvance.kt $VISTA/avances/HojaEditarAvance.kt
  $VISTA/clientes/FragRegistrarCliente.kt $VISTA/clientes/HojaEditarCliente.kt
  $VISTA/ordenes/FragNuevaOrden.kt $VISTA/ordenes/HojaObservacionCliente.kt $VISTA/ordenes/PantallaEdicionOrden.kt
  $VISTA/solicitudes/FragNuevaSolicitud.kt $VISTA/solicitudes/HojaEditarSolicitud.kt
  $VISTA/tecnicos/FragRegistrarTecnico.kt $VISTA/tecnicos/HojaEditarTecnico.kt
  $VISTA/vehiculos/FragRegistrarVehiculo.kt $VISTA/vehiculos/FragRegistrarVehiculoJefe.kt
  $VISTA/vehiculos/HojaEditarVehiculo.kt $VISTA/vehiculos/HojaEditarVehiculoJefe.kt
)
# Incluye rutas borradas (las clases locales salen de main y las fotos de ejemplo desaparecen).
LOCALES=(
  $BASE/arquitectura/datasource/local $PRUEBAS/arquitectura/datasource/local
  "$RES/drawable-nodpi/foto_demo_*"
  $VISTA/acceso/FragLogin.kt $RES/layout/acc_frag_login.xml $RES/values/cadenas_acc.xml
)
DOCUMENTACION=(README.md odd/tasks/mejoras-ux-clientes-tecnicos.md)

for ruta in "${RECIENTES[@]}" "${TARJETAS[@]}" "${CARGA[@]}" "${DOCUMENTACION[@]}" \
  $PRUEBAS/arquitectura/datasource/local $VISTA/acceso/FragLogin.kt; do
  [ -e "$ruta" ] || { echo "Falta '$ruta'. Revisa que estés en el repositorio correcto." >&2; exit 1; }
done

if [ -n "${SIMULAR:-}" ]; then
  echo "== Simulación en la rama '$RAMA' (no se crea ningún commit) =="
  simular() {
    local nombre="$1"; shift
    echo; echo "-- $nombre --"
    git add -n -A -- "$@" | sed 's/^/   /'
  }
  simular "clientes recientes" "${RECIENTES[@]}"
  simular "tarjetas" "${TARJETAS[@]}"
  simular "botón con carga" "${CARGA[@]}"
  simular "datos locales fuera de la app" "${LOCALES[@]}"
  simular "documentación" "${DOCUMENTACION[@]}"
  exit 0
fi

comprobar() {
  ./gradlew :app:testDebugUnitTest :app:assembleDebug -q
}

if [ -z "${SALTAR_PRUEBAS:-}" ]; then
  echo "== Comprobando que todo compila y las pruebas pasan =="
  comprobar
fi

# Hace un commit solo con las rutas indicadas; omite el commit si no hay cambios en ellas.
confirmar() {
  local asunto="$1" cuerpo="$2"; shift 2
  git add -A -- "$@"
  if git diff --cached --quiet -- "$@"; then
    echo "Sin cambios para: $asunto"
    return
  fi
  git commit -q -m "$asunto" -m "$cuerpo" -- "$@"
  echo "OK  $asunto"
  if [ -n "${VERIFICAR_CADA_COMMIT:-}" ]; then
    echo "    comprobando este commit..."
    comprobar
  fi
}

confirmar "feat(clientes): mostrar los clientes atendidos recientemente" \
"- sin búsqueda, la pantalla de clientes muestra los 5 con actividad más reciente (órdenes y solicitudes)
- los resultados traen todas las placas del cliente además de las que coincidieron
- la búsqueda ya no cierra la app si Firestore falla
- Textos.celular agrupa el celular de tres en tres y Textos.gerundio arma el texto de carga de los botones
Con sus pruebas." \
  "${RECIENTES[@]}"

confirmar "feat(ui): rediseñar las tarjetas de clientes y técnicos" \
"- celular y DNI como datos separados, cada uno con su ícono
- placas como chips (resaltadas las que coinciden) y si el cliente tiene cuenta
- estado y carga de trabajo del técnico en chips, resaltada si tiene órdenes en curso
- las listas de clientes y técnicos toman el margen de pantalla, como el resto de listas" \
  "${TARJETAS[@]}"

confirmar "feat(ui): mostrar el estado de carga en los botones de los formularios" \
"Mientras una operación espera al servidor, el botón muestra el texto en gerundio
(\"Registrando…\") y una rueda girando, y vuelve a su estado si hay error.
Se aplica a 17 formularios." \
  "${CARGA[@]}"

confirmar "refactor: sacar de la app los datos locales" \
"La app usa solo Firebase. Las fuentes de datos locales pasan a las pruebas, donde siguen
sirviendo de base de datos en memoria para las reglas de negocio. Se eliminan las fotos de
ejemplo y el selector de cuentas de demostración del inicio de sesión." \
  "${LOCALES[@]}"

confirmar "docs: describir la app sobre Firebase" \
"README actualizado (estado, estructura, pruebas y cómo instalar) y bitácora de las mejoras de UX." \
  "${DOCUMENTACION[@]}"

echo
git log --oneline -7
echo
echo "Quedó sin commitear (si lo hay):"
git status --short | sed 's/^/   /' || true

if [ -n "${SIN_PUBLICAR:-}" ]; then
  echo
  echo "SIN_PUBLICAR: no se hizo push ni PR."
  exit 0
fi

echo
echo "== Subiendo la rama '$RAMA' =="
git push -u origin "$RAMA"

if ! command -v gh >/dev/null; then
  echo "No está instalado gh. Abre el PR en: https://github.com/LeoSle4/PitLane/pull/new/$RAMA"
  exit 0
fi

if gh pr view "$RAMA" --json url -q .url >/dev/null 2>&1; then
  echo "El PR ya existía y quedó actualizado: $(gh pr view "$RAMA" --json url -q .url)"
  exit 0
fi

CUERPO="$(mktemp)"
trap 'rm -f "$CUERPO"' EXIT
cat > "$CUERPO" <<'EOF'
## Resumen

Conecta la app a Firebase y mejora la experiencia de las pantallas del Jefe de Taller.

### Firebase
- Authentication, Firestore, Storage y Cloud Functions (región `southamerica-west1`) como única fuente de datos.
- Lecturas filtradas por rol: el Jefe ve todo, el cliente solo lo suyo y el técnico solo sus órdenes asignadas.
- Reglas de seguridad de Firestore y Storage con 51 pruebas en el emulador.
- Funciones del servidor: registrar clientes, crear técnicos, desactivar y reactivar cuentas, verificar placas y avisar al cliente cuando cambia su orden (25 pruebas en el emulador).
- Script para crear al primer Jefe de Taller.

### Experiencia de uso
- Clientes atendidos recientemente al abrir la pestaña de clientes.
- Tarjetas de clientes y técnicos rediseñadas y alineadas con el margen de pantalla.
- Botones con estado de carga ("Registrando…" y una rueda) en 17 formularios.
- Se quitaron de la app los datos locales y las cuentas de demostración.

## Cómo probar
- `./gradlew :app:testDebugUnitTest` (108 pruebas).
- `cd firebase && npm install && npm test` (reglas) y `cd firebase/functions && npm install && npm test` (funciones).
- Instalar con **Run** en Android Studio e iniciar sesión con una cuenta real.

## Pendiente
- Notificaciones push reales con FCM.
- Limpiar la caché local al cerrar sesión.
EOF
gh pr create --base main --head "$RAMA" --title "feat: conectar la app a Firebase y mejorar la experiencia del Jefe" --body-file "$CUERPO"
