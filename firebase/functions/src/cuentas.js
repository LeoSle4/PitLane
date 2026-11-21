import { onCall } from 'firebase-functions/https';
import { autenticacion, bd, exigirJefe, FieldValue, HttpsError, sesion } from './comun.js';
import { terminosDePersona } from './busqueda.js';
import { normalizarCuenta, validarCuenta } from './validacion.js';

const usuarios = bd.collection('usuarios');
const dnis = bd.collection('dnis');
const ESTADOS_EN_TALLER = ['INGRESADO', 'EN_REPARACION', 'LISTO'];
const ESTADOS_EN_CURSO = ['INGRESADO', 'EN_REPARACION'];
const MENSAJE_DNI_REPETIDO = 'Ya existe un usuario con ese DNI';

/**
 * Perfil que ya usa el DNI: primero el de su reserva `dnis/{dni}` y, si no la hay, el que encuentre la consulta (un
 * perfil anterior a las reservas, mientras no se ejecute `rellenarDnis.mjs`). Es una comprobación previa para dar el
 * mensaje antes de crear la cuenta; la garantía es la reserva que escribe [escribirPerfilConDni].
 */
async function usuarioPorDni(dni) {
  const reserva = await dnis.doc(dni).get();
  if (reserva.exists) {
    const perfil = await usuarios.doc(reserva.data().usuarioId).get();
    if (perfil.exists) return { id: perfil.id, datos: perfil.data() };
  }
  const resultado = await usuarios.where('dni', '==', dni).limit(1).get();
  return resultado.empty ? null : { id: resultado.docs[0].id, datos: resultado.docs[0].data() };
}

/**
 * Escribe el perfil [id] y la reserva de su DNI en una sola transacción. Si la reserva ya existe y es de otro usuario,
 * no escribe nada y rechaza con el mensaje del DNI repetido; si es suya (un registro previo del Jefe), la conserva;
 * si no existe, la crea con `create`, que solo funciona si el id está libre.
 */
async function escribirPerfilConDni(id, dni, perfil, opciones = {}) {
  const reserva = dnis.doc(dni);
  await bd.runTransaction(async (transaccion) => {
    const actual = await transaccion.get(reserva);
    if (actual.exists && actual.data().usuarioId !== id) throw new HttpsError('already-exists', MENSAJE_DNI_REPETIDO);
    if (!actual.exists) transaccion.create(reserva, { usuarioId: id });
    transaccion.set(usuarios.doc(id), perfil, opciones);
  });
}

/**
 * Indica si un cliente registrado por el Jefe sin cuenta ya usa el correo, sin distinguir mayúsculas. Los correos
 * de las cuentas los vigila Authentication, que tampoco distingue mayúsculas y rechaza los repetidos al crearlas.
 */
async function correoEnUso(correo, exceptoId) {
  const buscado = correo.toLowerCase();
  const sinCuenta = await usuarios.where('tieneCuenta', '==', false).get();
  return sinCuenta.docs.some((documento) => documento.id !== exceptoId && (documento.data().correo ?? '').toLowerCase() === buscado);
}

function validar(cuenta) {
  const error = validarCuenta(cuenta);
  if (error) throw new HttpsError('invalid-argument', error);
}

/**
 * Crea la cuenta en Authentication con los reclamos de la sesión y luego escribe el perfil con [escribirPerfil].
 * Si algo falla después de crear la cuenta, la elimina para no dejar una cuenta sin perfil.
 */
async function abrirCuenta({ cuenta, id, rol, mensajeCorreoRepetido, escribirPerfil }) {
  let registro;
  try {
    registro = await autenticacion.createUser({
      email: cuenta.correo,
      password: cuenta.contrasena,
      displayName: `${cuenta.nombres} ${cuenta.apellidos}`,
    });
  } catch (error) {
    if (error.code === 'auth/email-already-exists') throw new HttpsError('already-exists', mensajeCorreoRepetido);
    throw error;
  }
  try {
    await autenticacion.setCustomUserClaims(registro.uid, { usuarioId: id, rol });
    await escribirPerfil(registro.uid);
  } catch (error) {
    await autenticacion.deleteUser(registro.uid).catch(() => {});
    throw error;
  }
}

const camposDePerfil = (cuenta) => ({
  nombres: cuenta.nombres,
  apellidos: cuenta.apellidos,
  dni: cuenta.dni,
  telefono: cuenta.telefono,
  correo: cuenta.correo,
});

/**
 * Registro de un cliente (sin sesión). Si el Jefe ya lo había registrado con el mismo DNI y sin cuenta, completa
 * ese registro para conservar sus vehículos e historial; en otro caso crea uno nuevo.
 */
export const registrarCliente = onCall(async (request) => {
  const cuenta = normalizarCuenta(request.data);
  validar(cuenta);
  const previo = await usuarioPorDni(cuenta.dni);
  if (previo && (previo.datos.tieneCuenta || previo.datos.rol !== 'CLIENTE')) {
    throw new HttpsError('already-exists', MENSAJE_DNI_REPETIDO);
  }
  const mensajeCorreoRepetido = 'Este correo ya está registrado';
  if (await correoEnUso(cuenta.correo, previo?.id)) throw new HttpsError('already-exists', mensajeCorreoRepetido);
  const id = previo?.id ?? `u-cli-${usuarios.doc().id}`;
  await abrirCuenta({
    cuenta, id, rol: 'CLIENTE', mensajeCorreoRepetido,
    escribirPerfil: (uid) => escribirPerfilConDni(id, cuenta.dni, {
      ...camposDePerfil(cuenta), rol: 'CLIENTE', activo: true, tieneCuenta: true, authUid: uid,
      terminos: terminosDePersona(cuenta.nombres, cuenta.apellidos, cuenta.dni),
      acceso: FieldValue.arrayUnion(id),
    }, { merge: true }),
  });
  return { id };
});

/** Creación de un técnico por el Jefe, sin cerrar la sesión del Jefe. */
export const crearTecnico = onCall(async (request) => {
  exigirJefe(request);
  const cuenta = normalizarCuenta(request.data);
  validar(cuenta);
  if (await usuarioPorDni(cuenta.dni)) throw new HttpsError('already-exists', MENSAJE_DNI_REPETIDO);
  const mensajeCorreoRepetido = 'Ya existe un usuario con ese correo';
  if (await correoEnUso(cuenta.correo)) throw new HttpsError('already-exists', mensajeCorreoRepetido);
  const id = `u-tec-${usuarios.doc().id}`;
  await abrirCuenta({
    cuenta, id, rol: 'TECNICO', mensajeCorreoRepetido,
    escribirPerfil: (uid) => escribirPerfilConDni(id, cuenta.dni, {
      ...camposDePerfil(cuenta), rol: 'TECNICO', activo: true, tieneCuenta: true, authUid: uid, acceso: [id],
    }),
  });
  return { id };
});

function idRecibido(datos) {
  const id = typeof datos?.usuarioId === 'string' ? datos.usuarioId.trim() : '';
  if (!id) throw new HttpsError('invalid-argument', 'Falta el usuario');
  return id;
}

/**
 * Desactiva una cuenta: la inhabilita en Authentication y revoca sus sesiones, porque las reglas de Firestore no
 * miran `activo`. El Jefe desactiva cuentas ajenas; un cliente solo puede eliminar la suya, y la app ya le pidió
 * su contraseña. Un técnico con órdenes en curso no se desactiva y un cliente con vehículos en el taller no se elimina.
 */
export const desactivarCuenta = onCall(async (request) => {
  const quien = sesion(request);
  const usuarioId = idRecibido(request.data);
  const propia = quien.usuarioId === usuarioId;
  if (propia ? quien.rol !== 'CLIENTE' : quien.rol !== 'JEFE') {
    throw new HttpsError('permission-denied', 'No tienes permiso para desactivar esta cuenta');
  }
  const destino = await usuarios.doc(usuarioId).get();
  if (!destino.exists) throw new HttpsError('not-found', 'No se encontró el usuario');
  const perfil = destino.data();
  if (perfil.rol === 'JEFE') throw new HttpsError('permission-denied', 'No tienes permiso para desactivar esta cuenta');

  const campo = perfil.rol === 'TECNICO' ? 'tecnicoId' : 'clienteId';
  const ordenes = (await bd.collection('ordenes').where(campo, '==', usuarioId).get()).docs.map((d) => d.data());
  if (propia && ordenes.some((orden) => ESTADOS_EN_TALLER.includes(orden.estado))) {
    throw new HttpsError('failed-precondition', 'Tienes un vehículo en el taller. Podrás eliminar tu cuenta cuando se entregue');
  }
  if (perfil.rol === 'TECNICO' && ordenes.some((orden) => ESTADOS_EN_CURSO.includes(orden.estado))) {
    throw new HttpsError('failed-precondition', 'Reasigna sus órdenes en curso antes de desactivarlo');
  }

  await usuarios.doc(usuarioId).update({ activo: false });
  if (perfil.authUid) {
    await autenticacion.updateUser(perfil.authUid, { disabled: true });
    await autenticacion.revokeRefreshTokens(perfil.authUid);
  }
  return { id: usuarioId };
});

/** Vuelve a activar una cuenta desactivada; solo el Jefe. */
export const reactivarCuenta = onCall(async (request) => {
  exigirJefe(request);
  const usuarioId = idRecibido(request.data);
  const destino = await usuarios.doc(usuarioId).get();
  if (!destino.exists) throw new HttpsError('not-found', 'No se encontró el usuario');
  await usuarios.doc(usuarioId).update({ activo: true });
  const { authUid } = destino.data();
  if (authUid) await autenticacion.updateUser(authUid, { disabled: false });
  return { id: usuarioId };
});
