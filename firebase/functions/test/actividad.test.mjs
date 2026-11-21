import assert from 'node:assert/strict';
import { before, beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

// Prueba los disparadores de `ultimaActividad` desplegados en el emulador de funciones.
const PROYECTO = 'demo-pitlane';
const HOST_FIRESTORE = process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080';

initializeApp({ projectId: PROYECTO });
const bd = getFirestore();
const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

async function limpiar() {
  await fetch(`http://${HOST_FIRESTORE}/emulator/v1/projects/${PROYECTO}/databases/(default)/documents`, { method: 'DELETE' });
}

/** Espera a que el cliente tenga `esperada` como `ultimaActividad`, o falla pasado el tiempo. */
async function actividad(clienteId, esperada) {
  let valor;
  for (let intento = 0; intento < 60; intento += 1) {
    valor = (await bd.collection('usuarios').doc(clienteId).get()).data()?.ultimaActividad;
    if (valor === esperada) return valor;
    await esperar(250);
  }
  throw new Error(`ultimaActividad de ${clienteId} era ${valor} y se esperaba ${esperada}`);
}

/** Da tiempo a que un disparador que no debe escribir termine, y lee el valor. */
const sinCambio = async (clienteId) => {
  await esperar(2500);
  return (await bd.collection('usuarios').doc(clienteId).get()).data()?.ultimaActividad;
};

const cliente = (id, extra = {}) =>
  bd.collection('usuarios').doc(id).set({ rol: 'CLIENTE', activo: true, tieneCuenta: false, acceso: [id], ...extra });

const orden = (id, clienteId, extra = {}) =>
  bd.collection('ordenes').doc(id).set({
    numero: `ORD-${id}`, clienteId, vehiculoId: 'v1', tecnicoId: 't1', estado: 'INGRESADO', avisosEnviados: 0,
    historial: [], fotos: [], creadaEn: 1_000, acceso: [clienteId, 't1'], ...extra,
  });

const solicitud = (id, clienteId, creadaEn) =>
  bd.collection('solicitudes').doc(id).set({ clienteId, vehiculoId: 'v1', descripcion: 'Falla', creadaEn, acceso: [clienteId] });

before(limpiar);
beforeEach(limpiar);

describe('ultimaActividad del cliente', () => {
  it('una orden nueva la pone en su creación y cada cambio de estado la sube al último del historial', async () => {
    await cliente('c1');
    await orden('o1', 'c1');
    await actividad('c1', 1_000);

    await bd.collection('ordenes').doc('o1').update({
      estado: 'EN_REPARACION', historial: [{ estado: 'EN_REPARACION', fechaHora: 5_000, usuarioId: 't1' }],
    });
    await actividad('c1', 5_000);
    await bd.collection('ordenes').doc('o1').update({
      estado: 'LISTO', historial: [
        { estado: 'EN_REPARACION', fechaHora: 5_000, usuarioId: 't1' },
        { estado: 'LISTO', fechaHora: 7_000, usuarioId: 't1' },
      ],
    });
    await actividad('c1', 7_000);
  });

  it('un evento más antiguo nunca la baja', async () => {
    await cliente('c1');
    await orden('o1', 'c1', { historial: [{ estado: 'EN_REPARACION', fechaHora: 9_000, usuarioId: 't1' }] });
    await actividad('c1', 9_000);
    // Otra orden más antigua llega después
    await orden('o2', 'c1', { creadaEn: 2_000 });
    await bd.collection('ordenes').doc('o2').update({ avisosEnviados: 1 });
    assert.equal(await sinCambio('c1'), 9_000);
  });

  it('una solicitud la fija en su creación y la actividad es el máximo entre órdenes y solicitudes', async () => {
    await cliente('c1');
    await solicitud('s1', 'c1', 3_000);
    await actividad('c1', 3_000);
    await orden('o1', 'c1', { creadaEn: 4_000 });
    await actividad('c1', 4_000);
    await solicitud('s2', 'c1', 2_000);
    assert.equal(await sinCambio('c1'), 4_000);
  });

  it('también se actualiza en un cliente sin cuenta', async () => {
    await cliente('c3', { tieneCuenta: false });
    await orden('o3', 'c3', { creadaEn: 6_000 });
    await actividad('c3', 6_000);
  });

  it('borrar una solicitud no la baja', async () => {
    await cliente('c1');
    await solicitud('s1', 'c1', 3_000);
    await actividad('c1', 3_000);
    await bd.collection('solicitudes').doc('s1').delete();
    assert.equal(await sinCambio('c1'), 3_000);
  });

  it('no hace nada si el usuario no es un cliente o no existe', async () => {
    await bd.collection('usuarios').doc('t1').set({ rol: 'TECNICO', activo: true, tieneCuenta: true, acceso: ['t1'] });
    await orden('o1', 't1');
    await solicitud('s1', 't1', 3_000);
    await orden('o2', 'fantasma');
    await solicitud('s2', 'fantasma', 3_000);
    await esperar(2500);
    assert.equal((await bd.collection('usuarios').doc('t1').get()).data().ultimaActividad, undefined);
    assert.equal((await bd.collection('usuarios').doc('fantasma').get()).exists, false);
  });
});
