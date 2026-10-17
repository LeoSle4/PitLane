import os, re, subprocess, sys, tempfile

DIR = os.path.dirname(os.path.abspath(__file__))
TMP = tempfile.mkdtemp()
D = 9  # profundidad de la caja 3D

def pares(txt):
    return [(float(a), float(b)) for a, b in re.findall(r'(-?\d+\.?\d*),(-?\d+\.?\d*)', txt)]

def leer_nodos(svg):
    r = {}
    for m in re.finditer(r'<g id="node\d+" class="node">\s*<title>(\w+)</title>(.*?)</g>', svg, re.S):
        pts = []
        for a in re.findall(r'(?:points|d)="([^"]+)"', m.group(2)):
            pts += pares(a)
        if pts:
            xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
            r[m.group(1)] = (min(xs), min(ys), max(xs), max(ys))
    return r

def leer_aristas(svg):
    r = {}
    for m in re.finditer(r'<g id="edge\d+" class="edge">\s*<title>(\w+)&#45;&gt;(\w+)</title>(.*?)</g>', svg, re.S):
        d = re.search(r'<path[^>]* d="([^"]+)"', m.group(3))
        if d: r[(m.group(1), m.group(2))] = pares(d.group(1))
    return r

def caja(n, ids, m=0):
    b = [n[i] for i in ids]
    return (min(c[0] for c in b) - m, min(c[1] for c in b) - m, max(c[2] for c in b) + m, max(c[3] for c in b) + m)

def cubo(l, t, r, b, estereotipo, nombre, fondo="#FFFFFF", borde="#9E9E9E", grosor=1.2):
    """Caja 3D (nodo de despliegue); su cara frontal arranca D puntos más abajo de t."""
    t = t + D
    cx = (l + r) / 2
    s = []
    s.append(f'<polygon points="{l:.1f},{t:.1f} {l+D:.1f},{t-D:.1f} {r+D:.1f},{t-D:.1f} {r:.1f},{t:.1f}" fill="{fondo}" stroke="{borde}" stroke-width="{grosor}"/>')
    s.append(f'<polygon points="{r:.1f},{t:.1f} {r+D:.1f},{t-D:.1f} {r+D:.1f},{b-D:.1f} {r:.1f},{b:.1f}" fill="{fondo}" stroke="{borde}" stroke-width="{grosor}"/>')
    s.append(f'<rect x="{l:.1f}" y="{t:.1f}" width="{r-l:.1f}" height="{b-t:.1f}" fill="{fondo}" stroke="{borde}" stroke-width="{grosor}"/>')
    s.append(f'<text x="{cx:.1f}" y="{t+16:.1f}" text-anchor="middle" font-family="Arial" font-weight="bold" font-size="11">{estereotipo}</text>')
    s.append(f'<text x="{cx:.1f}" y="{t+31:.1f}" text-anchor="middle" font-family="Arial" font-weight="bold" font-size="13">{nombre}</text>')
    return s

RUTAS = [
    ("A", "Estación de desarrollo → Smartphone", "Instalación del APK (ADB o archivo)"),
    ("B", "Estación de desarrollo → Proyecto Firebase", "firebase deploy: reglas de seguridad y funciones"),
    ("1", "Smartphone → Authentication", "HTTPS · inicio de sesión, registro y recuperación de contraseña"),
    ("2", "Smartphone → Cloud Storage", "HTTPS · subida y lectura de fotos, sujetas a storage.rules"),
    ("3", "Smartphone → Cloud Firestore", "HTTPS/gRPC · lectura, escritura y escucha en tiempo real, sujetas a firestore.rules"),
    ("4", "Smartphone → Cloud Functions", "HTTPS · funciones invocables: crear técnico, desactivar cuenta, unir cliente por DNI"),
    ("5", "Cloud Firestore ↔ Cloud Functions", "Disparadores por escritura y escrituras con Admin SDK (notificaciones)"),
    ("6", "Cloud Functions → Authentication", "Admin SDK · crear y desactivar cuentas, asignar el rol"),
    ("7", "Cloud Functions → Cloud Messaging", "HTTP v1 · envío del aviso push"),
    ("8", "Cloud Messaging → Smartphone", "Conexión persistente de Google Play services · entrega del aviso"),
]
NOTAS = [
    ("Proyecto", "Plan Blaze (lo exigen Storage y Functions) con alerta de presupuesto de USD 1"),
    ("Región", "southamerica-east1 (São Paulo): la más cercana a Lima; la región de Firestore no se puede cambiar después"),
]

def insignia(x, y, txt):
    return [f'<rect x="{x-15:.1f}" y="{y-9:.1f}" width="30" height="18" rx="9" fill="#FFFFFF" stroke="#424242" stroke-width="1"/>',
            f'<text x="{x:.1f}" y="{y+4:.1f}" text-anchor="middle" font-family="Arial" font-size="12">({txt})</text>']

def punto_medio_vertical(pts):
    """Centro del tramo vertical más largo de una ruta."""
    mejor, res = -1, None
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        if abs(x1 - x2) < 0.5 and abs(y1 - y2) > mejor:
            mejor, res = abs(y1 - y2), (x1, (y1 + y2) / 2)
    return res

def construir(esp, salida):
    subprocess.run(["python3", "-I", os.path.join(DIR, "generar.py"), "diagrama-despliegue.dot", str(esp)], check=True)
    base = os.path.join(TMP, "base.svg")
    subprocess.run(["dot", "-Tsvg", "diagrama-despliegue.dot", "-o", base], check=True)
    svg = open(base).read()
    n = leer_nodos(svg)
    ar = leer_aristas(svg)
    cx = lambda k: (n[k][0] + n[k][2]) / 2
    cy = lambda k: (n[k][1] + n[k][3]) / 2
    M = 16
    ETQ = 46
    capas = []

    # estación de desarrollo
    ws = caja(n, ["A_BUILD", "A_CLI"], M)
    ws = (ws[0], ws[1] - ETQ, ws[2], cy("A_WS"))
    # teléfono: entorno de ejecución dentro de la caja del dispositivo
    env = caja(n, ["APK", "GPS"], M)
    env = (env[0], env[1] - ETQ, env[2], env[3] + 4)
    anc = caja(n, ["p1", "p2", "p3", "p4", "p8"], 0)
    dev = (min(env[0] - M, anc[0] - M), env[1] - ETQ - M + 6, max(env[2] + M, anc[2] + M), cy("p4"))
    # servicios de Firebase (todos con la misma altura)
    arriba, abajo = cy("T_ST"), cy("Bb_FCM")
    def servicio(ids):
        c = caja(n, ids, M)
        return (c[0], arriba, c[2], abajo)
    st = servicio(["A_FOTOS", "A_SRULES"])
    fcm = servicio(["L_FCM"])
    fs = servicio(["DB", "A_RULES"])
    au = servicio(["A_USERS"])
    # Cloud Functions
    mitad = max(abs(cx("A_FN") - cx(k)) for k in ("T7", "T4", "T5", "T6")) + M
    mitad = max(mitad, (n["A_FN"][2] - n["A_FN"][0]) / 2 + M)
    fn = (cx("A_FN") - mitad, cy("T4"), cx("A_FN") + mitad, n["A_FN"][3] + M)
    # proyecto Firebase
    proy = (min(st[0], fn[0], cx("pB") - 24) - M, cy("pB"),
            max(au[2], fn[2]) + M + D, fn[3] + M)

    # paquete del proyecto
    capas.append(f'<rect x="{proy[0]:.1f}" y="{proy[1]:.1f}" width="{proy[2]-proy[0]:.1f}" height="{proy[3]-proy[1]:.1f}" rx="8" fill="#FFFFFF" stroke="#9E9E9E" stroke-width="1.2"/>')
    capas.append(f'<text x="{proy[0]+12:.1f}" y="{proy[1]+18:.1f}" font-family="Arial" font-weight="bold" font-size="13">Proyecto Firebase</text>')
    capas.append(f'<text x="{proy[0]+12:.1f}" y="{proy[1]+34:.1f}" font-family="Arial" font-weight="bold" font-size="13">Google Cloud</text>')
    capas += cubo(*ws, "«workstation»", "Estación de desarrollo", fondo="#F3E5F5", borde="#6A1B9A")
    capas += cubo(*dev, "«device»", "Smartphone Android [1..*]", fondo="#E3F2FD", borde="#1565C0")
    capas += cubo(*env, "«execution environment»", "Android 8.0 (API 26) o superior", fondo="#FFFFFF", borde="#1565C0")
    for rect, nombre in ((st, "Cloud Storage"), (fcm, "Firebase Cloud Messaging"), (fs, "Cloud Firestore"), (au, "Firebase Authentication")):
        capas += cubo(*rect, "«Firebase PaaS»", nombre, fondo="#FAFAFA", borde="#616161")
    capas += cubo(*fn, "«Firebase · execution environment»", "Cloud Functions", fondo="#FAFAFA", borde="#616161")

    # ruta (A): línea recta entre estación y teléfono
    y0 = cy("A_BUILD")
    xa, xb = ws[2] + D, dev[0]
    linea_a = f'<line x1="{xa:.1f}" y1="{y0:.1f}" x2="{xb:.1f}" y2="{y0:.1f}" stroke="#424242" stroke-width="1.2"/>'
    solape = xb - xa

    cont = (min(ws[0], dev[0], proy[0]), min(ws[1], dev[1], proy[1]), max(ws[2] + D, dev[2] + D, proy[2]), proy[3])
    ccx = (cont[0] + cont[2]) / 2
    titulo = [f'<text x="{ccx:.1f}" y="{cont[1]-58:.1f}" text-anchor="middle" font-family="Arial" font-weight="bold" font-size="20">Diagrama de Despliegue</text>',
              f'<text x="{ccx:.1f}" y="{cont[1]-34:.1f}" text-anchor="middle" font-family="Arial" font-size="15">Aplicación móvil para la recepción y seguimiento de reparaciones de vehículos en Servicio Eléctrico Freddy</text>']

    # números de ruta
    ins = []
    ins += insignia((xa + xb) / 2, y0, "A")
    etiquetas = {("A_WS", "pB"): "B", ("p1", "T_AUTH"): "1", ("p2", "T_ST"): "2", ("p3", "T_FS"): "3", ("p4", "T4"): "4",
                 ("Bb_FS", "T5"): "5", ("Bb_AUTH", "T6"): "6", ("Bb_FCM", "T7"): "7", ("p8", "T_FCM"): "8"}
    for k, e in etiquetas.items():
        x, y = punto_medio_vertical(ar[k])
        if e == "4":  # cruza la fila de servicios: la insignia va en el tramo libre sobre ellos
            y = (cy("p4") + arriba) / 2
        ins += insignia(x, y, e)

    # leyenda
    gx0, gx1 = cont[0], cont[2]
    ancho = gx1 - gx0
    yb = cont[3] + 36
    c1, c2 = 54, 330
    fila_h = 21
    leg = []
    total = 1 + len(RUTAS) + 1 + len(NOTAS)
    leg.append(f'<rect x="{gx0:.1f}" y="{yb:.1f}" width="{ancho:.1f}" height="{total*fila_h+6:.1f}" rx="8" fill="#FFFFFF" stroke="#9E9E9E" stroke-width="1.2"/>')
    def fila_t(i, cols, negrita=False, fondo=None):
        y = yb + 3 + i * fila_h
        out = []
        if fondo:
            out.append(f'<rect x="{gx0+3:.1f}" y="{y:.1f}" width="{ancho-6:.1f}" height="{fila_h}" fill="{fondo}"/>')
        xs = [gx0 + 14, gx0 + 14 + c1, gx0 + 14 + c1 + c2]
        for x, t in zip(xs, cols):
            if t:
                peso = ' font-weight="bold"' if negrita else ""
                out.append(f'<text x="{x:.1f}" y="{y+15:.1f}" font-family="Arial" font-size="12"{peso}>{t}</text>')
        out.append(f'<line x1="{gx0+3:.1f}" y1="{y+fila_h:.1f}" x2="{gx0+ancho-3:.1f}" y2="{y+fila_h:.1f}" stroke="#E0E0E0" stroke-width="1"/>')
        return out
    leg += fila_t(0, ["Ruta", "Origen → destino", "Protocolo y contenido"], True, "#EEEEEE")
    for i, (a, b, c) in enumerate(RUTAS, 1):
        leg += fila_t(i, [f"({a})", b, c])
    leg += fila_t(len(RUTAS) + 1, ["Nota", "Detalle", ""], True, "#EEEEEE")
    for i, (a, b) in enumerate(NOTAS, len(RUTAS) + 2):
        leg += fila_t(i, [a, b, ""])

    # lienzo: se recalcula para abarcar todo lo dibujado
    P = 24
    vx, vy = cont[0] - P, cont[1] - 80 - P
    vw, vh = (cont[2] - cont[0]) + 2 * P, (yb + total * fila_h + 6) - vy + P
    fondo = f'<rect x="{vx:.1f}" y="{vy:.1f}" width="{vw:.1f}" height="{vh:.1f}" fill="#FFFFFF"/>'
    svg2 = re.sub(r'<svg width="[\d.]+pt" height="[\d.]+pt"\s+viewBox="[^"]+"',
                  f'<svg width="{vw:.1f}pt" height="{vh:.1f}pt" viewBox="{vx:.1f} {vy:.1f} {vw:.1f} {vh:.1f}"', svg, count=1)
    svg2 = re.sub(r'(<g id="graph0" class="graph") transform="[^"]+"', r'\1', svg2, count=1)
    mpol = re.search(r'(<polygon fill="white"[^>]*/>)', svg2)
    svg2 = svg2[:mpol.end()] + "\n" + fondo + "\n" + "\n".join(capas) + "\n" + linea_a + "\n" + "\n".join(titulo) + "\n" + "\n".join(leg) + svg2[mpol.end():]
    svg2 = svg2.replace("</g>\n</svg>", "\n".join(ins) + "\n</g>\n</svg>")
    open(salida, "w").write(svg2)
    return solape, ws, dev, proy

if __name__ == "__main__":
    esp = float(sys.argv[1]) if len(sys.argv) > 1 else 4.7
    solape, ws, dev, proy = construir(esp, "diagrama-despliegue.svg")
    print("largo de la ruta (A):", round(solape), "pt")
    subprocess.run(["rsvg-convert", "-z", "1.3", "diagrama-despliegue.svg", "-o", "diagrama-despliegue.png"], check=True)
