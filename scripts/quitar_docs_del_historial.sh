#!/usr/bin/env bash
# Elimina la carpeta docs/ de TODO el historial de main.
# Los commits que solo tocaban docs/ desaparecen (quedan vacíos y se podan).
# No hace push: el push lo haces tú a mano al final.
set -euo pipefail

OBJETIVO="main"
STAMP="$(date +%Y%m%d%H%M%S)"
RESPALDO_DOCS="../PitLane-docs-respaldo-$STAMP"
RESPALDO_RAMA="backup-$OBJETIVO-antes-de-quitar-docs-$STAMP"

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Hay cambios sin commitear en archivos versionados. Haz commit o stash antes de seguir." >&2
  exit 1
fi

# 1. Copia de tus archivos de docs/ (siguen en disco, pero por seguridad).
[ -d docs ] && cp -R docs "$RESPALDO_DOCS" && echo "Copia de docs/: $RESPALDO_DOCS"

# 2. Respaldo de la rama que se va a reescribir.
git branch "$RESPALDO_RAMA" "$OBJETIVO"
echo "Respaldo de $OBJETIVO: $RESPALDO_RAMA"

# 3. Reescribir main sin docs/ y podar los commits que queden vacíos.
export FILTER_BRANCH_SQUELCH_WARNING=1
git filter-branch -f \
  --index-filter 'git rm -r -q --cached --ignore-unmatch docs' \
  --prune-empty \
  -- "$OBJETIVO"

# 4. Limpiar la referencia que deja filter-branch.
git update-ref -d "refs/original/refs/heads/$OBJETIVO" 2>/dev/null || true

echo
git log --format='%h %s' "$OBJETIVO" | head -25
echo
echo "Verificación (debe imprimir 0):"
git ls-tree -r --name-only "$OBJETIVO" docs | wc -l
echo
echo "Si todo está bien, publica tú:"
echo "  git push --force-with-lease origin $OBJETIVO"
echo "Si algo salió mal:  git branch -f $OBJETIVO $RESPALDO_RAMA"
