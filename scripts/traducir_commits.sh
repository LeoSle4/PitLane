#!/usr/bin/env bash
# Traduce al español los mensajes de los commits de la rama actual (main..HEAD)
# y crea primero el commit que saca /docs del repositorio.
# No hace push: el push lo haces tú a mano al final.
set -euo pipefail

BASE="main"
RAMA="$(git rev-parse --abbrev-ref HEAD)"
BACKUP="backup-antes-de-traducir-$(date +%Y%m%d%H%M%S)"

# 1. Debe haber cambios staged (el borrado de docs/ y el .gitignore) y nada sin stage.
if ! git diff --cached --quiet; then :; else
  echo "No hay cambios staged para el commit final (docs/ + .gitignore)." >&2; exit 1
fi
if ! git diff --quiet; then
  echo "Hay cambios sin stage en archivos versionados. Haz stash o commit antes de seguir." >&2; exit 1
fi

# 2. Respaldo de la rama tal como está hoy.
git branch "$BACKUP"
echo "Respaldo creado: $BACKUP"

# 3. Último commit, ya en español.
git commit -q -m "chore: eliminar la carpeta docs del repositorio y agregarla al gitignore"

# 4. Reescribir los mensajes de main..HEAD según el asunto original.
export FILTER_BRANCH_SQUELCH_WARNING=1
git filter-branch -f --msg-filter '
  IFS= read -r asunto
  resto="$(cat)"
  case "$asunto" in
    "chore(build): add lifecycle, fragment, coroutines and Lottie dependencies and enable view binding")
      nuevo="chore(build): agregar dependencias de lifecycle, fragment, coroutines y Lottie y habilitar view binding" ;;
    "feat(theme): add Japanese palette, typography, icons, brand logos and animations")
      nuevo="feat(theme): agregar paleta japonesa, tipografía, íconos, logos de marca y animaciones" ;;
    "feat(domain): add models, validation rules and repository contracts")
      nuevo="feat(domain): agregar modelos, reglas de validación y contratos de repositorio" ;;
    "feat(data): add data sources, repositories with role-based access and in-memory sample data")
      nuevo="feat(data): agregar fuentes de datos, repositorios con acceso por rol y datos de ejemplo en memoria" ;;
    "feat(viewmodel): add view models for every module")
      nuevo="feat(viewmodel): agregar view models de cada módulo" ;;
    "feat(ui): add shared components, action menu, screen transitions and navigation shell")
      nuevo="feat(ui): agregar componentes compartidos, menú de acciones, transiciones de pantalla y base de navegación" ;;
    "feat(access): add splash, animated travel scene, login, sign-up, recovery, profile and push notifications")
      nuevo="feat(access): agregar splash, escena animada de viaje, inicio de sesión, registro, recuperación, perfil y notificaciones push" ;;
    "feat(client): add vehicles, requests, orders, notifications and order tracking screens")
      nuevo="feat(client): agregar pantallas de vehículos, solicitudes, órdenes, notificaciones y seguimiento de órdenes" ;;
    "feat(manager): add technician, client and vehicle management screens")
      nuevo="feat(manager): agregar pantallas de gestión de técnicos, clientes y vehículos" ;;
    "feat(manager): add pending requests, order intake with client search, order detail with delivery and dashboard screens")
      nuevo="feat(manager): agregar solicitudes pendientes, registro de órdenes con búsqueda de cliente, detalle de orden con entrega y panel principal" ;;
    "feat(technician): add assigned orders, progress records and ready-state screens")
      nuevo="feat(technician): agregar pantallas de órdenes asignadas, registro de avances y estado listo" ;;
    "feat(navigation): connect role routes to their screens")
      nuevo="feat(navigation): conectar las rutas de cada rol con sus pantallas" ;;
    "test: cover validation, business rules, role-based access and local data sources")
      nuevo="test: cubrir validaciones, reglas de negocio, acceso por rol y fuentes de datos locales" ;;
    "docs(requirements): add HU-49 delivery, Entregado state and update traceability, totals and design guide")
      nuevo="docs(requirements): agregar HU-49 de entrega, estado Entregado y actualizar trazabilidad, totales y guía de diseño" ;;
    "docs(bpmn): add vehicle pickup and delivery to the repair process")
      nuevo="docs(bpmn): agregar la recogida y entrega del vehículo al proceso de reparación" ;;
    "docs(architecture): update component, deployment and dependency diagrams and add Firebase migration guide")
      nuevo="docs(architecture): actualizar diagramas de componentes, despliegue y dependencias y agregar guía de migración a Firebase" ;;
    "docs: add feature task logs")
      nuevo="docs: agregar bitácoras de tareas por funcionalidad" ;;
    *) nuevo="$asunto" ;;
  esac
  printf "%s\n" "$nuevo"
  [ -n "$resto" ] && printf "%s\n" "$resto"
  exit 0
' "$BASE..HEAD"

echo
git log --format='%h %s' "$BASE..HEAD"
echo
echo "Listo. Revisa el log y, si todo está bien, publica tú:"
echo "  git push --force-with-lease origin $RAMA"
echo "Si algo salió mal:  git reset --hard $BACKUP"
