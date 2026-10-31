import * as logger from 'firebase-functions/logger';
import { onCall } from 'firebase-functions/https';
import { defineSecret } from 'firebase-functions/params';
import nodemailer from 'nodemailer';
import {
  ESPERA_ENTRE_PEDIDOS_MS, VIGENCIA_MS, cifrarCodigo, evaluarCodigo, idDeCorreo, normalizarCorreo, nuevoCodigo,
} from './codigos.js';
import { autenticacion, bd, FieldValue, HttpsError } from './comun.js';
import { correoValido, validarContrasena } from './validacion.js';

/** Cuenta de Gmail del taller que envía los códigos y su contraseña de aplicación; se guardan como secretos. */
const CORREO_REMITENTE = defineSecret('CORREO_REMITENTE');
const CORREO_CLAVE_APP = defineSecret('CORREO_CLAVE_APP');

/** Registros de los códigos pedidos; ninguna regla de Firestore los deja leer desde la app. */
const codigos = bd.collection('codigosRestablecimiento');

const MENSAJE_VENCIDO = 'El código venció o no es válido. Pide uno nuevo';

/** En el emulador no se envían correos: el código queda en el registro de la función para poder probar. */
async function enviarCodigo(correo, codigo) {
  if (process.env.FUNCTIONS_EMULATOR === 'true') {
    logger.info('Código de restablecimiento (solo en el emulador)', { correo, codigo });
    return;
  }
  const remitente = CORREO_REMITENTE.value();
  const transporte = nodemailer.createTransport({ service: 'gmail', auth: { user: remitente, pass: CORREO_CLAVE_APP.value() } });
  await transporte.sendMail({
    from: `"Servicio Eléctrico Freddy" <${remitente}>`,
    to: correo,
    subject: `${codigo} es tu código para restablecer la contraseña`,
    text: `Tu código para restablecer la contraseña es ${codigo}. Vence en 10 minutos.\n\n`
      + 'Si no lo pediste, ignora este correo: tu contraseña no cambia.',
    html: `<p>Tu código para restablecer la contraseña es:</p><p style="font-size:28px;font-weight:bold;letter-spacing:6px">${codigo}</p>`
      + '<p>Vence en 10 minutos.</p><p>Si no lo pediste, ignora este correo: tu contraseña no cambia.</p>',
  });
}

/**
 * Envía un código de 6 dígitos al correo. Responde igual exista o no la cuenta, para no revelar qué correos están
 * registrados, y no deja pedir otro código antes de un minuto.
 */
export const solicitarCodigo = onCall({ secrets: [CORREO_REMITENTE, CORREO_CLAVE_APP] }, async (request) => {
  const correo = normalizarCorreo(request.data?.correo);
  if (!correoValido(correo)) throw new HttpsError('invalid-argument', 'Ingresa un correo válido');

  const registro = codigos.doc(idDeCorreo(correo));
  const ahora = Date.now();
  const previo = await registro.get();
  if (previo.exists && ahora - previo.data().enviadoEn < ESPERA_ENTRE_PEDIDOS_MS) {
    throw new HttpsError('failed-precondition', 'Espera un minuto antes de pedir otro código');
  }

  const codigo = nuevoCodigo();
  await registro.set({ hash: cifrarCodigo(correo, codigo), expira: ahora + VIGENCIA_MS, intentos: 0, enviadoEn: ahora });
  const cuenta = await autenticacion.getUserByEmail(correo).catch(() => null);
  if (cuenta && !cuenta.disabled) {
    try {
      await enviarCodigo(correo, codigo);
    } catch (error) {
      logger.error('No se pudo enviar el código', error);
      await registro.delete();
      throw new HttpsError('internal', 'No se pudo enviar el correo. Inténtalo más tarde');
    }
  }
  return { enviado: true };
});

/**
 * Cambia la contraseña si el código es correcto y sigue vigente. Cada código fallido suma un intento; al acertar,
 * el código se borra y se cierran las demás sesiones de la cuenta.
 */
export const restablecerContrasena = onCall(async (request) => {
  const correo = normalizarCorreo(request.data?.correo);
  const codigo = typeof request.data?.codigo === 'string' ? request.data.codigo.trim() : '';
  const contrasena = typeof request.data?.contrasena === 'string' ? request.data.contrasena : '';
  if (!correoValido(correo)) throw new HttpsError('invalid-argument', 'Ingresa un correo válido');
  if (!/^\d{6}$/.test(codigo)) throw new HttpsError('invalid-argument', 'Escribe el código de 6 dígitos');
  const errorContrasena = validarContrasena(contrasena);
  if (errorContrasena) throw new HttpsError('invalid-argument', errorContrasena);

  const registro = codigos.doc(idDeCorreo(correo));
  const resultado = await bd.runTransaction(async (transaccion) => {
    const documento = await transaccion.get(registro);
    const evaluacion = evaluarCodigo(documento.exists ? documento.data() : null, correo, codigo, Date.now());
    if (evaluacion === 'incorrecto') transaccion.update(registro, { intentos: FieldValue.increment(1) });
    if (evaluacion === 'valido') transaccion.delete(registro);
    return evaluacion;
  });
  if (resultado === 'incorrecto') throw new HttpsError('invalid-argument', 'El código no es correcto');
  if (resultado === 'agotado') throw new HttpsError('failed-precondition', 'Superaste los intentos. Pide un código nuevo');
  if (resultado === 'vencido') throw new HttpsError('failed-precondition', MENSAJE_VENCIDO);

  const cuenta = await autenticacion.getUserByEmail(correo).catch(() => null);
  if (!cuenta) throw new HttpsError('failed-precondition', MENSAJE_VENCIDO);
  if (cuenta.disabled) throw new HttpsError('failed-precondition', 'Tu cuenta está desactivada. Comunícate con el taller');
  await autenticacion.updateUser(cuenta.uid, { password: contrasena });
  await autenticacion.revokeRefreshTokens(cuenta.uid);
  return { restablecida: true };
});
