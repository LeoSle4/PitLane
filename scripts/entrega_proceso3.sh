#!/usr/bin/env bash
# Registra en la rama actual el trabajo del proceso 3 (entrega del vehículo), la carpeta arquitectura/,
# el generador del diagrama y el README, en commits Conventional Commits sin atribuciones.
# No hace push ni abre pull request: eso lo decides tú después.
#
# Uso:
#   bash scripts/entrega_proceso3.sh              # prueba, compila y crea los commits
#   SIMULAR=1 bash scripts/entrega_proceso3.sh    # solo muestra qué agregaría cada commit
#   SALTAR_PRUEBAS=1 bash scripts/entrega_proceso3.sh   # crea los commits sin correr Gradle
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

RAMA="$(git branch --show-current)"
if [ "$RAMA" = "main" ] || [ "$RAMA" = "master" ]; then
  echo "Estás en '$RAMA'. Cambia a la rama de desarrollo antes de ejecutar este script." >&2
  exit 1
fi
git config user.name >/dev/null || { echo "Configura git user.name antes de continuar." >&2; exit 1; }
git config user.email >/dev/null || { echo "Configura git user.email antes de continuar." >&2; exit 1; }

# Rutas de cada commit; se usan rutas explícitas para no arrastrar archivos ajenos (scripts sueltos, __pycache__).
CODIGO=(app odd/tasks/proceso-3-entrega.md)
GENERADOR=(scripts/diagrama-componentes/arquitectura_carpeta.py)
DOCUMENTACION=(README.md diagramas)

for ruta in "${CODIGO[@]}" "${GENERADOR[@]}" "${DOCUMENTACION[@]}"; do
  [ -e "$ruta" ] || { echo "Falta '$ruta'. Revisa que estés en el repositorio correcto." >&2; exit 1; }
done

if [ -n "${SIMULAR:-}" ]; then
  echo "== Simulación en la rama '$RAMA' (no se crea ningún commit) =="
  simular() {
    local nombre="$1"; shift
    echo; echo "-- $nombre --"
    git add -n -A -- "$@" | sed 's/^/   /'
  }
  simular "código y bitácora" "${CODIGO[@]}"
  simular "generador del diagrama" "${GENERADOR[@]}"
  simular "README y diagramas" "${DOCUMENTACION[@]}"
  exit 0
fi

if [ -z "${SALTAR_PRUEBAS:-}" ]; then
  echo "== Comprobando que todo compila y las pruebas pasan =="
  ./gradlew :app:testDebugUnitTest :app:assembleDebug
fi

# Hace un commit con las rutas indicadas; omite el commit si no hay cambios en ellas.
confirmar() {
  local asunto="$1" cuerpo="$2"; shift 2
  git add -A -- "$@"
  if git diff --cached --quiet; then
    echo "Sin cambios para: $asunto"
    return
  fi
  git commit -q -m "$asunto" -m "$cuerpo"
  echo "OK  $asunto"
}

confirmar "feat(entrega): implementar proceso 3 y agrupar capas en arquitectura" \
"Proceso 3 (HU-49 a HU-54 y RN-14):
- buscar órdenes listas por placa, cliente, DNI o número
- resumen de trabajos para explicar al cliente
- registrar la conformidad del cliente y observaciones con lo acordado
- entregar solo con la conformidad registrada
- constancia de entrega para el cliente

Se agrupan vista, viewmodel, repositorio (con impl) y datasource (con local) en la
carpeta arquitectura/, aparte de modelo/ y util/. Se actualizan manifest, layouts y pruebas.
Incluye la bitácora odd/tasks/proceso-3-entrega.md." \
  "${CODIGO[@]}"

confirmar "chore(diagramas): agregar generador del diagrama con arquitectura" \
"Script que parte del diagrama de componentes y le suma el marco de la carpeta
arquitectura/, modelo/ y util/ sin modificar los generadores existentes." \
  "${GENERADOR[@]}"

confirmar "docs: agregar README y diagramas de procesos y arquitectura" \
"README con la descripción de la app, los tres procesos BPMN, el diagrama de componentes,
las dependencias de los repositorios, el despliegue, los requisitos y cómo ejecutarla.
Las imágenes viven en diagramas/ porque la carpeta docs/ no se versiona." \
  "${DOCUMENTACION[@]}"

echo
git log --oneline -3
echo
echo "Quedó sin commitear (si lo hay):"
git status --short | sed 's/^/   /' || true
echo
echo "Siguiente paso (decides tú): git push -u origin $RAMA"
