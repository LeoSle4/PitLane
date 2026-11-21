/**
 * Claves que la app escribe en cada entrada del historial de una orden (`CambioEstado` en Kotlin). El mapeador las
 * escribe siempre, con `null` cuando no hay valor, y las reglas exigen que las entradas ya guardadas se reenvíen
 * idénticas; por eso una entrada antigua a la que le falta una clave tiene que completarse antes de desplegar las reglas.
 */
export const CLAVES_OPCIONALES = ['motivo', 'usuarioNombre'];

/**
 * Devuelve el historial con cada entrada completada con las claves opcionales que le falten (en `null`) y si hubo que
 * cambiar algo. No quita claves ni toca valores existentes, y deja intacto lo que no es un arreglo de mapas.
 */
export function normalizarHistorial(historial) {
  if (!Array.isArray(historial)) return { historial, cambio: false };
  let cambio = false;
  const normalizado = historial.map((entrada) => {
    if (entrada === null || typeof entrada !== 'object' || Array.isArray(entrada)) return entrada;
    const faltan = CLAVES_OPCIONALES.filter((clave) => !(clave in entrada));
    if (faltan.length === 0) return entrada;
    cambio = true;
    return { ...entrada, ...Object.fromEntries(faltan.map((clave) => [clave, null])) };
  });
  return { historial: normalizado, cambio };
}
