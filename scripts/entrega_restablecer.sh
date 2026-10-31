#!/usr/bin/env bash
# Registra el restablecimiento de contraseña con código por correo y el arreglo de la constancia de entrega, en una
# rama nueva desde main, con commits Conventional Commits sin atribuciones. Cada commit compila. Después sube la rama
# y abre el pull request hacia main si todavía no existe.
#
# Uso:
#   bash scripts/entrega_restablecer.sh                         # prueba, crea la rama y los commits, sube y abre el PR
#   SIMULAR=1 bash scripts/entrega_restablecer.sh               # solo muestra qué agregaría cada commit
#   SALTAR_PRUEBAS=1 bash scripts/entrega_restablecer.sh        # no corre Gradle antes de empezar
#   VERIFICAR_CADA_COMMIT=1 bash scripts/entrega_restablecer.sh # compila y prueba tras cada commit
#   SIN_PUBLICAR=1 bash scripts/entrega_restablecer.sh          # crea los commits pero no hace push ni PR
#   RAMA_NUEVA=otro-nombre bash scripts/entrega_restablecer.sh  # cambia el nombre de la rama
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git config user.name >/dev/null || { echo "Configura git user.name antes de continuar." >&2; exit 1; }
git config user.email >/dev/null || { echo "Configura git user.email antes de continuar." >&2; exit 1; }

BASE=app/src/main/java/com/leosle/pitlane
PRUEBAS=app/src/test/java/com/leosle/pitlane
DS=$BASE/arquitectura/datasource
RES=app/src/main/res
FN=firebase/functions

# Rutas explícitas de cada commit. Cada commit incluye solo sus rutas, aunque haya otros archivos preparados.
CONFORMIDAD=(
  $BASE/modelo/Entidades.kt $DS/firebase/Mapeadores.kt
  $BASE/arquitectura/repositorio/IOrdenRepository.kt $BASE/arquitectura/repositorio/impl/OrdenRepository.kt
  $BASE/arquitectura/vista/ordenes/FragConstancia.kt $BASE/arquitectura/vista/ordenes/FragEstadoOrden.kt
  $RES/layout/cli_frag_estado_orden.xml $RES/values/cadenas_cli.xml
  $PRUEBAS/arquitectura/datasource/firebase/MapeadoresTest.kt $PRUEBAS/arquitectura/repositorio/ReglasDeNegocioTest.kt
  firebase/firestore.rules firebase/test/firestore.test.mjs
)
SERVIDOR=(
  $FN/src/codigos.js $FN/src/restablecer.js $FN/src/validacion.js $FN/index.js
  $FN/package.json $FN/package-lock.json $FN/scripts/secretosDePrueba.mjs
  $FN/test/codigos.test.mjs $FN/test/funciones.test.mjs .gitignore
)
APP=(
  $BASE/util/Validador.kt $PRUEBAS/util/ValidadorTest.kt
  $DS/IUsuariosDataSource.kt $DS/firebase/UsuariosDataSource.kt $PRUEBAS/arquitectura/datasource/local/UsuariosDataSource.kt
  $BASE/arquitectura/repositorio/IAuthRepository.kt $BASE/arquitectura/repositorio/impl/AuthRepository.kt
  $BASE/arquitectura/viewmodel/AuthViewModel.kt $BASE/arquitectura/vista/acceso/FragRecuperar.kt
  $RES/layout/acc_frag_recuperar.xml $RES/values/cadenas_acc.xml
  $PRUEBAS/arquitectura/repositorio/RestablecerContrasenaTest.kt
)
DOCUMENTACION=(
  firebase/README.md README.md
  odd/tasks/restablecer-contrasena-codigo.md odd/tasks/mejoras-ux-clientes-tecnicos.md
)

for ruta in "${CONFORMIDAD[@]}" "${SERVIDOR[@]}" "${APP[@]}" "${DOCUMENTACION[@]}"; do
  [ -e "$ruta" ] || { echo "Falta '$ruta'. Revisa que estés en el repositorio correcto." >&2; exit 1; }
done

if [ -n "${SIMULAR:-}" ]; then
  echo "== Simulación (no se crea ninguna rama ni commit) =="
  simular() {
    local nombre="$1"; shift
    echo; echo "-- $nombre --"
    git add -n -A -- "$@" | sed 's/^/   /'
  }
  simular "conformidad del cliente y constancia" "${CONFORMIDAD[@]}"
  simular "funciones del servidor" "${SERVIDOR[@]}"
  simular "pantallas de la app" "${APP[@]}"
  simular "documentación" "${DOCUMENTACION[@]}"
  exit 0
fi

RAMA="$(git branch --show-current)"
if [ "$RAMA" = "main" ] || [ "$RAMA" = "master" ]; then
  RAMA="${RAMA_NUEVA:-feat/restablecer-con-codigo}"
  echo "Estás en la rama principal; creo la rama '$RAMA'."
  git switch -c "$RAMA"
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

confirmar "feat(ordenes): el cliente firma su conformidad desde la app" \
"- con la orden lista para recoger, el cliente ve una tarjeta para dar su conformidad; el Jefe
  la sigue pudiendo registrar en el taller, por ejemplo para clientes sin cuenta
- la regla de Firestore deja al cliente escribir solo la conformidad de su orden lista, una vez
  y con su propio usuario
- los cambios de estado y la conformidad guardan el nombre de quien los registró, así la
  constancia muestra quién registró la entrega aunque el cliente no pueda leer el perfil del Jefe" \
  "${CONFORMIDAD[@]}"

confirmar "feat(funciones): enviar un código para restablecer la contraseña" \
"- solicitarCodigo envía por Gmail un código de 6 dígitos; responde igual exista o no la cuenta
- restablecerContrasena cambia la contraseña con un código vigente y cierra las demás sesiones
- el código se guarda cifrado, vence a los 10 minutos, admite 5 intentos y se pide uno por minuto
- el correo y la contraseña de aplicación de Gmail son secretos de Firebase
- pruebas en el emulador (39 de funciones) y regla que impide leer los códigos desde la app" \
  "${SERVIDOR[@]}"

confirmar "feat(acceso): restablecer la contraseña con el código dentro de la app" \
"La recuperación pasa a dos pasos: pedir el código para el correo y luego escribir el código y la
contraseña nueva, con reenvío disponible pasado un minuto. Reemplaza al enlace de Firebase que
abría un formulario web." \
  "${APP[@]}"

confirmar "docs: configurar el correo de los códigos y registrar la funcionalidad" \
"Cómo crear la contraseña de aplicación de Gmail, guardar los secretos y desplegar, y las
bitácoras de la funcionalidad y del arreglo de la constancia." \
  "${DOCUMENTACION[@]}"

echo
git log --oneline -6
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

- **Restablecer la contraseña con un código:** la app pide un código de 6 dígitos que llega por correo y, con él, se escribe la contraseña nueva dentro de la app. Reemplaza al enlace de Firebase que abría un formulario web.
- **Conformidad firmada por el cliente:** con la orden lista para recoger, el cliente da su conformidad desde la app; el Jefe la sigue pudiendo registrar en el taller.
- **Constancia de entrega:** ahora muestra quién registró la entrega también en la vista del cliente.

## Seguridad del código
- Se guarda cifrado, vence a los 10 minutos y admite 5 intentos.
- Solo se puede pedir uno por minuto, y la respuesta es la misma exista o no la cuenta.
- Al cambiar la contraseña se cierran las demás sesiones de la cuenta.
- Ninguna regla de Firestore deja leer los códigos desde la app.

## Antes de desplegar
Configurar la cuenta de Gmail del taller y guardar los secretos `CORREO_REMITENTE` y `CORREO_CLAVE_APP` (ver `firebase/README.md`), y luego `firebase deploy --only firestore:rules,functions`.

## Cómo probar
- `./gradlew :app:testDebugUnitTest` (113 pruebas).
- `cd firebase/functions && npm test` (39 pruebas en el emulador) y `cd firebase && npm test` (53 pruebas de reglas).
EOF
gh pr create --base main --head "$RAMA" --title "feat: restablecer la contraseña con un código por correo" --body-file "$CUERPO"
