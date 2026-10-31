import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  ESPERA_ENTRE_PEDIDOS_MS, MAXIMO_INTENTOS, VIGENCIA_MS, cifrarCodigo, evaluarCodigo, idDeCorreo, normalizarCorreo, nuevoCodigo,
} from '../src/codigos.js';
import { correoValido, validarContrasena } from '../src/validacion.js';

describe('códigos de restablecimiento', () => {
  it('genera siempre 6 dígitos', () => {
    for (let i = 0; i < 200; i += 1) assert.match(nuevoCodigo(), /^\d{6}$/);
  });

  it('normaliza el correo para que la misma cuenta dé el mismo registro', () => {
    assert.equal(normalizarCorreo('  Ana@Correo.COM '), 'ana@correo.com');
    assert.equal(normalizarCorreo(undefined), '');
    assert.equal(idDeCorreo('ana@correo.com'), idDeCorreo(normalizarCorreo('ANA@correo.com')));
  });

  it('no guarda el código en texto plano', () => {
    assert.notEqual(cifrarCodigo('ana@correo.com', '123456'), '123456');
    assert.notEqual(cifrarCodigo('ana@correo.com', '123456'), cifrarCodigo('otro@correo.com', '123456'));
  });

  it('respeta la vigencia de 10 minutos, 5 intentos y 1 minuto entre pedidos', () => {
    assert.equal(VIGENCIA_MS, 600_000);
    assert.equal(MAXIMO_INTENTOS, 5);
    assert.equal(ESPERA_ENTRE_PEDIDOS_MS, 60_000);
  });

  it('distingue un código válido, incorrecto, agotado y vencido', () => {
    const correo = 'ana@correo.com';
    const registro = { hash: cifrarCodigo(correo, '123456'), expira: 1_000, intentos: 0 };
    assert.equal(evaluarCodigo(registro, correo, '123456', 500), 'valido');
    assert.equal(evaluarCodigo(registro, correo, '654321', 500), 'incorrecto');
    assert.equal(evaluarCodigo({ ...registro, intentos: 5 }, correo, '123456', 500), 'agotado');
    assert.equal(evaluarCodigo(registro, correo, '123456', 1_001), 'vencido');
    assert.equal(evaluarCodigo(null, correo, '123456', 500), 'vencido');
  });
});

describe('validación del correo y la contraseña', () => {
  it('aplica las mismas reglas que la app', () => {
    assert.equal(correoValido('ana@correo.com'), true);
    assert.equal(correoValido('ana@'), false);
    assert.equal(validarContrasena('Clave1234'), null);
    assert.equal(validarContrasena('corta1'), 'Mínimo 8 caracteres, con una letra y un número');
  });
});
