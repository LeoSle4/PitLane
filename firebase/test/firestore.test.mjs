import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  arrayUnion, collection, deleteDoc, doc, getDoc, getDocs, query, runTransaction, setDoc, Timestamp, updateDoc, where, writeBatch,
} from 'firebase/firestore';
import { after, before, beforeEach, describe, it } from 'node:test';
import {
  avance, crearEntorno, orden, perfil, preparar, resumenCliente, resumenTecnico, resumenVehiculo, sembrar, sesiones,
  sinInstantaneas, solicitud, vehiculo,
} from './entorno.mjs';

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
    await assertFails(setDoc(ref(s.anonimo, 'usuarios/c1/tokensDispositivo/x'), { token: 'x', expiraEn: Timestamp.fromMillis(Date.now() + 86_400_000) }));
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

  // La app escribe el vehículo y su reserva `placas/{placa}` en un mismo lote
  const reserva = (vehiculoId, clienteId) => ({ vehiculoId, clienteId });
  const registrar = (contexto, id, datos, placa = datos.placa, reservaDatos = reserva(id, datos.clienteId)) => {
    const lote = writeBatch(bd(contexto));
    lote.set(ref(contexto, `vehiculos/${id}`), datos, { merge: true });
    lote.set(ref(contexto, `placas/${placa}`), reservaDatos);
    return lote.commit();
  };

  it('el cliente registra su vehículo como lo hace la app (lote con merge, arrayUnion y la reserva de la placa)', async () => {
    const { acceso, ...campos } = vehiculo('c1', 'XYZ-987', ['c1']);
    const lote = writeBatch(bd(s.c1));
    lote.set(ref(s.c1, 'vehiculos/v9'), { ...campos, acceso: arrayUnion('c1') }, { merge: true });
    lote.set(ref(s.c1, 'placas/XYZ-987'), reserva('v9', 'c1'));
    await assertSucceeds(lote.commit());
  });

  it('el cliente no registra vehículos a nombre de otro ni con permisos de más', async () => {
    await assertFails(registrar(s.c1, 'v9', vehiculo('c2', 'XYZ-987', ['c2'])));
    await assertFails(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1', 't1'])));
    await assertFails(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1'], { activo: false })));
    await assertFails(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1'], { extra: 1 })));
    await assertFails(registrar(s.t1, 'v9', vehiculo('c1', 'XYZ-987', ['c1'])));
  });

  it('el Jefe registra un vehículo a nombre de un cliente', async () => {
    await assertSucceeds(registrar(s.jefe, 'v9', vehiculo('c2', 'XYZ-987', ['c2'])));
  });

  describe('placa única (placas/{placa})', () => {
    it('el vehículo sin su reserva se deniega, también al Jefe', async () => {
      await assertFails(setDoc(ref(s.c1, 'vehiculos/v9'), vehiculo('c1', 'XYZ-987', ['c1'])));
      await assertFails(setDoc(ref(s.jefe, 'vehiculos/v9'), vehiculo('c1', 'XYZ-987', ['c1'])));
    });

    it('la reserva sin su vehículo se deniega', async () => {
      await assertFails(setDoc(ref(s.c1, 'placas/XYZ-987'), reserva('v9', 'c1')));
      await assertFails(setDoc(ref(s.jefe, 'placas/XYZ-987'), reserva('v9', 'c1')));
    });

    it('una segunda unidad con una placa ya reservada se deniega', async () => {
      await assertSucceeds(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1'])));
      await assertFails(registrar(s.c2, 'v10', vehiculo('c2', 'XYZ-987', ['c2'])));
      await assertFails(registrar(s.jefe, 'v10', vehiculo('c2', 'XYZ-987', ['c2'])));
    });

    it('la placa solo admite el formato que produce la app: tres caracteres, guion y tres caracteres en mayúsculas', async () => {
      const intenta = (placa, id) => registrar(s.c1, id, vehiculo('c1', placa, ['c1']), placa, reserva(id, 'c1'));
      // Una placa con espacio al final conviviría con la misma sin espacio y rompería la unicidad
      for (const [i, mala] of ['XYZ-987 ', ' XYZ-987', 'XYZ 987', 'XYZ987', 'XY-9876', 'ÑYZ-987', 'XYZ-98', 'X'.repeat(80)].entries()) {
        await assertFails(intenta(mala, `vx${i}`));
      }
      await assertSucceeds(intenta('XYZ-987', 'vok'));
    });

    it('la placa debe estar en mayúsculas y coincidir con el id de la reserva', async () => {
      await assertFails(registrar(s.c1, 'v9', vehiculo('c1', 'xyz-987', ['c1'])));
      await assertFails(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1']), 'OTRA-111'));
    });

    it('la reserva debe apuntar a ese vehículo y a su dueño', async () => {
      await assertFails(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1']), 'XYZ-987', reserva('v8', 'c1')));
      await assertFails(registrar(s.jefe, 'v9', vehiculo('c1', 'XYZ-987', ['c1']), 'XYZ-987', reserva('v9', 'c2')));
    });

    it('la reserva solo lleva vehiculoId y clienteId', async () => {
      await assertFails(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1']), 'XYZ-987', { ...reserva('v9', 'c1'), extra: 1 }));
      await assertFails(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1']), 'XYZ-987', { vehiculoId: 'v9' }));
    });

    it('un cliente no reserva una placa a nombre de otro cliente', async () => {
      await assertFails(registrar(s.c1, 'v9', vehiculo('c2', 'XYZ-987', ['c2'])));
      await assertFails(registrar(s.t1, 'v9', vehiculo('c1', 'XYZ-987', ['c1'])));
    });

    it('una reserva no se actualiza', async () => {
      await registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1']));
      await assertFails(updateDoc(ref(s.c1, 'placas/XYZ-987'), { clienteId: 'c2' }));
      await assertFails(updateDoc(ref(s.jefe, 'placas/XYZ-987'), { clienteId: 'c2' }));
      await assertFails(setDoc(ref(s.jefe, 'placas/XYZ-987'), reserva('v9', 'c1')));
    });

    it('nadie lee las reservas desde la app, ni el Jefe', async () => {
      await registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1']));
      await assertFails(getDoc(ref(s.c1, 'placas/XYZ-987')));
      await assertFails(getDoc(ref(s.jefe, 'placas/XYZ-987')));
      await assertFails(lista(s.jefe, 'placas'));
    });

    it('el vehículo y su reserva se eliminan juntos; la reserva sola o el vehículo solo se deniegan', async () => {
      await registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1']));
      await assertFails(deleteDoc(ref(s.c1, 'vehiculos/v9')));
      await assertFails(deleteDoc(ref(s.c1, 'placas/XYZ-987')));
      await assertFails(deleteDoc(ref(s.c2, 'placas/XYZ-987')));
      const ajeno = writeBatch(bd(s.c2));
      ajeno.delete(ref(s.c2, 'vehiculos/v9'));
      ajeno.delete(ref(s.c2, 'placas/XYZ-987'));
      await assertFails(ajeno.commit());
      const lote = writeBatch(bd(s.c1));
      lote.delete(ref(s.c1, 'vehiculos/v9'));
      lote.delete(ref(s.c1, 'placas/XYZ-987'));
      await assertSucceeds(lote.commit());
      // la placa queda libre
      await assertSucceeds(registrar(s.c2, 'v10', vehiculo('c2', 'XYZ-987', ['c2'])));
    });

    it('el Jefe elimina el vehículo y su reserva en un lote', async () => {
      await registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1']));
      const lote = writeBatch(bd(s.jefe));
      lote.delete(ref(s.jefe, 'vehiculos/v9'));
      lote.delete(ref(s.jefe, 'placas/XYZ-987'));
      await assertSucceeds(lote.commit());
    });

    it('un vehículo anterior a las reservas se elimina sin reserva', async () => {
      await assertSucceeds(deleteDoc(ref(s.c1, 'vehiculos/v1')));
      await assertSucceeds(deleteDoc(ref(s.jefe, 'vehiculos/v2')));
    });

    it('la app borra también la reserva al eliminar un vehículo anterior a las reservas y el lote funciona', async () => {
      const lote = writeBatch(bd(s.c1));
      lote.delete(ref(s.c1, 'placas/ABC123'));
      lote.delete(ref(s.c1, 'vehiculos/v1'));
      await assertSucceeds(lote.commit());
    });

    it('la reserva de otro vehículo sigue protegida aunque se pida borrarla junto a un vehículo propio', async () => {
      await registrar(s.c2, 'v9', vehiculo('c2', 'XYZ-987', ['c2']));
      const lote = writeBatch(bd(s.c1));
      lote.delete(ref(s.c1, 'placas/XYZ-987'));
      lote.delete(ref(s.c1, 'vehiculos/v1'));
      await assertFails(lote.commit());
    });

    it('ocultar el vehículo (activo = false) conserva la reserva y funciona', async () => {
      await registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1']));
      await assertSucceeds(updateDoc(ref(s.c1, 'vehiculos/v9'), { activo: false }));
      await assertFails(registrar(s.c2, 'v10', vehiculo('c2', 'XYZ-987', ['c2'])));
      const lote = writeBatch(bd(s.c1));
      lote.update(ref(s.c1, 'vehiculos/v9'), { activo: false });
      lote.delete(ref(s.c1, 'placas/XYZ-987'));
      await assertFails(lote.commit());
    });
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

  it('la instantánea de la solicitud debe coincidir con el perfil del cliente y con su vehículo', async () => {
    const con = (extra) => solicitud('c1', 'v1', extra);
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s8'), con({ vehiculo: { ...resumenVehiculo(), placa: 'ZZZ999' } })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s8'), con({ vehiculo: { ...resumenVehiculo(), marca: 'Ferrari' } })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s8'), con({ cliente: { ...resumenCliente('c1'), dni: '00000000' } })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s8'), con({ cliente: { ...resumenCliente('c1'), nombres: 'Otro' } })));
    await assertSucceeds(setDoc(ref(s.c1, 'solicitudes/s8'), con({})));
  });

  it('el cliente crea solicitudes pendientes propias', async () => {
    await assertSucceeds(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1')));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1', { estado: 'ATENDIDA' })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c2', 'v2')));
    await assertFails(setDoc(ref(s.t1, 'solicitudes/s9'), solicitud('c1', 'v1')));
  });

  it('la solicitud nueva exige la instantánea del cliente y del vehículo, con sus claves exactas', async () => {
    await assertSucceeds(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1')));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s8'), sinInstantaneas(solicitud('c1', 'v1'))));
    const sinVehiculo = sinInstantaneas(solicitud('c1', 'v1'));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s7'), { ...sinVehiculo, cliente: resumenCliente('c1') }));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s6'), { ...sinVehiculo, vehiculo: resumenVehiculo() }));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s5'),
      solicitud('c1', 'v1', { cliente: { ...resumenCliente('c1'), correo: 'x@y.com' } })));
    const { telefono, ...sinTelefono } = resumenCliente('c1');
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s4'), solicitud('c1', 'v1', { cliente: sinTelefono })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s3'), solicitud('c1', 'v1', { vehiculo: { ...resumenVehiculo(), anio: '2018' } })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s2'), solicitud('c1', 'v1', { tecnico: resumenTecnico('t1') })));
  });

  it('el cliente no cambia la instantánea de su solicitud al editarla, pero la solicitud anterior al relleno se edita', async () => {
    await assertSucceeds(updateDoc(ref(s.c1, 'solicitudes/s1'), { descripcion: 'Ahora también hace ruido' }));
    await assertFails(updateDoc(ref(s.c1, 'solicitudes/s1'), { cliente: { ...resumenCliente('c1'), telefono: '1' } }));
    await assertFails(updateDoc(ref(s.c1, 'solicitudes/s1'), { vehiculo: { ...resumenVehiculo(), placa: 'ZZZ999' } }));
    await preparar(entorno, 'solicitudes/s5', sinInstantaneas(solicitud('c1', 'v1')));
    await assertSucceeds(updateDoc(ref(s.c1, 'solicitudes/s5'), { descripcion: 'Editada sin instantánea' }));
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

  it('la orden nueva exige las tres instantáneas con sus claves exactas', async () => {
    const { cliente, vehiculo: veh, tecnico, ...base } = orden('0009', 'c1', 't1', 'v1');
    await assertSucceeds(setDoc(ref(s.jefe, 'ordenes/o9'), orden('0009', 'c1', 't1', 'v1')));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o8'), { ...base, vehiculo: veh, tecnico }));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o7'), { ...base, cliente, tecnico }));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o6'), { ...base, cliente, vehiculo: veh }));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o5'), orden('0005', 'c1', 't1', 'v1', { tecnico: { ...resumenTecnico('t1'), dni: '1' } })));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o4'), orden('0004', 'c1', 't1', 'v1', { tecnico: { nombres: 'Solo nombre' } })));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o3'), orden('0003', 'c1', 't1', 'v1', { vehiculo: { ...resumenVehiculo(), anio: '2018' } })));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o2x'), orden('0002', 'c1', 't1', 'v1', { cliente: 'Juan' })));
  });

  it('el Jefe no cambia la instantánea del cliente ni del vehículo, y la del técnico solo al reasignar', async () => {
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { cliente: { ...resumenCliente('c1'), telefono: '1' } }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { vehiculo: { ...resumenVehiculo(), color: 'Azul' } }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { tecnico: resumenTecnico('t2') }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { tecnicoId: 't2', acceso: ['c1', 't2'], tecnico: { nombres: 'X' } }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida' }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { tecnicoId: 't2', acceso: ['c1', 't2'], tecnico: resumenTecnico('t2') }));
  });

  it('la orden anterior al relleno se sigue editando y reasignando, y el Jefe puede completarle la instantánea del técnico', async () => {
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o2'), { falla: 'Corregida' }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o2'), { tecnicoId: 't1', acceso: ['c2', 't1'] }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o2'), { tecnicoId: 't2', acceso: ['c2', 't2'], tecnico: resumenTecnico('t2') }));
    await assertSucceeds(updateDoc(ref(s.t2, 'ordenes/o2'), {
      estado: 'EN_REPARACION', historial: [{ estado: 'EN_REPARACION', fechaHora: 2, usuarioId: 't2', motivo: null }],
    }));
  });

  it('el técnico y el cliente no tocan las instantáneas', async () => {
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { tecnico: resumenTecnico('t2') }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', cliente: resumenCliente('c2') }));
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO' });
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), {
      conformidad: { fechaHora: 9, usuarioId: 'c1', usuarioNombre: 'N' }, cliente: resumenCliente('c2'),
    }));
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

  it('la instantánea del técnico solo se agrega o cambia al reasignar, no al editar otros datos', async () => {
    // o2 es una orden anterior al relleno (sin instantáneas) y t2 es su técnico: editarla no puede agregar `tecnico`
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o2'), { falla: 'Corregida', tecnico: resumenTecnico('t2') }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o2'), { falla: 'Corregida' }));
    // o1 ya tiene la suya: reescribirla sin cambios al editar otros datos sí pasa; cambiarla sin reasignar no
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', tecnico: resumenTecnico('t1') }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', tecnico: { nombres: 'Renombrado', apellidos: 'Apellido' } }));
  });

  it('el Jefe solo anula una orden Ingresado y solo entrega una orden Lista que ya tiene la conformidad', async () => {
    const conformidad = { fechaHora: 9, usuarioId: 'c1', usuarioNombre: 'Nombre' };
    // Anular: solo desde Ingresado
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ANULADA', motivoAnulacion: 'Duplicada' }));
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO', conformidad });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ANULADA', motivoAnulacion: 'Tarde' }));
    // Entregar: no desde Ingresado ni En reparación, ni sin la conformidad ya guardada, ni inventándola en el mismo cambio
    await preparar(entorno, 'ordenes/o1', { estado: 'INGRESADO', conformidad: null });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ENTREGADO' }));
    await preparar(entorno, 'ordenes/o1', { estado: 'EN_REPARACION', conformidad });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ENTREGADO' }));
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO', conformidad: null });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ENTREGADO' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ENTREGADO', conformidad }));
    // Con la conformidad ya registrada sí
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO', conformidad });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ENTREGADO' }));
  });

  it('una vez guardada, la conformidad no se borra ni se cambia, tampoco al entregar', async () => {
    const conformidad = { fechaHora: 9, usuarioId: 'c1', usuarioNombre: 'Nombre' };
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO', conformidad });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ENTREGADO', conformidad: null }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { conformidad: null }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { conformidad: { ...conformidad, usuarioId: 'j1' } }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ENTREGADO' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { conformidad: null }));
  });

  it('el Jefe no inicia la reparación, no marca Listo ni reabre una orden entregada o anulada', async () => {
    const conformidad = { fechaHora: 9, usuarioId: 'c1', usuarioNombre: 'Nombre' };
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'EN_REPARACION' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'LISTO' }));
    await preparar(entorno, 'ordenes/o1', { estado: 'ENTREGADO', conformidad });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'LISTO' }));
    await preparar(entorno, 'ordenes/o1', { estado: 'ANULADA', motivoAnulacion: 'Duplicada' });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'INGRESADO' }));
  });

  it('el Jefe no agrega campos que la orden no define, pero sí registra la conformidad y las observaciones', async () => {
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { extra: 'campo inventado' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', precio: 100 }));
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO' });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), {
      conformidad: { fechaHora: 9, usuarioId: 'j1', usuarioNombre: 'Jefe' },
    }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), {
      observacionesCliente: [{ texto: 'Ruido al frenar', acuerdo: 'Revisar', fechaHora: 9, usuarioId: 'j1' }],
    }));
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
  const ruta = (usuario, token) => `usuarios/${usuario}/tokensDispositivo/${token}`;
  const enDias = (dias) => Timestamp.fromMillis(Date.now() + dias * 86_400_000);
  const datos = (token, extra = {}) => ({ token, expiraEn: enDias(60), ...extra });

  it('el dueño guarda un documento por dispositivo y los dos conviven', async () => {
    await assertSucceeds(setDoc(ref(s.c1, ruta('c1', 'tok-a')), datos('tok-a'), { merge: true }));
    await assertSucceeds(setDoc(ref(s.c1, ruta('c1', 'tok-b')), datos('tok-b'), { merge: true }));
    await entorno.withSecurityRulesDisabled(async (c) => {
      const docs = await getDocs(collection(c.firestore(), 'usuarios/c1/tokensDispositivo'));
      if (docs.size !== 2) throw new Error(`Se esperaban 2 tokens y llegaron ${docs.size}`);
    });
  });

  it('el dueño vuelve a guardar el mismo token y actualiza el mismo documento', async () => {
    await assertSucceeds(setDoc(ref(s.c1, ruta('c1', 'tok-a')), datos('tok-a'), { merge: true }));
    await assertSucceeds(setDoc(ref(s.c1, ruta('c1', 'tok-a')), { token: 'tok-a', expiraEn: enDias(61) }, { merge: true }));
    await assertSucceeds(updateDoc(ref(s.c1, ruta('c1', 'tok-a')), { expiraEn: enDias(62) }));
  });

  it('el técnico también guarda el suyo', async () => {
    await assertSucceeds(setDoc(ref(s.t1, ruta('t1', 'tok-t')), datos('tok-t'), { merge: true }));
  });

  it('nadie toca los tokens de otro usuario, ni siquiera el Jefe', async () => {
    await preparar(entorno, ruta('c1', 'tok-a'), datos('tok-a'));
    for (const ajeno of [s.c2, s.t1, s.jefe]) {
      await assertFails(setDoc(ref(ajeno, ruta('c1', 'tok-x')), datos('tok-x')));
      await assertFails(setDoc(ref(ajeno, ruta('c1', 'tok-a')), datos('tok-a')));
      await assertFails(updateDoc(ref(ajeno, ruta('c1', 'tok-a')), { expiraEn: enDias(60) }));
      await assertFails(getDoc(ref(ajeno, ruta('c1', 'tok-a'))));
      await assertFails(lista(ajeno, 'usuarios/c1/tokensDispositivo'));
      await assertFails(deleteDoc(ref(ajeno, ruta('c1', 'tok-a'))));
    }
  });

  it('nadie lee tokens desde la app, ni los suyos', async () => {
    await preparar(entorno, ruta('c1', 'tok-a'), datos('tok-a'));
    await assertFails(getDoc(ref(s.c1, ruta('c1', 'tok-a'))));
    await assertFails(lista(s.c1, 'usuarios/c1/tokensDispositivo'));
  });

  it('la expiración debe ser una fecha futura de como máximo 90 días', async () => {
    await assertSucceeds(setDoc(ref(s.c1, ruta('c1', 'tok-a')), datos('tok-a', { expiraEn: enDias(89) })));
    await assertFails(setDoc(ref(s.c1, ruta('c1', 'tok-a')), datos('tok-a', { expiraEn: enDias(91) })));
    await assertFails(setDoc(ref(s.c1, ruta('c1', 'tok-a')), datos('tok-a', { expiraEn: enDias(-1) })));
    await assertFails(setDoc(ref(s.c1, ruta('c1', 'tok-a')), datos('tok-a', { expiraEn: Date.now() + 86_400_000 })));
  });

  it('rechaza campos de más, un token distinto del id, tipos erróneos y falta de expiraEn', async () => {
    await assertFails(setDoc(ref(s.c1, ruta('c1', 'tok-a')), datos('tok-a', { extra: 1 })));
    await assertFails(setDoc(ref(s.c1, ruta('c1', 'tok-a')), datos('otro')));
    await assertFails(setDoc(ref(s.c1, ruta('c1', 'tok-a')), { token: 123, expiraEn: enDias(60) }));
    await assertFails(setDoc(ref(s.c1, ruta('c1', 'tok-a')), { token: 'tok-a', expiraEn: '1' }));
    await assertFails(setDoc(ref(s.c1, ruta('c1', 'tok-a')), { token: 'tok-a' }));
    await assertFails(setDoc(ref(s.c1, ruta('c1', 'tok-a')), { expiraEn: enDias(60) }));
  });

  it('el dueño borra el suyo', async () => {
    await preparar(entorno, ruta('c1', 'tok-a'), datos('tok-a'));
    await assertSucceeds(deleteDoc(ref(s.c1, ruta('c1', 'tok-a'))));
  });

  it('la ruta antigua en la raíz queda denegada', async () => {
    await assertFails(setDoc(ref(s.c1, 'tokensDispositivo/c1'), { token: 'abc' }, { merge: true }));
    await assertFails(setDoc(ref(s.c1, 'tokensDispositivo/c1'), datos('abc')));
    await preparar(entorno, 'tokensDispositivo/c1', { token: 'abc' });
    await assertFails(updateDoc(ref(s.c1, 'tokensDispositivo/c1'), { token: 'nuevo' }));
    await assertFails(getDoc(ref(s.c1, 'tokensDispositivo/c1')));
  });

  it('las reglas de usuarios no se heredan: el cliente sigue sin leer el perfil de otro', async () => {
    await assertFails(getDoc(ref(s.c1, 'usuarios/c2')));
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

describe('consultas dirigidas de la app', () => {
  it('el cliente consulta sus órdenes de un vehículo con el filtro de acceso y recibe solo las suyas', async () => {
    const suyas = await assertSucceeds(lista(s.c1, 'ordenes', where('acceso', 'array-contains', 'c1'), where('vehiculoId', '==', 'v1')));
    if (suyas.size !== 1 || suyas.docs[0].id !== 'o1') throw new Error(`Se esperaba solo o1 y llegaron ${suyas.size}`);
    const ajenas = await assertSucceeds(lista(s.c1, 'ordenes', where('acceso', 'array-contains', 'c1'), where('vehiculoId', '==', 'v2')));
    if (ajenas.size !== 0) throw new Error('El cliente no debe recibir órdenes de otro vehículo');
  });

  it('la consulta por vehículo sin el filtro de acceso se deniega al cliente y al técnico', async () => {
    await assertFails(lista(s.c1, 'ordenes', where('vehiculoId', '==', 'v1')));
    await assertFails(lista(s.t1, 'ordenes', where('vehiculoId', '==', 'v1')));
    await assertFails(lista(s.c1, 'solicitudes', where('vehiculoId', '==', 'v1')));
    await assertFails(lista(s.c1, 'ordenes', where('acceso', 'array-contains', 'c2'), where('vehiculoId', '==', 'v2')));
  });

  it('el Jefe consulta por vehículo sin filtro de acceso', async () => {
    const delVehiculo = await assertSucceeds(lista(s.jefe, 'ordenes', where('vehiculoId', '==', 'v2')));
    if (delVehiculo.size !== 1 || delVehiculo.docs[0].id !== 'o2') throw new Error('Se esperaba solo o2');
    await assertSucceeds(lista(s.jefe, 'solicitudes', where('vehiculoId', '==', 'v1')));
  });

  it('el cliente consulta sus solicitudes de un vehículo con el filtro de acceso', async () => {
    const suyas = await assertSucceeds(lista(s.c1, 'solicitudes', where('acceso', 'array-contains', 'c1'), where('vehiculoId', '==', 'v1')));
    if (suyas.size !== 1) throw new Error(`Se esperaba 1 solicitud y llegaron ${suyas.size}`);
  });

  it('el técnico consulta los avances de su orden con el filtro de acceso', async () => {
    const delTecnico = await assertSucceeds(lista(s.t1, 'avances', where('acceso', 'array-contains', 't1'), where('ordenId', '==', 'o1')));
    if (delTecnico.size !== 1 || delTecnico.docs[0].id !== 'a1') throw new Error('Se esperaba solo a1');
    await assertFails(lista(s.t1, 'avances', where('ordenId', '==', 'o1')));
    await assertFails(lista(s.t2, 'avances', where('acceso', 'array-contains', 't1'), where('ordenId', '==', 'o1')));
  });

  it('observar una orden es un get de un solo documento: el dueño lo lee y el ajeno no', async () => {
    await assertSucceeds(getDoc(ref(s.c1, 'ordenes/o1')));
    await assertFails(getDoc(ref(s.c2, 'ordenes/o1')));
    await assertFails(getDoc(ref(s.c1, 'ordenes/o2')));
  });
});
