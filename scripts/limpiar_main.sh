#!/usr/bin/env bash
# Sobre la rama main:
#   1. quita odd/ de todo el historial (los commits que solo tocaban odd/ desaparecen),
#   2. traduce al español los mensajes de los commits.
# No hace push: el push lo haces tú a mano al final.
set -euo pipefail

OBJETIVO="main"
STAMP="$(date +%Y%m%d%H%M%S)"
RESPALDO_RAMA="backup-$OBJETIVO-antes-de-limpiar-$STAMP"

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Hay cambios sin commitear en archivos versionados. Haz commit o stash antes de seguir." >&2
  exit 1
fi

# Main local debe coincidir con el remoto; si no, se aborta (evita pisar commits ajenos).
git fetch -q origin
LOCAL="$(git rev-parse "refs/heads/$OBJETIVO")"
REMOTO="$(git rev-parse --verify -q "refs/remotes/origin/$OBJETIVO" || true)"
if [ "$LOCAL" != "$REMOTO" ]; then
  echo "main local ($LOCAL) no coincide con origin/main (${REMOTO:-inexistente}). Sincroniza antes de seguir." >&2
  exit 1
fi

# Copia de odd/ por si acaso (docs/ ya tiene su propia copia).
[ -d odd ] && cp -R odd "../PitLane-odd-respaldo-$STAMP" && echo "Copia de odd/: ../PitLane-odd-respaldo-$STAMP"

git branch "$RESPALDO_RAMA" "$OBJETIVO"
echo "Respaldo de $OBJETIVO: $RESPALDO_RAMA"

export FILTER_BRANCH_SQUELCH_WARNING=1
git filter-branch -f \
  --index-filter 'git rm -r -q --cached --ignore-unmatch odd' \
  --msg-filter '
  IFS= read -r asunto || true
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
    "docs(architecture): update component, deployment and dependency diagrams and add Firebase migration guide")
      nuevo="docs(architecture): actualizar diagramas de componentes, despliegue y dependencias y agregar guía de migración a Firebase" ;;
    "Merge pull request #1 from LeoSle4/feat/app-ui-funcionalidad-local")
      nuevo="Fusionar pull request #1 desde LeoSle4/feat/app-ui-funcionalidad-local" ;;
    *) nuevo="$asunto" ;;
  esac
  printf "%s\n" "$nuevo"
  [ -n "$resto" ] && printf "%s\n" "$resto"
  exit 0
' \
  --prune-empty \
  -- "$OBJETIVO"

git update-ref -d "refs/original/refs/heads/$OBJETIVO" 2>/dev/null || true

echo
git log --format='%h %s' "$OBJETIVO"
echo
echo "Archivos de odd/ en main (debe ser 0): $(git ls-tree -r --name-only "$OBJETIVO" odd | wc -l)"
echo
echo "Si todo está bien, publica tú:"
echo "  git push --force-with-lease origin $OBJETIVO"
echo "Si algo salió mal:  git branch -f $OBJETIVO $RESPALDO_RAMA"
