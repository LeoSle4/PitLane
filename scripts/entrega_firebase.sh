#!/usr/bin/env bash
# Registra en una rama la conexión de la app a Firebase (dependencias, datasources, cableado, reglas de
# seguridad y Cloud Functions) en commits Conventional Commits sin atribuciones. El orden de los commits es tal
# que cada uno compila. No hace push ni abre pull request: eso lo decides tú después.
#
# Uso:
#   bash scripts/entrega_firebase.sh                         # prueba, compila y crea los commits
#   SIMULAR=1 bash scripts/entrega_firebase.sh               # solo muestra qué agregaría cada commit
#   SALTAR_PRUEBAS=1 bash scripts/entrega_firebase.sh        # no corre Gradle antes de empezar
#   VERIFICAR_CADA_COMMIT=1 bash scripts/entrega_firebase.sh # compila y prueba tras cada commit
#
# Si estás en main, crea la rama feat/conectar-firebase (puedes darle otro nombre con RAMA_NUEVA=...).
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git config user.name >/dev/null || { echo "Configura git user.name antes de continuar." >&2; exit 1; }
git config user.email >/dev/null || { echo "Configura git user.email antes de continuar." >&2; exit 1; }

BASE=app/src/main/java/com/leosle/pitlane
PRUEBAS=app/src/test/java/com/leosle/pitlane
DS=$BASE/arquitectura/datasource
REPO=$BASE/arquitectura/repositorio

# Rutas explícitas de cada commit, para no arrastrar archivos ajenos (scripts sueltos, claves, builds).
DEPENDENCIAS=(.gitignore build.gradle.kts app/build.gradle.kts gradle/libs.versions.toml)
BASES=(
  $DS/firebase/Mapeadores.kt $DS/firebase/Alcance.kt $DS/firebase/TareasFirebase.kt $DS/firebase/MensajesDeError.kt
  $PRUEBAS/arquitectura/datasource/firebase/MapeadoresTest.kt
  $PRUEBAS/arquitectura/datasource/firebase/AlcanceTest.kt
  $PRUEBAS/arquitectura/datasource/firebase/MensajesDeErrorTest.kt
)
CONTRATOS=(
  $DS/IDocumentosDataSource.kt $DS/IFotoStorage.kt $DS/IUsuariosDataSource.kt
  $DS/local/DocumentosDataSource.kt $DS/local/FotoStorage.kt $DS/local/UsuariosDataSource.kt
  $REPO/IAuthRepository.kt $REPO/impl/AuthRepository.kt $REPO/impl/AvanceRepository.kt
  $REPO/impl/OrdenRepository.kt $REPO/impl/SolicitudRepository.kt $REPO/impl/VehiculoRepository.kt
  $BASE/arquitectura/viewmodel/AuthViewModel.kt $BASE/arquitectura/viewmodel/ViewModelBase.kt
  $BASE/arquitectura/vista/acceso/FragSplash.kt $BASE/util/Validador.kt
  $PRUEBAS/arquitectura/repositorio/FuentesLocalesTest.kt
  $PRUEBAS/arquitectura/viewmodel/ProtegidoTest.kt
)
DATASOURCES=(
  $DS/firebase/UsuariosDataSource.kt $DS/firebase/DocumentosDataSource.kt
  $DS/firebase/ColeccionFirestore.kt $DS/firebase/FotoStorage.kt
  $PRUEBAS/arquitectura/datasource/firebase/NumeroDeOrdenTest.kt
  $PRUEBAS/arquitectura/datasource/firebase/FotoStorageTest.kt
)
CABLEADO=($BASE/Dependencias.kt $BASE/arquitectura/vista/comun/Imagenes.kt)
SERVIDOR=(firebase)
BITACORA=(odd/tasks/firebase-datasources.md)

TODAS=("${DEPENDENCIAS[@]}" "${BASES[@]}" "${CONTRATOS[@]}" "${DATASOURCES[@]}" "${CABLEADO[@]}" "${SERVIDOR[@]}" "${BITACORA[@]}")
for ruta in "${TODAS[@]}"; do
  [ -e "$ruta" ] || { echo "Falta '$ruta'. Revisa que estés en el repositorio correcto." >&2; exit 1; }
done

if [ -n "${SIMULAR:-}" ]; then
  echo "== Simulación (no se crea ningún commit ni rama) =="
  simular() {
    local nombre="$1"; shift
    echo; echo "-- $nombre --"
    git add -n -A -- "$@" | sed 's/^/   /'
  }
  simular "dependencias" "${DEPENDENCIAS[@]}"
  simular "mapeadores, alcance y errores" "${BASES[@]}"
  simular "contratos y repositorios" "${CONTRATOS[@]}"
  simular "datasources de Firebase" "${DATASOURCES[@]}"
  simular "cableado de la app" "${CABLEADO[@]}"
  simular "reglas y Cloud Functions" "${SERVIDOR[@]}"
  simular "bitácora" "${BITACORA[@]}"
  exit 0
fi

RAMA="$(git branch --show-current)"
if [ "$RAMA" = "main" ] || [ "$RAMA" = "master" ]; then
  RAMA="${RAMA_NUEVA:-feat/conectar-firebase}"
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
  if [ -n "${VERIFICAR_CADA_COMMIT:-}" ]; then
    echo "    comprobando este commit..."
    comprobar
  fi
}

confirmar "chore(firebase): agregar dependencias de Firebase y el plugin google-services" \
"Authentication, Firestore, Storage y Functions con el BoM 34.19.0. El BoM 35.x trae
librerías compiladas con Kotlin 2.4 y el proyecto usa 2.2, por eso no se sube.
google-services.json queda fuera del repositorio, que es público." \
  "${DEPENDENCIAS[@]}"

confirmar "feat(firebase): convertir el modelo a documentos y definir el alcance de lectura por rol" \
"- mapeadores entre el modelo y los documentos de Firestore; la contraseña nunca se escribe
- alcance de lectura: el Jefe lee todo, los demás solo lo que los incluye en el campo acceso
- mensajes de error de Authentication y de las funciones del servidor
- espera de tareas de Firebase sin bloquear el hilo
Con sus pruebas." \
  "${BASES[@]}"

confirmar "refactor(datasource): preparar los contratos para un servidor real" \
"- IDocumentosDataSource.buscarPlaca: la placa se busca entre todos los vehículos en el servidor
- IUsuariosDataSource.restaurarSesion: el splash espera la sesión guardada en lugar de decidir a los 1,6 s
- IFotoStorage.subir recibe el cliente dueño y archivo() entrega la foto lista para mostrar
- ViewModelBase.ejecutar atrapa las fallas técnicas y devuelve un error con mensaje
Las implementaciones locales se adaptan y siguen sirviendo a las pruebas." \
  "${CONTRATOS[@]}"

confirmar "feat(firebase): datasources de usuarios, documentos y fotos sobre Firebase" \
"- UsuariosDataSource: Authentication, perfiles en Firestore y funciones del servidor para crear cuentas
- DocumentosDataSource: colecciones filtradas por rol, número de orden transaccional y escrituras en lote
- permisos del técnico y del cliente (acceso) actualizados al crear o reasignar una orden
- FotoStorage: fotos en Cloud Storage dentro de la carpeta del cliente dueño
Con sus pruebas." \
  "${DATASOURCES[@]}"

confirmar "feat(firebase): conectar la app a Firebase" \
"Dependencias usa los datasources de Firebase y las clases locales quedan solo para las pruebas.
Imagenes descarga en segundo plano las fotos que no están en el teléfono." \
  "${CABLEADO[@]}"

confirmar "feat(firebase): reglas de seguridad y Cloud Functions con sus pruebas" \
"- reglas de Firestore y de Storage: cada cliente ve lo suyo, el técnico solo sus órdenes asignadas
- funciones: registrarCliente, crearTecnico, desactivarCuenta, reactivarCuenta, verificarPlaca y notificarOrden
- script para sembrar al primer Jefe
- pruebas en el emulador: 51 de las reglas y 25 de las funciones
- README con cómo probar, desplegar y sembrar al Jefe" \
  "${SERVIDOR[@]}"

confirmar "docs(firebase): bitácora de la conexión a Firebase" \
"Especificaciones, tareas y registro de decisiones de la conexión a Firebase." \
  "${BITACORA[@]}"

echo
git log --oneline -8
echo
echo "Quedó sin commitear (si lo hay):"
git status --short | sed 's/^/   /' || true
echo
echo "Siguiente paso (decides tú): git push -u origin $RAMA"
