import { getMessaging } from 'firebase-admin/messaging';
import * as logger from 'firebase-functions/logger';

/** Códigos con los que FCM dice que un token ya no sirve: la app se desinstaló o el token se renovó. */
const CODIGOS_TOKEN_INVALIDO = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

/** Indica si el error de FCM quiere decir que el token ya no sirve y hay que borrarlo. */
export function tokenInvalido(codigo) {
  return CODIGOS_TOKEN_INVALIDO.has(codigo);
}

/**
 * Arma el mensaje FCM de un aviso. Lleva solo `data`: la app arma la notificación y así el aviso con la app a la vista
 * no se duplica. Los valores de `data` deben ser texto. Con prioridad alta para que llegue aunque el teléfono esté en reposo.
 */
export function mensajePush(tokens, notificacionId, aviso, ordenId, esIngreso) {
  return {
    tokens,
    data: {
      orden_id: ordenId,
      notificacion_id: notificacionId,
      es_ingreso: String(esIngreso),
      titulo: aviso.titulo,
      mensaje: aviso.mensaje,
    },
    android: { priority: 'high' },
  };
}

/** De las respuestas de `sendEachForMulticast`, devuelve los tokens que FCM rechazó por inválidos o no registrados. */
export function tokensParaBorrar(tokens, respuestas) {
  return tokens.filter((_, i) => !respuestas[i]?.success && tokenInvalido(respuestas[i]?.error?.code));
}

/**
 * Envía el aviso a los dispositivos del cliente y borra los tokens que FCM rechaza. Nunca lanza: un fallo del push no
 * debe hacer fallar ni reintentar el disparador, porque la notificación de la bandeja ya quedó creada.
 * En el emulador no se envía nada: queda en el registro de la función para poder probar.
 */
export async function enviarPush(usuarioRef, notificacionId, aviso, ordenId, esIngreso) {
  try {
    const documentos = (await usuarioRef.collection('tokensDispositivo').get()).docs;
    const tokens = documentos.map((d) => d.data().token).filter((t) => typeof t === 'string' && t);
    if (tokens.length === 0) return;
    if (process.env.FUNCTIONS_EMULATOR === 'true') {
      logger.info('Push FCM (solo en el emulador, no se envía)', { notificacionId, dispositivos: tokens.length, titulo: aviso.titulo });
      return;
    }
    const { responses } = await getMessaging().sendEachForMulticast(mensajePush(tokens, notificacionId, aviso, ordenId, esIngreso));
    const invalidos = new Set(tokensParaBorrar(tokens, responses));
    await Promise.all(documentos.filter((d) => invalidos.has(d.data().token)).map((d) => d.ref.delete()));
  } catch (error) {
    logger.error('No se pudo enviar el push FCM', { notificacionId, error: String(error) });
  }
}
