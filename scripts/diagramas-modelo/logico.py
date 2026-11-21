#!/usr/bin/env python3
"""Logical NoSQL model of PitLane (UML class notation): collections, embedded documents and references.

Usage: python3 -I scripts/diagramas-modelo/logico.py docs/04-arquitectura/modelo-logico
Writes clases.svg/.png. Names match the ER. Field markers: ID document id, REF reference by id (Firestore does
not check it), EMB embedded document, SUB subcollection. Rows under "copias a propósito (desnormalizado)" repeat
data on purpose, each with its reason in green.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dibujo import TINTA, Lienzo  # noqa: E402

C = [240, 710, 1180, 1650, 2120]     # column centers
W = 400                                     # box width
MAESTRA, DETALLE, DETALLE_COL, CATALOGO, EXTERNA, APOYO, SUBTIPO = (
    "#dbeafe", "#ffedd5", "#fde68a", "#f3e8ff", "#dcfce7", "#e5e7eb", "#eff6ff")
FS = ("copias a propósito (desnormalizado)", "sep")
ST, CF, REG = "Cloud Storage", "Cloud Function", "reglas"

# clave: (carril, estereotipo, nombre, campos, relleno, pie); a field's third item is its Firebase tool or reason
DOCUMENTOS = {
    "CUE": (1, "«Firebase Authentication»", "CUENTA",
            [("uid", "id", "la crea una " + CF), ("correo", "", "sin repetir: Authentication"), ("contraseña  (cifrada)", ""),
             ("fechaRegistro", ""), ("claims: usuarioId, rol", "", "viajan en el token")],
            EXTERNA, "fuera de Firestore: vive en Authentication"),
    "DIS": (1, "«subcolección de USUARIO»", "DISPOSITIVO",
            [("token", "id"), ("expiraEn", "", "TTL 60 días")],
            DETALLE_COL, "usuarios/{id}/tokensDispositivo/{token}"),
    "USU": (2, "«colección»", "USUARIO",
            [("id", "id", "lo genera el servidor"), ("authUid  → CUENTA", "ref", "vacío si no tiene cuenta"),
             ("dni", "", "sin repetir: reserva dnis/{dni}"), ("nombres", ""), ("apellidos", ""), ("telefono", ""),
             ("correo", ""), ("rol  {CLIENTE, JEFE, TECNICO}", "", "claim + " + REG),
             ("activo", "", CF + " + Authentication"), ("tokensDispositivo/  → DISPOSITIVO", "sub"),
             ("notificaciones/  → NOTIFICACIÓN", "sub", "bandeja: la ruta es el dueño"),
             FS, ("acceso[]", "", "quién lo lee: " + REG)],
            MAESTRA, "usuarios/{id}"),
    "CLI": (3, "«subtipo: rol = CLIENTE»", "CLIENTE",
            [("tieneCuenta", ""), FS, ("terminos[]", "", "búsqueda: array-contains"),
             ("ultimaActividad", "", "la mantiene una " + CF)],
            SUBTIPO, "mismo documento de usuarios/{id}"),
    "JEF": (3, "«subtipo: rol = JEFE»", "JEFE DE TALLER", [("sin campos propios", "")], SUBTIPO,
            "mismo documento de usuarios/{id}"),
    "TEC": (3, "«subtipo: rol = TECNICO»", "TÉCNICO", [("sin campos propios", "")], SUBTIPO,
            "mismo documento de usuarios/{id}"),
    "PLA": (4, "«colección de reserva»", "RESERVA DE PLACA",
            [("placa", "id", "el id ES la placa"), ("vehiculoId  → VEHÍCULO", "ref"), ("clienteId  → USUARIO", "ref")],
            APOYO, "placas/{placa}: se escribe con el VEHÍCULO"),
    "CNT": (4, "«colección de apoyo»", "CONTADOR",
            [("id", "id", "\"ordenes\""), ("ultimo", "", "genera el siguiente número")],
            APOYO, "contadores/ordenes"),
    "NOT": (1, "«subcolección de USUARIO»", "NOTIFICACIÓN",
            [("id", "id", "n-{evento}: la crea una " + CF), ("ordenId  → ORDEN", "ref"),
             ("esIngreso", "", "ingreso o cambio de estado"), ("titulo", ""), ("mensaje", ""), ("fechaHora", ""),
             ("leida", "", "solo esto cambia: " + REG), FS, ("usuarioId", "", "copia de la ruta"),
             ("expiraEn", "", "TTL 90 días")],
            DETALLE_COL, "usuarios/{id}/notificaciones/{id}"),
    "DNI": (4, "«colección de reserva»", "RESERVA DE DNI",
            [("dni", "id", "el id ES el DNI"), ("usuarioId  → USUARIO", "ref")],
            APOYO, "dnis/{dni}: se escribe con el USUARIO"),
    "NUM": (4, "«colección de reserva»", "RESERVA DE NÚMERO DE ORDEN",
            [("numero", "id", "el id ES el número"), ("ordenId  → ORDEN", "ref")],
            APOYO, "numerosOrden/{numero}: se escribe con la ORDEN"),
    "FSO": (0, "«embebido en SOLICITUD»", "FOTO DE SOLICITUD",
            [("id", ""), ("ruta", "", ST), ("fechaHora", "")],
            DETALLE, "arreglo solicitudes.fotos[]"),
    "SOL": (0, "«colección»", "SOLICITUD",
            [("id", "id"), ("vehiculoId  → VEHÍCULO", "ref"), ("descripcion", ""), ("fechaLlegada", ""),
             ("estado  {PENDIENTE, ATENDIDA, CANCELADA}", ""), ("creadaEn", "", "≈ hora del servidor"),
             ("sintomas[]  → SÍNTOMA DE SOLICITUD", "emb"), ("fotos[]  → FOTO DE SOLICITUD", "emb"), FS,
             ("clienteId  → USUARIO", "ref", "copia: dueño del VEHÍCULO"), ("ordenId  → ORDEN", "ref", "copia de ORDEN.solicitudId"),
             ("cliente {…}, vehiculo {…}", "", "instantáneas"), ("esquema", "", "versión"), ("acceso[]", "", REG)],
            MAESTRA, "solicitudes/{id}"),
    "VEH": (1, "«colección»", "VEHÍCULO",
            [("id", "id"), ("clienteId  → USUARIO", "ref"), ("placa", "", "sin repetir: reserva placas/{placa}"), ("marca", ""),
             ("modelo", ""), ("anio", ""), ("color", ""), ("activo", ""), FS,
             ("terminosPlaca[]", "", "búsqueda: array-contains"), ("acceso[]", "", REG)],
            MAESTRA, "vehiculos/{id}"),
    "ORD": (2, "«colección»", "ORDEN",
            [("id", "id"), ("numero", "", "sin repetir: reserva numerosOrden"), ("vehiculoId  → VEHÍCULO", "ref"),
             ("tecnicoId  → USUARIO", "ref"), ("solicitudId  → SOLICITUD  {opcional}", "ref"), ("falla", ""),
             ("creadaEn  (fecha de ingreso)", "", "≈ hora del servidor"), ("kilometraje", ""), ("combustible  {RESERVA … LLENO}", "", "5 niveles"),
             ("observaciones  (de ingreso)", ""), ("editadaPor  → USUARIO", "ref", "auditoría"), ("editadaEn", "", "≈ hora del servidor"),
             ("historial[]  → HISTORIAL DE ESTADO", "emb", "solo crece: " + REG),
             ("observacionesCliente[]  → OBSERVACIÓN", "emb"), ("conformidad  → CONFORMIDAD", "emb", "máx. 1 y fija"),
             ("fotos[]  → FOTO DE ORDEN", "emb"), ("avances/  → AVANCE", "sub", "crece sin tope"), FS,
             ("clienteId  → USUARIO", "ref", "copia: dueño del VEHÍCULO"), ("estado", "", "copia: último del historial"),
             ("motivoAnulacion", "", "copia del historial"), ("ultimoAviso", "", "copia: último AVISO_LISTO"),
             ("cliente {…}, vehiculo {…}, tecnico {…}", "", "instantáneas"),
             ("avisosEnviados", "", "dispara una " + CF), ("esquema", "", "versión"), ("acceso[]", "", REG)],
            MAESTRA, "ordenes/{id}"),
    "AVA": (3, "«subcolección de ORDEN»", "AVANCE",
            [("id", "id", "lo genera Firestore"), ("tecnicoId  → USUARIO", "ref", "el de la ORDEN padre: " + REG),
             ("tipo  {HALLAZGO, TRABAJO_REALIZADO}", ""), ("descripcion", "", "solo EN_REPARACION: " + REG),
             ("fechaHora", "", "≈ hora del servidor"), ("fotos[]  → FOTO DE AVANCE", "emb")],
            DETALLE_COL, "ordenes/{id}/avances/{id}"),
    "FAV": (4, "«embebido en AVANCE»", "FOTO DE AVANCE",
            [("id", ""), ("ruta", "", ST), ("fechaHora", "")],
            DETALLE, "arreglo avances.fotos[]"),
    "SIN": (0, "«embebido en SOLICITUD»", "SÍNTOMA DE SOLICITUD",
            [("texto", "", "un valor por elemento")],
            DETALLE, "arreglo solicitudes.sintomas[]"),
    "OBS": (1, "«embebido en ORDEN»", "OBSERVACIÓN",
            [("fechaHora", "", "≈ hora del servidor"), ("usuarioId  → USUARIO", "ref"), ("texto", ""), ("acuerdo", "")],
            DETALLE, "arreglo ordenes.observacionesCliente[]"),
    "HIS": (2, "«embebido en ORDEN»", "HISTORIAL DE ESTADO",
            [("estado  → ESTADO DE ORDEN", "ref", "transición válida: " + REG), ("fechaHora", "", "solo se agrega · ≈ hora del servidor"),
             ("usuarioId  → USUARIO", "ref", "= quien escribe"), ("motivo", ""), FS, ("usuarioNombre", "", "copia: firma")],
            DETALLE, "arreglo ordenes.historial[]"),
    "CON": (3, "«embebido en ORDEN»", "CONFORMIDAD",
            [("usuarioId  → USUARIO", "ref"), ("fechaHora", "", "≈ hora del servidor"), FS, ("usuarioNombre", "", "copia: firma")],
            DETALLE, "mapa ordenes.conformidad"),
    "FOR": (4, "«embebido en ORDEN»", "FOTO DE ORDEN",
            [("id", ""), ("ruta", "", ST), ("fechaHora", "")],
            DETALLE, "arreglo ordenes.fotos[]"),
    "EST": (2, "«catálogo»", "ESTADO DE ORDEN",
            [("codigo", "", "es el valor guardado"), ("nombre", ""), ("secuencia", "")],
            CATALOGO, "enum en el código; no es colección"),
}


def alto(clave):
    return 50 + len(DOCUMENTOS[clave][3]) * 19 + 14 + 22      # same formula as Lienzo.clase


def dibujar():
    # top edge of every box; stacked boxes in one cell follow each other
    arriba = {"FSO": 90, "CUE": 90, "USU": 90, "CLI": 90, "PLA": 90}
    arriba["DIS"] = arriba["CUE"] + alto("CUE") + 30
    arriba["NOT"] = arriba["DIS"] + alto("DIS") + 30
    arriba["JEF"] = arriba["CLI"] + alto("CLI") + 18
    arriba["TEC"] = arriba["JEF"] + alto("JEF") + 18
    arriba["DNI"] = arriba["PLA"] + alto("PLA") + 30
    arriba["NUM"] = arriba["DNI"] + alto("DNI") + 30
    arriba["CNT"] = arriba["NUM"] + alto("NUM") + 30
    fila_b = max(arriba[k] + alto(k) for k in arriba) + 90
    for k in ("SOL", "VEH", "ORD", "AVA", "FAV"):
        arriba[k] = fila_b
    yb = arriba["ORD"] + alto("ORD")                         # bottom of ORDEN: the detail lanes start here
    fila_c = yb + 150
    for k in ("SIN", "OBS", "HIS", "CON", "FOR"):
        arriba[k] = fila_c
    arriba["EST"] = arriba["HIS"] + alto("HIS") + 70
    fin = arriba["EST"] + alto("EST")

    c = Lienzo(2370, max(fin, arriba["EST"] + 7 * 28) + 440)
    for clave, (col, est, nombre, filas, relleno, pie) in DOCUMENTOS.items():
        c.clase(clave, C[col], arriba[clave] + alto(clave) / 2, est, nombre, filas, relleno, ancho=W, nota=pie if pie.startswith("fuera") else "Firestore: " + pie)

    b = c.borde

    def asoc(puntos, m1, m2, p1, p2):
        c.camino(puntos, grosor=1.5)
        c.cardinalidad(*p1, m1)
        c.cardinalidad(*p2, m2)

    def ref(puntos, m1, m2, p1, p2):
        """Reference by id: dashed line from the referencing document to the referenced one (arrow)."""
        c.camino(puntos, grosor=1.5, discontinuo=True, flecha=True)
        c.cardinalidad(*p1, m1)
        c.cardinalidad(*p2, m2)

    def compo(maestra, lado, f, resto, m_detalle, p_detalle):
        """Composition: filled diamond on the master's border, then the orthogonal path to the detail."""
        x, y = b(maestra, lado, f)
        hacia = {"bottom": "up", "top": "down", "left": "right", "right": "left"}[lado]
        c.camino([(x, y)] + resto, grosor=1.5)
        c.rombo_uml(x, y, hacia)
        c.cardinalidad(*p_detalle, m_detalle)

    # ---- USUARIO and its satellites ----
    yc = b("CUE", "right")[1]
    ref([(b("USU", "left")[0], yc), b("CUE", "right")], "1", "0..1",
        (b("USU", "left")[0] - 14, yc + 15), (b("CUE", "right")[0] + 22, yc - 13))
    yd = b("DIS", "right")[1]                              # DISPOSITIVO sits beside USUARIO: a straight link
    compo("USU", "left", (yd - b("USU", "top")[1]) / alto("USU"), [(b("DIS", "right")[0], yd)], "0..*",
          (b("DIS", "right")[0] + 24, yd - 13))
    # generalization: one hollow triangle on USUARIO, a bus joining the three subtypes
    xbus = b("CLI", "left")[0] - 34
    ys = [b(k, "left")[1] for k in ("CLI", "JEF", "TEC")]
    for y in ys:
        c.camino([(b("CLI", "left")[0], y), (xbus, y)], grosor=1.5)
    yu = b("USU", "right", 0.35)[1]
    c.camino([(xbus, min(ys)), (xbus, max(ys))], grosor=1.5)
    c.camino([(xbus, yu), (b("USU", "right")[0] + 19, yu)], grosor=1.5)
    c.triangulo_uml(b("USU", "right")[0], yu, hacia="left")
    carril = fila_b - 45                                     # horizontal lane between the two upper rows
    # USUARIO (cliente) 1 — 0..* VEHÍCULO
    xv0, yv0 = b("USU", "bottom", 0.32)
    xv, yv = b("VEH", "top")
    ref([(xv, yv), (xv, carril), (xv0, carril), (xv0, yv0)], "0..*", "1", (xv + 24, yv - 14), (xv0 + 14, yv0 + 14))
    # USUARIO (técnico) 1 — 0..* ORDEN
    xt, yt = b("USU", "bottom", 0.5)
    ref([b("ORD", "top"), (xt, yt)], "0..*", "1", (xt + 24, b("ORD", "top")[1] - 14), (xt + 14, yt + 14))
    # USUARIO ◆ NOTIFICACIÓN (subcolección), below DISPOSITIVO
    xn0, _ = b("USU", "bottom", 0.1)
    yn = b("NOT", "right")[1]
    compo("USU", "bottom", 0.1, [(xn0, yn), (b("NOT", "right")[0], yn)], "0..*", (b("NOT", "right")[0] + 24, yn - 13))

    # ---- the middle row ----
    yf = fila_b + 70
    ref([(b("ORD", "left")[0], yf), (b("VEH", "right")[0], yf)], "0..*", "1",
        (b("ORD", "left")[0] - 24, yf + 15), (b("VEH", "right")[0] + 14, yf - 13))
    ref([(b("SOL", "right")[0], yf), (b("VEH", "left")[0], yf)], "0..*", "1",
        (b("SOL", "right")[0] + 24, yf - 13), (b("VEH", "left")[0] - 14, yf + 15))
    compo("ORD", "right", (yf - fila_b) / alto("ORD"), [(b("AVA", "left")[0], yf)], "0..*", (b("AVA", "left")[0] - 24, yf - 13))
    compo("AVA", "right", (yf - fila_b) / alto("AVA"), [(b("FAV", "left")[0], yf)], "0..*", (b("FAV", "left")[0] - 24, yf - 13))
    compo("SOL", "top", 0.5, [b("FSO", "bottom")], "0..*", (C[0] + 24, b("FSO", "bottom")[1] + 14))
    xs1 = b("SOL", "bottom", 0.3)[0]
    compo("SOL", "bottom", 0.3, [(xs1, b("SIN", "top")[1])], "0..*", (xs1 + 24, b("SIN", "top")[1] - 14))

    # ---- ORDEN details: nested lanes so nothing crosses ----
    l1, l2, l3 = yb + 35, yb + 70, yb + 105
    xs, ys2 = b("SOL", "bottom", 0.75)
    xo2, yo2 = b("ORD", "bottom", 0.12)
    ref([(xo2, yo2), (xo2, l1), (xs, l1), (xs, ys2)], "0..1", "0..1", (xo2 - 24, yo2 + 14), (xs + 22, ys2 + 14))
    xo3 = b("ORD", "bottom", 0.32)[0]
    compo("ORD", "bottom", 0.32, [(xo3, l3), (C[1], l3), b("OBS", "top")], "0..*", (C[1] + 24, b("OBS", "top")[1] - 14))
    compo("ORD", "bottom", 0.5, [b("HIS", "top")], "1..*", (C[2] + 24, b("HIS", "top")[1] - 14))
    xo4 = b("ORD", "bottom", 0.68)[0]
    compo("ORD", "bottom", 0.68, [(xo4, l2), (C[3], l2), b("CON", "top")], "0..1", (C[3] + 24, b("CON", "top")[1] - 14))
    xo5 = b("ORD", "bottom", 0.86)[0]
    compo("ORD", "bottom", 0.86, [(xo5, l1), (C[4], l1), b("FOR", "top")], "0..*", (C[4] + 24, b("FOR", "top")[1] - 14))
    ref([b("HIS", "bottom"), b("EST", "top")], "0..*", "1",
         (C[2] + 24, b("HIS", "bottom")[1] + 14), (C[2] + 14, b("EST", "top")[1] - 14))

    leyenda(c, arriba["EST"], max(fin, arriba["EST"] + 7 * 28) + 50)
    return c


def leyenda(c, y_leyenda, y_apoyo):
    items = [(MAESTRA, "colección: documentos propios"), (SUBTIPO, "subtipo: mismo documento, distinto rol"),
             (DETALLE, "documento detalle embebido en su padre"), (DETALLE_COL, "documento detalle en colección o subcolección"),
             (CATALOGO, "catálogo (enum del código)"), (APOYO, "reserva o apoyo: Firestore no garantiza valores sin repetir"),
             (EXTERNA, "Firebase Authentication (fuera de Firestore)")]
    for i, (color, texto) in enumerate(items):
        y = y_leyenda + 10 + i * 28
        c.formas.append(f'<rect x="60" y="{y - 10}" width="28" height="20" fill="{color}" stroke="{TINTA}"/>')
        c.texto(100, y, texto, 13, ancla="start")
    notas = [("ID: id del documento.  REF: referencia por id (Firestore no la verifica).", TINTA),
             ("EMB: documento embebido.  SUB: subcolección.", TINTA),
             ("◆ embebido o subcolección: vive dentro de su padre.  ⇢ referencia por id.", TINTA),
             ("Bajo «copias a propósito»: datos repetidos para leer menos (desnormalizado).", TINTA),
             ("Texto verde: herramienta de Firebase o motivo de la copia.", "#0f766e"),
             ("REF sin línea (embebidos, NOTIFICACIÓN, reservas): se leen en el campo.", TINTA),
             ("Pie de cada caja: ruta en Firestore.", TINTA)]
    for i, (t, color) in enumerate(notas):
        c.texto(1440, y_leyenda + 10 + i * 28, t, 13, ancla="start", color=color)

    c.fondos.append(f'<rect x="40" y="{y_apoyo}" width="2290" height="355" rx="10" fill="#f8fafc" stroke="#94a3b8" stroke-width="1.4"/>')
    c.texto(70, y_apoyo + 30, "Herramientas de Firebase: qué hace cada una en este modelo", 17, "bold", ancla="start")
    reglas = [
        ("Herramienta", "Qué hace en este modelo"),
        ("Authentication", "CUENTA (correo y contraseña cifrada); USUARIO.authUid la referencia; usuarioId y rol viajan como claims del token"),
        ("Cloud Firestore", "colecciones; subcolecciones bajo su dueño (avances, notificaciones, dispositivos); detalles pequeños embebidos; referencias por id"),
        ("Reglas de seguridad", "quién lee y escribe (acceso[]); transiciones del estado; HISTORIAL DE ESTADO solo crece; CONFORMIDAD máx. 1 y fija; fechas ≈ hora del servidor; exigen la reserva con su documento"),
        ("Transacción", "el CONTADOR entrega el siguiente número de ORDEN sin que dos clientes reciban el mismo"),
        ("Lote atómico", "valores sin repetir: el id de la reserva es el valor; VEHÍCULO+placa, USUARIO+DNI y ORDEN+número se escriben juntos o ninguno"),
        ("Cloud Functions", "crean CUENTA, USUARIO y DNI en una transacción, la NOTIFICACIÓN de cada cambio (en la bandeja del cliente) y ultimaActividad"),
        ("Política TTL", "borra NOTIFICACIÓN (90 días) y DISPOSITIVO (60 días) sin código"),
        ("Cloud Storage", "archivos de las fotos (JPG o PNG, hasta 5 MB) con sus propias reglas"),
        ("Índices", "consultas por acceso[] + estado, cliente, vehículo u orden; terminos[] con array-contains"),
    ]
    x0, y, anchos = 70, y_apoyo + 52, [260, 1960]
    for i, (a, b) in enumerate(reglas):
        relleno = "#e2e8f0" if i == 0 else ("#fff" if i % 2 else "#f1f5f9")
        c.formas.append(f'<rect x="{x0}" y="{y}" width="{sum(anchos)}" height="28" fill="{relleno}" stroke="#cbd5e1"/>')
        c.formas.append(f'<line x1="{x0 + anchos[0]}" y1="{y}" x2="{x0 + anchos[0]}" y2="{y + 28}" stroke="#cbd5e1"/>')
        c.texto(x0 + 12, y + 14, a, 13, "bold", "#0f766e" if i else TINTA, ancla="start", capa=c.formas)
        c.texto(x0 + anchos[0] + 12, y + 14, b, 13, "bold" if i == 0 else "normal", ancla="start", capa=c.formas)
        y += 28


def main():
    destino = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(destino, exist_ok=True)
    print(dibujar().guardar(os.path.join(destino, "clases.svg")))


if __name__ == "__main__":
    main()
