import os, re, subprocess, sys, tempfile
DIR = os.path.dirname(os.path.abspath(__file__))
TMP = tempfile.mkdtemp()

def nodos(svg):
    r = {}
    for m in re.finditer(r'<g id="node\d+" class="node">\s*<title>(\w+)</title>(.*?)</g>', svg, re.S):
        xs, ys = [], []
        for p in re.findall(r'points="([^"]+)"', m.group(2)):
            for par in p.split():
                x, y = par.split(",")
                xs.append(float(x)); ys.append(float(y))
        if xs: r[m.group(1)] = (min(xs), min(ys), max(xs), max(ys))
    return r

def caja(n, ids):
    b = [n[i] for i in ids if i in n]
    return (min(c[0] for c in b), min(c[1] for c in b), max(c[2] for c in b), max(c[3] for c in b))

def construir(SL, SR, salida_svg):
    subprocess.run(["python3", "-I", os.path.join(DIR, "generar.py"), "diagrama-componentes.dot", str(SL), str(SR)], check=True)
    subprocess.run(["dot", "-Tsvg", "diagrama-componentes.dot", "-o", os.path.join(TMP, "base.svg")], check=True)
    svg = open(os.path.join(TMP, "base.svg")).read()
    n = nodos(svg)
    cols = ["ACC", "NOT", "PER", "CLI", "TEC", "VEH", "SOL", "ORD", "AVA"]
    grupos = {
        "vista": (["V_NAV", "V_MSG", "sp_vista", "spr_vista"] + [f"V_{c}" for c in cols],
                  "Capa de Presentación — Vista", "Activity / Fragment + layout XML"),
        "vm": (["sp_vm", "spr_vm"] + [f"VM_{c}" for c in cols],
               "Capa de Presentación — ViewModel", "androidx ViewModel + StateFlow"),
        "repo": (["DEP", "sp_ifaz", "spr_ifaz", "spr_repo", "A_USU", "A_DOC", "A_FOT"] + [f"I_{c}" for c in cols] + [f"R_{c}" for c in cols],
                 "Capa de Datos — Repository", "implementa las interfaces de acceso a datos"),
        "ds": (["D_USU", "D_DOC", "D_FOT", "spl_ds", "spr_ds"], "Capa de Datos — Fuentes remotas", "encapsulan el Firebase SDK"),
        "nube": (["C_ST", "C_AUTH", "C_FS", "C_FCM", "C_FN", "spl_nube", "spr_nube"], "Firebase · Google Cloud", ""),
    }
    M = 16
    ETQ = 40  # alto reservado para el titulo de cada capa
    cajas = {k: caja(n, v[0]) for k, v in grupos.items()}
    # las tres capas superiores comparten el mismo ancho
    x0 = min(cajas[k][0] for k in ("vista", "vm", "repo"))
    x1 = max(cajas[k][2] for k in ("vista", "vm", "repo"))
    eje = (n["D_DOC"][0] + n["D_DOC"][2]) / 2
    marcos = []
    for k, (ids, t1, t2) in grupos.items():
        l, t, r, b = cajas[k]
        if k in ("vista", "vm", "repo"):
            l, r = x0, x1
        top = t - ETQ
        bot = b + 8
        if k == "repo":
            bot = n["A_DOC"][1] + (n["A_DOC"][3] - n["A_DOC"][1]) / 2
        if k in ("ds", "nube"):
            l -= 4; r += 4
        marcos.append((k, l - (M if k in ("vista", "vm", "repo") else 0), top, r + (M if k in ("vista", "vm", "repo") else 0), bot, t1, t2))
    capas = []
    for k, l, t, r, b, t1, t2 in marcos:
        capas.append(f'<rect x="{l:.1f}" y="{t:.1f}" width="{r-l:.1f}" height="{b-t:.1f}" rx="10" fill="#FFFFFF" stroke="#9E9E9E" stroke-width="1"/>')
        capas.append(f'<text x="{l+10:.1f}" y="{t+16:.1f}" font-family="Arial" font-weight="bold" font-size="13">{t1}</text>')
        if t2:
            capas.append(f'<text x="{l+10:.1f}" y="{t+31:.1f}" font-family="Arial" font-size="12">{t2}</text>')
    # insertar tras el fondo del grafo
    m = re.search(r'(<polygon fill="white"[^>]*/>)', svg)
    svg2 = svg[:m.end()] + "\n" + "\n".join(capas) + svg[m.end():]
    open(salida_svg, "w").write(svg2)
    L = min(c[0] for k, c in cajas.items() if k in ("vista", "vm", "repo"))
    R = max(c[2] for k, c in cajas.items() if k in ("vista", "vm", "repo"))
    return eje, L, R

if __name__ == "__main__":
    SL, SR = 2.4, 2.4
    for it in range(6):
        eje, L, R = construir(SL, SR, "diagrama-componentes.svg")
        d = (2 * eje - L - R) / 72
        print(it, "SL", round(SL, 2), "SR", round(SR, 2), "centro", round((L + R) / 2), "eje", round(eje), "d", round(d, 2))
        if abs(d) < 0.03: break
        if d >= 0: SR += d
        else: SL += -d
    subprocess.run(["rsvg-convert", "-z", "1.3", "diagrama-componentes.svg", "-o", "diagrama-componentes.png"], check=True)
    print("final", SL, SR)
