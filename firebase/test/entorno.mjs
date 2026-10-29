import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { readFileSync } from 'node:fs';
import { doc, setDoc, setLogLevel } from 'firebase/firestore';

setLogLevel('error');

const leer = (archivo) => readFileSync(new URL(`../${archivo}`, import.meta.url), 'utf8');

/**
 * Crea el entorno de pruebas con las reglas reales. El proyecto debe ser el mismo con el que arranca el emulador
 * (`demo-pitlane`): las reglas de Storage consultan el Firestore de ese proyecto.
 */
export function crearEntorno(proyecto) {
  return initializeTestEnvironment({
    projectId: proyecto,
    firestore: { rules: leer('firestore.rules') },
    storage: { rules: leer('storage.rules') },
  });
}

/** Sesiones de prueba con los reclamos que pondría el servidor: j1 Jefe, c1 y c2 clientes, t1 y t2 técnicos. */
export function sesiones(entorno) {
  const con = (uidAuth, usuarioId, rol) => entorno.authenticatedContext(uidAuth, { usuarioId, rol });
  return {
    jefe: con('auth-j1', 'j1', 'JEFE'),
    c1: con('auth-c1', 'c1', 'CLIENTE'),
    c2: con('auth-c2', 'c2', 'CLIENTE'),
    t1: con('auth-t1', 't1', 'TECNICO'),
    t2: con('auth-t2', 't2', 'TECNICO'),
    anonimo: entorno.unauthenticatedContext(),
    sinReclamos: entorno.authenticatedContext('auth-sin-reclamos', {}),
  };
}

export const perfil = (id, rol, acceso, extra = {}) => ({
  nombres: `Nombre ${id}`, apellidos: 'Apellido', dni: `1${id}`, telefono: '999111222', correo: `${id}@correo.com`,
  rol, activo: true, tieneCuenta: true, acceso, ...extra,
});

export const orden = (id, clienteId, tecnicoId, vehiculoId, extra = {}) => ({
  numero: `ORD-${id}`, clienteId, vehiculoId, solicitudId: null, falla: 'Batería descargada', kilometraje: 100,
  combustible: 'MEDIO', observaciones: '', fotos: [], tecnicoId, estado: 'INGRESADO', historial: [], creadaEn: 1,
  motivoAnulacion: null, avisosEnviados: 0, ultimoAviso: null, editadaPor: null, editadaEn: null, conformidad: null,
  observacionesCliente: [], acceso: [clienteId, tecnicoId], ...extra,
});

export const vehiculo = (clienteId, placa, acceso, extra = {}) => ({
  clienteId, placa, marca: 'Toyota', modelo: 'Yaris', anio: 2018, color: 'Rojo', activo: true, acceso, ...extra,
});

export const solicitud = (clienteId, vehiculoId, extra = {}) => ({
  clienteId, vehiculoId, descripcion: 'No arranca el motor', sintomas: [], fechaLlegada: 1, fotos: [],
  estado: 'PENDIENTE', creadaEn: 1, ordenId: null, acceso: [clienteId], ...extra,
});

export const avance = (ordenId, tecnicoId, acceso, extra = {}) => ({
  ordenId, tecnicoId, tipo: 'HALLAZGO', descripcion: 'Fusible quemado', fotos: [], fechaHora: 1, acceso, ...extra,
});

/**
 * Datos de partida. c1 tiene una orden con t1 y c2 otra con t2; c3 es un cliente sin cuenta.
 * El `acceso` de cada documento incluye a su dueño y al técnico que comparte una orden.
 */
export async function sembrar(entorno) {
  const datos = {
    'usuarios/j1': perfil('j1', 'JEFE', ['j1']),
    'usuarios/c1': perfil('c1', 'CLIENTE', ['c1', 't1']),
    'usuarios/c2': perfil('c2', 'CLIENTE', ['c2', 't2']),
    'usuarios/c3': perfil('c3', 'CLIENTE', ['c3'], { tieneCuenta: false }),
    'usuarios/t1': perfil('t1', 'TECNICO', ['t1', 'c1']),
    'usuarios/t2': perfil('t2', 'TECNICO', ['t2', 'c2']),
    'vehiculos/v1': vehiculo('c1', 'ABC123', ['c1', 't1']),
    'vehiculos/v2': vehiculo('c2', 'DEF456', ['c2', 't2']),
    'solicitudes/s1': solicitud('c1', 'v1'),
    'ordenes/o1': orden('0001', 'c1', 't1', 'v1'),
    'ordenes/o2': orden('0002', 'c2', 't2', 'v2'),
    'avances/a1': avance('o1', 't1', ['c1', 't1']),
    'notificaciones/n1': { usuarioId: 'c1', titulo: 'Vehículo ingresado', mensaje: 'm', fechaHora: 1, leida: false, ordenId: 'o1', esIngreso: true },
    'notificaciones/n2': { usuarioId: 'c2', titulo: 'Vehículo ingresado', mensaje: 'm', fechaHora: 1, leida: false, ordenId: 'o2', esIngreso: true },
    'contadores/ordenes': { ultimo: 1 },
  };
  await entorno.withSecurityRulesDisabled(async (contexto) => {
    const bd = contexto.firestore();
    for (const [ruta, valor] of Object.entries(datos)) await setDoc(doc(bd, ruta), valor);
  });
}

/** Cambia un documento saltándose las reglas, para preparar una situación concreta. */
export async function preparar(entorno, ruta, valor) {
  await entorno.withSecurityRulesDisabled(async (contexto) => {
    await setDoc(doc(contexto.firestore(), ruta), valor, { merge: true });
  });
}
