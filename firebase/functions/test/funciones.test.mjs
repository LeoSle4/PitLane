import assert from 'node:assert/strict';
import { before, beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';

// Prueba las funciones desplegadas en el emulador, con inicio de sesión real contra el emulador de Authentication.
const PROYECTO = 'demo-pitlane';
const REGION = 'southamerica-west1';
const HOST_AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099';
const HOST_FIRESTORE = process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080';
const CLAVE = 'Clave1234';

initializeApp({ projectId: PROYECTO });
const autenticacion = getAuth();
const bd = getFirestore();

/** Llama a una función como lo hace la app y devuelve `{ resultado }` o `{ error: { estado, mensaje } }`. */
async function llamar(nombre, data, token) {
  const respuesta = await fetch(`http://127.0.0.1:5001/${PROYECTO}/${REGION}/${nombre}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ data }),
  });
  const cuerpo = await respuesta.json();
  return cuerpo.error ? { error: { estado: cuerpo.error.status, mensaje: cuerpo.error.message } } : { resultado: cuerpo.result };
}

/** Inicia sesión en el emulador y devuelve el token, o nulo si la cuenta no puede entrar. */
async function iniciarSesion(correo, contrasena = CLAVE) {
  const respuesta = await fetch(`http://${HOST_AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=clave`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: correo, password: contrasena, returnSecureToken: true }),
  });
  const cuerpo = await respuesta.json();
  return cuerpo.idToken ?? null;
}

const reclamos = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString());

/** Crea una cuenta con sus reclamos y su perfil, como lo haría el servidor. */
async function crearCuenta(id, rol, correo, extra = {}) {
  const registro = await autenticacion.createUser({ email: correo, password: CLAVE });
  await autenticacion.setCustomUserClaims(registro.uid, { usuarioId: id, rol });
  await bd.collection('usuarios').doc(id).set({
    nombres: `Nombre ${id}`, apellidos: 'Apellido', dni: `9${id.replace(/\D/g, '').padStart(7, '0')}`, telefono: '987654321',
    correo, rol, activo: true, tieneCuenta: true, authUid: registro.uid, acceso: [id], ...extra,
  });
  return registro.uid;
}

const orden = (id, clienteId, tecnicoId, estado, extra = {}) =>
  bd.collection('ordenes').doc(id).set({
    numero: `ORD-000${id.replace(/\D/g, '')}`, clienteId, vehiculoId: 'v1', tecnicoId, estado, avisosEnviados: 0,
    historial: [], fotos: [], creadaEn: 1, acceso: [clienteId, tecnicoId], ...extra,
  });

const datosCliente = (extra = {}) => ({
  nombres: 'Ana', apellidos: 'Torres', dni: '12345678', telefono: '987654321', correo: 'ana@correo.com', contrasena: CLAVE, ...extra,
});

async function limpiar() {
  await fetch(`http://${HOST_FIRESTORE}/emulator/v1/projects/${PROYECTO}/databases/(default)/documents`, { method: 'DELETE' });
  await fetch(`http://${HOST_AUTH}/emulator/v1/projects/${PROYECTO}/accounts`, { method: 'DELETE' });
}

let tokenJefe;
before(limpiar);
beforeEach(async () => {
  await limpiar();
  await crearCuenta('j1', 'JEFE', 'jefe@correo.com');
  tokenJefe = await iniciarSesion('jefe@correo.com');
});

describe('registrarCliente', () => {
  it('crea la cuenta y el perfil, y la sesión lleva los reclamos del servidor', async () => {
    const { resultado, error } = await llamar('registrarCliente', datosCliente());
    assert.equal(error, undefined);
    assert.match(resultado.id, /^u-cli-/);

    const perfil = (await bd.collection('usuarios').doc(resultado.id).get()).data();
    assert.equal(perfil.rol, 'CLIENTE');
    assert.equal(perfil.activo, true);
    assert.equal(perfil.tieneCuenta, true);
    assert.deepEqual(perfil.acceso, [resultado.id]);
    assert.equal('contrasena' in perfil, false);

    const token = await iniciarSesion('ana@correo.com');
    assert.ok(token, 'la cuenta nueva debe poder iniciar sesión');
    assert.equal(reclamos(token).usuarioId, resultado.id);
    assert.equal(reclamos(token).rol, 'CLIENTE');
  });

  it('rechaza datos inválidos con el mensaje de la app', async () => {
    assert.deepEqual((await llamar('registrarCliente', datosCliente({ dni: '123' }))).error,
      { estado: 'INVALID_ARGUMENT', mensaje: 'El DNI debe tener 8 dígitos' });
    assert.equal((await llamar('registrarCliente', datosCliente({ contrasena: 'corta' }))).error.estado, 'INVALID_ARGUMENT');
    assert.equal((await llamar('registrarCliente', undefined)).error.estado, 'INVALID_ARGUMENT');
  });

  it('rechaza un DNI que ya tiene cuenta y un correo ya registrado', async () => {
    await llamar('registrarCliente', datosCliente());
    assert.deepEqual((await llamar('registrarCliente', datosCliente({ correo: 'otra@correo.com' }))).error,
      { estado: 'ALREADY_EXISTS', mensaje: 'Ya existe un usuario con ese DNI' });
    assert.deepEqual((await llamar('registrarCliente', datosCliente({ dni: '87654321' }))).error,
      { estado: 'ALREADY_EXISTS', mensaje: 'Este correo ya está registrado' });
  });

  it('rechaza el correo de otro usuario registrado por el Jefe sin cuenta', async () => {
    await bd.collection('usuarios').doc('u-cli-otro').set({
      dni: '55555555', correo: 'Ana@Correo.com', rol: 'CLIENTE', tieneCuenta: false, activo: true, acceso: ['u-cli-otro'],
    });
    assert.deepEqual((await llamar('registrarCliente', datosCliente())).error,
      { estado: 'ALREADY_EXISTS', mensaje: 'Este correo ya está registrado' });
  });

  it('une el registro previo del Jefe con el mismo DNI y conserva sus permisos', async () => {
    await bd.collection('usuarios').doc('u-cli-previo').set({
      nombres: 'Ana', apellidos: 'Torres', dni: '12345678', telefono: '987654321', correo: '', rol: 'CLIENTE',
      activo: true, tieneCuenta: false, acceso: ['u-cli-previo', 't1'],
    });
    const { resultado } = await llamar('registrarCliente', datosCliente());
    assert.equal(resultado.id, 'u-cli-previo');
    const perfil = (await bd.collection('usuarios').doc('u-cli-previo').get()).data();
    assert.equal(perfil.tieneCuenta, true);
    assert.equal(perfil.correo, 'ana@correo.com');
    assert.deepEqual([...perfil.acceso].sort(), ['t1', 'u-cli-previo']);
    assert.equal(reclamos(await iniciarSesion('ana@correo.com')).usuarioId, 'u-cli-previo');
  });
});

describe('crearTecnico', () => {
  it('solo lo hace el Jefe con sesión', async () => {
    assert.equal((await llamar('crearTecnico', datosCliente())).error.estado, 'UNAUTHENTICATED');
    await crearCuenta('c1', 'CLIENTE', 'c1@correo.com');
    const tokenCliente = await iniciarSesion('c1@correo.com');
    assert.equal((await llamar('crearTecnico', datosCliente(), tokenCliente)).error.estado, 'PERMISSION_DENIED');
  });

  it('crea el técnico sin cerrar la sesión del Jefe y con sus reclamos', async () => {
    const { resultado, error } = await llamar('crearTecnico', datosCliente({ correo: 'tec@correo.com' }), tokenJefe);
    assert.equal(error, undefined);
    assert.match(resultado.id, /^u-tec-/);
    const perfil = (await bd.collection('usuarios').doc(resultado.id).get()).data();
    assert.equal(perfil.rol, 'TECNICO');
    assert.deepEqual(perfil.acceso, [resultado.id]);
    const token = await iniciarSesion('tec@correo.com');
    assert.equal(reclamos(token).rol, 'TECNICO');
    assert.equal(reclamos(token).usuarioId, resultado.id);
  });

  it('rechaza DNI o correo repetidos con su mensaje', async () => {
    await llamar('crearTecnico', datosCliente({ correo: 'tec@correo.com' }), tokenJefe);
    assert.deepEqual((await llamar('crearTecnico', datosCliente({ correo: 'otro@correo.com' }), tokenJefe)).error,
      { estado: 'ALREADY_EXISTS', mensaje: 'Ya existe un usuario con ese DNI' });
    assert.deepEqual((await llamar('crearTecnico', datosCliente({ dni: '87654321', correo: 'tec@correo.com' }), tokenJefe)).error,
      { estado: 'ALREADY_EXISTS', mensaje: 'Ya existe un usuario con ese correo' });
  });
});

describe('desactivarCuenta y reactivarCuenta', () => {
  beforeEach(async () => {
    await crearCuenta('t1', 'TECNICO', 't1@correo.com');
    await crearCuenta('c1', 'CLIENTE', 'c1@correo.com');
  });

  it('el Jefe desactiva a un técnico: queda inactivo y ya no puede iniciar sesión', async () => {
    const { error } = await llamar('desactivarCuenta', { usuarioId: 't1' }, tokenJefe);
    assert.equal(error, undefined);
    assert.equal((await bd.collection('usuarios').doc('t1').get()).data().activo, false);
    assert.equal(await iniciarSesion('t1@correo.com'), null);
  });

  it('no desactiva a un técnico con órdenes en curso', async () => {
    await orden('o1', 'c1', 't1', 'EN_REPARACION');
    assert.deepEqual((await llamar('desactivarCuenta', { usuarioId: 't1' }, tokenJefe)).error,
      { estado: 'FAILED_PRECONDITION', mensaje: 'Reasigna sus órdenes en curso antes de desactivarlo' });
    assert.equal((await bd.collection('usuarios').doc('t1').get()).data().activo, true);
    await bd.collection('ordenes').doc('o1').update({ estado: 'LISTO' });
    assert.equal((await llamar('desactivarCuenta', { usuarioId: 't1' }, tokenJefe)).error, undefined);
  });

  it('un cliente elimina su propia cuenta cuando no tiene vehículos en el taller', async () => {
    const token = await iniciarSesion('c1@correo.com');
    await orden('o1', 'c1', 't1', 'LISTO');
    assert.deepEqual((await llamar('desactivarCuenta', { usuarioId: 'c1' }, token)).error,
      { estado: 'FAILED_PRECONDITION', mensaje: 'Tienes un vehículo en el taller. Podrás eliminar tu cuenta cuando se entregue' });
    await bd.collection('ordenes').doc('o1').update({ estado: 'ENTREGADO' });
    assert.equal((await llamar('desactivarCuenta', { usuarioId: 'c1' }, token)).error, undefined);
    assert.equal(await iniciarSesion('c1@correo.com'), null);
  });

  it('nadie desactiva cuentas que no le corresponden', async () => {
    const tokenCliente = await iniciarSesion('c1@correo.com');
    const tokenTecnico = await iniciarSesion('t1@correo.com');
    assert.equal((await llamar('desactivarCuenta', { usuarioId: 't1' }, tokenCliente)).error.estado, 'PERMISSION_DENIED');
    assert.equal((await llamar('desactivarCuenta', { usuarioId: 't1' }, tokenTecnico)).error.estado, 'PERMISSION_DENIED');
    assert.equal((await llamar('desactivarCuenta', { usuarioId: 'j1' }, tokenJefe)).error.estado, 'PERMISSION_DENIED');
    assert.equal((await llamar('desactivarCuenta', { usuarioId: 'nadie' }, tokenJefe)).error.estado, 'NOT_FOUND');
    assert.equal((await llamar('desactivarCuenta', {}, tokenJefe)).error.estado, 'INVALID_ARGUMENT');
    assert.equal((await llamar('desactivarCuenta', { usuarioId: 'c1' })).error.estado, 'UNAUTHENTICATED');
  });

  it('el Jefe reactiva una cuenta y vuelve a poder iniciar sesión; nadie más lo hace', async () => {
    await llamar('desactivarCuenta', { usuarioId: 't1' }, tokenJefe);
    const tokenCliente = await iniciarSesion('c1@correo.com');
    assert.equal((await llamar('reactivarCuenta', { usuarioId: 't1' }, tokenCliente)).error.estado, 'PERMISSION_DENIED');
    assert.equal((await llamar('reactivarCuenta', { usuarioId: 't1' }, tokenJefe)).error, undefined);
    assert.equal((await bd.collection('usuarios').doc('t1').get()).data().activo, true);
    assert.ok(await iniciarSesion('t1@correo.com'));
    assert.equal((await llamar('reactivarCuenta', { usuarioId: 'nadie' }, tokenJefe)).error.estado, 'NOT_FOUND');
  });
});

describe('verificarPlaca', () => {
  beforeEach(async () => {
    await crearCuenta('c1', 'CLIENTE', 'c1@correo.com');
    await bd.collection('vehiculos').doc('v1').set({ clienteId: 'c2', placa: 'ABC-123', activo: false });
  });

  it('al Jefe le dice quién es el dueño y al cliente solo que existe', async () => {
    assert.deepEqual((await llamar('verificarPlaca', { placa: 'ABC-123' }, tokenJefe)).resultado, { existe: true, clienteId: 'c2' });
    const tokenCliente = await iniciarSesion('c1@correo.com');
    assert.deepEqual((await llamar('verificarPlaca', { placa: 'ABC-123' }, tokenCliente)).resultado, { existe: true });
  });

  it('responde que no existe cuando nadie tiene la placa', async () => {
    assert.deepEqual((await llamar('verificarPlaca', { placa: 'ZZZ-999' }, tokenJefe)).resultado, { existe: false });
  });

  it('exige sesión y una placa', async () => {
    assert.equal((await llamar('verificarPlaca', { placa: 'ABC-123' })).error.estado, 'UNAUTHENTICATED');
    assert.equal((await llamar('verificarPlaca', { placa: ' ' }, tokenJefe)).error.estado, 'INVALID_ARGUMENT');
  });
});

describe('notificarOrden', () => {
  const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

  /** Espera a que el disparador cree las notificaciones esperadas del usuario, o falla pasado el tiempo. */
  async function notificaciones(usuarioId, cantidad) {
    for (let intento = 0; intento < 60; intento += 1) {
      const lista = (await bd.collection('notificaciones').where('usuarioId', '==', usuarioId).get()).docs
        .map((d) => d.data()).sort((a, b) => a.fechaHora - b.fechaHora);
      if (lista.length >= cantidad) return lista;
      await esperar(250);
    }
    throw new Error(`No llegaron ${cantidad} notificaciones`);
  }

  beforeEach(async () => {
    await crearCuenta('c1', 'CLIENTE', 'c1@correo.com');
    await bd.collection('vehiculos').doc('v1').set({ clienteId: 'c1', placa: 'ABC-123', activo: true });
  });

  it('avisa al cliente con cuenta el ingreso y cada cambio de estado, y no la entrega', async () => {
    await orden('o1', 'c1', 't1', 'INGRESADO');
    let lista = await notificaciones('c1', 1);
    assert.equal(lista[0].titulo, 'Vehículo ingresado');
    assert.equal(lista[0].mensaje, 'Tu vehículo ABC-123 fue ingresado. Orden ORD-0001');
    assert.equal(lista[0].esIngreso, true);
    assert.equal(lista[0].leida, false);
    assert.equal(lista[0].ordenId, 'o1');

    await bd.collection('ordenes').doc('o1').update({ estado: 'EN_REPARACION' });
    lista = await notificaciones('c1', 2);
    assert.equal(lista[1].titulo, 'Vehículo en reparación');
    assert.equal(lista[1].esIngreso, false);

    await bd.collection('ordenes').doc('o1').update({ estado: 'LISTO', avisosEnviados: 1 });
    lista = await notificaciones('c1', 3);
    assert.equal(lista[2].titulo, 'Vehículo listo');

    await bd.collection('ordenes').doc('o1').update({ avisosEnviados: 2 });
    lista = await notificaciones('c1', 4);
    assert.equal(lista[3].titulo, 'Vehículo listo');

    await bd.collection('ordenes').doc('o1').update({ estado: 'ENTREGADO' });
    await esperar(2000);
    assert.equal((await bd.collection('notificaciones').where('usuarioId', '==', 'c1').get()).size, 4);
  });

  it('no avisa a un cliente sin cuenta', async () => {
    await bd.collection('usuarios').doc('c3').set({ rol: 'CLIENTE', tieneCuenta: false, activo: true, acceso: ['c3'] });
    await bd.collection('vehiculos').doc('v1').update({ clienteId: 'c3' });
    await orden('o3', 'c3', 't1', 'INGRESADO');
    await esperar(2500);
    assert.equal((await bd.collection('notificaciones').get()).size, 0);
  });
});
