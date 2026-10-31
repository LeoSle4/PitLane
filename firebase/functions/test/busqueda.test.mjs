import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { normalizar, terminosDePersona, terminosDePlaca } from '../src/busqueda.js';

// Los mismos ejemplos y listas esperadas que `BusquedaTest.kt`: si uno cambia, el otro también.
describe('busqueda', () => {
  it('terminosDePersona reproduce el ejemplo acordado', () => {
    const esperado = [
      'mar', 'mari', 'maria', 'jos', 'jose', 'per', 'pere', 'perez',
      '723', '234', '345', '456', '567', '678', '7234', '2345', '3456', '4567', '5678',
      '72345', '23456', '34567', '45678', '723456', '234567', '345678',
      '7234567', '2345678', '72345678',
    ].sort();
    assert.deepEqual(terminosDePersona('María José', 'Pérez', '72345678'), esperado);
  });

  it('terminosDePlaca reproduce el ejemplo acordado', () => {
    const esperado = ['abc', 'bc1', 'c12', '123', 'abc1', 'bc12', 'c123', 'abc12', 'bc123', 'abc123'].sort();
    assert.deepEqual(terminosDePlaca('ABC-123'), esperado);
    assert.deepEqual(terminosDePlaca('abc123'), esperado);
  });

  it('la ñ y las tildes se normalizan', () => {
    assert.deepEqual(terminosDePersona('Ñandú', '', ''), ['nan', 'nand', 'nandu']);
  });

  it('la palabra con menos de tres letras no aporta', () => {
    assert.deepEqual(terminosDePersona('Al de la Cruz', 'Ng', ''), ['cru', 'cruz']);
  });

  it('normalizar quita tildes y separadores', () => {
    assert.equal(normalizar('  María-José '), 'maria jose');
    assert.equal(normalizar('--'), '');
  });

  it('la palabra se corta a 20 caracteres', () => {
    const terminos = terminosDePersona('a'.repeat(30), '', '');
    assert.equal(terminos.length, 18);
    assert.equal(terminos.at(-1).length, 20);
  });

  it('sin repetidos, ordenado y con un máximo de 120 conservando los más cortos', () => {
    const nombres = Array.from({ length: 10 }, (_, i) => 'p'.repeat(i + 1) + 'x'.repeat(18)).join(' ');
    const terminos = terminosDePersona(nombres, 'Zzzzzzzzzzzzzzzzzzzz', '12345678901234567890');
    assert.equal(terminos.length, 120);
    assert.deepEqual(terminos, [...new Set(terminos)].sort());
    assert.ok(terminos.includes('123') && terminos.includes('ppp'));
  });
});
