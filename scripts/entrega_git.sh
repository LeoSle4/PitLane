#!/usr/bin/env bash
# Crea la rama de desarrollo, registra el trabajo en commits por unidad (Conventional Commits, sin atribuciones)
# y abre el pull request hacia main.
# Ejecutar desde cualquier carpeta del repositorio:  bash scripts/entrega_git.sh
set -euo pipefail

RAMA="feat/app-ui-funcionalidad-local"
BASE="main"
TITULO_PR="feat: app completa con diseño japonés y funcionalidad local (MVVM + Repository)"
CARPETA_TEMPORAL="$(mktemp -d)"
trap 'rm -rf "$CARPETA_TEMPORAL"' EXIT

cd "$(git rev-parse --show-toplevel)"

# Comprobaciones previas: rama base actual, rama nueva inexistente y herramientas disponibles.
if [ "$(git branch --show-current)" != "$BASE" ]; then
  echo "Debes estar en la rama '$BASE' para crear la rama de desarrollo." >&2
  exit 1
fi
if git show-ref --verify --quiet "refs/heads/$RAMA"; then
  echo "La rama '$RAMA' ya existe. Elimínala o cambia el nombre en este script." >&2
  exit 1
fi
command -v gh >/dev/null || { echo "Instala GitHub CLI (gh) para abrir el pull request." >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Inicia sesión en GitHub CLI con: gh auth login" >&2; exit 1; }

# El índice de CodeGraph es local de cada copia del repositorio; no se versiona.
if ! grep -qxF '.codegraph/' .gitignore; then
  printf '.codegraph/\n' >> .gitignore
fi

git switch -c "$RAMA"

# Agrega las rutas indicadas (incluidas las eliminaciones) y crea un commit con el mensaje dado.
confirmar() {
  local mensaje="$1"
  shift
  git add -A -- "$@"
  if git diff --cached --quiet; then
    echo "Sin cambios para: $mensaje" >&2
    return 0
  fi
  git commit -m "$mensaje"
}

RES="app/src/main/res"
SRC="app/src/main/java/com/leosle/pitlane"

confirmar "chore(build): add lifecycle, fragment, coroutines and Lottie dependencies and enable view binding" \
  gradle/libs.versions.toml app/build.gradle.kts .gitignore

confirmar "feat(theme): add Japanese palette, typography, icons, brand logos and animations" \
  "$RES/values/colors.xml" "$RES/values-night" "$RES/values/dimens.xml" "$RES/values/styles.xml" \
  "$RES/values/themes.xml" "$RES/color" "$RES/font" "$RES/animator/trazo_*" "$RES/animator/destello_*" \
  "$RES/animator/pulso_*" "$RES/drawable/ic_*" "$RES/drawable/fondo_*" "$RES/drawable/avd_*" \
  "$RES/drawable/vector_*" "$RES/drawable/ilustracion_vacio.xml" "$RES/drawable/divisor_*" \
  "$RES/drawable-nodpi"

confirmar "feat(domain): add models, validation rules and repository contracts" \
  "$SRC/modelo" "$SRC/util" "$SRC/arquitectura/repositorio/I*.kt"

confirmar "feat(data): add data sources, repositories with role-based access and in-memory sample data" \
  "$SRC/arquitectura/datasource" "$SRC/arquitectura/repositorio" "$SRC/Dependencias.kt"

confirmar "feat(viewmodel): add view models for every module" \
  "$SRC/arquitectura/viewmodel"

confirmar "feat(ui): add shared components, action menu, screen transitions and navigation shell" \
  "$SRC/arquitectura/vista/comun" ":(exclude)$SRC/arquitectura/vista/comun/AyudasVista.kt" "$SRC/arquitectura/vista/PrincipalActivity.kt" \
  "$RES/layout/include_*" "$RES/layout/dlg_*" "$RES/layout/hoja_*" "$RES/layout/item_*" \
  "$RES/layout/activity_principal.xml" "$RES/values/cadenas_comunes.xml" "$RES/values/strings.xml" \
  "$RES/anim/pantalla_*" "$RES/anim/pestana_*" "$RES/anim/menu_*" \
  "$SRC/MainActivity.kt" "$RES/layout/activity_main.xml"

confirmar "feat(access): add splash, animated travel scene, login, sign-up, recovery, profile and push notifications" \
  "$SRC/AplicacionPitLane.kt" "$SRC/arquitectura/vista/acceso" "$SRC/arquitectura/vista/perfil" "$SRC/arquitectura/vista/notificaciones/MensajesPushService.kt" \
  app/src/main/AndroidManifest.xml "$RES/xml/rutas_fotos.xml" "$RES/raw" \
  "$RES/layout/acc_*" "$RES/layout/per_*" "$RES/layout/activity_acceso.xml" "$RES/layout/view_escena_viaje.xml" \
  "$RES/values/cadenas_acc.xml" "$RES/values/cadenas_per.xml" "$RES/drawable/acc_*" "$RES/animator/acc_*"

confirmar "feat(client): add vehicles, requests, orders, notifications and order tracking screens" \
  "$SRC/arquitectura/vista/vehiculos/FragMisVehiculos.kt" \
  "$SRC/arquitectura/vista/vehiculos/FragRegistrarVehiculo.kt" \
  "$SRC/arquitectura/vista/vehiculos/HojaEditarVehiculo.kt" \
  "$SRC/arquitectura/vista/solicitudes/FragMisSolicitudes.kt" \
  "$SRC/arquitectura/vista/solicitudes/FragNuevaSolicitud.kt" \
  "$SRC/arquitectura/vista/solicitudes/HojaEditarSolicitud.kt" \
  "$SRC/arquitectura/vista/solicitudes/ControladorFormularioSolicitud.kt" \
  "$SRC/arquitectura/vista/ordenes/FragComprobante.kt" \
  "$SRC/arquitectura/vista/ordenes/FragEstadoOrden.kt" \
  "$SRC/arquitectura/vista/ordenes/FragLineaTiempo.kt" \
  "$SRC/arquitectura/vista/ordenes/FragMisOrdenes.kt" \
  "$SRC/arquitectura/vista/avances/FragHallazgos.kt" \
  "$SRC/arquitectura/vista/notificaciones/FragBandeja.kt" \
  "$SRC/arquitectura/vista/comun/AyudasVista.kt" \
  "$RES/layout/cli_*" \
  "$RES/values/cadenas_cli.xml" \
  "$RES/drawable/cli_*"

confirmar "feat(manager): add technician, client and vehicle management screens" \
  "$SRC/arquitectura/vista/vehiculos/FragRegistrarVehiculoJefe.kt" \
  "$SRC/arquitectura/vista/vehiculos/HojaEditarVehiculoJefe.kt" \
  "$SRC/arquitectura/vista/clientes/FragBuscarClientes.kt" \
  "$SRC/arquitectura/vista/clientes/FragDetalleCliente.kt" \
  "$SRC/arquitectura/vista/clientes/FragRegistrarCliente.kt" \
  "$SRC/arquitectura/vista/clientes/HojaEditarCliente.kt" \
  "$SRC/arquitectura/vista/tecnicos/FragListaTecnicos.kt" \
  "$SRC/arquitectura/vista/tecnicos/FragRegistrarTecnico.kt" \
  "$SRC/arquitectura/vista/tecnicos/HojaEditarTecnico.kt" \
  "$RES/layout/jc_*" \
  "$RES/values/cadenas_jc.xml" \
  "$RES/drawable/jc_*"

confirmar "feat(manager): add pending requests, order intake with client search, order detail with delivery and dashboard screens" \
  "$SRC/arquitectura/vista/solicitudes/FragSolicitudesPendientes.kt" \
  "$SRC/arquitectura/vista/solicitudes/HojaSolicitudJefe.kt" \
  "$SRC/arquitectura/vista/ordenes/AyudasOrden.kt" \
  "$SRC/arquitectura/vista/ordenes/FragConfirmacionIngreso.kt" \
  "$SRC/arquitectura/vista/ordenes/FragDetalleOrdenJefe.kt" \
  "$SRC/arquitectura/vista/ordenes/FragNuevaOrden.kt" \
  "$SRC/arquitectura/vista/ordenes/FragPanelOrdenes.kt" \
  "$SRC/arquitectura/vista/ordenes/PantallaEdicionOrden.kt" \
  "$RES/layout/jo_*" \
  "$RES/values/cadenas_jo.xml" \
  "$RES/values/estilos_jo.xml" \
  "$RES/drawable/jo_*" \
  "$RES/anim/jo_*"

confirmar "feat(technician): add assigned orders, progress records and ready-state screens" \
  "$SRC/arquitectura/vista/ordenes/FragDetalleOrdenTecnico.kt" \
  "$SRC/arquitectura/vista/ordenes/FragMisOrdenesAsignadas.kt" \
  "$SRC/arquitectura/vista/avances/ControladorFormularioAvance.kt" \
  "$SRC/arquitectura/vista/avances/FragRegistrarAvance.kt" \
  "$SRC/arquitectura/vista/avances/HojaEditarAvance.kt" \
  "$RES/layout/tec_*" \
  "$RES/values/cadenas_tec.xml" \
  "$RES/drawable/tec_*"

confirmar "feat(navigation): connect role routes to their screens" \
  "$SRC/arquitectura/vista/navegacion"

confirmar "test: cover validation, business rules, role-based access and local data sources" \
  app/src/test

confirmar "docs(requirements): add HU-49 delivery, Entregado state and update traceability, totals and design guide" \
  docs/02-requisitos docs/03-diseno docs/README.md

confirmar "docs(bpmn): add vehicle pickup and delivery to the repair process" \
  docs/01-procesos

confirmar "docs(architecture): update component, deployment and dependency diagrams and add Firebase migration guide" \
  docs/04-arquitectura docs/05-desarrollo scripts/diagrama-componentes scripts/diagrama-despliegue

confirmar "docs(presentation): add interface elements guide for the exhibition" \
  docs/05-exposicion

confirmar "docs: add feature task logs" \
  odd

# Avisa si quedó algo sin registrar antes de publicar (este script no se versiona).
PENDIENTE="$(git status --porcelain -- . ':(exclude)scripts/entrega_git.sh')"
if [ -n "$PENDIENTE" ]; then
  echo "Hay archivos sin registrar (revisa antes de continuar):" >&2
  echo "$PENDIENTE" >&2
fi

cat > "$CARPETA_TEMPORAL/cuerpo_pr.md" <<'CUERPO'
## Resumen

App Android completa (Kotlin + XML, MVVM + Repository) con las 56 historias de usuario, diseño japonés y funcionalidad local en memoria. La integración con Firebase queda para el siguiente avance.

- **Arquitectura:** vistas por módulo (Fragment + ViewBinding) → ViewModels (`StateFlow`) → interfaces `I*Repository` → repositorios → fuentes de datos (`UsuariosDataSource`, `DocumentosDataSource`, `FotoStorage`), con los mismos nombres del diagrama de componentes. Solo las fuentes tienen versión local en memoria; el único punto de cambio hacia Firebase es `Dependencias.kt` (guía en `docs/05-desarrollo/cambio-a-firebase.md`).
- **Roles y reglas:** Cliente, Jefe de Taller y Técnico Electricista. Las lecturas y acciones se filtran con el usuario de la sesión (RN-06, RN-07 y RN-08), el equivalente local de `request.auth`.
- **Diseño:** paleta japonesa clara y oscura, Lora y Plus Jakarta Sans, íconos Heroicons, barra inferior de vidrio, escena animada con auto (Lottie) y sol o luna según la hora, formularios por pasos, menú de acciones propio y transiciones entre pantallas.
- **Entrega del vehículo (HU-49):** nuevo estado `Entregado` que el Jefe registra desde el detalle de la orden; agregado también al BPMN del proceso 2 (recoger y entregar el vehículo) y a la matriz de trazabilidad.
- **Cámara:** foto a tamaño completo con `FileProvider`.

## Notas de revisión

- Los commits de pantallas y de navegación se revisan juntos: `PrincipalActivity` y las rutas por rol solo compilan cuando todos están presentes. El build queda verde en la cabeza del PR.
- Las reglas del servidor ya están aisladas en las fuentes locales (avisos de órdenes en `FuncionesLocales`, cuentas en `UsuariosDataSource`, correlativo en `DocumentosDataSource`); al migrar pasan a Cloud Functions y a las reglas de Firestore sin tocar los repositorios.
- Los diagramas de componentes y despliegue muestran la arquitectura con Firebase como diseño; hoy la sesión y los datos viven en memoria y los avisos solo llegan mientras el proceso esté vivo.
- Los diagramas se regeneran con `scripts/diagrama-*/marcos.py` y el BPMN se exporta con bpmn-js.

## Pruebas

- [x] `./gradlew :app:testDebugUnitTest` (68 pruebas)
- [x] `./gradlew :app:assembleDebug`
- [x] Recorrido manual en emulador (API 37) de los roles Jefe y Cliente: panel, técnicos, clientes, vehículos, avisos, línea de tiempo, cámara y entrega
- [x] Diagramas de componentes y despliegue actualizados (`docs/04-arquitectura`) y BPMN del proceso 2
- [ ] Recorrido completo de las 56 historias en el emulador con los tres roles
CUERPO

git push -u origin "$RAMA"
gh pr create --base "$BASE" --head "$RAMA" --title "$TITULO_PR" --body-file "$CARPETA_TEMPORAL/cuerpo_pr.md"
