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

/** Instantáneas que el ingreso guarda en la orden y la solicitud; coinciden con los perfiles y vehículos sembrados. */
export const resumenCliente = (id) => ({ nombres: `Nombre ${id}`, apellidos: 'Apellido', dni: `1${id}`, telefono: '999111222' });
export const resumenVehiculo = (placa = 'ABC123') => ({ placa, marca: 'Toyota', modelo: 'Yaris', anio: 2018, color: 'Rojo' });
export const resumenTecnico = (id) => ({ nombres: `Nombre ${id}`, apellidos: 'Apellido' });

/** Quita las instantáneas: así eran los documentos anteriores al relleno (`rellenarInstantaneas.mjs`). */
export const sinInstantaneas = ({ cliente, vehiculo, tecnico, esquema, ...resto }) => resto;

/** Quita solo la versión de esquema: un documento con instantáneas pero anterior a `esquema` (versión 1). */
export const sinEsquema = ({ esquema, ...resto }) => resto;

/**
 * La hora de la prueba. Las reglas exigen que toda fecha que la app escribe esté cerca de `request.time` (S4); una
 * sola constante mantiene iguales las entradas del historial que se reenvían. Los datos sembrados guardan fechas
 * antiguas a propósito: una fecha que la escritura no cambia no se comprueba.
 */
export const AHORA = Date.now();

/** Una entrada del historial de una orden, como la escribe la app (`CambioEstado`). */
export const cambio = (estado, usuarioId, fechaHora, extra = {}) => ({
  estado, fechaHora, usuarioId, motivo: null, usuarioNombre: 'Nombre', ...extra,
});

/** Historial con el que nace toda orden: el ingreso firmado por el Jefe. */
export const historialInicial = () => [cambio('INGRESADO', 'j1', AHORA)];

export const orden = (id, clienteId, tecnicoId, vehiculoId, extra = {}) => ({
  numero: `ORD-${id}`, clienteId, vehiculoId, solicitudId: null, falla: 'Batería descargada', kilometraje: 100,
  combustible: 'MEDIO', observaciones: '', fotos: [], tecnicoId, estado: 'INGRESADO', historial: historialInicial(), creadaEn: AHORA,
  motivoAnulacion: null, avisosEnviados: 0, ultimoAviso: null, editadaPor: null, editadaEn: null, conformidad: null,
  observacionesCliente: [], acceso: [clienteId, tecnicoId],
  cliente: resumenCliente(clienteId), vehiculo: resumenVehiculo(), tecnico: resumenTecnico(tecnicoId), esquema: 2, ...extra,
});

export const vehiculo = (clienteId, placa, acceso, extra = {}) => ({
  clienteId, placa, marca: 'Toyota', modelo: 'Yaris', anio: 2018, color: 'Rojo', activo: true, acceso, ...extra,
});

export const solicitud = (clienteId, vehiculoId, extra = {}) => ({
  clienteId, vehiculoId, descripcion: 'No arranca el motor', sintomas: [], fechaLlegada: 1, fotos: [],
  estado: 'PENDIENTE', creadaEn: AHORA, ordenId: null, acceso: [clienteId],
  cliente: resumenCliente(clienteId), vehiculo: resumenVehiculo(), esquema: 2, ...extra,
});

/** Un avance vive en la subcolección de su orden, `ordenes/{ordenId}/avances/{id}`: la ruta dice de qué orden es. */
export const avance = (tecnicoId, extra = {}) => ({
  tecnicoId, tipo: 'HALLAZGO', descripcion: 'Fusible quemado', fotos: [], fechaHora: AHORA, ...extra,
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
    // Las fechas sembradas son antiguas (1): como no las cambia ninguna escritura, las reglas no las comprueban
    'solicitudes/s1': solicitud('c1', 'v1', { creadaEn: 1 }),
    'ordenes/o1': orden('0001', 'c1', 't1', 'v1', { creadaEn: 1 }),
    // o2 es anterior al relleno: no tiene instantáneas y las actualizaciones deben seguir funcionando
    'ordenes/o2': sinInstantaneas(orden('0002', 'c2', 't2', 'v2', { creadaEn: 1 })),
    'numerosOrden/ORD-0001': { ordenId: 'o1' },
    'numerosOrden/ORD-0002': { ordenId: 'o2' },
    'ordenes/o1/avances/a1': avance('t1', { fechaHora: 1 }),
    'usuarios/c1/notificaciones/n1': { usuarioId: 'c1', titulo: 'Vehículo ingresado', mensaje: 'm', fechaHora: 1, leida: false, ordenId: 'o1', esIngreso: true },
    'usuarios/c2/notificaciones/n2': { usuarioId: 'c2', titulo: 'Vehículo ingresado', mensaje: 'm', fechaHora: 1, leida: false, ordenId: 'o2', esIngreso: true },
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
