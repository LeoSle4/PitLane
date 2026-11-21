#!/usr/bin/env python3
"""Genera los documentos JSON del modelo físico de Firestore a partir del seed de la app.

Fuente de los datos: BaseDatosLocal.cargarSemilla (app/src/test/.../datasource/local/BaseDatosLocal.kt).
Las formas salen de Mapeadores.kt y de las reglas; `terminos` y `terminosPlaca` replican util/Busqueda.kt.

Uso: python3 -I scripts/modelo-fisico/generar_documentos.py docs/04-arquitectura/modelo-fisico/documentos
Los `Timestamp` de Firestore se escriben como {"$timestamp": "<ISO-8601>"}; el resto de las fechas son milisegundos.
"""
import json
import re
import sys
import unicodedata
from datetime import datetime, timedelta, timezone
from pathlib import Path

AHORA_DT = datetime(2026, 11, 9, 20, 0, 0, tzinfo=timezone.utc)  # "ahora" de referencia del ejemplo
AHORA = int(AHORA_DT.timestamp() * 1000)
HORA = 3_600_000
DIA = 24 * HORA


def ts(millis):
    """Un Timestamp de Firestore."""
    return {"$timestamp": datetime.fromtimestamp(millis / 1000, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}


# ----------------------------------------------------------------- búsqueda (réplica de util/Busqueda.kt)
def normalizar(texto):
    sin_marcas = "".join(c for c in unicodedata.normalize("NFD", texto.lower()) if not unicodedata.category(c).startswith("M"))
    return re.sub(r"[\W_]+", " ", sin_marcas).strip()


def subcadenas(texto):
    return [texto[i:i + n] for n in range(3, len(texto) + 1) for i in range(0, len(texto) - n + 1)]


def terminos_persona(nombres, apellidos, dni):
    palabras = [p for p in normalizar(f"{nombres} {apellidos}").split(" ") if p]
    prefijos = [p[:20][:i] for p in palabras for i in range(3, len(p[:20]) + 1)]
    terminos = set(prefijos + subcadenas(normalizar(dni).replace(" ", "")))
    if len(terminos) > 120:
        terminos = set(sorted(terminos, key=lambda t: (len(t), t))[:120])
    return sorted(terminos)


def terminos_placa(placa):
    return sorted(set(subcadenas(normalizar(placa).replace(" ", ""))))


# ----------------------------------------------------------------- seed
U = {  # id: (nombres, apellidos, dni, telefono, correo, rol, activo, tieneCuenta)
    "u-jefe": ("Freddy", "Quispe Mamani", "40123456", "987654321", "jefe@freddy.pe", "JEFE", True, True),
    "u-tec-luis": ("Luis", "Ramos Huanca", "45671234", "976543210", "luis@freddy.pe", "TECNICO", True, True),
    "u-tec-marco": ("Marco", "Ccori Torres", "46781235", "965432109", "marco@freddy.pe", "TECNICO", True, True),
    "u-tec-pedro": ("Pedro", "Salas Rojas", "41890123", "954321098", "pedro@freddy.pe", "TECNICO", False, True),
    "u-cli-ana": ("Ana", "Torres Vega", "71234567", "912345678", "cliente@freddy.pe", "CLIENTE", True, True),
    "u-cli-carlos": ("Carlos", "Mendoza Díaz", "72345678", "923456789", "carlos@freddy.pe", "CLIENTE", True, True),
    "u-cli-jorge": ("Jorge", "Paredes Luna", "43219876", "934567890", "", "CLIENTE", True, False),
    "u-cli-lucia": ("Lucía", "Rojas Soto", "74561239", "945678901", "nuevo@freddy.pe", "CLIENTE", True, True),
}
V = {  # id: (clienteId, placa, marca, modelo, anio, color)
    "v-yaris": ("u-cli-ana", "ABC-123", "Toyota", "Yaris", 2018, "Plata"),
    "v-accent": ("u-cli-ana", "XYZ-789", "Hyundai", "Accent", 2020, "Blanco"),
    "v-rio": ("u-cli-carlos", "BCD-456", "Kia", "Rio", 2019, "Rojo"),
    "v-versa": ("u-cli-carlos", "JKL-654", "Nissan", "Versa", 2016, "Gris"),
    "v-sail": ("u-cli-jorge", "FGH-321", "Chevrolet", "Sail", 2017, "Azul"),
}


def nombre(uid):
    return f"{U[uid][0]} {U[uid][1]}"


def foto(fid, recurso, hace):
    return {"id": fid, "ruta": f"recurso:{recurso}", "fechaHora": AHORA - hace}


def resumen_cliente(uid):
    n, a, dni, tel = U[uid][:4]
    return {"nombres": n, "apellidos": a, "dni": dni, "telefono": tel}


def resumen_vehiculo(vid):
    _, placa, marca, modelo, anio, color = V[vid]
    return {"placa": placa, "marca": marca, "modelo": modelo, "anio": anio, "color": color}


def resumen_tecnico(uid):
    return {"nombres": U[uid][0], "apellidos": U[uid][1]}


def cambio(estado, cuando, uid, motivo=None):
    return {"estado": estado, "fechaHora": cuando, "usuarioId": uid, "motivo": motivo, "usuarioNombre": nombre(uid)}


SOLICITUDES = {  # id: (cliente, vehiculo, descripcion, sintomas, fechaLlegada, fotos, estado, creadaEn, ordenId)
    "s-1": ("u-cli-ana", "v-yaris", "El auto no arranca por las mañanas y el tablero parpadea al girar la llave.",
            ["No arranca", "Tablero con fallas"], AHORA - 9 * DIA, [], "ATENDIDA", AHORA - 10 * DIA, "o-2"),
    "s-2": ("u-cli-ana", "v-accent", "Las luces delanteras fallan de noche y vuelven a prender solas.",
            ["Luces fallan"], AHORA - 30 * DIA, [], "CANCELADA", AHORA - 31 * DIA, None),
    "s-3": ("u-cli-carlos", "v-versa", "La batería se descarga en dos días aunque el auto no se use. Sospecho de una fuga de corriente.",
            ["Batería se descarga"], AHORA + 2 * DIA, [foto("f-s3-1", "foto_demo_bateria", 3 * HORA)], "PENDIENTE", AHORA - 3 * HORA, None),
    "s-4": ("u-cli-carlos", "v-rio", "El motor se apaga en los semáforos y se enciende la luz de batería.",
            ["No arranca", "Batería se descarga"], AHORA - 2 * DIA, [], "ATENDIDA", AHORA - 3 * DIA, "o-3"),
    "s-5": ("u-cli-ana", "v-accent", "El vidrio eléctrico del copiloto no sube ni baja y el botón no responde.",
            ["Otro"], AHORA + 1 * DIA, [], "PENDIENTE", AHORA - 5 * HORA, None),
}

ANULACION = "Registrada por error: el vehículo no ingresó al taller"
ORDENES = {  # id: dict con los campos propios de la orden
    "o-1": dict(numero="ORD-0001", cliente="u-cli-jorge", vehiculo="v-sail", solicitud=None,
                falla="Revisión del sistema de carga", km=84500, combustible="MEDIO",
                obs="Rayón leve en la puerta trasera derecha.",
                fotos=[foto("f-o1-1", "foto_demo_frente", 20 * DIA), foto("f-o1-2", "foto_demo_lateral", 20 * DIA)],
                tecnico="u-tec-marco", estado="ANULADA",
                historial=[cambio("INGRESADO", AHORA - 20 * DIA, "u-jefe"),
                           cambio("ANULADA", AHORA - 20 * DIA + 10 * 60_000, "u-jefe", ANULACION)],
                creada=AHORA - 20 * DIA, anulacion=ANULACION, avisos=0, ultimo=None),
    "o-2": dict(numero="ORD-0002", cliente="u-cli-ana", vehiculo="v-yaris", solicitud="s-1",
                falla="El auto no arranca por las mañanas y el tablero parpadea al girar la llave.", km=61200,
                combustible="TRES_CUARTOS", obs="Sin golpes visibles. Espejo izquierdo con rajadura.",
                fotos=[foto("f-o2-1", "foto_demo_frente", 9 * DIA), foto("f-o2-2", "foto_demo_lateral", 9 * DIA),
                       foto("f-o2-3", "foto_demo_tablero", 9 * DIA)],
                tecnico="u-tec-luis", estado="LISTO",
                historial=[cambio("INGRESADO", AHORA - 9 * DIA, "u-jefe"), cambio("EN_REPARACION", AHORA - 8 * DIA, "u-tec-luis"),
                           cambio("LISTO", AHORA - 6 * HORA, "u-tec-luis")],
                creada=AHORA - 9 * DIA, anulacion=None, avisos=1, ultimo=AHORA - 6 * HORA),
    "o-3": dict(numero="ORD-0003", cliente="u-cli-carlos", vehiculo="v-rio", solicitud="s-4",
                falla="El motor se apaga en los semáforos y se enciende la luz de batería.", km=47800, combustible="MEDIO",
                obs="Paragolpes delantero con raspones antiguos.",
                fotos=[foto("f-o3-1", "foto_demo_frente", 2 * DIA), foto("f-o3-2", "foto_demo_motor", 2 * DIA)],
                tecnico="u-tec-luis", estado="EN_REPARACION",
                historial=[cambio("INGRESADO", AHORA - 2 * DIA, "u-jefe"), cambio("EN_REPARACION", AHORA - 1 * DIA, "u-tec-luis")],
                creada=AHORA - 2 * DIA, anulacion=None, avisos=0, ultimo=None),
    "o-4": dict(numero="ORD-0004", cliente="u-cli-jorge", vehiculo="v-sail", solicitud=None,
                falla="Luces de freno intermitentes y radio que se apaga solo.", km=90350, combustible="UN_CUARTO",
                obs="Tapa del tablero floja.", fotos=[foto("f-o4-1", "foto_demo_lateral", 3 * HORA)],
                tecnico="u-tec-marco", estado="INGRESADO", historial=[cambio("INGRESADO", AHORA - 3 * HORA, "u-jefe")],
                creada=AHORA - 3 * HORA, anulacion=None, avisos=0, ultimo=None),
}
AVANCES = {
    "a-1": ("o-2", "u-tec-luis", "HALLAZGO", "Batería con 9.8 V en reposo y alternador sin carga. Terminales sulfatados.",
            [foto("f-a1-1", "foto_demo_bateria", 7 * DIA)], AHORA - 7 * DIA),
    "a-2": ("o-2", "u-tec-luis", "TRABAJO_REALIZADO", "Cambio de alternador y limpieza de terminales. Carga estable en 14.2 V.",
            [foto("f-a2-1", "foto_demo_motor", 1 * DIA), foto("f-a2-2", "foto_demo_bateria", 1 * DIA)], AHORA - 1 * DIA),
    "a-3": ("o-3", "u-tec-luis", "HALLAZGO", "Fuga de corriente de 0.9 A en el circuito del radio. Cableado pelado bajo el tablero.",
            [foto("f-a3-1", "foto_demo_tablero", 20 * HORA)], AHORA - 20 * HORA),
}
NOTIFICACIONES = {  # id: (usuario, titulo, mensaje, hace, leida, orden, tipo)
    "n-1": ("u-cli-ana", "Vehículo ingresado", "Tu vehículo ABC-123 fue ingresado. Orden ORD-0002", 9 * DIA, True, "o-2", "INGRESO"),
    "n-2": ("u-cli-ana", "Vehículo en reparación", "Tu vehículo ABC-123 está en reparación", 8 * DIA, True, "o-2", "CAMBIO_ESTADO"),
    "n-3": ("u-cli-ana", "Vehículo listo", "Tu vehículo ABC-123 está listo para recoger", 6 * HORA, False, "o-2", "CAMBIO_ESTADO"),
    "n-4": ("u-cli-carlos", "Vehículo ingresado", "Tu vehículo BCD-456 fue ingresado. Orden ORD-0003", 2 * DIA, True, "o-3", "INGRESO"),
    "n-5": ("u-cli-carlos", "Vehículo en reparación", "Tu vehículo BCD-456 está en reparación", 1 * DIA, False, "o-3", "CAMBIO_ESTADO"),
}


# ----------------------------------------------------------------- derivados
def acceso_usuario(uid):
    acc = [uid]
    for o in ORDENES.values():
        if o["cliente"] == uid and o["tecnico"] not in acc: acc.append(o["tecnico"])
        if o["tecnico"] == uid and o["cliente"] not in acc: acc.append(o["cliente"])
    return acc


def acceso_vehiculo(vid):
    acc = [V[vid][0]]
    for o in ORDENES.values():
        if o["vehiculo"] == vid and o["tecnico"] not in acc: acc.append(o["tecnico"])
    return acc


def ultima_actividad(uid):
    eventos = [(o["historial"][-1]["fechaHora"] if o["historial"] else o["creada"]) for o in ORDENES.values() if o["cliente"] == uid]
    eventos += [s[7] for s in SOLICITUDES.values() if s[0] == uid]
    return max(eventos) if eventos else None


def usuarios():
    docs = {}
    for uid, (n, a, dni, tel, correo, rol, activo, cuenta) in U.items():
        d = {"nombres": n, "apellidos": a, "dni": dni, "telefono": tel, "correo": correo, "rol": rol, "activo": activo, "tieneCuenta": cuenta}
        if rol == "CLIENTE":
            d["terminos"] = terminos_persona(n, a, dni)
        d["acceso"] = acceso_usuario(uid)
        if rol == "CLIENTE" and ultima_actividad(uid) is not None:
            d["ultimaActividad"] = ultima_actividad(uid)
        docs[uid] = d
    return docs


def vehiculos():
    return {vid: {"clienteId": c, "placa": p, "marca": m, "modelo": mo, "anio": an, "color": co, "activo": True,
                  "terminosPlaca": terminos_placa(p), "acceso": acceso_vehiculo(vid)}
            for vid, (c, p, m, mo, an, co) in V.items()}


def placas():
    return {p: {"vehiculoId": vid, "clienteId": c} for vid, (c, p, *_r) in V.items()}


def solicitudes():
    return {sid: {"clienteId": c, "vehiculoId": v, "descripcion": d, "sintomas": sin, "fechaLlegada": ll, "fotos": fo, "estado": est,
                  "creadaEn": cre, "ordenId": oid, "esquema": 2, "cliente": resumen_cliente(c), "vehiculo": resumen_vehiculo(v), "acceso": [c]}
            for sid, (c, v, d, sin, ll, fo, est, cre, oid) in SOLICITUDES.items()}


def ordenes():
    docs = {}
    for oid, o in ORDENES.items():
        docs[oid] = {
            "numero": o["numero"], "clienteId": o["cliente"], "vehiculoId": o["vehiculo"], "solicitudId": o["solicitud"],
            "falla": o["falla"], "kilometraje": o["km"], "combustible": o["combustible"], "observaciones": o["obs"], "fotos": o["fotos"],
            "tecnicoId": o["tecnico"], "estado": o["estado"], "historial": o["historial"], "creadaEn": o["creada"],
            "motivoAnulacion": o["anulacion"], "avisosEnviados": o["avisos"], "ultimoAviso": o["ultimo"],
            "editadaPor": None, "editadaEn": None, "conformidad": None, "observacionesCliente": [],
            "esquema": 2, "cliente": resumen_cliente(o["cliente"]), "vehiculo": resumen_vehiculo(o["vehiculo"]),
            "tecnico": resumen_tecnico(o["tecnico"]), "acceso": [o["cliente"], o["tecnico"]],
        }
    return docs


def avances():  # subcolección de la orden: la ruta dice de qué orden es (sin ordenId ni acceso)
    return {f"ordenes/{oid}/avances/{aid}": {"tecnicoId": t, "tipo": tipo, "descripcion": d, "fotos": fo, "fechaHora": fh}
            for aid, (oid, t, tipo, d, fo, fh) in AVANCES.items()}


def notificaciones():  # subcolección del usuario que la recibe (bandeja)
    return {f"usuarios/{u}/notificaciones/{nid}": {"usuarioId": u, "titulo": t, "mensaje": m, "fechaHora": AHORA - hace, "leida": le,
                                                   "ordenId": oid, "esIngreso": tipo == "INGRESO",
                                                   "expiraEn": ts(AHORA - hace + 90 * DIA)}
            for nid, (u, t, m, hace, le, oid, tipo) in NOTIFICACIONES.items()}


def numeros_orden():  # reserva: el id del documento ES el número, así Firestore no deja repetirlo
    return {o["numero"]: {"ordenId": oid} for oid, o in ORDENES.items()}


def dnis():  # reserva: el id del documento ES el DNI, así Firestore no deja repetirlo
    return {dni: {"usuarioId": uid} for uid, (_n, _a, dni, *_r) in U.items()}


def dispositivos():  # no están en el seed: ejemplo ilustrativo de la subcolección
    return {
        "usuarios/u-cli-ana/tokensDispositivo/fcm_token_ejemplo_ana_1": {"expiraEn": ts(AHORA + 60 * DIA)},
        "usuarios/u-cli-ana/tokensDispositivo/fcm_token_ejemplo_ana_2": {"expiraEn": ts(AHORA + 47 * DIA)},
        "usuarios/u-tec-luis/tokensDispositivo/fcm_token_ejemplo_luis_1": {"expiraEn": ts(AHORA + 60 * DIA)},
    }


def contadores():
    return {"ordenes": {"ultimo": max(int(o["numero"].split("-")[1]) for o in ORDENES.values())}}


COLECCIONES = [
    ("usuarios", "usuarios/{usuarioId}", usuarios), ("vehiculos", "vehiculos/{vehiculoId}", vehiculos),
    ("placas", "placas/{placa}", placas), ("solicitudes", "solicitudes/{solicitudId}", solicitudes),
    ("dnis", "dnis/{dni}", dnis), ("numerosOrden", "numerosOrden/{numero}", numeros_orden),
    ("ordenes", "ordenes/{ordenId}", ordenes), ("avances", "ordenes/{ordenId}/avances/{avanceId}", avances),
    ("notificaciones", "usuarios/{usuarioId}/notificaciones/{notificacionId}", notificaciones),
    ("contadores", "contadores/{contador}", contadores),
    ("tokensDispositivo", "usuarios/{usuarioId}/tokensDispositivo/{token}", dispositivos),
]

if __name__ == "__main__":
    destino = Path(sys.argv[1])
    destino.mkdir(parents=True, exist_ok=True)
    for nombre_col, ruta, fabrica in COLECCIONES:
        docs = fabrica()
        contenido = {"coleccion": nombre_col, "ruta": ruta, "documentos": docs}
        (destino / f"{nombre_col}.json").write_text(json.dumps(contenido, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{nombre_col}.json: {len(docs)} documentos")
