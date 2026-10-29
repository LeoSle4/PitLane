const PATRON_CORREO = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

const texto = (valor) => (typeof valor === 'string' ? valor : '');

/** Deja los datos de una cuenta como cadenas sin espacios sobrantes; la contraseña se conserva tal cual. */
export function normalizarCuenta(datos) {
  const origen = datos ?? {};
  return {
    nombres: texto(origen.nombres).trim(),
    apellidos: texto(origen.apellidos).trim(),
    dni: texto(origen.dni).trim(),
    telefono: texto(origen.telefono).trim(),
    correo: texto(origen.correo).trim(),
    contrasena: texto(origen.contrasena),
  };
}

const soloDigitos = (valor) => /^\d+$/.test(valor);

/** Mismas reglas que la validación de los formularios de la app; devuelve el primer error o nulo. */
export function validarCuenta(cuenta) {
  if (!cuenta.nombres) return 'Ingresa los nombres';
  if (!cuenta.apellidos) return 'Ingresa los apellidos';
  if (cuenta.dni.length !== 8 || !soloDigitos(cuenta.dni)) return 'El DNI debe tener 8 dígitos';
  if (cuenta.telefono.length !== 9 || !soloDigitos(cuenta.telefono) || !cuenta.telefono.startsWith('9')) {
    return 'El celular debe tener 9 dígitos y empezar con 9';
  }
  if (!PATRON_CORREO.test(cuenta.correo)) return 'Ingresa un correo válido';
  if (cuenta.contrasena.length < 8 || !/\p{L}/u.test(cuenta.contrasena) || !/\d/.test(cuenta.contrasena)) {
    return 'Mínimo 8 caracteres, con una letra y un número';
  }
  return null;
}
