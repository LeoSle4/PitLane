import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  arrayUnion, collection, deleteDoc, doc, getDoc, getDocs, query, runTransaction, setDoc, updateDoc, where,
} from 'firebase/firestore';
import { after, before, beforeEach, describe, it } from 'node:test';
import { avance, crearEntorno, orden, perfil, preparar, sembrar, sesiones, solicitud, vehiculo } from './entorno.mjs';

let entorno;
let s;
const bd = (contexto) => contexto.firestore();
const ref = (contexto, ruta) => doc(bd(contexto), ruta);
const lista = (contexto, coleccion, ...condiciones) => getDocs(query(collection(bd(contexto), coleccion), ...condiciones));

before(async () => { entorno = await crearEntorno('demo-pitlane'); });
after(async () => { await entorno.cleanup(); });
beforeEach(async () => {
  await entorno.clearFirestore();
  await sembrar(entorno);
  s = sesiones(entorno);
});

describe('sin sesión válida', () => {
  it('nadie sin sesión lee ni escribe', async () => {
    await assertFails(getDoc(ref(s.anonimo, 'usuarios/c1')));
    await assertFails(setDoc(ref(s.anonimo, 'tokensDispositivo/c1'), { token: 'x' }));
  });

  it('una cuenta sin los reclamos del servidor no lee nada', async () => {
    await assertFails(getDoc(ref(s.sinReclamos, 'vehiculos/v1')));
    await assertFails(getDoc(ref(s.sinReclamos, 'usuarios/c1')));
  });
});

describe('usuarios', () => {
  it('el cliente lee su perfil y el del técnico de su orden, y ningún otro', async () => {
    await assertSucceeds(getDoc(ref(s.c1, 'usuarios/c1')));
    await assertSucceeds(getDoc(ref(s.c1, 'usuarios/t1')));
    await assertFails(getDoc(ref(s.c1, 'usuarios/c2')));
    await assertFails(getDoc(ref(s.c1, 'usuarios/t2')));
    await assertFails(getDoc(ref(s.c1, 'usuarios/j1')));
  });

  it('el técnico lee a sus clientes y no a los de otros', async () => {
    await assertSucceeds(getDoc(ref(s.t1, 'usuarios/c1')));
    await assertFails(getDoc(ref(s.t1, 'usuarios/c2')));
  });

  it('el cliente solo puede listar con el filtro de acceso y el Jefe lista todo', async () => {
    const propios = await assertSucceeds(lista(s.c1, 'usuarios', where('acceso', 'array-contains', 'c1')));
    if (propios.size !== 2) throw new Error(`Se esperaban 2 perfiles y llegaron ${propios.size}`);
    await assertFails(lista(s.c1, 'usuarios'));
    await assertSucceeds(lista(s.jefe, 'usuarios'));
  });

  it('cada usuario edita solo nombres, apellidos y teléfono de su perfil', async () => {
    await assertSucceeds(updateDoc(ref(s.c1, 'usuarios/c1'), { nombres: 'Nuevo', telefono: '988' }));
    await assertSucceeds(updateDoc(ref(s.t1, 'usuarios/t1'), { apellidos: 'Nuevo' }));
    await assertFails(updateDoc(ref(s.c1, 'usuarios/c1'), { rol: 'JEFE' }));
    await assertFails(updateDoc(ref(s.c1, 'usuarios/c1'), { activo: false }));
    await assertFails(updateDoc(ref(s.c1, 'usuarios/c1'), { acceso: ['c1', 'c2'] }));
    await assertFails(updateDoc(ref(s.c1, 'usuarios/c2'), { nombres: 'Intruso' }));
  });

  it('el Jefe edita datos de contacto pero no el rol ni el estado de la cuenta', async () => {
    await assertSucceeds(updateDoc(ref(s.jefe, 'usuarios/c1'), { telefono: '977' }));
    await assertFails(updateDoc(ref(s.jefe, 'usuarios/c1'), { rol: 'JEFE' }));
    await assertFails(updateDoc(ref(s.jefe, 'usuarios/c1'), { activo: false }));
    await assertFails(updateDoc(ref(s.jefe, 'usuarios/c1'), { tieneCuenta: false }));
  });

  it('el Jefe registra clientes sin cuenta como lo hace la app (set con merge y arrayUnion)', async () => {
    const { acceso, ...campos } = perfil('c9', 'CLIENTE', [], { tieneCuenta: false });
    await assertSucceeds(setDoc(ref(s.jefe, 'usuarios/c9'), { ...campos, acceso: arrayUnion('c9') }, { merge: true }));
  });

  it('el Jefe no puede crear técnicos, cuentas ni perfiles ajenos, y nadie más crea perfiles', async () => {
    await assertFails(setDoc(ref(s.jefe, 'usuarios/t9'), perfil('t9', 'TECNICO', ['t9'], { tieneCuenta: false })));
    await assertFails(setDoc(ref(s.jefe, 'usuarios/c9'), perfil('c9', 'CLIENTE', ['c9'])));
    await assertFails(setDoc(ref(s.jefe, 'usuarios/c9'), perfil('c9', 'CLIENTE', ['c9', 'c1'], { tieneCuenta: false })));
    await assertFails(setDoc(ref(s.c1, 'usuarios/c9'), perfil('c9', 'CLIENTE', ['c9'], { tieneCuenta: false })));
  });

  it('el Jefe solo elimina clientes sin cuenta', async () => {
    await assertSucceeds(deleteDoc(ref(s.jefe, 'usuarios/c3')));
    await assertFails(deleteDoc(ref(s.jefe, 'usuarios/c1')));
    await assertFails(deleteDoc(ref(s.jefe, 'usuarios/t1')));
    await assertFails(deleteDoc(ref(s.c1, 'usuarios/c3')));
  });
});

describe('vehiculos', () => {
  it('cada rol lee solo lo que le corresponde', async () => {
    await assertSucceeds(getDoc(ref(s.c1, 'vehiculos/v1')));
    await assertFails(getDoc(ref(s.c1, 'vehiculos/v2')));
    await assertSucceeds(getDoc(ref(s.t1, 'vehiculos/v1')));
    await assertFails(getDoc(ref(s.t1, 'vehiculos/v2')));
    await assertFails(getDoc(ref(s.t2, 'vehiculos/v1')));
    await assertSucceeds(getDoc(ref(s.jefe, 'vehiculos/v2')));
  });

  it('el cliente lista con el filtro de acceso y no la colección completa', async () => {
    await assertSucceeds(lista(s.c1, 'vehiculos', where('acceso', 'array-contains', 'c1')));
    await assertFails(lista(s.c1, 'vehiculos'));
  });

  it('el cliente registra su vehículo como lo hace la app (set con merge y arrayUnion)', async () => {
    const { acceso, ...campos } = vehiculo('c1', 'XYZ987', []);
    await assertSucceeds(setDoc(ref(s.c1, 'vehiculos/v9'), { ...campos, acceso: arrayUnion('c1') }, { merge: true }));
  });

  it('el cliente no registra vehículos a nombre de otro ni con permisos de más', async () => {
    await assertFails(setDoc(ref(s.c1, 'vehiculos/v9'), vehiculo('c2', 'XYZ987', ['c2'])));
    await assertFails(setDoc(ref(s.c1, 'vehiculos/v9'), vehiculo('c1', 'XYZ987', ['c1', 't1'])));
    await assertFails(setDoc(ref(s.c1, 'vehiculos/v9'), vehiculo('c1', 'XYZ987', ['c1'], { activo: false })));
    await assertFails(setDoc(ref(s.c1, 'vehiculos/v9'), vehiculo('c1', 'XYZ987', ['c1'], { extra: 1 })));
    await assertFails(setDoc(ref(s.t1, 'vehiculos/v9'), vehiculo('c1', 'XYZ987', ['c1'])));
  });

  it('el Jefe registra un vehículo a nombre de un cliente', async () => {
    await assertSucceeds(setDoc(ref(s.jefe, 'vehiculos/v9'), vehiculo('c2', 'XYZ987', ['c2'])));
  });

  it('el cliente edita marca, modelo, año y color de su vehículo, y no la placa ni el dueño', async () => {
    await assertSucceeds(updateDoc(ref(s.c1, 'vehiculos/v1'), { marca: 'Kia', anio: 2020 }));
    await assertSucceeds(updateDoc(ref(s.c1, 'vehiculos/v1'), { activo: false }));
    await assertFails(updateDoc(ref(s.c1, 'vehiculos/v1'), { placa: 'ZZZ999' }));
    await assertFails(updateDoc(ref(s.c1, 'vehiculos/v1'), { clienteId: 'c2' }));
    await assertFails(updateDoc(ref(s.c1, 'vehiculos/v2'), { marca: 'Kia' }));
    await assertFails(updateDoc(ref(s.t1, 'vehiculos/v1'), { marca: 'Kia' }));
  });

  it('el Jefe agrega a un técnico al acceso de un vehículo', async () => {
    await assertSucceeds(updateDoc(ref(s.jefe, 'vehiculos/v1'), { acceso: arrayUnion('t2') }));
    await assertFails(updateDoc(ref(s.jefe, 'vehiculos/v1'), { placa: 'ZZZ999' }));
  });

  it('el cliente elimina solo su vehículo, el Jefe cualquiera y el técnico ninguno', async () => {
    await assertSucceeds(deleteDoc(ref(s.c1, 'vehiculos/v1')));
    await assertFails(deleteDoc(ref(s.c1, 'vehiculos/v2')));
    await assertFails(deleteDoc(ref(s.t2, 'vehiculos/v2')));
    await assertSucceeds(deleteDoc(ref(s.jefe, 'vehiculos/v2')));
  });
});

describe('solicitudes', () => {
  it('la ve su cliente y el Jefe, no otro cliente ni el técnico', async () => {
    await assertSucceeds(getDoc(ref(s.c1, 'solicitudes/s1')));
    await assertSucceeds(getDoc(ref(s.jefe, 'solicitudes/s1')));
    await assertFails(getDoc(ref(s.c2, 'solicitudes/s1')));
    await assertFails(getDoc(ref(s.t1, 'solicitudes/s1')));
  });

  it('el cliente crea solicitudes pendientes propias', async () => {
    await assertSucceeds(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1')));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1', { estado: 'ATENDIDA' })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c2', 'v2')));
    await assertFails(setDoc(ref(s.t1, 'solicitudes/s9'), solicitud('c1', 'v1')));
  });

  it('el cliente edita y cancela su solicitud pendiente, sin atenderla él mismo', async () => {
    await assertSucceeds(updateDoc(ref(s.c1, 'solicitudes/s1'), { descripcion: 'Ahora también hace ruido' }));
    await assertFails(updateDoc(ref(s.c1, 'solicitudes/s1'), { estado: 'ATENDIDA' }));
    await assertFails(updateDoc(ref(s.c1, 'solicitudes/s1'), { ordenId: 'o1' }));
    await assertFails(updateDoc(ref(s.c2, 'solicitudes/s1'), { descripcion: 'Intruso' }));
    await assertSucceeds(updateDoc(ref(s.c1, 'solicitudes/s1'), { estado: 'CANCELADA' }));
  });

  it('una solicitud que ya no está pendiente no se puede modificar', async () => {
    await preparar(entorno, 'solicitudes/s2', solicitud('c1', 'v1', { estado: 'ATENDIDA', ordenId: 'o1' }));
    await assertFails(updateDoc(ref(s.c1, 'solicitudes/s2'), { descripcion: 'Tarde' }));
    await assertFails(updateDoc(ref(s.jefe, 'solicitudes/s2'), { estado: 'CANCELADA' }));
  });

  it('el Jefe la marca atendida o cancelada pero no cambia su contenido', async () => {
    await assertSucceeds(updateDoc(ref(s.jefe, 'solicitudes/s1'), { estado: 'ATENDIDA', ordenId: 'o1' }));
    await assertFails(updateDoc(ref(s.jefe, 'solicitudes/s1'), { descripcion: 'Reescrita' }));
  });

  it('solo el Jefe las elimina', async () => {
    await assertFails(deleteDoc(ref(s.c1, 'solicitudes/s1')));
    await assertSucceeds(deleteDoc(ref(s.jefe, 'solicitudes/s1')));
  });
});

describe('ordenes', () => {
  it('cada rol lee solo las órdenes que le corresponden', async () => {
    await assertSucceeds(getDoc(ref(s.c1, 'ordenes/o1')));
    await assertFails(getDoc(ref(s.c1, 'ordenes/o2')));
    await assertSucceeds(getDoc(ref(s.t1, 'ordenes/o1')));
    await assertFails(getDoc(ref(s.t1, 'ordenes/o2')));
    await assertSucceeds(getDoc(ref(s.jefe, 'ordenes/o2')));
  });

  it('el técnico lista con el filtro de acceso y recibe solo las suyas', async () => {
    const suyas = await assertSucceeds(lista(s.t1, 'ordenes', where('acceso', 'array-contains', 't1')));
    if (suyas.size !== 1) throw new Error(`Se esperaba 1 orden y llegaron ${suyas.size}`);
    await assertFails(lista(s.t1, 'ordenes'));
    await assertFails(lista(s.t1, 'ordenes', where('tecnicoId', '==', 't1')));
  });

  it('solo el Jefe crea órdenes, Ingresadas y con el acceso del cliente y del técnico', async () => {
    // Cada intento usa un id nuevo: repetir el id sería una actualización, no una creación.
    await assertSucceeds(setDoc(ref(s.jefe, 'ordenes/o9'), orden('0009', 'c1', 't1', 'v1')));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o8'), orden('0008', 'c1', 't1', 'v1', { acceso: ['c1'] })));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o7'), orden('0007', 'c1', 't1', 'v1', { estado: 'LISTO' })));
    await assertFails(setDoc(ref(s.c1, 'ordenes/o6'), orden('0006', 'c1', 't1', 'v1')));
    await assertFails(setDoc(ref(s.t1, 'ordenes/o5'), orden('0005', 'c1', 't1', 'v1')));
  });

  it('el técnico asignado inicia la reparación y marca la orden como lista, en ese orden', async () => {
    const historial = (estado) => [{ estado, fechaHora: 2, usuarioId: 't1', motivo: null }];
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'LISTO', historial: historial('LISTO') }));
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', historial: historial('EN_REPARACION') }));
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o1'), {
      estado: 'LISTO', historial: historial('LISTO'), avisosEnviados: 1, ultimoAviso: 5,
    }));
  });

  it('el técnico no edita la orden, no la reasigna y no toca la de otro técnico', async () => {
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { falla: 'Otra falla' }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { tecnicoId: 't2', acceso: ['c1', 't2'] }));
    await assertFails(updateDoc(ref(s.t2, 'ordenes/o1'), { estado: 'EN_REPARACION' }));
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), { estado: 'EN_REPARACION' }));
  });

  it('el Jefe corrige la orden y la reasigna actualizando el acceso, sin cambiar su cliente ni vehículo', async () => {
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida' }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { tecnicoId: 't2', acceso: ['c1', 't2'] }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o2'), { tecnicoId: 't1' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o2'), { clienteId: 'c1', acceso: ['c1', 't2'] }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o2'), { numero: 'ORD-9999' }));
  });

  it('el cliente firma su conformidad solo en su orden lista, una vez y con su propio usuario', async () => {
    const conformidad = (usuarioId) => ({ conformidad: { fechaHora: 9, usuarioId, usuarioNombre: 'Nombre' } });
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), conformidad('c1')));
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO' });
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), conformidad('j1')));
    await assertFails(updateDoc(ref(s.c2, 'ordenes/o1'), conformidad('c2')));
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), { ...conformidad('c1'), estado: 'ENTREGADO' }));
    await assertSucceeds(updateDoc(ref(s.c1, 'ordenes/o1'), conformidad('c1')));
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), conformidad('c1')));
  });

  it('nadie elimina órdenes', async () => {
    await assertFails(deleteDoc(ref(s.jefe, 'ordenes/o1')));
    await assertFails(deleteDoc(ref(s.c1, 'ordenes/o1')));
  });
});

describe('avances', () => {
  it('los leen el cliente de la orden, su técnico y el Jefe', async () => {
    await assertSucceeds(getDoc(ref(s.c1, 'avances/a1')));
    await assertSucceeds(getDoc(ref(s.t1, 'avances/a1')));
    await assertSucceeds(getDoc(ref(s.jefe, 'avances/a1')));
    await assertFails(getDoc(ref(s.c2, 'avances/a1')));
    await assertFails(getDoc(ref(s.t2, 'avances/a1')));
  });

  it('el técnico registra avances en su orden con el mismo acceso que ella', async () => {
    await assertSucceeds(setDoc(ref(s.t1, 'avances/a9'), avance('o1', 't1', ['c1', 't1'])));
    await assertFails(setDoc(ref(s.t1, 'avances/a9'), avance('o1', 't1', ['t1'])));
    await assertFails(setDoc(ref(s.t1, 'avances/a9'), avance('o2', 't1', ['c2', 't2'])));
    await assertFails(setDoc(ref(s.t1, 'avances/a9'), avance('o1', 't2', ['c1', 't1'])));
    await assertFails(setDoc(ref(s.t2, 'avances/a9'), avance('o1', 't2', ['c1', 't1'])));
  });

  it('ni el cliente ni el Jefe registran avances', async () => {
    await assertFails(setDoc(ref(s.c1, 'avances/a9'), avance('o1', 't1', ['c1', 't1'])));
    await assertFails(setDoc(ref(s.jefe, 'avances/a9'), avance('o1', 't1', ['c1', 't1'])));
  });

  it('el técnico edita y elimina sus avances; el Jefe solo actualiza el acceso', async () => {
    await assertSucceeds(updateDoc(ref(s.t1, 'avances/a1'), { descripcion: 'Fusible cambiado' }));
    await assertFails(updateDoc(ref(s.t1, 'avances/a1'), { ordenId: 'o2' }));
    await assertFails(updateDoc(ref(s.t2, 'avances/a1'), { descripcion: 'Intruso' }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'avances/a1'), { acceso: ['c1', 't2'] }));
    await assertFails(updateDoc(ref(s.jefe, 'avances/a1'), { descripcion: 'Reescrito' }));
    await assertFails(deleteDoc(ref(s.t2, 'avances/a1')));
    await assertFails(deleteDoc(ref(s.jefe, 'avances/a1')));
    await assertSucceeds(deleteDoc(ref(s.t1, 'avances/a1')));
  });

  it('un técnico que dejó de tener la orden ya no modifica sus avances anteriores', async () => {
    await preparar(entorno, 'ordenes/o1', { tecnicoId: 't2', acceso: ['c1', 't2'] });
    await assertFails(updateDoc(ref(s.t1, 'avances/a1'), { descripcion: 'Tarde' }));
    await assertFails(deleteDoc(ref(s.t1, 'avances/a1')));
  });
});

describe('notificaciones', () => {
  it('cada usuario lee solo las suyas, incluido el Jefe', async () => {
    await assertSucceeds(getDoc(ref(s.c1, 'notificaciones/n1')));
    await assertFails(getDoc(ref(s.c1, 'notificaciones/n2')));
    await assertFails(getDoc(ref(s.jefe, 'notificaciones/n1')));
    await assertSucceeds(lista(s.c1, 'notificaciones', where('usuarioId', '==', 'c1')));
    await assertFails(lista(s.c1, 'notificaciones'));
  });

  it('el usuario solo puede marcar como leída una notificación suya', async () => {
    await assertSucceeds(updateDoc(ref(s.c1, 'notificaciones/n1'), { leida: true }));
    await assertFails(updateDoc(ref(s.c1, 'notificaciones/n1'), { leida: false }));
    await assertFails(updateDoc(ref(s.c1, 'notificaciones/n1'), { titulo: 'Cambiado' }));
    await assertFails(updateDoc(ref(s.c1, 'notificaciones/n2'), { leida: true }));
  });

  it('nadie crea ni elimina notificaciones: lo hace el servidor', async () => {
    const nueva = { usuarioId: 'c1', titulo: 't', mensaje: 'm', fechaHora: 1, leida: false, ordenId: null, esIngreso: false };
    await assertFails(setDoc(ref(s.c1, 'notificaciones/n9'), nueva));
    await assertFails(setDoc(ref(s.jefe, 'notificaciones/n9'), nueva));
    await assertFails(deleteDoc(ref(s.c1, 'notificaciones/n1')));
  });
});

describe('contadores', () => {
  it('el Jefe suma uno al contador en una transacción, como lo hace la app', async () => {
    await assertSucceeds(runTransaction(bd(s.jefe), async (t) => {
      const actual = await t.get(doc(bd(s.jefe), 'contadores/ordenes'));
      t.set(doc(bd(s.jefe), 'contadores/ordenes'), { ultimo: actual.data().ultimo + 1 });
    }));
  });

  it('el contador no admite saltos, retrocesos ni otros roles', async () => {
    await assertFails(setDoc(ref(s.jefe, 'contadores/ordenes'), { ultimo: 5 }));
    await assertFails(setDoc(ref(s.jefe, 'contadores/ordenes'), { ultimo: 1 }));
    await assertFails(deleteDoc(ref(s.jefe, 'contadores/ordenes')));
    await assertFails(getDoc(ref(s.c1, 'contadores/ordenes')));
    await assertFails(setDoc(ref(s.t1, 'contadores/ordenes'), { ultimo: 2 }));
  });

  it('el primer número de orden es el 1', async () => {
    await entorno.withSecurityRulesDisabled(async (c) => { await deleteDoc(doc(c.firestore(), 'contadores/ordenes')); });
    await assertFails(setDoc(ref(s.jefe, 'contadores/ordenes'), { ultimo: 3 }));
    await assertSucceeds(setDoc(ref(s.jefe, 'contadores/ordenes'), { ultimo: 1 }));
  });
});

describe('tokensDispositivo', () => {
  it('cada usuario guarda solo su token y nadie lo lee desde la app', async () => {
    await assertSucceeds(setDoc(ref(s.c1, 'tokensDispositivo/c1'), { token: 'abc' }, { merge: true }));
    await assertFails(setDoc(ref(s.c1, 'tokensDispositivo/c2'), { token: 'abc' }));
    await assertFails(setDoc(ref(s.c1, 'tokensDispositivo/c1'), { token: 123 }));
    await assertFails(setDoc(ref(s.c1, 'tokensDispositivo/c1'), { token: 'abc', extra: 1 }));
    await assertFails(getDoc(ref(s.c1, 'tokensDispositivo/c1')));
  });
});

describe('codigosRestablecimiento', () => {
  it('nadie lee ni escribe los códigos desde la app, ni siquiera el Jefe', async () => {
    await preparar(entorno, 'codigosRestablecimiento/x', { hash: 'abc', expira: 1, intentos: 0, enviadoEn: 1 });
    await assertFails(getDoc(ref(s.jefe, 'codigosRestablecimiento/x')));
    await assertFails(getDoc(ref(s.anonimo, 'codigosRestablecimiento/x')));
    await assertFails(setDoc(ref(s.c1, 'codigosRestablecimiento/y'), { hash: 'abc' }));
    await assertFails(lista(s.jefe, 'codigosRestablecimiento'));
  });
});
