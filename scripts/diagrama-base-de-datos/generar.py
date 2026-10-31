#!/usr/bin/env python3
"""Genera el diagrama de clases del modelo de datos de Firestore (SVG) con cajas en cuadrícula y líneas ortogonales.

Uso: python3 -I scripts/diagrama-base-de-datos/generar.py docs/04-arquitectura/diagrama-base-de-datos.svg
"""
import sys
from xml.sax.saxutils import escape

CHAR, LH, HEAD, PAD = 7.3, 17, 46, 12
MARK = 36
GX, GY = 170, 84
ML, MT, MR, MB = 130, 130, 60, 90

AZUL, NARANJA, VERDE, GRIS, MORADO, AMBAR = "#1F4E79", "#C55A11", "#548235", "#6B6B6B", "#6B4E9B", "#BF9000"

def a(marca, texto):
    return (marca, texto)


CAJAS = {
    "vehiculos": dict(col=0, row=0, est="colección", color=AZUL, attrs=[
        a("ID", "id : string"), a("REF", "clienteId : string"), a("", "placa : string  (única)"), a("", "marca : string"),
        a("", "modelo : string"), a("", "anio : int"), a("", "color : string"), a("", "activo : boolean"),
        a("", "acceso : string[]")]),
    "usuarios": dict(col=1, row=0, est="colección", color=AZUL, attrs=[
        a("ID", "id : string"), a("", "nombres : string"), a("", "apellidos : string"), a("", "dni : string"),
        a("", "telefono : string"), a("", "correo : string"), a("", "rol : Rol"), a("", "activo : boolean"),
        a("", "tieneCuenta : boolean"), a("", "acceso : string[]")]),
    "tokensDispositivo": dict(col=2, row=0, est="colección", color=GRIS, attrs=[
        a("ID", "id : string  (= usuarioId)"), a("", "token : string  (FCM)")]),
    "solicitudes": dict(col=0, row=1, est="colección", color=AZUL, attrs=[
        a("ID", "id : string"), a("REF", "clienteId : string"), a("REF", "vehiculoId : string"),
        a("REF", "ordenId : string?"), a("", "descripcion : string"), a("", "sintomas : string[]"),
        a("", "fechaLlegada : long"), a("", "creadaEn : long"), a("", "fotos : Foto[]"),
        a("", "estado : EstadoSolicitud"), a("", "acceso : string[]")]),
    "ordenes": dict(col=1, row=1, est="colección", color=NARANJA, attrs=[
        a("ID", "id : string"), a("", "numero : string  (ORD-0001)"), a("REF", "clienteId : string"),
        a("REF", "vehiculoId : string"), a("REF", "tecnicoId : string"), a("REF", "solicitudId : string?"),
        a("", "falla : string"), a("", "kilometraje : int"), a("", "combustible : NivelCombustible"),
        a("", "observaciones : string"), a("", "estado : EstadoOrden"), a("", "historial : CambioEstado[]"),
        a("", "fotos : Foto[]"), a("", "motivoAnulacion : string?"), a("", "creadaEn : long"),
        a("", "editadaPor : string?"), a("", "editadaEn : long?"), a("", "avisosEnviados : int"),
        a("", "ultimoAviso : long?"), a("", "conformidad : Conformidad?"),
        a("", "observacionesCliente : ObservacionCliente[]"), a("", "acceso : string[]")]),
    "notificaciones": dict(col=2, row=1, est="colección · la crea el servidor", color=VERDE, attrs=[
        a("ID", "id : string  (n-<idEvento>)"), a("REF", "usuarioId : string"), a("REF", "ordenId : string"),
        a("", "titulo : string"), a("", "mensaje : string"), a("", "fechaHora : long"), a("", "leida : boolean"),
        a("", "esIngreso : boolean")]),
    "Cloud Storage": dict(col=0, row=2, est="almacenamiento de archivos", color=AMBAR, attrs=[
        a("", "ruta : fotos/{clienteId}/{archivo}"), a("", "formato : JPG | PNG"), a("", "tamaño máximo : 5 MB"),
        a("", "acceso : storage.rules")]),
    "avances": dict(col=1, row=2, est="colección", color=AZUL, attrs=[
        a("ID", "id : string"), a("REF", "ordenId : string"), a("REF", "tecnicoId : string"),
        a("", "tipo : TipoAvance"), a("", "descripcion : string"), a("", "fotos : Foto[]"),
        a("", "fechaHora : long"), a("", "acceso : string[]")]),
    "contadores": dict(col=2, row=2, est="colección", color=GRIS, attrs=[
        a("ID", "id : string"), a("", "ultimo : int  (último número de orden)")]),
    "Tipos embebidos": dict(col=0, row=3, est="objetos dentro del documento", color=MORADO, attrs=[
        a("", "Foto {id, ruta, fechaHora}"), a("", "CambioEstado {estado, fechaHora,"),
        a("", "    usuarioId, usuarioNombre?, motivo?}"), a("", "Conformidad {fechaHora, usuarioId,"),
        a("", "    usuarioNombre?}"), a("", "ObservacionCliente {texto, acuerdo,"),
        a("", "    fechaHora, usuarioId}")]),
    "Enumeraciones": dict(col=1, row=3, est="valores permitidos", color=MORADO, attrs=[
        a("", "Rol: CLIENTE, TECNICO, JEFE"), a("", "EstadoSolicitud: PENDIENTE,"), a("", "    ATENDIDA, CANCELADA"),
        a("", "EstadoOrden: INGRESADO, EN_REPARACION,"), a("", "    LISTO, ENTREGADO, ANULADA"),
        a("", "TipoAvance: HALLAZGO, TRABAJO_REALIZADO"), a("", "NivelCombustible: RESERVA, UN_CUARTO,"),
        a("", "    MEDIO, TRES_CUARTOS, LLENO")]),
    "codigosRestablecimiento": dict(col=2, row=3, est="colección · solo servidor, sin relaciones", color=GRIS, attrs=[
        a("ID", "id : string  (SHA-256 del correo)"), a("", "hash : string  (código cifrado)"),
        a("", "expira : long"), a("", "intentos : int"), a("", "enviadoEn : long")]),
}


def medir():
    ancho = 0
    for c in CAJAS.values():
        for _, t in c["attrs"]:
            ancho = max(ancho, MARK + len(t) * CHAR)
    return int(ancho + 2 * PAD + 8)


CW = medir()
for c in CAJAS.values():
    c["h"] = HEAD + 10 + len(c["attrs"]) * LH + 10
ALTO_FILA = [max(c["h"] for c in CAJAS.values() if c["row"] == r) for r in range(4)]
for c in CAJAS.values():
    c["w"], c["h"] = CW, ALTO_FILA[c["row"]]
    c["x"] = ML + c["col"] * (CW + GX)
    c["y"] = MT + sum(ALTO_FILA[: c["row"]]) + c["row"] * GY

ANCHO = ML + 3 * CW + 2 * GX + MR
ALTO = MT + sum(ALTO_FILA) + 3 * GY + MB


def lado(nombre, lado_, f):
    b = CAJAS[nombre]
    if lado_ == "top":
        return (b["x"] + f * b["w"], b["y"])
    if lado_ == "bottom":
        return (b["x"] + f * b["w"], b["y"] + b["h"])
    if lado_ == "left":
        return (b["x"], b["y"] + f * b["h"])
    return (b["x"] + b["w"], b["y"] + f * b["h"])


out = []


def w(s):
    out.append(s)


def texto(x, y, s, tam=12, peso="normal", color="#222", ancla="start", fam="Helvetica, Arial, sans-serif", estilo="normal", halo=False):
    h = ' stroke="#fff" stroke-width="4" paint-order="stroke"' if halo else ""
    w(f'<text x="{x:.1f}" y="{y:.1f}" font-family="{fam}" font-size="{tam}" font-weight="{peso}" font-style="{estilo}" '
      f'fill="{color}" text-anchor="{ancla}" xml:space="preserve"{h}>{escape(s)}</text>')


def linea(pts, discontinua=False):
    d = " ".join(("M" if i == 0 else "L") + f"{x:.1f},{y:.1f}" for i, (x, y) in enumerate(pts))
    ds = ' stroke-dasharray="7 5"' if discontinua else ""
    w(f'<path d="{d}" fill="none" stroke="#444" stroke-width="1.6"{ds}/>')


def mult(p, d, s):
    """Multiplicidad junto a un extremo; d es la dirección en la que la línea sale de la caja."""
    x, y = p
    if d[0] != 0:
        texto(x + d[0] * 16, y - 7, s, 13, "bold", "#000", "middle", halo=True)
    else:
        texto(x + 10, y + d[1] * 16 + 4, s, 13, "bold", "#000", "start", halo=True)


def nombre_h(pts, i, s):
    (x1, y1), (x2, y2) = pts[i], pts[i + 1]
    texto((x1 + x2) / 2, y1 - 7, s, 11, "normal", "#555", "middle", estilo="italic", halo=True)


def nombre_v(pts, i, s, dx=8):
    (x1, y1), (x2, y2) = pts[i], pts[i + 1]
    texto(x1 + dx, (y1 + y2) / 2 + 4, s, 11, "normal", "#555", "start", estilo="italic", halo=True)


w(f'<svg xmlns="http://www.w3.org/2000/svg" width="{ANCHO}" height="{ALTO}" viewBox="0 0 {ANCHO} {ALTO}">')
w(f'<rect width="{ANCHO}" height="{ALTO}" fill="#fff"/>')
texto(ANCHO / 2, 46, "PitLane · Diagrama de clases del modelo de datos (Cloud Firestore)", 22, "bold", "#1F2937", "middle")
texto(ANCHO / 2, 70, "Base de datos de documentos (NoSQL): cada caja es una colección; las líneas son referencias por id", 13,
      "normal", "#555", "middle")

# ---- Líneas (se dibujan antes que las cajas para que queden debajo)
# Fila superior
y = lado("vehiculos", "right", 0.30)[1]
p = [lado("vehiculos", "right", 0.30), lado("usuarios", "left", 0.30)]
linea(p); mult(p[0], (1, 0), "*"); mult(p[1], (-1, 0), "1"); nombre_h(p, 0, "clienteId")
p = [lado("usuarios", "right", 0.30), lado("tokensDispositivo", "left", 0.30)]
linea(p); mult(p[0], (1, 0), "1"); mult(p[1], (-1, 0), "0..1"); nombre_h(p, 0, "id = usuarioId")

# usuarios -> ordenes (cliente y técnico)
for f, nom in ((0.35, "clienteId"), (0.65, "tecnicoId")):
    p = [lado("usuarios", "bottom", f), lado("ordenes", "top", f)]
    linea(p); mult(p[0], (0, 1), "1"); mult(p[1], (0, -1), "*"); nombre_v(p, 0, nom)

# vehiculos -> solicitudes
p = [lado("vehiculos", "bottom", 0.40), lado("solicitudes", "top", 0.40)]
linea(p); mult(p[0], (0, 1), "1"); mult(p[1], (0, -1), "*"); nombre_v(p, 0, "vehiculoId")

# vehiculos -> ordenes (codo en el pasillo entre filas 0 y 1)
ym = CAJAS["vehiculos"]["y"] + ALTO_FILA[0] + GY / 2
p = [lado("vehiculos", "bottom", 0.82), (lado("vehiculos", "bottom", 0.82)[0], ym), (lado("ordenes", "top", 0.12)[0], ym),
     lado("ordenes", "top", 0.12)]
linea(p); mult(p[0], (0, 1), "1"); mult(p[3], (0, -1), "*"); nombre_h(p, 1, "vehiculoId")

# usuarios -> notificaciones (codo simétrico)
p = [lado("usuarios", "bottom", 0.88), (lado("usuarios", "bottom", 0.88)[0], ym), (lado("notificaciones", "top", 0.20)[0], ym),
     lado("notificaciones", "top", 0.20)]
linea(p); mult(p[0], (0, 1), "1"); mult(p[3], (0, -1), "*"); nombre_h(p, 1, "usuarioId")

# usuarios -> solicitudes (rodea por arriba y por la izquierda, sin cruzar nada)
yarr = CAJAS["usuarios"]["y"] - 36
xizq = ML - 64
p = [lado("usuarios", "top", 0.50), (lado("usuarios", "top", 0.50)[0], yarr), (xizq, yarr),
     (xizq, lado("solicitudes", "left", 0.25)[1]), lado("solicitudes", "left", 0.25)]
linea(p); mult(p[0], (0, -1), "1"); mult(p[4], (1, 0), "*"); nombre_h(p, 1, "clienteId")

# Fila central
p = [lado("solicitudes", "right", 0.12), lado("ordenes", "left", 0.12)]
linea(p); mult(p[0], (1, 0), "0..1"); mult(p[1], (-1, 0), "0..1"); nombre_h(p, 0, "ordenId / solicitudId")
p = [lado("ordenes", "right", 0.12), lado("notificaciones", "left", 0.12)]
linea(p); mult(p[0], (1, 0), "1"); mult(p[1], (-1, 0), "*"); nombre_h(p, 0, "ordenId")

# ordenes -> avances
p = [lado("ordenes", "bottom", 0.50), lado("avances", "top", 0.50)]
linea(p); mult(p[0], (0, 1), "1"); mult(p[1], (0, -1), "*"); nombre_v(p, 0, "ordenId")

# Fila inferior (pasillo entre filas 1 y 2)
ym2 = CAJAS["ordenes"]["y"] + ALTO_FILA[1] + GY / 2
p = [lado("ordenes", "bottom", 0.12), (lado("ordenes", "bottom", 0.12)[0], ym2), (lado("Cloud Storage", "top", 0.88)[0], ym2),
     lado("Cloud Storage", "top", 0.88)]
linea(p, True); nombre_h(p, 1, "fotos[].ruta")
p = [lado("solicitudes", "bottom", 0.40), lado("Cloud Storage", "top", 0.40)]
linea(p, True); nombre_v(p, 0, "fotos[].ruta")
p = [lado("ordenes", "bottom", 0.88), (lado("ordenes", "bottom", 0.88)[0], ym2), (lado("contadores", "top", 0.12)[0], ym2),
     lado("contadores", "top", 0.12)]
linea(p, True); nombre_h(p, 1, "numero (transacción)")
p = [lado("avances", "left", 0.30), lado("Cloud Storage", "right", 0.30)]
linea(p, True); nombre_h(p, 0, "fotos[].ruta")

# ---- Cajas
for nombre, c in CAJAS.items():
    x, y, bw, bh = c["x"], c["y"], c["w"], c["h"]
    w(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="4" fill="#fff" stroke="#333" stroke-width="1.4"/>')
    w(f'<path d="M{x},{y + 4} a4,4 0 0 1 4,-4 h{bw - 8} a4,4 0 0 1 4,4 V{y + HEAD} H{x} Z" fill="{c["color"]}"/>')
    texto(x + bw / 2, y + 17, f"«{c['est']}»", 10.5, "normal", "#fff", "middle", estilo="italic")
    texto(x + bw / 2, y + 36, nombre, 15, "bold", "#fff", "middle")
    ty = y + HEAD + 10 + 12
    for marca, t in c["attrs"]:
        if marca:
            texto(x + PAD, ty, marca, 10.5, "bold", "#C55A11" if marca == "REF" else "#1F4E79", "start")
        texto(x + PAD + MARK, ty, t, 12, "normal", "#222", "start", "Menlo, Consolas, monospace")
        ty += LH

# ---- Leyenda
ly = ALTO - 46
texto(ML, ly, "ID  identificador del documento     REF  referencia por id a otro documento: Firestore no la verifica, la cuidan las reglas y los repositorios",
      12, "normal", "#444")
texto(ML, ly + 20, "1 · * · 0..1  multiplicidad     línea continua  referencia entre colecciones     línea discontinua  ruta de archivo o numeración     ?  campo opcional",
      12, "normal", "#444")
w("</svg>")

with open(sys.argv[1], "w", encoding="utf-8") as f:
    f.write("\n".join(out))
