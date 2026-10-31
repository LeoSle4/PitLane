#!/usr/bin/env python3
"""Genera los diagramas conceptual y lógico NoSQL de PitLane (SVG) con cajas en cuadrícula y líneas ortogonales.

Uso: python3 -I scripts/diagrama-base-de-datos/fases.py docs/04-arquitectura
Escribe diagrama-conceptual.svg y diagrama-logico-nosql.svg en la carpeta indicada.
"""
import sys
from xml.sax.saxutils import escape

LH, HEAD, PAD = 18, 46, 14
GX, GY = 190, 92
ML, MT, MR, MB = 140, 130, 70, 100

AZUL, NARANJA, VERDE, GRIS, MORADO, AMBAR = "#1F4E79", "#C55A11", "#548235", "#6B6B6B", "#6B4E9B", "#BF9000"
MONO = "Menlo, Consolas, monospace"
SANS = "Helvetica, Arial, sans-serif"

# Posición de cada rol en la cuadrícula 3×3 (columna, fila); es la misma en los dos diagramas
POS = {"VEH": (0, 0), "USU": (1, 0), "DISP": (2, 0), "SOL": (0, 1), "ORD": (1, 1), "NOTIF": (2, 1),
       "C1": (0, 2), "AVA": (1, 2), "C3": (2, 2)}


class Diagrama:
    def __init__(self, titulo, subtitulo, cajas, aristas, leyenda):
        self.titulo, self.subtitulo, self.cajas, self.aristas, self.leyenda = titulo, subtitulo, cajas, aristas, leyenda
        self.out = []
        self.medir()

    def medir(self):
        ancho = 0
        for c in self.cajas.values():
            for _, t, _ in c["items"]:
                ancho = max(ancho, len(t) * 7.3 + 30)
            ancho = max(ancho, len(c["nombre"]) * 9 + 30, len(c.get("est", "")) * 6.3 + 30)
        self.cw = int(ancho + 2 * PAD)
        for c in self.cajas.values():
            c["h"] = HEAD + 14 + len(c["items"]) * LH + 12
        self.filas = [max(c["h"] for r, c in self.cajas.items() if POS[r][1] == f) for f in range(3)]
        for r, c in self.cajas.items():
            col, fila = POS[r]
            c["w"], c["h"] = self.cw, self.filas[fila]
            c["x"] = ML + col * (self.cw + GX)
            c["y"] = MT + sum(self.filas[:fila]) + fila * GY
        self.ancho = ML + 3 * self.cw + 2 * GX + MR
        self.alto = MT + sum(self.filas) + 2 * GY + MB

    def p(self, rol, lado, f):
        b = self.cajas[rol]
        return {"top": (b["x"] + f * b["w"], b["y"]), "bottom": (b["x"] + f * b["w"], b["y"] + b["h"]),
                "left": (b["x"], b["y"] + f * b["h"]), "right": (b["x"] + b["w"], b["y"] + f * b["h"])}[lado]

    def w(self, s):
        self.out.append(s)

    def texto(self, x, y, s, tam=12, peso="normal", color="#222", ancla="start", fam=SANS, estilo="normal", halo=False):
        h = ' stroke="#fff" stroke-width="4" paint-order="stroke"' if halo else ""
        self.w(f'<text x="{x:.1f}" y="{y:.1f}" font-family="{fam}" font-size="{tam}" font-weight="{peso}" '
               f'font-style="{estilo}" fill="{color}" text-anchor="{ancla}" xml:space="preserve"{h}>{escape(s)}</text>')

    def linea(self, pts, discontinua=False):
        d = " ".join(("M" if i == 0 else "L") + f"{x:.1f},{y:.1f}" for i, (x, y) in enumerate(pts))
        ds = ' stroke-dasharray="7 5"' if discontinua else ""
        self.w(f'<path d="{d}" fill="none" stroke="#444" stroke-width="1.6"{ds}/>')

    def mult(self, p, d, s):
        x, y = p
        if d[0] != 0:
            self.texto(x + d[0] * 24, y - 7, s, 12.5, "bold", "#000", "middle", halo=True)
        else:
            self.texto(x + 9, y + d[1] * 18 + 4, s, 12.5, "bold", "#000", "start", halo=True)

    def nombre_h(self, pts, i, s):
        (x1, y1), (x2, _) = pts[i], pts[i + 1]
        self.texto((x1 + x2) / 2, y1 - 7, s, 11.5, "normal", "#444", "middle", estilo="italic", halo=True)

    def nombre_v(self, pts, i, s, dx=9, ancla="start"):
        (x1, y1), (_, y2) = pts[i], pts[i + 1]
        self.texto(x1 + dx, y1 + (y2 - y1) * 0.38 + 4, s, 11.5, "normal", "#444", ancla, estilo="italic", halo=True)

    # ---- aristas: cada una es (clave, texto, mult origen, mult destino, discontinua)
    def aristas_dibujar(self):
        ya = self.cajas["VEH"]["y"] + self.filas[0] + GY / 2          # pasillo entre filas 0 y 1
        yb = self.cajas["ORD"]["y"] + self.filas[1] + GY / 2          # pasillo entre filas 1 y 2
        arr, izq = self.cajas["USU"]["y"] - 40, ML - 70
        a = {k: v for k, v in self.aristas.items()}

        def poner(clave, pts, o, d, h=None, v=None, dirs=None):
            if clave not in a:
                return
            txt, m1, m2, disc = a[clave]
            self.linea(pts, disc)
            d1, d2 = dirs
            if m1:
                self.mult(pts[0], d1, m1)
            if m2:
                self.mult(pts[-1], d2, m2)
            if h is not None:
                self.nombre_h(pts, h, txt)
            elif v is not None:
                # De las dos líneas paralelas usuario-orden, la primera lleva su nombre a la izquierda para que no se pisen
                if clave == "USU-ORD-c":
                    self.nombre_v(pts, v, txt, -9, "end")
                else:
                    self.nombre_v(pts, v, txt)

        poner("VEH-USU", [self.p("VEH", "right", .30), self.p("USU", "left", .30)], 0, 0, h=0, dirs=((1, 0), (-1, 0)))
        poner("USU-DISP", [self.p("USU", "right", .30), self.p("DISP", "left", .30)], 0, 0, h=0, dirs=((1, 0), (-1, 0)))
        poner("USU-ORD-c", [self.p("USU", "bottom", .35), self.p("ORD", "top", .35)], 0, 0, v=0, dirs=((0, 1), (0, -1)))
        poner("USU-ORD-t", [self.p("USU", "bottom", .65), self.p("ORD", "top", .65)], 0, 0, v=0, dirs=((0, 1), (0, -1)))
        poner("VEH-SOL", [self.p("VEH", "bottom", .40), self.p("SOL", "top", .40)], 0, 0, v=0, dirs=((0, 1), (0, -1)))
        x0 = self.p("VEH", "bottom", .82)[0]
        poner("VEH-ORD", [self.p("VEH", "bottom", .82), (x0, ya), (self.p("ORD", "top", .12)[0], ya), self.p("ORD", "top", .12)],
              0, 0, h=1, dirs=((0, 1), (0, -1)))
        x1 = self.p("USU", "bottom", .88)[0]
        poner("USU-NOTIF", [self.p("USU", "bottom", .88), (x1, ya), (self.p("NOTIF", "top", .20)[0], ya), self.p("NOTIF", "top", .20)],
              0, 0, h=1, dirs=((0, 1), (0, -1)))
        poner("USU-SOL", [self.p("USU", "top", .50), (self.p("USU", "top", .50)[0], arr), (izq, arr),
                          (izq, self.p("SOL", "left", .25)[1]), self.p("SOL", "left", .25)],
              0, 0, h=1, dirs=((0, -1), (1, 0)))
        poner("SOL-ORD", [self.p("SOL", "right", .12), self.p("ORD", "left", .12)], 0, 0, h=0, dirs=((1, 0), (-1, 0)))
        poner("ORD-NOTIF", [self.p("ORD", "right", .12), self.p("NOTIF", "left", .12)], 0, 0, h=0, dirs=((1, 0), (-1, 0)))
        poner("ORD-AVA", [self.p("ORD", "bottom", .50), self.p("AVA", "top", .50)], 0, 0, v=0, dirs=((0, 1), (0, -1)))
        poner("SOL-C1", [self.p("SOL", "bottom", .40), self.p("C1", "top", .40)], 0, 0, v=0, dirs=((0, 1), (0, -1)))
        poner("ORD-C1", [self.p("ORD", "bottom", .12), (self.p("ORD", "bottom", .12)[0], yb), (self.p("C1", "top", .88)[0], yb),
                         self.p("C1", "top", .88)], 0, 0, h=1, dirs=((0, 1), (0, -1)))
        poner("ORD-C3", [self.p("ORD", "bottom", .88), (self.p("ORD", "bottom", .88)[0], yb), (self.p("C3", "top", .12)[0], yb),
                         self.p("C3", "top", .12)], 0, 0, h=1, dirs=((0, 1), (0, -1)))
        poner("AVA-C1", [self.p("AVA", "left", .30), self.p("C1", "right", .30)], 0, 0, h=0, dirs=((-1, 0), (1, 0)))

    def cajas_dibujar(self):
        for c in self.cajas.values():
            x, y, bw, bh = c["x"], c["y"], c["w"], c["h"]
            self.w(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="4" fill="#fff" stroke="#333" stroke-width="1.4"/>')
            self.w(f'<path d="M{x},{y + 4} a4,4 0 0 1 4,-4 h{bw - 8} a4,4 0 0 1 4,4 V{y + HEAD} H{x} Z" fill="{c["color"]}"/>')
            if c.get("est"):
                self.texto(x + bw / 2, y + 17, c["est"], 10.5, "normal", "#fff", "middle", estilo="italic")
            self.texto(x + bw / 2, y + 36, c["nombre"], 15, "bold", "#fff", "middle")
            ty = y + HEAD + 14
            for marca, t, tipo in c["items"]:
                if tipo == "emb":
                    self.w(f'<rect x="{x + 6}" y="{ty - 13}" width="{bw - 12}" height="{LH}" rx="3" fill="#EFE9F7"/>')
                if marca:
                    col = {"REF": "#C55A11", "ID": "#1F4E79", "◆": "#6B4E9B"}.get(marca, "#444")
                    self.texto(x + PAD, ty, marca, 10.5, "bold", col, "start")
                self.texto(x + PAD + 34, ty, t, 12, "normal", "#222", "start", MONO)
                ty += LH

    def render(self, ruta):
        self.w(f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.ancho}" height="{self.alto}" viewBox="0 0 {self.ancho} {self.alto}">')
        self.w(f'<rect width="{self.ancho}" height="{self.alto}" fill="#fff"/>')
        self.texto(self.ancho / 2, 46, self.titulo, 22, "bold", "#1F2937", "middle")
        self.texto(self.ancho / 2, 70, self.subtitulo, 13, "normal", "#555", "middle")
        self.aristas_dibujar()
        self.cajas_dibujar()
        for i, linea in enumerate(self.leyenda):
            self.texto(ML, self.alto - 52 + i * 20, linea, 12, "normal", "#444")
        self.w("</svg>")
        with open(ruta, "w", encoding="utf-8") as f:
            f.write("\n".join(self.out))


def caja(nombre, est, color, items):
    return dict(nombre=nombre, est=est, color=color, items=items)


def a(texto, tipo="attr", marca=""):
    return (marca, texto, tipo)


# ---------------------------------------------------------------- Fase 1: conceptual
CONCEPTUAL_CAJAS = {
    "VEH": caja("VEHÍCULO", "entidad", AZUL, [a("placa (única)"), a("marca"), a("modelo"), a("año"), a("color")]),
    "USU": caja("USUARIO", "entidad · Cliente, Técnico o Jefe", AZUL,
                [a("nombre completo"), a("DNI"), a("teléfono"), a("correo"), a("rol")]),
    "DISP": caja("DISPOSITIVO", "entidad", GRIS, [a("identificador del dispositivo")]),
    "SOL": caja("SOLICITUD", "entidad", AZUL, [a("descripción"), a("síntomas"), a("fecha de llegada"), a("estado")]),
    "ORD": caja("ORDEN DE INGRESO", "entidad", NARANJA,
                [a("número"), a("falla"), a("kilometraje"), a("combustible"), a("estado"), a("historial de estados"),
                 a("observaciones del cliente")]),
    "NOTIF": caja("NOTIFICACIÓN", "entidad", VERDE, [a("título"), a("mensaje"), a("fecha y hora"), a("leída")]),
    "C1": caja("FOTO", "entidad", AMBAR, [a("archivo"), a("fecha y hora")]),
    "AVA": caja("AVANCE", "entidad", AZUL, [a("tipo (hallazgo o trabajo)"), a("descripción"), a("fecha y hora")]),
    "C3": caja("CONFORMIDAD", "entidad", MORADO, [a("fecha y hora"), a("quién la firma")]),
}
CONCEPTUAL_ARISTAS = {
    "VEH-USU": ("pertenece a", "(1,1)", "(0,N)", False),
    "USU-DISP": ("usa", "(0,N)", "(1,1)", False),
    "USU-ORD-c": ("solicita", "(0,N)", "(1,1)", False),
    "USU-ORD-t": ("atiende", "(0,N)", "(1,1)", False),
    "VEH-SOL": ("se reporta en", "(0,N)", "(1,1)", False),
    "VEH-ORD": ("ingresa en", "(0,N)", "(1,1)", False),
    "USU-NOTIF": ("recibe", "(0,N)", "(1,1)", False),
    "USU-SOL": ("envía", "(0,N)", "(1,1)", False),
    "SOL-ORD": ("origina", "(0,1)", "(0,1)", False),
    "ORD-NOTIF": ("genera", "(0,N)", "(1,1)", False),
    "ORD-AVA": ("registra", "(0,N)", "(1,1)", False),
    "SOL-C1": ("incluye", "(0,N)", "(1,1)", False),
    "ORD-C1": ("incluye", "(0,N)", "(1,1)", False),
    "AVA-C1": ("incluye", "(0,N)", "(1,1)", False),
    "ORD-C3": ("se acepta con", "(0,1)", "(1,1)", False),
}
CONCEPTUAL_LEYENDA = [
    "Fase 1 · Modelo conceptual: independiente de la tecnología (sirve para Firestore, MongoDB o una base relacional). Solo entidades, atributos y relaciones.",
    "(mín,máx) cardinalidad de participación de la entidad en la relación     N = muchos     la línea con verbo es la relación",
]

# ---------------------------------------------------------------- Fase 2: lógico NoSQL
LOGICO_CAJAS = {
    "VEH": caja("vehiculos", "documento", AZUL,
                [a("id", marca="ID"), a("clienteId", marca="REF"), a("placa"), a("marca"), a("modelo"), a("anio"), a("color"),
                 a("activo")]),
    "USU": caja("usuarios", "documento", AZUL,
                [a("id", marca="ID"), a("nombres"), a("apellidos"), a("dni"), a("telefono"), a("correo"), a("rol"),
                 a("activo"), a("tieneCuenta")]),
    "DISP": caja("dispositivos", "documento · hijo de usuarios", GRIS,
                 [a("id", marca="ID"), a("usuarioId", marca="REF"), a("token")]),
    "SOL": caja("solicitudes", "documento", AZUL,
                [a("id", marca="ID"), a("clienteId", marca="REF"), a("vehiculoId", marca="REF"), a("ordenId", marca="REF"),
                 a("descripcion"), a("sintomas[]"), a("fechaLlegada"), a("estado"),
                 a("cliente  {instantánea}", "emb", "◆"), a("vehiculo  {instantánea}", "emb", "◆"),
                 a("fotos[]  Foto", "emb", "◆")]),
    "ORD": caja("ordenes", "documento · agregado principal", NARANJA,
                [a("id", marca="ID"), a("numero"), a("clienteId", marca="REF"), a("vehiculoId", marca="REF"),
                 a("tecnicoId", marca="REF"), a("solicitudId", marca="REF"), a("falla"), a("kilometraje"),
                 a("combustible"), a("observaciones"), a("estado"), a("motivoAnulacion"), a("creadaEn"),
                 a("cliente  {instantánea}", "emb", "◆"), a("vehiculo  {instantánea}", "emb", "◆"),
                 a("tecnico  {instantánea}", "emb", "◆"), a("fotos[]  Foto", "emb", "◆"),
                 a("historial[]  CambioEstado", "emb", "◆"), a("conformidad  Conformidad", "emb", "◆"),
                 a("observacionesCliente[]", "emb", "◆")]),
    "NOTIF": caja("notificaciones", "documento · lo crea el servidor", VERDE,
                  [a("id", marca="ID"), a("usuarioId", marca="REF"), a("ordenId", marca="REF"), a("titulo"), a("mensaje"),
                   a("fechaHora"), a("leida"), a("esIngreso")]),
    "C1": caja("¿Cuándo EMBEBER?", "criterio de modelado", MORADO,
               [a("Se lee siempre junto con el padre"), a("Crece poco y tiene tope (≤ decenas)"), a("Lo escribe el mismo actor"),
                a("No se consulta por sí solo"), a("Debe conservar el valor del momento"),
                a("Ej.: historial, fotos, conformidad,"), a("instantánea de cliente y vehículo")]),
    "AVA": caja("avances", "documento", AZUL,
                [a("id", marca="ID"), a("ordenId", marca="REF"), a("tecnicoId", marca="REF"), a("tipo"), a("descripcion"),
                 a("fechaHora"), a("fotos[]  Foto", "emb", "◆")]),
    "C3": caja("¿Cuándo REFERENCIAR?", "criterio de modelado", MORADO,
               [a("Crece sin tope (avances, avisos)"), a("Lo escribe otro actor distinto"), a("Se consulta por sí solo"),
                a("Se comparte entre varios padres"), a("Debe seguir vivo sin el padre"),
                a("Ej.: avances, notificaciones,"), a("vehículos, usuarios")]),
}
LOGICO_ARISTAS = {
    "VEH-USU": ("clienteId", "N", "1", False),
    "USU-DISP": ("usuarioId", "1", "N", False),
    "USU-ORD-c": ("clienteId", "1", "N", False),
    "USU-ORD-t": ("tecnicoId", "1", "N", False),
    "VEH-SOL": ("vehiculoId", "1", "N", False),
    "VEH-ORD": ("vehiculoId", "1", "N", False),
    "USU-NOTIF": ("usuarioId", "1", "N", False),
    "USU-SOL": ("clienteId", "1", "N", False),
    "SOL-ORD": ("ordenId / solicitudId", "0..1", "0..1", False),
    "ORD-NOTIF": ("ordenId", "1", "N", False),
    "ORD-AVA": ("ordenId", "1", "N", False),
}
LOGICO_LEYENDA = [
    "Fase 2 · Modelo lógico NoSQL (entidad-relación para documentos): decide qué se EMBEBE (◆, vive dentro del documento) y qué se REFERENCIA (REF, línea).",
    "ID identificador del documento     REF referencia por id (la base no la verifica)     ◆ embebido: composición, se lee y se escribe con su documento     [] lista     {} objeto",
]

if __name__ == "__main__":
    destino = sys.argv[1]
    Diagrama("PitLane · Fase 1: Modelo conceptual (entidad-relación)",
             "Entidades, atributos principales y relaciones, sin decidir todavía la tecnología de base de datos",
             CONCEPTUAL_CAJAS, CONCEPTUAL_ARISTAS, CONCEPTUAL_LEYENDA).render(f"{destino}/diagrama-conceptual.svg")
    Diagrama("PitLane · Fase 2: Modelo lógico NoSQL (entidad-relación para documentos)",
             "Colecciones, documentos y lo que va embebido frente a lo que se referencia por id",
             LOGICO_CAJAS, LOGICO_ARISTAS, LOGICO_LEYENDA).render(f"{destino}/diagrama-logico-nosql.svg")
