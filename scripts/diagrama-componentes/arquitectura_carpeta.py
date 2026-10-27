"""Genera una variante del diagrama de componentes que agrega el marco de la carpeta arquitectura/ y las carpetas modelo/ y util/.

Parte del mismo diagrama que genera marcos.py y lo trabaja en una carpeta temporal, de modo que no modifica
diagrama-componentes.dot, .svg ni .png. Uso: python3 arquitectura_carpeta.py <salida.png>
"""
import os, re, subprocess, sys, tempfile

DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, DIR)
import marcos

ETIQUETAS_CARPETA = ["vista/", "viewmodel/", "repositorio/  ·  impl/", "datasource/  ·  local/", None]
EXTRA = 400   # ancho agregado a la derecha del lienzo
TOP = 60      # alto agregado arriba para el marco de arquitectura/ sin pisar el título
MONO = "Menlo, Monaco, Courier New, monospace"


def generar_base(trabajo):
    """Construye el diagrama centrado, igual que marcos.py, y devuelve el SVG resultante."""
    os.chdir(trabajo)
    SL = SR = 2.4
    for _ in range(6):
        eje, L, R = marcos.construir(SL, SR, "base.svg")
        d = (2 * eje - L - R) / 72
        if abs(d) < 0.03:
            break
        if d >= 0:
            SR += d
        else:
            SL += -d
    return open("base.svg").read()


def capas(svg):
    """Cajas (x, y, ancho, alto) de las cinco capas, en el orden vista, viewmodel, repository, fuentes y Firebase."""
    patron = r'<rect x="([-\d.]+)" y="([-\d.]+)" width="([\d.]+)" height="([\d.]+)" rx="10" fill="#FFFFFF" stroke="#9E9E9E"'
    return [tuple(float(v) for v in m.groups()) for m in re.finditer(patron, svg)]


def caja_texto(x, y, ancho, alto, fondo, borde, lineas, centrado=True):
    """Rectángulo redondeado con un título en negrita y líneas de texto debajo."""
    partes = [f'<rect x="{x:.1f}" y="{y:.1f}" width="{ancho:.1f}" height="{alto:.1f}" rx="10" fill="{fondo}" stroke="{borde}" stroke-width="1.5"/>']
    cx = x + ancho / 2
    ty = y + 26
    for i, linea in enumerate(lineas):
        peso = ' font-weight="bold"' if i == 0 else ""
        tam = 14 if i == 0 else 12
        partes.append(f'<text x="{cx:.1f}" y="{ty:.1f}" text-anchor="middle" font-family="Arial"{peso} font-size="{tam}">{linea}</text>')
        ty += 22 if i == 0 else 18
    return "\n".join(partes)


def flecha(puntos, etiqueta, color):
    """Línea discontinua con punta que pasa por los puntos indicados y una etiqueta sobre su último tramo."""
    d = "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in puntos)
    x2, y2 = puntos[-1]
    x1 = puntos[-2][0]
    return (f'<path d="{d}" fill="none" stroke="{color}" stroke-width="1.6" stroke-dasharray="7,4" marker-end="url(#punta_{color[1:]})"/>'
            f'<text x="{(x1 + x2) / 2:.1f}" y="{y2 - 6:.1f}" text-anchor="middle" font-family="Arial" font-size="11" fill="{color}">{etiqueta}</text>')


def marcador(color):
    return (f'<marker id="punta_{color[1:]}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto">'
            f'<path d="M0,0 L10,5 L0,10 z" fill="{color}"/></marker>')


def agregar(svg):
    cs = capas(svg)
    vista, vm, repo, ds = cs[0], cs[1], cs[2], cs[3]
    izq = min(c[0] for c in cs[:4]) - 24
    arriba = min(c[1] for c in cs[:4]) - 40
    der = max(c[0] + c[2] for c in cs[:4]) + 24
    abajo = max(c[1] + c[3] for c in cs[:4]) + 14

    piezas = [
        "<defs>" + marcador("#00695C") + marcador("#455A64") + "</defs>",
        f'<rect x="{izq:.1f}" y="{arriba:.1f}" width="{der - izq:.1f}" height="{abajo - arriba:.1f}" rx="14" fill="#F7F9FA" stroke="#37474F" stroke-width="2.2" stroke-dasharray="10,6"/>',
        f'<text x="{izq + 14:.1f}" y="{arriba + 25:.1f}" font-family="{MONO}" font-weight="bold" font-size="17" fill="#37474F">arquitectura/</text>',
    ]
    inserto_antes = "\n".join(piezas)

    # Carpeta de cada capa, a la derecha de su título
    etiquetas = []
    for caja, texto in zip(cs[:4], ETIQUETAS_CARPETA):
        if texto:
            etiquetas.append(f'<text x="{caja[0] + caja[2] - 12:.1f}" y="{caja[1] + 16:.1f}" text-anchor="end" font-family="{MONO}" font-size="12" font-weight="bold" fill="#546E7A">{texto}</text>')

    # Columna derecha: árbol de carpetas, modelo/ y util/
    x_ext = der + 90
    ancho = 270
    vm_cy = vm[1] + vm[3] / 2
    repo_cy = repo[1] + repo[3] / 2
    y_modelo = vm_cy - 42
    y_util = max(repo_cy - 42, y_modelo + 84 + 30)
    modelo = caja_texto(x_ext, y_modelo, ancho, 84, "#E0F2F1", "#00695C", ["modelo/", "Entidades · Enumeraciones", "Proyecciones · DatosFormulario"])
    util = caja_texto(x_ext, y_util, ancho, 84, "#ECEFF1", "#455A64", ["util/", "Resultado · Validador", "Fechas · Textos · Reloj"])

    x_codo = der + 40
    flechas = [
        flecha([(vm[0] + vm[2], vm_cy), (x_codo, vm_cy), (x_codo, y_modelo + 42), (x_ext, y_modelo + 42)], "usa", "#00695C"),
        flecha([(repo[0] + repo[2], repo_cy), (x_codo + 16, repo_cy), (x_codo + 16, y_util + 42), (x_ext, y_util + 42)], "usa", "#455A64"),
    ]
    # Insertar el marco de arquitectura/ antes de las capas, y lo demás al final del grafo
    primera = re.search(r'<rect x="[-\d.]+" y="[-\d.]+" width="[\d.]+" height="[\d.]+" rx="10" fill="#FFFFFF" stroke="#9E9E9E"', svg)
    svg = svg[:primera.start()] + inserto_antes + "\n" + svg[primera.start():]
    cierre = svg.rindex("</g>")
    svg = svg[:cierre] + "\n".join(etiquetas + [modelo, util] + flechas) + "\n" + svg[cierre:]

    # Ampliar el lienzo: margen izquierdo si el marco queda fuera, espacio arriba y columna a la derecha
    m = re.search(r'translate\(([\d.]+) ([\d.]+)\)', svg)
    tx, ty = float(m.group(1)), float(m.group(2))
    dx = max(0.0, 24 - (izq + tx))
    svg = svg.replace(m.group(0), f"translate({tx + dx:.1f} {ty + TOP:.1f})", 1)

    # El título conserva su posición en pantalla: se compensa el desplazamiento del grupo y se centra en el lienzo nuevo
    def mover_titulo(mt):
        x = float(mt.group(2)) + (EXTRA - dx) / 2
        y = float(mt.group(3)) - TOP
        return f'{mt.group(1)}x="{x:.2f}" y="{y:.2f}"{mt.group(4)}'
    svg = re.sub(r'(<text xml:space="preserve" text-anchor="start" )x="([-\d.]+)" y="([-\d.]+)"([^>]*>(?:Diagrama de Componentes|Aplicación móvil para))', mover_titulo, svg)

    # Fondo blanco que cubre todo el lienzo ampliado
    svg = re.sub(r'(<g id="graph0"[^>]*>)', r'\1\n<rect x="-5000" y="-5000" width="20000" height="20000" fill="#FFFFFF"/>', svg, count=1)

    def ampliar(mh):
        ancho = int(mh.group(1)) + EXTRA + int(dx)
        alto = int(mh.group(2)) + TOP
        return f'<svg width="{ancho}pt" height="{alto}pt"\n viewBox="0.00 0.00 {ancho:.2f} {alto:.2f}"'
    svg = re.sub(r'<svg width="(\d+)pt" height="(\d+)pt"\s+viewBox="0.00 0.00 ([\d.]+) ([\d.]+)"', ampliar, svg, count=1)
    return svg


if __name__ == "__main__":
    salida_png = os.path.abspath(sys.argv[1])
    trabajo = tempfile.mkdtemp()
    svg = agregar(generar_base(trabajo))
    ruta_svg = os.path.splitext(salida_png)[0] + ".svg"
    open(ruta_svg, "w").write(svg)
    subprocess.run(["rsvg-convert", "-z", "1.3", ruta_svg, "-o", salida_png], check=True)
    print("listo:", salida_png)
