import { createHash, randomInt, timingSafeEqual } from 'node:crypto';

/** Minutos que vale un código para restablecer la contraseña. */
export const VIGENCIA_MS = 10 * 60 * 1000;

/** Intentos fallidos que admite un código antes de exigir uno nuevo. */
export const MAXIMO_INTENTOS = 5;

/** Tiempo mínimo entre dos pedidos de código para el mismo correo. */
export const ESPERA_ENTRE_PEDIDOS_MS = 60 * 1000;

/** Correo en minúsculas y sin espacios, para que la misma cuenta siempre dé el mismo registro. */
export const normalizarCorreo = (correo) => (typeof correo === 'string' ? correo.trim().toLowerCase() : '');

/** Código aleatorio de 6 dígitos, con ceros a la izquierda si hace falta. */
export const nuevoCodigo = () => String(randomInt(0, 1_000_000)).padStart(6, '0');

/** Identificador del registro del código: el correo cifrado, para no guardar correos como claves. */
export const idDeCorreo = (correo) => createHash('sha256').update(correo).digest('hex');

/** El código se guarda cifrado junto con el correo; nunca en texto plano. */
export const cifrarCodigo = (correo, codigo) => createHash('sha256').update(`${correo}:${codigo}`).digest('hex');

/**
 * Evalúa el código ingresado contra el registro guardado y devuelve `valido`, `incorrecto`, `agotado` (sin intentos)
 * o `vencido` (no hay registro o ya pasó su vigencia).
 */
export function evaluarCodigo(registro, correo, codigo, ahora) {
  if (!registro || ahora > registro.expira) return 'vencido';
  if (registro.intentos >= MAXIMO_INTENTOS) return 'agotado';
  const esperado = Buffer.from(registro.hash, 'hex');
  const recibido = Buffer.from(cifrarCodigo(correo, codigo), 'hex');
  return esperado.length === recibido.length && timingSafeEqual(esperado, recibido) ? 'valido' : 'incorrecto';
}
