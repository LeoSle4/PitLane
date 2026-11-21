import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  arrayUnion, collection, deleteDoc, deleteField, doc, getDoc, getDocs, limit, orderBy, query, runTransaction, setDoc, Timestamp, updateDoc, where, writeBatch,
} from 'firebase/firestore';
import { after, before, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  AHORA, avance, cambio, crearEntorno, historialInicial, orden, perfil, preparar, resumenCliente, resumenTecnico, resumenVehiculo, sembrar, sesiones,
  sinEsquema, sinInstantaneas, solicitud, vehiculo,
} from './entorno.mjs';

let entorno;
let s;
const bd = (contexto) => contexto.firestore();
const ref = (contexto, ruta) => doc(bd(contexto), ruta);
const lista = (contexto, coleccion, ...condiciones) => getDocs(query(collection(bd(contexto), coleccion), ...condiciones));

/** DNI de 8 dígitos a partir del id de prueba (c9 → 00000009). */
const dniDe = (id) => id.replace(/\D/g, '').padStart(8, '0');
/** Como la app: el perfil (set con merge) y la reserva `dnis/{dni}` de su DNI en un mismo lote. */
const registrarConDni = (contexto, id, datos, dni = datos.dni, reservaDatos = { usuarioId: id }) => {
  const lote = writeBatch(bd(contexto));
  lote.set(ref(contexto, `usuarios/${id}`), datos, { merge: true });
  lote.set(ref(contexto, `dnis/${dni}`), reservaDatos, { merge: true });
  return lote.commit();
};

/** Como la app: la orden y la reserva `numerosOrden/{numero}` de su número en un mismo lote. */
const crearOrden = (contexto, id, datos, numero = datos.numero, reservaDatos = { ordenId: id }) => {
  const lote = writeBatch(bd(contexto));
  lote.set(ref(contexto, `ordenes/${id}`), datos);
  lote.set(ref(contexto, `numerosOrden/${numero}`), reservaDatos);
  return lote.commit();
};

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

  describe('última actividad (ultimaActividad, la mantiene el servidor)', () => {
    beforeEach(async () => {
      await preparar(entorno, 'usuarios/c1', { ultimaActividad: 1_800_000_000_000 });
      await preparar(entorno, 'usuarios/c3', { ultimaActividad: 1_700_000_000_000 });
    });

    it('un cliente que ya tiene el campo se edita igual que antes: la app no lo reenvía', async () => {
      await assertSucceeds(updateDoc(ref(s.jefe, 'usuarios/c1'), { telefono: '977' }));
      await assertSucceeds(updateDoc(ref(s.c1, 'usuarios/c1'), { nombres: 'Nuevo', telefono: '988' }));
      const { acceso, ...campos } = perfil('c1', 'CLIENTE', [], { tieneCuenta: true });
      await assertSucceeds(setDoc(ref(s.jefe, 'usuarios/c1'), { ...campos, acceso: arrayUnion('c1') }, { merge: true }));
    });

    it('nadie lo escribe desde la app', async () => {
      await assertFails(updateDoc(ref(s.jefe, 'usuarios/c1'), { ultimaActividad: 1 }));
      await assertFails(updateDoc(ref(s.c1, 'usuarios/c1'), { ultimaActividad: 9_900_000_000_000 }));
      await assertFails(updateDoc(ref(s.t1, 'usuarios/c1'), { ultimaActividad: 1 }));
    });

    it('el Jefe consulta los más recientes (orderBy desc con límite) y el cliente y el técnico no', async () => {
      const recientes = await assertSucceeds(lista(s.jefe, 'usuarios', orderBy('ultimaActividad', 'desc'), limit(10)));
      if (recientes.docs.map((d) => d.id).join() !== 'c1,c3') throw new Error(`Se esperaba c1,c3 y llegó ${recientes.docs.map((d) => d.id)}`);
      await assertFails(lista(s.c1, 'usuarios', orderBy('ultimaActividad', 'desc'), limit(10)));
      await assertFails(lista(s.t1, 'usuarios', orderBy('ultimaActividad', 'desc'), limit(10)));
    });
  });

  it('el Jefe registra clientes sin cuenta como lo hace la app (lote con set merge, arrayUnion y la reserva del DNI)', async () => {
    const { acceso, ...campos } = perfil('c9', 'CLIENTE', [], { tieneCuenta: false, dni: '00000009' });
    const lote = writeBatch(bd(s.jefe));
    lote.set(ref(s.jefe, 'usuarios/c9'), { ...campos, acceso: arrayUnion('c9') }, { merge: true });
    lote.set(ref(s.jefe, 'dnis/00000009'), { usuarioId: 'c9' }, { merge: true });
    await assertSucceeds(lote.commit());
  });

  it('el Jefe no puede crear técnicos, cuentas ni perfiles ajenos, y nadie más crea perfiles', async () => {
    await assertFails(setDoc(ref(s.jefe, 'usuarios/t9'), perfil('t9', 'TECNICO', ['t9'], { tieneCuenta: false })));
    await assertFails(setDoc(ref(s.jefe, 'usuarios/c9'), perfil('c9', 'CLIENTE', ['c9'])));
    await assertFails(setDoc(ref(s.jefe, 'usuarios/c9'), perfil('c9', 'CLIENTE', ['c9', 'c1'], { tieneCuenta: false })));
    await assertFails(setDoc(ref(s.c1, 'usuarios/c9'), perfil('c9', 'CLIENTE', ['c9'], { tieneCuenta: false })));
  });

  describe('términos de búsqueda (terminos)', () => {
    const terminos = ['ana', 'tor', '123'];
    const sinCuenta = (extra = {}, id = 'c9') => perfil(id, 'CLIENTE', [id], { tieneCuenta: false, dni: dniDe(id), ...extra });
    // El perfil nuevo va siempre con la reserva de su DNI (ver «DNI único»)
    const crear = (id, datos) => registrarConDni(s.jefe, id, datos);

    it('el Jefe registra un cliente sin cuenta con terminos y también sin ellos (app anterior)', async () => {
      await assertSucceeds(crear('c9', sinCuenta({ terminos })));
      await assertSucceeds(crear('c8', sinCuenta({}, 'c8')));
    });

    it('terminos debe ser una lista de 120 elementos como máximo al crear', async () => {
      await assertSucceeds(crear('c9', sinCuenta({ terminos: Array.from({ length: 120 }, (_, i) => `t${i}`) })));
      await assertFails(crear('c8', sinCuenta({ terminos: Array.from({ length: 121 }, (_, i) => `t${i}`) }, 'c8')));
      await assertFails(crear('c7', sinCuenta({ terminos: 'ana' }, 'c7')));
    });

    it('un cliente registrado sin terminos que el Jefe o el propio usuario edita con la app nueva gana el campo', async () => {
      await assertSucceeds(updateDoc(ref(s.jefe, 'usuarios/c1'), { telefono: '977', terminos }));
      await assertSucceeds(updateDoc(ref(s.c1, 'usuarios/c1'), { nombres: 'Nuevo', terminos }));
      await assertSucceeds(setDoc(ref(s.c1, 'usuarios/c1'), { ...perfil('c1', 'CLIENTE', ['c1', 't1']), nombres: 'Otro', terminos }, { merge: true }));
    });

    it('terminos al actualizar debe ser una lista de 120 elementos como máximo', async () => {
      await assertFails(updateDoc(ref(s.jefe, 'usuarios/c1'), { terminos: Array.from({ length: 121 }, (_, i) => `t${i}`) }));
      await assertFails(updateDoc(ref(s.c1, 'usuarios/c1'), { terminos: Array.from({ length: 121 }, (_, i) => `t${i}`) }));
      await assertFails(updateDoc(ref(s.jefe, 'usuarios/c1'), { terminos: 'ana' }));
      await assertFails(updateDoc(ref(s.c1, 'usuarios/c1'), { terminos: { a: 1 } }));
    });

    it('terminos no abre otros campos ni a otros usuarios', async () => {
      await assertFails(updateDoc(ref(s.c1, 'usuarios/c2'), { terminos }));
      await assertFails(updateDoc(ref(s.t1, 'usuarios/c1'), { terminos }));
      await assertFails(updateDoc(ref(s.c1, 'usuarios/c1'), { rol: 'JEFE', terminos }));
      await assertFails(updateDoc(ref(s.jefe, 'usuarios/c1'), { activo: false, terminos }));
    });
  });

  it('el Jefe solo elimina clientes sin cuenta', async () => {
    await assertSucceeds(deleteDoc(ref(s.jefe, 'usuarios/c3')));
    await assertFails(deleteDoc(ref(s.jefe, 'usuarios/c1')));
    await assertFails(deleteDoc(ref(s.jefe, 'usuarios/t1')));
    await assertFails(deleteDoc(ref(s.c1, 'usuarios/c3')));
  });

  describe('DNI único (dnis/{dni})', () => {
    const sinCuenta = (id, extra = {}) => perfil(id, 'CLIENTE', [id], { tieneCuenta: false, dni: dniDe(id), ...extra });
    const leerSinReglas = async (ruta) => {
      let datos = null;
      await entorno.withSecurityRulesDisabled(async (contexto) => {
        const documento = await getDoc(doc(contexto.firestore(), ruta));
        if (documento.exists()) datos = documento.data();
      });
      return datos;
    };
    const eliminarConDni = (contexto, id, dni) => {
      const lote = writeBatch(bd(contexto));
      lote.delete(ref(contexto, `dnis/${dni}`));
      lote.delete(ref(contexto, `usuarios/${id}`));
      return lote.commit();
    };

    it('el perfil y la reserva se crean juntos: la reserva queda con el usuarioId', async () => {
      await assertSucceeds(registrarConDni(s.jefe, 'c9', sinCuenta('c9')));
      if ((await leerSinReglas('dnis/00000009'))?.usuarioId !== 'c9') throw new Error('La reserva no apunta a c9');
      if ((await leerSinReglas('usuarios/c9'))?.dni !== '00000009') throw new Error('El perfil no se creó');
    });

    it('el perfil sin su reserva se deniega', async () => {
      await assertFails(setDoc(ref(s.jefe, 'usuarios/c9'), sinCuenta('c9')));
    });

    it('la reserva sin su perfil se deniega', async () => {
      await assertFails(setDoc(ref(s.jefe, 'dnis/00000009'), { usuarioId: 'c9' }));
      await assertFails(setDoc(ref(s.jefe, 'dnis/00000001'), { usuarioId: 'c1' }));
    });

    it('un segundo perfil con un DNI ya reservado se deniega y no se crea', async () => {
      await assertSucceeds(registrarConDni(s.jefe, 'c9', sinCuenta('c9')));
      await assertFails(registrarConDni(s.jefe, 'c8', sinCuenta('c8', { dni: '00000009' })));
      if (await leerSinReglas('usuarios/c8')) throw new Error('El perfil duplicado no debía crearse');
      if ((await leerSinReglas('dnis/00000009'))?.usuarioId !== 'c9') throw new Error('La reserva cambió de dueño');
    });

    it('la reserva debe apuntar a ese perfil y coincidir con su DNI', async () => {
      await assertFails(registrarConDni(s.jefe, 'c9', sinCuenta('c9'), '00000009', { usuarioId: 'c8' }));
      await assertFails(registrarConDni(s.jefe, 'c9', sinCuenta('c9'), '00000008'));
    });

    it('la reserva solo lleva usuarioId', async () => {
      await assertFails(registrarConDni(s.jefe, 'c9', sinCuenta('c9'), '00000009', { usuarioId: 'c9', extra: 1 }));
      await assertFails(registrarConDni(s.jefe, 'c9', sinCuenta('c9'), '00000009', {}));
    });

    it('el DNI solo admite el formato de la app: 8 dígitos', async () => {
      for (const [i, malo] of ['1234567', '123456789', '1234567a', ' 12345678', '12345678 '].entries()) {
        await assertFails(registrarConDni(s.jefe, `cx${i}`, sinCuenta(`cx${i}`, { dni: malo }), malo, { usuarioId: `cx${i}` }));
      }
    });

    it('solo el Jefe reserva un DNI', async () => {
      await assertFails(registrarConDni(s.c1, 'c9', sinCuenta('c9')));
      await assertFails(registrarConDni(s.t1, 'c9', sinCuenta('c9')));
    });

    it('nadie lee las reservas desde la app, ni el Jefe, y no se actualizan', async () => {
      await registrarConDni(s.jefe, 'c9', sinCuenta('c9'));
      await assertFails(getDoc(ref(s.jefe, 'dnis/00000009')));
      await assertFails(getDoc(ref(s.c1, 'dnis/00000009')));
      await assertFails(lista(s.jefe, 'dnis'));
      await assertFails(updateDoc(ref(s.jefe, 'dnis/00000009'), { usuarioId: 'c8' }));
      await assertFails(setDoc(ref(s.jefe, 'dnis/00000009'), { usuarioId: 'c9' }));
    });

    it('editar el perfil sin tocar el DNI sigue funcionando y el DNI no cambia', async () => {
      await registrarConDni(s.jefe, 'c9', sinCuenta('c9'));
      await assertSucceeds(updateDoc(ref(s.jefe, 'usuarios/c9'), { nombres: 'Otro', telefono: '977' }));
      await assertFails(updateDoc(ref(s.jefe, 'usuarios/c9'), { dni: '00000008' }));
    });

    it('el perfil y su reserva se eliminan juntos; el perfil solo o la reserva sola se deniegan', async () => {
      await registrarConDni(s.jefe, 'c9', sinCuenta('c9'));
      await assertFails(deleteDoc(ref(s.jefe, 'usuarios/c9')));
      await assertFails(deleteDoc(ref(s.jefe, 'dnis/00000009')));
      await assertFails(eliminarConDni(s.c1, 'c9', '00000009'));
      await assertSucceeds(eliminarConDni(s.jefe, 'c9', '00000009'));
      if (await leerSinReglas('dnis/00000009')) throw new Error('La reserva debía borrarse');
      // el DNI queda libre
      await assertSucceeds(registrarConDni(s.jefe, 'c8', sinCuenta('c8', { dni: '00000009' })));
    });

    it('un perfil anterior a las reservas se elimina, también junto con la reserva que no tiene (como la app)', async () => {
      await assertSucceeds(eliminarConDni(s.jefe, 'c3', '1c3'));
    });

    it('la reserva de otro perfil sigue protegida aunque se pida borrarla junto a otro perfil', async () => {
      await registrarConDni(s.jefe, 'c9', sinCuenta('c9'));
      await assertFails(eliminarConDni(s.jefe, 'c3', '00000009'));
    });
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

  describe('términos de la placa (terminosPlaca)', () => {
    const terminosPlaca = ['abc', 'bc1', 'abc1'];

    it('el cliente y el Jefe registran un vehículo con terminosPlaca y también sin ellos (app anterior)', async () => {
      await assertSucceeds(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1'], { terminosPlaca })));
      await assertSucceeds(registrar(s.jefe, 'v10', vehiculo('c2', 'XYZ-988', ['c2'], { terminosPlaca })));
      await assertSucceeds(registrar(s.c1, 'v11', vehiculo('c1', 'XYZ-989', ['c1'])));
    });

    it('terminosPlaca al crear debe ser una lista de 12 elementos como máximo', async () => {
      await assertSucceeds(registrar(s.c1, 'v9', vehiculo('c1', 'XYZ-987', ['c1'], { terminosPlaca: Array.from({ length: 12 }, (_, i) => `t${i}`) })));
      await assertFails(registrar(s.c1, 'v10', vehiculo('c1', 'XYZ-988', ['c1'], { terminosPlaca: Array.from({ length: 13 }, (_, i) => `t${i}`) })));
      await assertFails(registrar(s.c1, 'v11', vehiculo('c1', 'XYZ-989', ['c1'], { terminosPlaca: 'xyz' })));
    });

    it('un vehículo anterior (sin terminosPlaca) editado por el cliente o el Jefe con la app nueva gana el campo', async () => {
      await assertSucceeds(updateDoc(ref(s.c1, 'vehiculos/v1'), { marca: 'Kia', terminosPlaca }));
      await assertSucceeds(updateDoc(ref(s.jefe, 'vehiculos/v2'), { color: 'Azul', terminosPlaca }));
    });

    it('terminosPlaca al actualizar debe ser una lista de 12 elementos como máximo', async () => {
      await assertFails(updateDoc(ref(s.c1, 'vehiculos/v1'), { terminosPlaca: Array.from({ length: 13 }, (_, i) => `t${i}`) }));
      await assertFails(updateDoc(ref(s.jefe, 'vehiculos/v1'), { terminosPlaca: Array.from({ length: 13 }, (_, i) => `t${i}`) }));
      await assertFails(updateDoc(ref(s.c1, 'vehiculos/v1'), { terminosPlaca: 'abc' }));
      await assertFails(updateDoc(ref(s.jefe, 'vehiculos/v1'), { terminosPlaca: 7 }));
    });

    it('terminosPlaca no abre otros campos ni a otros usuarios', async () => {
      await assertFails(updateDoc(ref(s.c1, 'vehiculos/v2'), { terminosPlaca }));
      await assertFails(updateDoc(ref(s.t1, 'vehiculos/v1'), { terminosPlaca }));
      await assertFails(updateDoc(ref(s.c1, 'vehiculos/v1'), { acceso: ['c1', 't2'], terminosPlaca }));
    });
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

  it('la solicitud nueva exige esquema 2 y la solicitud anterior sigue editándose sin él', async () => {
    const { esquema, ...v1 } = solicitud('c1', 'v1');
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), v1));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1', { esquema: 1 })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1', { esquema: '2' })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1', { esquema: 3 })));
    await assertSucceeds(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1')));
    await preparar(entorno, 'solicitudes/s5', sinEsquema(solicitud('c1', 'v1')));
    await assertSucceeds(updateDoc(ref(s.c1, 'solicitudes/s5'), { descripcion: 'Editada sin esquema' }));
    await assertFails(updateDoc(ref(s.c1, 'solicitudes/s5'), { esquema: 2 }));
    await assertFails(updateDoc(ref(s.c1, 'solicitudes/s1'), { esquema: 3 }));
    await assertFails(updateDoc(ref(s.jefe, 'solicitudes/s1'), { estado: 'ATENDIDA', ordenId: 'o1', esquema: 3 }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'solicitudes/s5'), { estado: 'ATENDIDA', ordenId: 'o1' }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'solicitudes/s1'), { estado: 'ATENDIDA', ordenId: 'o1' }));
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

describe('historial de la orden: solo crece (cada cambio de estado agrega una entrada al final)', () => {
  const con = (...entradas) => [...historialInicial(), ...entradas];

  it('el técnico agrega su entrada al final y el estado es el de esa entrada', async () => {
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', historial: con(cambio('EN_REPARACION', 't1', AHORA)) }));
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o1'), {
      estado: 'LISTO', historial: con(cambio('EN_REPARACION', 't1', AHORA), cambio('LISTO', 't1', AHORA)),
    }));
  });

  it('nadie borra ni reescribe las entradas anteriores', async () => {
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', historial: [cambio('EN_REPARACION', 't1', AHORA)] }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), {
      estado: 'EN_REPARACION', historial: [cambio('INGRESADO', 'j1', AHORA, { usuarioNombre: 'Otro' }), cambio('EN_REPARACION', 't1', AHORA)],
    }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { historial: [] }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { historial: [cambio('INGRESADO', 'j1', 5)] }));
  });

  it('la entrada nueva la firma quien hace el cambio, con el estado nuevo y solo los campos de un cambio', async () => {
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', historial: con(cambio('EN_REPARACION', 'j1', AHORA)) }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', historial: con(cambio('LISTO', 't1', AHORA)) }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), {
      estado: 'EN_REPARACION', historial: con(cambio('EN_REPARACION', 't1', AHORA, { precio: 1 })),
    }));
  });

  it('una entrada con motivo o usuarioNombre que no es texto se rechaza (bloquearía la orden)', async () => {
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', historial: con(cambio('EN_REPARACION', 't1', AHORA, { motivo: 5 })) }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), {
      estado: 'EN_REPARACION', historial: con(cambio('EN_REPARACION', 't1', AHORA, { usuarioNombre: { x: 1 } })),
    }));
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o1'), {
      estado: 'EN_REPARACION', historial: con(cambio('EN_REPARACION', 't1', AHORA, { usuarioNombre: null })),
    }));
  });

  it('cambiar el estado exige su entrada, y agregar una entrada exige cambiar el estado', async () => {
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { estado: 'ANULADA', motivoAnulacion: 'Duplicada' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', historial: con(cambio('INGRESADO', 'j1', AHORA)) }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), {
      estado: 'LISTO', historial: con(cambio('EN_REPARACION', 't1', AHORA), cambio('LISTO', 't1', AHORA)),
    }));
  });

  it('el Jefe anula y entrega agregando su entrada, y edita reenviando el historial sin cambios', async () => {
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', historial: historialInicial() }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), {
      estado: 'ANULADA', motivoAnulacion: 'Duplicada', historial: con(cambio('ANULADA', 'j1', AHORA, { motivo: 'Duplicada' })),
    }));
    const previo = con(cambio('EN_REPARACION', 't1', AHORA), cambio('LISTO', 't1', AHORA));
    await preparar(entorno, 'ordenes/o2', { estado: 'LISTO', historial: previo, conformidad: { fechaHora: AHORA, usuarioId: 'c2', usuarioNombre: 'N' } });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o2'), { estado: 'ENTREGADO', historial: [...previo, cambio('ENTREGADO', 'j1', AHORA)] }));
  });

  it('una entrada antigua sin firma bloquea la orden hasta el relleno (rellenarHistorial.mjs), y después se edita', async () => {
    // El mapeador reenvía `usuarioNombre: null`; para Firestore una clave ausente no es igual a una clave en null.
    const { usuarioNombre, ...sinFirma } = cambio('INGRESADO', 'j1', 1);
    await preparar(entorno, 'ordenes/o1', { historial: [sinFirma] });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', historial: [{ ...sinFirma, usuarioNombre: null }] }));
    await preparar(entorno, 'ordenes/o1', { historial: [{ ...sinFirma, usuarioNombre: null }] });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', historial: [{ ...sinFirma, usuarioNombre: null }] }));
  });

  it('la orden nace con una sola entrada: el ingreso firmado por el Jefe que la crea', async () => {
    await assertSucceeds(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1')));
    await assertFails(crearOrden(s.jefe, 'o8', orden('0008', 'c1', 't1', 'v1', { historial: [] })));
    await assertFails(crearOrden(s.jefe, 'o7', orden('0007', 'c1', 't1', 'v1', { historial: [cambio('INGRESADO', 't1', AHORA)] })));
    await assertFails(crearOrden(s.jefe, 'o6', orden('0006', 'c1', 't1', 'v1', {
      historial: [cambio('INGRESADO', 'j1', AHORA), cambio('EN_REPARACION', 'j1', AHORA)],
    })));
  });
});

describe('ordenes', () => {
  // Cambio de estado como lo escribe la app: el estado nuevo y su entrada agregada al final del historial.
  const conCambio = (estado, usuarioId) => ({ estado, historial: [...historialInicial(), cambio(estado, usuarioId, AHORA)] });

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
    await assertSucceeds(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1')));
    await assertFails(crearOrden(s.jefe, 'o8', orden('0008', 'c1', 't1', 'v1', { acceso: ['c1'] })));
    await assertFails(crearOrden(s.jefe, 'o7', orden('0007', 'c1', 't1', 'v1', { estado: 'LISTO' })));
    await assertFails(crearOrden(s.c1, 'o6', orden('0006', 'c1', 't1', 'v1')));
    await assertFails(crearOrden(s.t1, 'o5', orden('0005', 'c1', 't1', 'v1')));
  });

  it('la orden nueva exige las tres instantáneas con sus claves exactas', async () => {
    const { cliente, vehiculo: veh, tecnico, ...base } = orden('0009', 'c1', 't1', 'v1');
    await assertSucceeds(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1')));
    await assertFails(crearOrden(s.jefe, 'o8', { ...base, vehiculo: veh, tecnico }));
    await assertFails(crearOrden(s.jefe, 'o7', { ...base, cliente, tecnico }));
    await assertFails(crearOrden(s.jefe, 'o6', { ...base, cliente, vehiculo: veh }));
    await assertFails(crearOrden(s.jefe, 'o5', orden('0005', 'c1', 't1', 'v1', { tecnico: { ...resumenTecnico('t1'), dni: '1' } })));
    await assertFails(crearOrden(s.jefe, 'o4', orden('0004', 'c1', 't1', 'v1', { tecnico: { nombres: 'Solo nombre' } })));
    await assertFails(crearOrden(s.jefe, 'o3', orden('0003', 'c1', 't1', 'v1', { vehiculo: { ...resumenVehiculo(), anio: '2018' } })));
    await assertFails(crearOrden(s.jefe, 'o2x', orden('0002', 'c1', 't1', 'v1', { cliente: 'Juan' })));
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
      ...conCambio('EN_REPARACION', 't2'),
    }));
  });

  it('la orden nueva exige esquema 2', async () => {
    const { esquema, ...v1 } = orden('0009', 'c1', 't1', 'v1');
    await assertFails(crearOrden(s.jefe, 'o9', v1));
    await assertFails(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1', { esquema: 1 })));
    await assertFails(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1', { esquema: '2' })));
    await assertFails(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1', { esquema: 3 })));
    await assertSucceeds(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1')));
  });

  it('el Jefe no cambia ni quita el esquema de una orden', async () => {
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { esquema: 1 }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { esquema: 3 }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { esquema: deleteField() }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida' }));
  });

  it('una orden de la versión 1 recibe esquema 2 solo con las tres instantáneas, y sin él sigue funcionando', async () => {
    await preparar(entorno, 'ordenes/o3', sinEsquema(orden('0003', 'c1', 't1', 'v1')));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o3'), { falla: 'Corregida' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o3'), { esquema: 3 }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o3'), { esquema: '2' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o2'), { esquema: 2 }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o3'), { esquema: 2 }));
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o3'), { ...conCambio('EN_REPARACION', 't1') }));
  });

  it('el técnico y el cliente avanzan órdenes de la versión 1 y de la 2, y no tocan el esquema', async () => {
    await preparar(entorno, 'ordenes/o3', sinEsquema(orden('0003', 'c1', 't1', 'v1', { estado: 'LISTO' })));
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO' });
    const firma = { fechaHora: AHORA, usuarioId: 'c1', usuarioNombre: 'N' };
    await assertSucceeds(updateDoc(ref(s.c1, 'ordenes/o3'), { conformidad: firma }));
    await assertSucceeds(updateDoc(ref(s.c1, 'ordenes/o1'), { conformidad: firma }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o3'), { esquema: 2 }));
    await preparar(entorno, 'ordenes/o4', sinEsquema(orden('0004', 'c1', 't1', 'v1')));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o4'), { ...conCambio('EN_REPARACION', 't1'), esquema: 2 }));
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o4'), { ...conCambio('EN_REPARACION', 't1') }));
  });

  it('el técnico y el cliente no tocan las instantáneas', async () => {
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { tecnico: resumenTecnico('t2') }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { ...conCambio('EN_REPARACION', 't1'), cliente: resumenCliente('c2') }));
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO' });
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), {
      conformidad: { fechaHora: AHORA, usuarioId: 'c1', usuarioNombre: 'N' }, cliente: resumenCliente('c2'),
    }));
  });

  it('el técnico asignado inicia la reparación y marca la orden como lista, en ese orden', async () => {
    const reparacion = [...historialInicial(), cambio('EN_REPARACION', 't1', AHORA)];
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { ...conCambio('LISTO', 't1') }));
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', historial: reparacion }));
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o1'), {
      estado: 'LISTO', historial: [...reparacion, cambio('LISTO', 't1', AHORA)], avisosEnviados: 1, ultimoAviso: AHORA,
    }));
  });

  it('el técnico no edita la orden, no la reasigna y no toca la de otro técnico', async () => {
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { falla: 'Otra falla' }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { tecnicoId: 't2', acceso: ['c1', 't2'] }));
    await assertFails(updateDoc(ref(s.t2, 'ordenes/o1'), { ...conCambio('EN_REPARACION', 't2') }));
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), { ...conCambio('EN_REPARACION', 'c1') }));
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
    const conformidad = { fechaHora: AHORA, usuarioId: 'c1', usuarioNombre: 'Nombre' };
    // Anular: solo desde Ingresado
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('ANULADA', 'j1'), motivoAnulacion: 'Duplicada' }));
    await preparar(entorno, 'ordenes/o1', { historial: historialInicial(), estado: 'LISTO', conformidad });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('ANULADA', 'j1'), motivoAnulacion: 'Tarde' }));
    // Entregar: no desde Ingresado ni En reparación, ni sin la conformidad ya guardada, ni inventándola en el mismo cambio
    await preparar(entorno, 'ordenes/o1', { historial: historialInicial(), estado: 'INGRESADO', conformidad: null });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('ENTREGADO', 'j1') }));
    await preparar(entorno, 'ordenes/o1', { historial: historialInicial(), estado: 'EN_REPARACION', conformidad });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('ENTREGADO', 'j1') }));
    await preparar(entorno, 'ordenes/o1', { historial: historialInicial(), estado: 'LISTO', conformidad: null });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('ENTREGADO', 'j1') }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('ENTREGADO', 'j1'), conformidad }));
    // Con la conformidad ya registrada sí
    await preparar(entorno, 'ordenes/o1', { historial: historialInicial(), estado: 'LISTO', conformidad });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('ENTREGADO', 'j1') }));
  });

  it('una vez guardada, la conformidad no se borra ni se cambia, tampoco al entregar', async () => {
    const conformidad = { fechaHora: AHORA, usuarioId: 'c1', usuarioNombre: 'Nombre' };
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO', conformidad });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('ENTREGADO', 'j1'), conformidad: null }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { conformidad: null }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { conformidad: { ...conformidad, usuarioId: 'j1' } }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('ENTREGADO', 'j1') }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { conformidad: null }));
  });

  it('el Jefe no inicia la reparación, no marca Listo ni reabre una orden entregada o anulada', async () => {
    const conformidad = { fechaHora: AHORA, usuarioId: 'c1', usuarioNombre: 'Nombre' };
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('EN_REPARACION', 'j1') }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('LISTO', 'j1') }));
    await preparar(entorno, 'ordenes/o1', { estado: 'ENTREGADO', conformidad });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('LISTO', 'j1') }));
    await preparar(entorno, 'ordenes/o1', { estado: 'ANULADA', motivoAnulacion: 'Duplicada' });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { ...conCambio('INGRESADO', 'j1') }));
  });

  it('el Jefe no agrega campos que la orden no define, pero sí registra la conformidad y las observaciones', async () => {
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { extra: 'campo inventado' }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', precio: 100 }));
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO' });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), {
      conformidad: { fechaHora: AHORA, usuarioId: 'j1', usuarioNombre: 'Jefe' },
    }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), {
      observacionesCliente: [{ texto: 'Ruido al frenar', acuerdo: 'Revisar', fechaHora: AHORA, usuarioId: 'j1' }],
    }));
  });

  it('el cliente firma su conformidad solo en su orden lista, una vez y con su propio usuario', async () => {
    const conformidad = (usuarioId) => ({ conformidad: { fechaHora: AHORA, usuarioId, usuarioNombre: 'Nombre' } });
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
  // Cada avance vive dentro de su orden: ordenes/{ordenId}/avances/{id}. Las reglas autorizan desde la orden padre
  // (get de la ruta), así que el avance no copia el `acceso` de la orden ni guarda su `ordenId`.
  const a1 = 'ordenes/o1/avances/a1';
  const nuevo = (ordenId = 'o1') => `ordenes/${ordenId}/avances/a9`;
  // Los avances se escriben con la orden En reparación (como AvanceRepository); las pruebas de otros estados lo cambian.
  beforeEach(async () => {
    await preparar(entorno, 'ordenes/o1', { estado: 'EN_REPARACION' });
    await preparar(entorno, 'ordenes/o2', { estado: 'EN_REPARACION' });
  });

  it('los leen el cliente de la orden, su técnico y el Jefe; nadie más', async () => {
    await assertSucceeds(getDoc(ref(s.c1, a1)));
    await assertSucceeds(getDoc(ref(s.t1, a1)));
    await assertSucceeds(getDoc(ref(s.jefe, a1)));
    await assertFails(getDoc(ref(s.c2, a1)));
    await assertFails(getDoc(ref(s.t2, a1)));
    await assertFails(getDoc(ref(s.anonimo, a1)));
    await assertFails(getDoc(ref(s.sinReclamos, a1)));
  });

  it('los avances de una orden se leen como su subcolección, sin filtrar por acceso ni por orden', async () => {
    await preparar(entorno, 'ordenes/o1/avances/a2', avance('t1', { fechaHora: 2 }));
    for (const quien of [s.c1, s.t1, s.jefe]) {
      const deLaOrden = await assertSucceeds(lista(quien, 'ordenes/o1/avances', orderBy('fechaHora')));
      const ids = deLaOrden.docs.map((d) => d.id).join(',');
      if (ids !== 'a1,a2') throw new Error(`Se esperaban a1,a2 en orden y llegaron ${ids}`);
    }
    await assertFails(lista(s.c2, 'ordenes/o1/avances'));
    await assertFails(lista(s.t2, 'ordenes/o1/avances'));
    await assertFails(lista(s.anonimo, 'ordenes/o1/avances'));
  });

  it('los avances de una orden inexistente solo los vería el Jefe, que lo lee todo', async () => {
    await assertFails(lista(s.c1, 'ordenes/o404/avances'));
    await assertFails(lista(s.t1, 'ordenes/o404/avances'));
    await assertFails(getDoc(ref(s.t1, 'ordenes/o404/avances/a1')));
  });

  it('la colección de primer nivel `avances` ya no tiene reglas', async () => {
    await preparar(entorno, 'avances/viejo', { ...avance('t1'), ordenId: 'o1', acceso: ['c1', 't1'] });
    await assertFails(getDoc(ref(s.jefe, 'avances/viejo')));
    await assertFails(getDoc(ref(s.t1, 'avances/viejo')));
    await assertFails(lista(s.t1, 'avances', where('acceso', 'array-contains', 't1'), where('ordenId', '==', 'o1')));
    await assertFails(setDoc(ref(s.t1, 'avances/a9'), { ...avance('t1'), ordenId: 'o1', acceso: ['c1', 't1'] }));
  });

  it('el técnico asignado registra avances en la subcolección de su orden', async () => {
    await assertSucceeds(setDoc(ref(s.t1, nuevo()), avance('t1')));
    const guardado = await getDoc(ref(s.t1, nuevo()));
    if (guardado.data().tecnicoId !== 't1') throw new Error('El avance debe guardar a su técnico');
  });

  it('nadie más registra avances, ni con campos de más, ni a nombre de otro técnico', async () => {
    await assertFails(setDoc(ref(s.t1, nuevo('o2')), avance('t1')));
    await assertFails(setDoc(ref(s.t1, nuevo()), avance('t2')));
    await assertFails(setDoc(ref(s.t2, nuevo()), avance('t2')));
    await assertFails(setDoc(ref(s.c1, nuevo()), avance('t1')));
    await assertFails(setDoc(ref(s.jefe, nuevo()), avance('t1')));
    await assertFails(setDoc(ref(s.t1, nuevo()), avance('t1', { acceso: ['c1', 't1'] })));
    await assertFails(setDoc(ref(s.t1, nuevo()), avance('t1', { ordenId: 'o1' })));
    await assertFails(setDoc(ref(s.t1, nuevo('o404')), avance('t1')));
  });

  it('el técnico edita y elimina solo sus avances; el Jefe no los toca', async () => {
    await assertSucceeds(updateDoc(ref(s.t1, a1), { descripcion: 'Fusible cambiado', tipo: 'TRABAJO_REALIZADO', fotos: [] }));
    await assertFails(updateDoc(ref(s.t1, a1), { tecnicoId: 't2' }));
    await assertFails(updateDoc(ref(s.t1, a1), { fechaHora: 9 }));
    await assertFails(updateDoc(ref(s.t2, a1), { descripcion: 'Intruso' }));
    await assertFails(updateDoc(ref(s.jefe, a1), { descripcion: 'Reescrito' }));
    await assertFails(updateDoc(ref(s.jefe, a1), { acceso: ['c1', 't2'] }));
    await assertFails(updateDoc(ref(s.c1, a1), { descripcion: 'Cliente' }));
    await assertFails(deleteDoc(ref(s.t2, a1)));
    await assertFails(deleteDoc(ref(s.jefe, a1)));
    await assertFails(deleteDoc(ref(s.c1, a1)));
    await assertSucceeds(deleteDoc(ref(s.t1, a1)));
  });

  it('fuera de En reparación nadie registra, edita ni elimina avances, pero se siguen leyendo', async () => {
    for (const estado of ['INGRESADO', 'LISTO', 'ENTREGADO', 'ANULADA']) {
      await preparar(entorno, 'ordenes/o1', { estado });
      await assertFails(setDoc(ref(s.t1, nuevo()), avance('t1')));
      await assertFails(updateDoc(ref(s.t1, a1), { descripcion: 'Fusible cambiado' }));
      await assertFails(deleteDoc(ref(s.t1, a1)));
      await assertSucceeds(getDoc(ref(s.c1, a1)));
      await assertSucceeds(lista(s.t1, 'ordenes/o1/avances'));
    }
    await preparar(entorno, 'ordenes/o1', { estado: 'EN_REPARACION' });
    await assertSucceeds(setDoc(ref(s.t1, nuevo()), avance('t1')));
    await assertSucceeds(updateDoc(ref(s.t1, a1), { descripcion: 'Fusible cambiado' }));
    await assertSucceeds(deleteDoc(ref(s.t1, a1)));
  });

  it('al reasignar la orden, el técnico nuevo lee y registra sin copiar permisos y el anterior ya no', async () => {
    await preparar(entorno, 'ordenes/o1', { tecnicoId: 't2', acceso: ['c1', 't2'] });
    await assertSucceeds(getDoc(ref(s.t2, a1)));
    await assertSucceeds(lista(s.t2, 'ordenes/o1/avances'));
    await assertSucceeds(setDoc(ref(s.t2, nuevo()), avance('t2')));
    await assertFails(getDoc(ref(s.t1, a1)));
    await assertFails(lista(s.t1, 'ordenes/o1/avances'));
    await assertFails(updateDoc(ref(s.t1, a1), { descripcion: 'Tarde' }));
    await assertFails(deleteDoc(ref(s.t1, a1)));
    await assertFails(setDoc(ref(s.t1, 'ordenes/o1/avances/a10'), avance('t1')));
    // Un avance ajeno sigue siendo de su autor: el técnico nuevo no lo edita
    await assertFails(updateDoc(ref(s.t2, a1), { descripcion: 'Ajeno' }));
  });
});

describe('fechas del servidor (S4): toda fecha que la app escribe está cerca de request.time', () => {
  // Las fechas son milisegundos del reloj del dispositivo; las reglas aceptan 15 minutos de diferencia con el servidor.
  const HORA = 60 * 60 * 1000;
  const pasada = () => Date.now() - HORA;
  const futura = () => Date.now() + HORA;
  const con = (...entradas) => [...historialInicial(), ...entradas];

  it('la orden nace con creadaEn y la fecha de su ingreso del servidor', async () => {
    for (const creadaEn of [pasada(), futura(), 1, String(AHORA), Timestamp.now()]) {
      await assertFails(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1', { creadaEn })));
    }
    await assertFails(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1', {
      historial: [cambio('INGRESADO', 'j1', pasada())],
    })));
    await assertFails(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1', {
      historial: [cambio('INGRESADO', 'j1', futura())],
    })));
    await assertSucceeds(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1', {
      creadaEn: Date.now(), historial: [cambio('INGRESADO', 'j1', Date.now())],
    })));
  });

  it('la entrada nueva del historial lleva la hora del servidor; las anteriores, aunque sean antiguas, no se miran', async () => {
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', historial: con(cambio('EN_REPARACION', 't1', pasada())) }));
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), { estado: 'EN_REPARACION', historial: con(cambio('EN_REPARACION', 't1', futura())) }));
    const antiguo = [cambio('INGRESADO', 'j1', 1)];
    await preparar(entorno, 'ordenes/o1', { historial: antiguo });
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o1'), {
      estado: 'EN_REPARACION', historial: [...antiguo, cambio('EN_REPARACION', 't1', Date.now())],
    }));
  });

  it('editadaEn y ultimoAviso solo se comprueban cuando la escritura los cambia', async () => {
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', editadaPor: 'j1', editadaEn: pasada() }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', editadaPor: 'j1', editadaEn: futura() }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida', editadaPor: 'j1', editadaEn: Date.now() }));
    // Una edición anterior (fecha antigua) se reenvía intacta al editar otra cosa
    await preparar(entorno, 'ordenes/o1', { editadaEn: 1, estado: 'LISTO', avisosEnviados: 1, ultimoAviso: 1 });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Otra', editadaEn: 1, ultimoAviso: 1 }));
    // Reenviar el aviso fija ultimoAviso: con la hora del servidor, no adelantada para saltarse las 24 horas
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { avisosEnviados: 2, ultimoAviso: pasada() }));
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { avisosEnviados: 2, ultimoAviso: Date.now() }));
  });

  it('el técnico marca Listo con ultimoAviso del servidor', async () => {
    const reparacion = [...historialInicial(), cambio('EN_REPARACION', 't1', AHORA)];
    await preparar(entorno, 'ordenes/o1', { estado: 'EN_REPARACION', historial: reparacion });
    const listo = (ultimoAviso) => ({
      estado: 'LISTO', historial: [...reparacion, cambio('LISTO', 't1', AHORA)], avisosEnviados: 1, ultimoAviso,
    });
    await assertFails(updateDoc(ref(s.t1, 'ordenes/o1'), listo(pasada())));
    await assertSucceeds(updateDoc(ref(s.t1, 'ordenes/o1'), listo(Date.now())));
  });

  it('un documento anterior que gana el campo vacío no fija ninguna fecha', async () => {
    await entorno.withSecurityRulesDisabled(async (contexto) => {
      const { editadaEn, ultimoAviso, observacionesCliente, ...sinCampos } = orden('0003', 'c1', 't1', 'v1', { creadaEn: 1 });
      await setDoc(doc(contexto.firestore(), 'ordenes/o3'), sinCampos);
    });
    await assertSucceeds(setDoc(ref(s.jefe, 'ordenes/o3'), orden('0003', 'c1', 't1', 'v1', { creadaEn: 1, falla: 'Otra' }), { merge: true }));
  });

  it('como la app, una escritura con merge que reenvía la orden entera con sus fechas antiguas pasa', async () => {
    const guardada = orden('0001', 'c1', 't1', 'v1', { creadaEn: 1 });
    await assertSucceeds(setDoc(ref(s.jefe, 'ordenes/o1'), {
      ...guardada, falla: 'Corregida', editadaPor: 'j1', editadaEn: Date.now(),
    }, { merge: true }));
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o1'), { ...guardada, creadaEn: Date.now() }, { merge: true }));
  });

  it('la conformidad lleva la hora del servidor, la firme el cliente o el Jefe', async () => {
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO' });
    const firma = (usuarioId, fechaHora) => ({ conformidad: { fechaHora, usuarioId, usuarioNombre: 'N' } });
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), firma('c1', pasada())));
    await assertFails(updateDoc(ref(s.c1, 'ordenes/o1'), firma('c1', futura())));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), firma('j1', pasada())));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { conformidad: { usuarioId: 'j1', usuarioNombre: 'N' } }));
    await assertSucceeds(updateDoc(ref(s.c1, 'ordenes/o1'), firma('c1', Date.now())));
    // Ya guardada, se reenvía intacta al entregar aunque pasen días
    await preparar(entorno, 'ordenes/o1', { conformidad: { fechaHora: 1, usuarioId: 'c1', usuarioNombre: 'N' } });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), {
      estado: 'ENTREGADO', historial: con(cambio('ENTREGADO', 'j1', Date.now())),
      conformidad: { fechaHora: 1, usuarioId: 'c1', usuarioNombre: 'N' },
    }));
  });

  it('la observación nueva del cliente lleva la hora del servidor; las anteriores no se miran', async () => {
    await preparar(entorno, 'ordenes/o1', { estado: 'LISTO' });
    const obs = (fechaHora, texto = 'Ruido al frenar') => ({ texto, acuerdo: 'Revisar', fechaHora, usuarioId: 'j1' });
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { observacionesCliente: [obs(pasada())] }));
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), { observacionesCliente: [obs(futura())] }));
    await preparar(entorno, 'ordenes/o1', { observacionesCliente: [obs(1)] });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { observacionesCliente: [obs(1), obs(Date.now(), 'Vibra el volante')] }));
    // La nueva va al final y de una en una: así no se esconde una fecha inventada entre las anteriores
    await assertFails(updateDoc(ref(s.jefe, 'ordenes/o1'), {
      observacionesCliente: [obs(pasada(), 'Otra'), obs(1), obs(Date.now(), 'Vibra el volante')],
    }));
  });

  it('la solicitud nace con creadaEn del servidor y se edita reenviando la suya antigua', async () => {
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1', { creadaEn: pasada() })));
    await assertFails(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1', { creadaEn: futura() })));
    await assertSucceeds(setDoc(ref(s.c1, 'solicitudes/s9'), solicitud('c1', 'v1', { creadaEn: Date.now() })));
    // s1 se sembró con creadaEn: 1; editarla con merge la reenvía y pasa, pero no se puede mover
    await assertSucceeds(setDoc(ref(s.c1, 'solicitudes/s1'), solicitud('c1', 'v1', { creadaEn: 1, descripcion: 'Ahora hace ruido' }), { merge: true }));
    await assertFails(updateDoc(ref(s.c1, 'solicitudes/s1'), { creadaEn: Date.now() }));
  });

  it('el avance nace con la hora del servidor y se edita reenviando la suya antigua', async () => {
    await preparar(entorno, 'ordenes/o1', { estado: 'EN_REPARACION' });
    await assertFails(setDoc(ref(s.t1, 'ordenes/o1/avances/a9'), avance('t1', { fechaHora: pasada() })));
    await assertFails(setDoc(ref(s.t1, 'ordenes/o1/avances/a9'), avance('t1', { fechaHora: futura() })));
    await assertFails(setDoc(ref(s.t1, 'ordenes/o1/avances/a9'), avance('t1', { fechaHora: null })));
    await assertSucceeds(setDoc(ref(s.t1, 'ordenes/o1/avances/a9'), avance('t1', { fechaHora: Date.now() })));
    // a1 se sembró con fechaHora: 1
    await assertSucceeds(setDoc(ref(s.t1, 'ordenes/o1/avances/a1'), avance('t1', { fechaHora: 1, descripcion: 'Fusible cambiado' }), { merge: true }));
  });
});

describe('notificaciones', () => {
  // Cada aviso vive en la bandeja de su dueño: usuarios/{usuarioId}/notificaciones/{id}. El dueño lo da la ruta.
  const avisoDe = (usuarioId) => ({ usuarioId, titulo: 't', mensaje: 'm', fechaHora: 1, leida: false, ordenId: null, esIngreso: false });

  it('cada usuario lee solo su bandeja, incluido el Jefe', async () => {
    await assertSucceeds(getDoc(ref(s.c1, 'usuarios/c1/notificaciones/n1')));
    await assertSucceeds(lista(s.c1, 'usuarios/c1/notificaciones'));
    await assertFails(getDoc(ref(s.c1, 'usuarios/c2/notificaciones/n2')));
    await assertFails(lista(s.c1, 'usuarios/c2/notificaciones'));
    await assertFails(getDoc(ref(s.jefe, 'usuarios/c1/notificaciones/n1')));
    await assertFails(lista(s.jefe, 'usuarios/c1/notificaciones'));
    await assertFails(getDoc(ref(s.t1, 'usuarios/c1/notificaciones/n1')));
    await assertFails(getDoc(ref(s.anonimo, 'usuarios/c1/notificaciones/n1')));
  });

  it('la bandeja se lee sin filtrar por usuarioId: la ruta ya dice de quién es', async () => {
    const leidas = await assertSucceeds(lista(s.c1, 'usuarios/c1/notificaciones', orderBy('fechaHora', 'desc')));
    assert.deepEqual(leidas.docs.map((d) => d.id), ['n1']);
  });

  it('el dueño de la ruta manda aunque el campo usuarioId diga otra cosa', async () => {
    await preparar(entorno, 'usuarios/c1/notificaciones/n7', avisoDe('c2'));
    await assertSucceeds(getDoc(ref(s.c1, 'usuarios/c1/notificaciones/n7')));
    await assertFails(getDoc(ref(s.c2, 'usuarios/c1/notificaciones/n7')));
  });

  it('el usuario solo puede marcar como leída una notificación de su bandeja', async () => {
    await assertFails(updateDoc(ref(s.c1, 'usuarios/c1/notificaciones/n1'), { leida: false }));
    await assertFails(updateDoc(ref(s.c1, 'usuarios/c1/notificaciones/n1'), { titulo: 'Cambiado' }));
    await assertFails(updateDoc(ref(s.c1, 'usuarios/c1/notificaciones/n1'), { leida: true, titulo: 'Cambiado' }));
    await assertFails(updateDoc(ref(s.c1, 'usuarios/c1/notificaciones/n1'), { usuarioId: 'c2' }));
    await assertFails(updateDoc(ref(s.c1, 'usuarios/c2/notificaciones/n2'), { leida: true }));
    await assertFails(updateDoc(ref(s.jefe, 'usuarios/c1/notificaciones/n1'), { leida: true }));
    await assertSucceeds(updateDoc(ref(s.c1, 'usuarios/c1/notificaciones/n1'), { leida: true }));
    // Como la app: vuelve a escribir el documento completo con merge y solo cambia `leida`
    await assertSucceeds(setDoc(ref(s.c1, 'usuarios/c1/notificaciones/n1'),
      { usuarioId: 'c1', titulo: 'Vehículo ingresado', mensaje: 'm', fechaHora: 1, leida: true, ordenId: 'o1', esIngreso: true }, { merge: true }));
  });

  it('nadie crea ni elimina notificaciones desde la app: lo hace el servidor', async () => {
    await assertFails(setDoc(ref(s.c1, 'usuarios/c1/notificaciones/n9'), avisoDe('c1')));
    await assertFails(setDoc(ref(s.c1, 'usuarios/c2/notificaciones/n9'), avisoDe('c2')));
    await assertFails(setDoc(ref(s.jefe, 'usuarios/c1/notificaciones/n9'), avisoDe('c1')));
    await assertFails(deleteDoc(ref(s.c1, 'usuarios/c1/notificaciones/n1')));
    await assertFails(deleteDoc(ref(s.jefe, 'usuarios/c1/notificaciones/n1')));
  });

  it('la colección de primer nivel `notificaciones` ya no existe para la app', async () => {
    await preparar(entorno, 'notificaciones/n1', avisoDe('c1'));
    await assertFails(getDoc(ref(s.c1, 'notificaciones/n1')));
    await assertFails(lista(s.c1, 'notificaciones', where('usuarioId', '==', 'c1')));
    await assertFails(updateDoc(ref(s.c1, 'notificaciones/n1'), { leida: true }));
    await assertFails(setDoc(ref(s.c1, 'notificaciones/n9'), avisoDe('c1')));
  });
});

describe('número de orden único (numerosOrden/{numero})', () => {
  const existe = async (ruta) => {
    let hay = false;
    await entorno.withSecurityRulesDisabled(async (c) => { hay = (await getDoc(doc(c.firestore(), ruta))).exists(); });
    return hay;
  };

  it('la orden y la reserva de su número se crean juntas', async () => {
    await assertSucceeds(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1')));
    if (!(await existe('numerosOrden/ORD-0009'))) throw new Error('La reserva no se creó');
  });

  it('la orden sin la reserva de su número se deniega', async () => {
    await assertFails(setDoc(ref(s.jefe, 'ordenes/o9'), orden('0009', 'c1', 't1', 'v1')));
  });

  it('la reserva sin su orden se deniega', async () => {
    await assertFails(setDoc(ref(s.jefe, 'numerosOrden/ORD-0009'), { ordenId: 'o9' }));
    await assertFails(setDoc(ref(s.jefe, 'numerosOrden/ORD-0001'), { ordenId: 'o1' }));
  });

  it('una segunda orden con un número ya reservado se deniega y no se crea', async () => {
    await assertFails(crearOrden(s.jefe, 'o9', orden('0001', 'c1', 't1', 'v1')));
    if (await existe('ordenes/o9')) throw new Error('La orden duplicada no debía crearse');
  });

  it('la reserva debe apuntar a esa orden y coincidir con su número', async () => {
    await assertFails(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1'), 'ORD-0009', { ordenId: 'o8' }));
    await assertFails(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1'), 'ORD-0008'));
  });

  it('el número es un texto', async () => {
    await assertFails(crearOrden(s.jefe, 'o9', orden('0009', 'c1', 't1', 'v1', { numero: 9 }), '9'));
  });

  it('solo el Jefe reserva, nadie la lee, la cambia ni la borra', async () => {
    const lote = (contexto) => {
      const l = writeBatch(bd(contexto));
      l.set(ref(contexto, 'ordenes/o9'), orden('0009', 'c1', 't1', 'v1'));
      l.set(ref(contexto, 'numerosOrden/ORD-0009'), { ordenId: 'o9' });
      return l.commit();
    };
    await assertFails(lote(s.c1));
    await assertFails(lote(s.t1));
    await assertFails(getDoc(ref(s.jefe, 'numerosOrden/ORD-0001')));
    await assertFails(updateDoc(ref(s.jefe, 'numerosOrden/ORD-0001'), { ordenId: 'o2' }));
    await assertFails(deleteDoc(ref(s.jefe, 'numerosOrden/ORD-0001')));
  });

  it('una orden anterior a las reservas se sigue editando sin ella', async () => {
    await entorno.withSecurityRulesDisabled(async (c) => { await deleteDoc(doc(c.firestore(), 'numerosOrden/ORD-0001')); });
    await assertSucceeds(updateDoc(ref(s.jefe, 'ordenes/o1'), { falla: 'Corregida' }));
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

  it('observar una orden es un get de un solo documento: el dueño lo lee y el ajeno no', async () => {
    await assertSucceeds(getDoc(ref(s.c1, 'ordenes/o1')));
    await assertFails(getDoc(ref(s.c2, 'ordenes/o1')));
    await assertFails(getDoc(ref(s.c1, 'ordenes/o2')));
  });

  it('el técnico consulta sus órdenes en curso con el filtro de acceso y recibe solo las suyas', async () => {
    await preparar(entorno, 'ordenes/o3', orden('0003', 'c1', 't1', 'v1', { estado: 'ENTREGADO' }));
    const enCurso = ['INGRESADO', 'EN_REPARACION'];
    const suyas = await assertSucceeds(lista(s.t1, 'ordenes', where('acceso', 'array-contains', 't1'), where('estado', 'in', enCurso)));
    if (suyas.size !== 1 || suyas.docs[0].id !== 'o1') throw new Error(`Se esperaba solo o1 y llegaron ${suyas.size}`);
    await assertFails(lista(s.t1, 'ordenes', where('estado', 'in', enCurso)));
    await assertFails(lista(s.t1, 'ordenes', where('acceso', 'array-contains', 't2'), where('estado', 'in', enCurso)));
  });

  it('el Jefe consulta las órdenes por estado sin filtro de acceso', async () => {
    await preparar(entorno, 'ordenes/o3', orden('0003', 'c1', 't1', 'v1', { estado: 'ENTREGADO' }));
    const activas = await assertSucceeds(lista(s.jefe, 'ordenes', where('estado', 'in', ['INGRESADO', 'EN_REPARACION', 'LISTO'])));
    if (activas.size !== 2 || activas.docs.some((d) => d.id === 'o3')) throw new Error('Se esperaban solo o1 y o2');
    await assertSucceeds(lista(s.jefe, 'ordenes', where('estado', '==', 'LISTO')));
  });

  it('el cliente consulta sus vehículos, órdenes y solicitudes por clienteId con el filtro de acceso', async () => {
    for (const coleccion of ['vehiculos', 'ordenes', 'solicitudes']) {
      const suyos = await assertSucceeds(lista(s.c1, coleccion, where('acceso', 'array-contains', 'c1'), where('clienteId', '==', 'c1')));
      if (suyos.size < 1 || suyos.docs.some((d) => d.data().clienteId !== 'c1')) throw new Error(`${coleccion}: solo debe recibir lo suyo`);
      await assertFails(lista(s.c1, coleccion, where('clienteId', '==', 'c1')));
      await assertFails(lista(s.c1, coleccion, where('acceso', 'array-contains', 'c2'), where('clienteId', '==', 'c2')));
    }
  });

  it('el Jefe consulta vehículos, órdenes y solicitudes por clienteId sin filtro de acceso', async () => {
    for (const coleccion of ['vehiculos', 'ordenes', 'solicitudes']) {
      await assertSucceeds(lista(s.jefe, coleccion, where('clienteId', '==', 'c1')));
    }
  });

  it('la búsqueda de clientes del Jefe (terminos y terminosPlaca con array-contains y límite) se acepta; al cliente y al técnico se les deniega', async () => {
    const porNombre = [where('terminos', 'array-contains', 'mar'), limit(30)];
    const porPlaca = [where('terminosPlaca', 'array-contains', 'abc'), limit(30)];
    await assertSucceeds(lista(s.jefe, 'usuarios', ...porNombre));
    await assertSucceeds(lista(s.jefe, 'vehiculos', ...porPlaca));
    for (const quien of [s.c1, s.t1]) {
      await assertFails(lista(quien, 'usuarios', ...porNombre));
      await assertFails(lista(quien, 'vehiculos', ...porPlaca));
    }
  });
});
