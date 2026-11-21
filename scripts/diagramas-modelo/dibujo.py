"""Minimal SVG drawing kit for the PitLane data-model diagrams.

Every connector is drawn before the shapes, so a line aimed at a shape's center is clipped by the shape's fill.
Relationship lines are given as explicit orthogonal waypoints; attribute lines are short straight segments.
"""
from xml.sax.saxutils import escape

SANS = "Helvetica, Arial, sans-serif"
MONO = "Menlo, Consolas, monospace"
TINTA = "#1f2937"
ROJO = "#b91c1c"


def ancho_texto(texto, tam, negrita=False):
    """Rough text width in px for Helvetica; good enough to size boxes."""
    factor = 0.62 if negrita else 0.56
    return len(texto) * tam * factor


class Lienzo:
    def __init__(self, ancho, alto, fondo="#ffffff"):
        self.ancho, self.alto, self.fondo = ancho, alto, fondo
        self.fondos, self.lineas, self.formas, self.textos = [], [], [], []
        self.cajas = {}

    # ---------- primitives ----------
    def texto(self, x, y, s, tam=13, peso="normal", color=TINTA, ancla="middle", fam=SANS, estilo="normal",
              subrayado=None, halo=False, capa=None):
        h = ' stroke="#fff" stroke-width="4" paint-order="stroke"' if halo else ""
        dec = ""
        if subrayado == "solido":
            dec = ' text-decoration="underline"'
        out = (f'<text x="{x:.1f}" y="{y:.1f}" font-family="{fam}" font-size="{tam}" font-weight="{peso}" '
               f'font-style="{estilo}" fill="{color}" text-anchor="{ancla}" dominant-baseline="central" xml:space="preserve"{dec}{h}>'
               f'{escape(s)}</text>')
        (capa if capa is not None else self.textos).append(out)
        if subrayado == "punteado":
            w = ancho_texto(s, tam, peso == "bold")
            x0 = x - w / 2 if ancla == "middle" else x
            (capa if capa is not None else self.textos).append(
                f'<line x1="{x0:.1f}" y1="{y + tam * 0.62:.1f}" x2="{x0 + w:.1f}" y2="{y + tam * 0.62:.1f}" '
                f'stroke="{color}" stroke-width="1.2" stroke-dasharray="4 3"/>')

    def camino(self, puntos, doble=False, grosor=1.6, color=TINTA, discontinuo=False, flecha=False):
        d = " ".join(("M" if i == 0 else "L") + f"{x:.1f},{y:.1f}" for i, (x, y) in enumerate(puntos))
        ds = ' stroke-dasharray="7 5"' if discontinuo else ""
        fin = ' marker-end="url(#flecha)"' if flecha else ""
        if doble:
            self.lineas.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="6" stroke-linejoin="miter"/>')
            self.lineas.append(f'<path d="{d}" fill="none" stroke="#fff" stroke-width="2.4" stroke-linejoin="miter"/>')
        else:
            self.lineas.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{grosor}"{ds}{fin}/>')

    # ---------- Chen shapes ----------
    def entidad(self, clave, x, y, nombre, debil=False, ancho=None, alto=None, relleno="#dbeafe", servicio=None,
                color_servicio="#1e40af"):
        """Entity box; servicio is an optional second line naming the Firebase tool that stores it."""
        alto = alto or (60 if servicio else 46)
        w = ancho or max(150, ancho_texto(nombre, 14, True) + 36, ancho_texto(servicio or "", 11) + 30)
        self.cajas[clave] = (x, y, w, alto, "rect")
        r = f'<rect x="{x - w / 2:.1f}" y="{y - alto / 2:.1f}" width="{w:.1f}" height="{alto}" fill="{relleno}" stroke="{TINTA}" stroke-width="1.6"/>'
        self.formas.append(r)
        if debil:
            self.formas.append(f'<rect x="{x - w / 2 + 5:.1f}" y="{y - alto / 2 + 5:.1f}" width="{w - 10:.1f}" height="{alto - 10}" fill="none" stroke="{TINTA}" stroke-width="1.3"/>')
        if servicio:
            self.texto(x, y - 9, nombre, 14, "bold", capa=self.formas)
            self.texto(x, y + 13, servicio, 11, color=color_servicio, estilo="italic", capa=self.formas)
        else:
            self.texto(x, y, nombre, 14, "bold", capa=self.formas)

    def relacion(self, clave, x, y, nombre, identificadora=False, ancho=None, alto=74, relleno="#fef9c3",
                 servicio=None, color_servicio="#991b1b"):
        w = ancho or max(140, ancho_texto(nombre, 12.5) + 70, ancho_texto(servicio or "", 10.5) + 80)
        self.cajas[clave] = (x, y, w, alto, "rombo")
        pts = f"{x},{y - alto / 2} {x + w / 2},{y} {x},{y + alto / 2} {x - w / 2},{y}"
        self.formas.append(f'<polygon points="{pts}" fill="{relleno}" stroke="{TINTA}" stroke-width="1.6"/>')
        if identificadora:
            k = 9
            pts2 = f"{x},{y - alto / 2 + k * 0.75} {x + w / 2 - k},{y} {x},{y + alto / 2 - k * 0.75} {x - w / 2 + k},{y}"
            self.formas.append(f'<polygon points="{pts2}" fill="none" stroke="{TINTA}" stroke-width="1.2"/>')
        if servicio:
            self.texto(x, y - 7, nombre, 12.5, capa=self.formas)
            self.texto(x, y + 11, servicio, 10.5, color=color_servicio, estilo="italic", capa=self.formas)
        else:
            self.texto(x, y, nombre, 12.5, capa=self.formas)

    def jerarquia(self, clave, x, y, letra="d", r=15):
        self.cajas[clave] = (x, y, 2 * r, 2 * r, "circulo")
        self.formas.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#ede9fe" stroke="{TINTA}" stroke-width="1.6"/>')
        self.texto(x, y, letra, 13, "bold", capa=self.formas)

    def atributo(self, duenio, x, y, nombre, clave=None, derivado=False, multivaluado=False, tam=12, desde=None,
                 relleno="#fff"):
        """Ellipse joined to its owner by a straight line. clave: 'solido' (key) or 'punteado' (partial key).
        desde: point on the owner's border where the line starts (default: the owner's center)."""
        w = max(96, ancho_texto(nombre, tam) + 30)
        h = 34
        ox, oy = desde or self.cajas[duenio][:2]
        self.lineas.append(f'<line x1="{ox}" y1="{oy}" x2="{x}" y2="{y}" stroke="{TINTA}" stroke-width="1.2"/>')
        borde = ' stroke-dasharray="5 4"' if derivado else ""
        self.formas.append(f'<ellipse cx="{x}" cy="{y}" rx="{w / 2:.1f}" ry="{h / 2}" fill="{relleno}" stroke="{TINTA}" stroke-width="1.3"{borde}/>')
        if multivaluado:
            self.formas.append(f'<ellipse cx="{x}" cy="{y}" rx="{w / 2 - 4:.1f}" ry="{h / 2 - 4}" fill="none" stroke="{TINTA}" stroke-width="1.1"/>')
        self.texto(x, y, nombre, tam, subrayado=clave, capa=self.formas)
        self.cajas[f"{duenio}.{nombre}"] = (x, y, w, h, "elipse")
        return f"{duenio}.{nombre}"

    def cardinalidad(self, x, y, s):
        self.texto(x, y, s, 15, "bold", ROJO, halo=True)

    # ---------- class / table shapes ----------
    def clase(self, clave, x, y, estereotipo, nombre, filas, relleno="#dbeafe", ancho=None, nota=None, italica=False):
        """UML class box centered on (x, y). filas: list of (text, kind) or (text, kind, tag) with kind in '', 'id',
        'ref', 'emb', 'sub', 'sep'; tag is a short right-aligned note (the Firebase tool behind that field)."""
        tam, lh = 12.5, 19
        w = ancho or max(ancho_texto(nombre, 16, True) + 40, ancho_texto(estereotipo, 11) + 30,
                         *(ancho_texto(f[0], tam) * 1.02 + 60 + (ancho_texto(f[2], 10.5) + 20 if len(f) > 2 else 0)
                           for f in filas)) if filas else (ancho or 200)
        cab = 50 if estereotipo else 34
        h = cab + len(filas) * lh + 14 + (22 if nota else 0)
        x0, y0 = x - w / 2, y - h / 2
        self.cajas[clave] = (x, y, w, h, "rect")
        self.formas.append(f'<rect x="{x0:.1f}" y="{y0:.1f}" width="{w:.1f}" height="{h:.1f}" fill="{relleno}" stroke="{TINTA}" stroke-width="1.5"/>')
        if estereotipo:
            self.texto(x, y0 + 15, estereotipo, 11, color="#4b5563", capa=self.formas)
        self.texto(x, y0 + cab - 15, nombre, 16, "bold", estilo="italic" if italica else "normal", capa=self.formas)
        self.formas.append(f'<line x1="{x0:.1f}" y1="{y0 + cab:.1f}" x2="{x0 + w:.1f}" y2="{y0 + cab:.1f}" stroke="{TINTA}" stroke-width="1.2"/>')
        yy = y0 + cab + 7 + lh / 2
        for fila in filas:
            t, tipo = fila[0], fila[1]
            if len(fila) > 2:
                self.texto(x0 + w - 10, yy, fila[2], 10.5, "bold", "#0f766e", ancla="end", estilo="italic", capa=self.formas)
            if tipo == "sep":
                self.formas.append(f'<line x1="{x0 + 8:.1f}" y1="{yy:.1f}" x2="{x0 + w - 8:.1f}" y2="{yy:.1f}" stroke="#9ca3af" stroke-width="1" stroke-dasharray="3 3"/>')
                if t:
                    self.texto(x, yy, t, 10.5, color="#6b7280", halo=True, capa=self.formas)
            else:
                marca = {"id": "ID", "ref": "REF", "emb": "EMB", "sub": "SUB"}.get(tipo, "")
                color = {"ID": "#7c2d12", "EMB": "#c2410c", "SUB": "#a16207"}.get(marca.split(" ")[0] if marca else "", "#1e40af")
                if marca:
                    self.texto(x0 + 10, yy, marca, 10, "bold", color, ancla="start", capa=self.formas)
                clave = tipo == "id"
                self.texto(x0 + 52, yy, t, tam, "bold" if clave else "normal", ancla="start",
                           subrayado="solido" if clave else None, capa=self.formas)
            yy += lh
        if nota:
            self.formas.append(f'<line x1="{x0:.1f}" y1="{yy - 4:.1f}" x2="{x0 + w:.1f}" y2="{yy - 4:.1f}" stroke="{TINTA}" stroke-width="1"/>')
            self.texto(x, yy + 8, nota, 10.5, color="#92400e", estilo="italic", capa=self.formas)
        return (x0, y0, w, h)

    def borde(self, clave, lado, f=0.5):
        """Point on a box side: lado in top/bottom/left/right, f = fraction along that side."""
        x, y, w, h, _ = self.cajas[clave]
        return {"top": (x - w / 2 + f * w, y - h / 2), "bottom": (x - w / 2 + f * w, y + h / 2),
                "left": (x - w / 2, y - h / 2 + f * h), "right": (x + w / 2, y - h / 2 + f * h)}[lado]

    def rombo_uml(self, x, y, lado, relleno=True):
        """Composition diamond attached at (x, y) pointing toward lado (the owner's side)."""
        a, b = 13, 7
        dx, dy = {"right": (1, 0), "left": (-1, 0), "down": (0, 1), "up": (0, -1)}[lado]
        p0 = (x, y)
        p2 = (x - dx * 2 * a, y - dy * 2 * a)
        mid = (x - dx * a, y - dy * a)
        p1 = (mid[0] + dy * b, mid[1] + dx * b)
        p3 = (mid[0] - dy * b, mid[1] - dx * b)
        pts = " ".join(f"{px:.1f},{py:.1f}" for px, py in (p0, p1, p2, p3))
        self.formas.append(f'<polygon points="{pts}" fill="{TINTA if relleno else "#fff"}" stroke="{TINTA}" stroke-width="1.4"/>')

    def triangulo_uml(self, x, y, s=12, hacia="up"):
        """Hollow generalization arrow head with its tip at (x, y) pointing up or left."""
        if hacia == "left":
            pts = f"{x},{y} {x + 1.6 * s},{y - s} {x + 1.6 * s},{y + s}"
        else:
            pts = f"{x},{y} {x + s},{y + 1.6 * s} {x - s},{y + 1.6 * s}"
        self.formas.append(f'<polygon points="{pts}" fill="#fff" stroke="{TINTA}" stroke-width="1.5"/>')

    # ---------- output ----------
    def svg(self):
        defs = ('<defs><marker id="flecha" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto-start-reverse">'
                f'<path d="M0,0 L10,5 L0,10 z" fill="{TINTA}"/></marker></defs>')
        cuerpo = "\n".join(self.fondos + self.lineas + self.formas + self.textos)
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.ancho}" height="{self.alto}" '
                f'viewBox="0 0 {self.ancho} {self.alto}">\n{defs}\n'
                f'<rect width="100%" height="100%" fill="{self.fondo}"/>\n{cuerpo}\n</svg>\n')

    def guardar(self, ruta_svg):
        import subprocess
        with open(ruta_svg, "w", encoding="utf-8") as f:
            f.write(self.svg())
        ruta_png = ruta_svg[:-4] + ".png"
        subprocess.run(["rsvg-convert", "-z", "2", "-o", ruta_png, ruta_svg], check=True)
        return ruta_png
