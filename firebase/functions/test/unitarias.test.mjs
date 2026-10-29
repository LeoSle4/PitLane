import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { avisoDeOrden } from '../src/avisos.js';
import { normalizarCuenta, validarCuenta } from '../src/validacion.js';

const valida = () => ({
  nombres: 'Ana', apellidos: 'Torres', dni: '12345678', telefono: '987654321', correo: 'ana@correo.com', contrasena: 'Clave1234',
});

describe('validarCuenta', () => {
  it('acepta una cuenta válida', () => {
    assert.equal(validarCuenta(valida()), null);
  });

  it('rechaza cada campo inválido con el mensaje de la app', () => {
    assert.equal(validarCuenta({ ...valida(), nombres: '' }), 'Ingresa los nombres');
    assert.equal(validarCuenta({ ...valida(), apellidos: '' }), 'Ingresa los apellidos');
    assert.equal(validarCuenta({ ...valida(), dni: '1234567' }), 'El DNI debe tener 8 dígitos');
    assert.equal(validarCuenta({ ...valida(), dni: '1234567a' }), 'El DNI debe tener 8 dígitos');
    assert.equal(validarCuenta({ ...valida(), telefono: '887654321' }), 'El celular debe tener 9 dígitos y empezar con 9');
    assert.equal(validarCuenta({ ...valida(), telefono: '98765432' }), 'El celular debe tener 9 dígitos y empezar con 9');
    assert.equal(validarCuenta({ ...valida(), correo: 'sin-arroba' }), 'Ingresa un correo válido');
    assert.equal(validarCuenta({ ...valida(), contrasena: 'corta1' }), 'Mínimo 8 caracteres, con una letra y un número');
    assert.equal(validarCuenta({ ...valida(), contrasena: 'sololetras' }), 'Mínimo 8 caracteres, con una letra y un número');
    assert.equal(validarCuenta({ ...valida(), contrasena: '1234567890' }), 'Mínimo 8 caracteres, con una letra y un número');
  });

  it('normaliza espacios y descarta valores que no son texto', () => {
    const cuenta = normalizarCuenta({ ...valida(), nombres: '  Ana ', dni: 12345678, correo: ' ana@correo.com ' });
    assert.equal(cuenta.nombres, 'Ana');
    assert.equal(cuenta.dni, '');
    assert.equal(cuenta.correo, 'ana@correo.com');
    assert.deepEqual(normalizarCuenta(undefined), { nombres: '', apellidos: '', dni: '', telefono: '', correo: '', contrasena: '' });
  });
});

describe('avisoDeOrden', () => {
  const orden = (estado, extra = {}) => ({ numero: 'ORD-0001', estado, avisosEnviados: 0, ...extra });

  it('avisa el ingreso de una orden nueva', () => {
    assert.deepEqual(avisoDeOrden(null, orden('INGRESADO'), 'ABC-123'),
      { titulo: 'Vehículo ingresado', mensaje: 'Tu vehículo ABC-123 fue ingresado. Orden ORD-0001' });
  });

  it('avisa los cambios a En reparación, Listo y Anulada', () => {
    assert.equal(avisoDeOrden(orden('INGRESADO'), orden('EN_REPARACION'), 'ABC-123').titulo, 'Vehículo en reparación');
    assert.equal(avisoDeOrden(orden('EN_REPARACION'), orden('LISTO'), 'ABC-123').mensaje, 'Tu vehículo ABC-123 está listo para recoger');
    assert.equal(avisoDeOrden(orden('INGRESADO'), orden('ANULADA'), 'ABC-123').mensaje, 'Tu orden ORD-0001 fue anulada');
  });

  it('no avisa al entregar ni cuando nada relevante cambia', () => {
    assert.equal(avisoDeOrden(orden('LISTO'), orden('ENTREGADO'), 'ABC-123'), null);
    assert.equal(avisoDeOrden(orden('INGRESADO'), orden('INGRESADO', { falla: 'otra' }), 'ABC-123'), null);
  });

  it('avisa de nuevo cuando se reenvía el aviso de listo', () => {
    const reenviada = avisoDeOrden(orden('LISTO', { avisosEnviados: 1 }), orden('LISTO', { avisosEnviados: 2 }), 'ABC-123');
    assert.equal(reenviada.titulo, 'Vehículo listo');
    assert.equal(avisoDeOrden(orden('EN_REPARACION', { avisosEnviados: 0 }), orden('EN_REPARACION', { avisosEnviados: 1 }), 'ABC-123'), null);
  });
});
