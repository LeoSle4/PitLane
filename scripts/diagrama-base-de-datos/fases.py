#!/usr/bin/env python3
"""Genera los tres diagramas de PitLane por fases (SVG): conceptual, lógico NoSQL y físico de Firestore con cajas en cuadrícula y líneas ortogonales.

Uso: python3 -I scripts/diagrama-base-de-datos/fases.py docs/04-arquitectura
Escribe diagrama-conceptual.svg, diagrama-logico-nosql.svg y diagrama-fisico-firestore.svg en la carpeta indicada.
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
       "C1": (0, 2), "AVA": (1, 2), "C3": (2, 2), "D1": (0, 3), "D2": (1, 3), "D3": (2, 3)}


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
        self.nfilas = max(POS[r][1] for r in self.cajas) + 1
        self.filas = [max(c["h"] for r, c in self.cajas.items() if POS[r][1] == f) for f in range(self.nfilas)]
        for r, c in self.cajas.items():
            col, fila = POS[r]
            c["w"], c["h"] = self.cw, self.filas[fila]
            c["x"] = ML + col * (self.cw + GX)
            c["y"] = MT + sum(self.filas[:fila]) + fila * GY
        self.ancho = ML + 3 * self.cw + 2 * GX + MR
        self.alto = MT + sum(self.filas) + (self.nfilas - 1) * GY + MB

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
                if tipo in ("emb", "sub", "idx"):
                    fondo = {"emb": "#EFE9F7", "sub": "#E0F0EE", "idx": "#FFF4D6"}[tipo]
                    self.w(f'<rect x="{x + 6}" y="{ty - 13}" width="{bw - 12}" height="{LH}" rx="3" fill="{fondo}"/>')
                if marca:
                    col = {"REF": "#C55A11", "ID": "#1F4E79", "◆": "#6B4E9B", "SUB": "#0F766E", "IDX": "#8A6D00"}.get(marca, "#444")
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
                 a("activo"), a("terminosPlaca[]  (búsqueda)", "emb", "◆")]),
    "USU": caja("usuarios", "documento", AZUL,
                [a("id", marca="ID"), a("nombres"), a("apellidos"), a("dni"), a("telefono"), a("correo"), a("rol"),
                 a("activo"), a("tieneCuenta"), a("terminos[]  (búsqueda, solo clientes)", "emb", "◆")]),
    "DISP": caja("dispositivos", "documento · hijo de usuarios", GRIS,
                 [a("id = token", marca="ID"), a("token"), a("expiraEn  (vence solo)")]),
    "SOL": caja("solicitudes", "documento", AZUL,
                [a("id", marca="ID"), a("clienteId", marca="REF"), a("vehiculoId", marca="REF"), a("ordenId", marca="REF"),
                 a("descripcion"), a("sintomas[]"), a("fechaLlegada"), a("estado"), a("esquema  (2)"),
                 a("cliente  {instantánea}", "emb", "◆"), a("vehiculo  {instantánea}", "emb", "◆"),
                 a("fotos[]  Foto", "emb", "◆")]),
    "ORD": caja("ordenes", "documento · agregado principal", NARANJA,
                [a("id", marca="ID"), a("numero"), a("clienteId", marca="REF"), a("vehiculoId", marca="REF"),
                 a("tecnicoId", marca="REF"), a("solicitudId", marca="REF"), a("falla"), a("kilometraje"),
                 a("combustible"), a("observaciones"), a("estado"), a("motivoAnulacion"), a("creadaEn"), a("esquema  (2)"),
                 a("cliente  {instantánea}", "emb", "◆"), a("vehiculo  {instantánea}", "emb", "◆"),
                 a("tecnico  {instantánea}", "emb", "◆"), a("fotos[]  Foto", "emb", "◆"),
                 a("historial[]  CambioEstado", "emb", "◆"), a("conformidad  Conformidad", "emb", "◆"),
                 a("observacionesCliente[]", "emb", "◆")]),
    "NOTIF": caja("notificaciones", "documento · lo crea el servidor", VERDE,
                  [a("id", marca="ID"), a("usuarioId", marca="REF"), a("ordenId", marca="REF"), a("titulo"), a("mensaje"),
                   a("fechaHora"), a("leida"), a("esIngreso"), a("expiraEn  (vence solo)")]),
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

# ---------------------------------------------------------------- Fase 3: físico Cloud Firestore
def e(texto, marca=""):
    return (marca, texto, "attr")


def emb(texto):
    return ("◆", texto, "emb")


def sub(texto, marca="SUB"):
    return (marca, texto, "sub")


def idx(texto):
    return ("IDX", texto, "idx")


FISICO_CAJAS = {
    "VEH": caja("vehiculos", "colección · /vehiculos/{id}", AZUL,
                [e("id : string  (automático)", "ID"), e("clienteId : string", "REF"), e("placa : string  (única, mayúsculas)"),
                 e("marca : string"), e("modelo : string"), e("anio : number"), e("color : string"), e("activo : boolean"),
                 e("acceso : array<string>"),
                 e("terminosPlaca : array<string>  (búsqueda)"), idx("acceso ▸ clienteId"), idx("terminosPlaca  ·  array-contains")]),
    "USU": caja("usuarios", "colección · /usuarios/{id}", AZUL,
                [e("id : string  (u-cli-… · u-tec-… · u-jefe)", "ID"), e("nombres : string"), e("apellidos : string"),
                 e("dni : string"), e("telefono : string"), e("correo : string"), e("rol : string  (CLIENTE|TECNICO|JEFE)"),
                 e("activo : boolean"), e("tieneCuenta : boolean"), e("acceso : array<string>"),
                 e("terminos : array<string>  (solo clientes)"), idx("terminos  ·  array-contains"),
                 sub("subcolección  tokensDispositivo/{token}", "SUB"), sub("    token : string  (= id del documento)", ""),
                 sub("    expiraEn : timestamp", ""), idx("expiraEn  ·  política TTL")]),
    "DISP": caja("placas", "colección · /placas/{placa}  índice de unicidad", GRIS,
                 [e("id : string  (= vehiculos.placa)", "ID"), e("vehiculoId : string", "REF"), e("clienteId : string", "REF"),
                  e("Se crea en el mismo lote que su vehículo"), e("(Firestore solo crea un id libre)")]),
    "SOL": caja("solicitudes", "colección · /solicitudes/{id}", AZUL,
                [e("id : string  (automático)", "ID"), e("clienteId : string", "REF"), e("vehiculoId : string", "REF"),
                 e("ordenId : string?", "REF"), e("descripcion : string"), e("sintomas : array<string>"),
                 e("fechaLlegada : number"), e("creadaEn : number"), e("estado : string"), e("acceso : array<string>"),
                 e("esquema : number  (2)"),
                 emb("cliente : map  ResumenCliente"), emb("vehiculo : map  ResumenVehiculo"), emb("fotos : array<map>  Foto"),
                 idx("acceso ▸ vehiculoId"), idx("acceso ▸ clienteId")]),
    "ORD": caja("ordenes", "colección · /ordenes/{id}", NARANJA,
                [e("id : string  (automático)", "ID"), e("numero : string  (ORD-0001)"), e("clienteId : string", "REF"),
                 e("vehiculoId : string", "REF"), e("tecnicoId : string", "REF"), e("solicitudId : string?", "REF"),
                 e("falla : string"), e("kilometraje : number"), e("combustible : string"), e("observaciones : string"),
                 e("estado : string"), e("motivoAnulacion : string?"), e("creadaEn : number"), e("editadaPor : string?"),
                 e("editadaEn : number?"), e("avisosEnviados : number"), e("ultimoAviso : number?"),
                 e("acceso : array<string>"), e("esquema : number  (2)"),
                 emb("cliente : map  ResumenCliente"), emb("vehiculo : map  ResumenVehiculo"),
                 emb("tecnico : map  ResumenTecnico"), emb("fotos : array<map>  Foto"),
                 emb("historial : array<map>  CambioEstado"), emb("conformidad : map?  Conformidad"),
                 emb("observacionesCliente : array<map>"), idx("acceso ▸ vehiculoId"), idx("acceso ▸ estado"),
                 idx("acceso ▸ clienteId")]),
    "NOTIF": caja("notificaciones", "colección · /notificaciones/{id}  la crea el servidor", VERDE,
                  [e("id : string  (n-<idEvento>)", "ID"), e("usuarioId : string", "REF"), e("ordenId : string", "REF"),
                   e("titulo : string"), e("mensaje : string"), e("fechaHora : number"), e("leida : boolean"),
                   e("esIngreso : boolean"), e("expiraEn : timestamp"), idx("expiraEn  ·  política TTL (90 días)")]),
    "C1": caja("Cloud Storage", "archivos · fotos/{clienteId}/{archivo}", AMBAR,
               [e("formato : JPG | PNG"), e("tamaño máximo : 5 MB"), e("acceso : storage.rules"),
                e("(lee firestore.get del técnico)")]),
    "AVA": caja("avances", "colección · /avances/{id}", AZUL,
                [e("id : string  (automático)", "ID"), e("ordenId : string", "REF"), e("tecnicoId : string", "REF"),
                 e("tipo : string  (HALLAZGO|TRABAJO_REALIZADO)"), e("descripcion : string"), e("fechaHora : number"),
                 e("acceso : array<string>"), emb("fotos : array<map>  Foto"), idx("acceso ▸ ordenId")]),
    "C3": caja("contadores", "colección · /contadores/ordenes", GRIS,
               [e("id : string  (ordenes)", "ID"), e("ultimo : number"), e("Se incrementa en una transacción"),
                e("(solo el Jefe crea órdenes)")]),
    "D1": caja("Tipos embebidos", "mapas dentro del documento", MORADO,
               [e("Foto {id, ruta, fechaHora}"), e("CambioEstado {estado, fechaHora, usuarioId,"),
                e("    usuarioNombre?, motivo?}"), e("Conformidad {fechaHora, usuarioId,"), e("    usuarioNombre?}"),
                e("ObservacionCliente {texto, acuerdo,"), e("    fechaHora, usuarioId}"),
                e("ResumenCliente {nombres, apellidos,"), e("    dni, telefono}"),
                e("ResumenVehiculo {placa, marca, modelo,"), e("    anio, color}"), e("ResumenTecnico {nombres, apellidos}")]),
    "D2": caja("Enumeraciones", "valores permitidos (string)", MORADO,
               [e("rol: CLIENTE, TECNICO, JEFE"), e("EstadoSolicitud: PENDIENTE,"), e("    ATENDIDA, CANCELADA"),
                e("EstadoOrden: INGRESADO, EN_REPARACION,"), e("    LISTO, ENTREGADO, ANULADA"),
                e("tipo: HALLAZGO, TRABAJO_REALIZADO"), e("combustible: RESERVA, UN_CUARTO,"),
                e("    MEDIO, TRES_CUARTOS, LLENO")]),
    "D3": caja("codigosRestablecimiento", "colección · solo servidor, sin relaciones", GRIS,
               [e("id : string  (SHA-256 del correo)", "ID"), e("hash : string  (código cifrado)"), e("expira : number"),
                e("intentos : number"), e("enviadoEn : number"), e("expiraEn : timestamp"), idx("expiraEn  ·  política TTL")]),
}
FISICO_ARISTAS = {
    "VEH-USU": ("clienteId", "N", "1", False),
    "USU-ORD-c": ("clienteId", "1", "N", False),
    "USU-ORD-t": ("tecnicoId", "1", "N", False),
    "VEH-SOL": ("vehiculoId", "1", "N", False),
    "VEH-ORD": ("vehiculoId", "1", "N", False),
    "USU-NOTIF": ("usuarioId", "1", "N", False),
    "USU-SOL": ("clienteId", "1", "N", False),
    "SOL-ORD": ("ordenId / solicitudId", "0..1", "0..1", False),
    "ORD-NOTIF": ("ordenId", "1", "N", False),
    "ORD-AVA": ("ordenId", "1", "N", False),
    "SOL-C1": ("fotos[].ruta", None, None, True),
    "ORD-C1": ("fotos[].ruta", None, None, True),
    "AVA-C1": ("fotos[].ruta", None, None, True),
    "ORD-C3": ("numero (transacción)", None, None, True),
}
FISICO_LEYENDA = [
    "Fase 3 · Modelo físico para Cloud Firestore: rutas de colección, tipos de Firestore, índices compuestos y políticas TTL. En MongoDB este diagrama sería otro (ver docs).",
    "ID id del documento     REF referencia por id (no verificada)     ◆ mapa embebido     SUB subcolección     IDX índice compuesto o política TTL     placas se escribe en el mismo lote que vehiculos",
]

if __name__ == "__main__":
    destino = sys.argv[1]
    Diagrama("PitLane · Fase 1: Modelo conceptual (entidad-relación)",
             "Entidades, atributos principales y relaciones, sin decidir todavía la tecnología de base de datos",
             CONCEPTUAL_CAJAS, CONCEPTUAL_ARISTAS, CONCEPTUAL_LEYENDA).render(f"{destino}/diagrama-conceptual.svg")
    Diagrama("PitLane · Fase 2: Modelo lógico NoSQL (entidad-relación para documentos)",
             "Colecciones, documentos y lo que va embebido frente a lo que se referencia por id",
             LOGICO_CAJAS, LOGICO_ARISTAS, LOGICO_LEYENDA).render(f"{destino}/diagrama-logico-nosql.svg")
    Diagrama("PitLane · Fase 3: Modelo físico (Cloud Firestore)",
             "Colecciones y subcolecciones reales, tipos de Firestore, índices y política TTL del proyecto",
             FISICO_CAJAS, FISICO_ARISTAS, FISICO_LEYENDA).render(f"{destino}/diagrama-fisico-firestore.svg")
