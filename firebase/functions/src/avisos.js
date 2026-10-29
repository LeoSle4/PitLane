/**
 * Aviso que corresponde a un cambio en una orden, o nulo si no hay que avisar. Reproduce lo que hacía la
 * versión local de la app: avisa al ingresar la orden, al pasar a En reparación, Listo o Anulada, y al reenviar
 * el aviso de listo (sube `avisosEnviados` estando Listo).
 */
export function avisoDeOrden(anterior, nueva, placa) {
  if (!anterior) {
    return { titulo: 'Vehículo ingresado', mensaje: `Tu vehículo ${placa} fue ingresado. Orden ${nueva.numero}` };
  }
  if (anterior.estado !== nueva.estado) return avisoPorEstado(nueva, placa);
  if (nueva.estado === 'LISTO' && (nueva.avisosEnviados ?? 0) > (anterior.avisosEnviados ?? 0)) {
    return { titulo: 'Vehículo listo', mensaje: `Tu vehículo ${placa} está listo para recoger` };
  }
  return null;
}

function avisoPorEstado(orden, placa) {
  switch (orden.estado) {
    case 'EN_REPARACION':
      return { titulo: 'Vehículo en reparación', mensaje: `Tu vehículo ${placa} está en reparación` };
    case 'LISTO':
      return { titulo: 'Vehículo listo', mensaje: `Tu vehículo ${placa} está listo para recoger` };
    case 'ANULADA':
      return { titulo: 'Orden anulada', mensaje: `Tu orden ${orden.numero} fue anulada` };
    default:
      return null;
  }
}
