// Términos de búsqueda de los documentos de Firestore (S8). Gemelo de `Busqueda.kt` en la app: las dos
// implementaciones deben producir exactamente las mismas listas; las pruebas de ambos lados usan los mismos ejemplos.
const LARGO_MINIMO = 3;
const LARGO_MAXIMO_PALABRA = 20;
const MAXIMO_TERMINOS_PERSONA = 120;

/** Minúsculas, sin tildes y solo letras y dígitos: lo demás se vuelve un único espacio y los extremos se recortan. */
export function normalizar(texto) {
  return String(texto ?? '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

const prefijosDe = (palabra) => {
  const prefijos = [];
  for (let largo = LARGO_MINIMO; largo <= palabra.length; largo += 1) prefijos.push(palabra.slice(0, largo));
  return prefijos;
};

const subcadenasDe = (texto) => {
  const subcadenas = [];
  for (let largo = LARGO_MINIMO; largo <= texto.length; largo += 1) {
    for (let inicio = 0; inicio + largo <= texto.length; inicio += 1) subcadenas.push(texto.slice(inicio, inicio + largo));
  }
  return subcadenas;
};

// Orden por unidades UTF-16, igual que `String.compareTo` de Kotlin.
const porTexto = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

/**
 * Términos de un cliente: prefijos de al menos 3 letras de cada palabra de "nombres apellidos" (palabra cortada a
 * 20 caracteres) y todas las subcadenas de al menos 3 caracteres del DNI. Sin repetidos, ordenados y como mucho 120;
 * si hubiera más se conservan los más cortos y, a igual largo, los primeros alfabéticamente.
 */
export function terminosDePersona(nombres, apellidos, dni) {
  const prefijos = normalizar(`${nombres ?? ''} ${apellidos ?? ''}`).split(' ').filter(Boolean)
    .flatMap((palabra) => prefijosDe(palabra.slice(0, LARGO_MAXIMO_PALABRA)));
  let terminos = [...new Set([...prefijos, ...subcadenasDe(normalizar(dni).replaceAll(' ', ''))])];
  if (terminos.length > MAXIMO_TERMINOS_PERSONA) {
    terminos = terminos.sort((a, b) => a.length - b.length || porTexto(a, b)).slice(0, MAXIMO_TERMINOS_PERSONA);
  }
  return terminos.sort(porTexto);
}

/** Términos de un vehículo: la placa sin guion y en minúsculas y todas sus subcadenas de al menos 3 caracteres. */
export function terminosDePlaca(placa) {
  return [...new Set(subcadenasDe(normalizar(placa).replaceAll(' ', '')))].sort(porTexto);
}
