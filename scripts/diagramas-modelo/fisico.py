#!/usr/bin/env python3
"""Physical model of the order history in Cloud Firestore.

Usage: python3 -I scripts/diagramas-modelo/fisico.py docs/04-arquitectura/modelo-fisico
Reads documentos/ordenes.json from that folder and writes historial-fisico.svg/.png with three panels:
the real document, the write sequence of one state change, and the logical-to-physical mapping.
"""
import json
import os
import sys
from datetime import datetime, timedelta, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dibujo import MONO, ROJO, SANS, TINTA, Lienzo  # noqa: E402

LIMA = timezone(timedelta(hours=-5))
NUEVA, ESTADO, EMBEBIDO, MARCO = "#dcfce7", "#fef3c7", "#ffedd5", "#f8fafc"


def fecha(ms):
    return datetime.fromtimestamp(ms / 1000, LIMA).strftime("%d/%m/%Y %H:%M")


def panel(c, x, y, w, h, titulo):
    c.fondos.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{MARCO}" stroke="#94a3b8" stroke-width="1.4"/>')
    c.texto(x + 20, y + 26, titulo, 17, "bold", ancla="start", capa=c.fondos)


def documento(c, orden_id, o, x0, y0):
    """The order document as Firestore stores it, with the history array expanded."""
    lh, tam = 21, 13
    lineas = [(f"ordenes/{orden_id}", "titulo", None)]
    for k in ("numero", "clienteId", "vehiculoId", "tecnicoId"):
        lineas.append((f'{k}: "{o[k]}"', "", None))
    lineas.append((f'estado: "{o["estado"]}"', "estado", "← copia de la última entrada (estado actual)"))
    lineas.append(("historial: [", "", "← documento detalle HISTORIAL DE ESTADO (embebido)"))
    n = len(o["historial"])
    for i, h in enumerate(o["historial"]):
        tipo = "nueva" if i == n - 1 else "entrada"
        lineas.append((f"  {i}: {{", tipo, "← entrada agregada por el cambio" if i == n - 1 else None))
        lineas.append((f'     estado: "{h["estado"]}",', tipo, None))
        lineas.append((f'     fechaHora: {h["fechaHora"]},', tipo, f"  {fecha(h['fechaHora'])}"))
        lineas.append((f'     usuarioId: "{h["usuarioId"]}",', tipo, None))
        lineas.append((f'     usuarioNombre: "{h["usuarioNombre"]}",', tipo, "← firma desnormalizada"))
        lineas.append((f'     motivo: {json.dumps(h["motivo"], ensure_ascii=False)} }}', tipo, None))
    lineas.append(("]", "", None))
    lineas.append((f'avisosEnviados: {o["avisosEnviados"]}', "", None))
    lineas.append(('conformidad: null,  observacionesCliente: [],  fotos: [ … ]', "", "← otros detalles embebidos"))
    lineas.append(('cliente: { … }, vehiculo: { … }, tecnico: { … }', "", "← instantáneas"))
    lineas.append((f'acceso: {json.dumps(o["acceso"])},  esquema: {o["esquema"]}', "", None))

    w = 600
    h = len(lineas) * lh + 24
    c.formas.append(f'<rect x="{x0}" y="{y0}" width="{w}" height="{h}" rx="6" fill="#fff" stroke="{TINTA}" stroke-width="1.5"/>')
    y = y0 + 12 + lh / 2
    for texto, tipo, nota in lineas:
        relleno = {"estado": ESTADO, "entrada": EMBEBIDO, "nueva": NUEVA}.get(tipo)
        if relleno:
            c.formas.append(f'<rect x="{x0 + 6}" y="{y - lh / 2:.1f}" width="{w - 12}" height="{lh}" fill="{relleno}"/>')
        peso = "bold" if tipo in ("titulo", "estado") else "normal"
        c.texto(x0 + 16, y, texto, tam, peso, ancla="start", fam=MONO, capa=c.formas)
        if nota:
            c.texto(x0 + w + 12, y, nota, 12, color="#475569", estilo="italic", ancla="start", capa=c.formas)
        y += lh
    return x0, y0, w, h


def secuencia(c, x0, y0, o):
    """Sequence of one state change (EN_REPARACION → LISTO) with its lifelines."""
    actores = [("App del técnico", 0), ("Reglas de\nseguridad", 1), ("ordenes/{id}", 2), ("Cloud Functions", 3),
               ("notificaciones\ny usuarios", 4)]
    paso = 230
    xs = [x0 + 110 + i * paso for i in range(len(actores))]
    yfin = y0 + 640
    for (nombre, i) in actores:
        x = xs[i]
        c.formas.append(f'<rect x="{x - 95}" y="{y0}" width="190" height="52" rx="6" fill="#dbeafe" stroke="{TINTA}" stroke-width="1.4"/>')
        for k, parte in enumerate(nombre.split("\n")):
            c.texto(x, y0 + 26 + (k - (len(nombre.split(chr(10))) - 1) / 2) * 17, parte, 13.5, "bold", capa=c.formas)
        c.camino([(x, y0 + 52), (x, yfin)], grosor=1.2, discontinuo=True, color="#64748b")

    ultima = o["historial"][-1]
    mensajes = [
        (0, 2, "1. lee la orden (1 lectura)", False),
        (0, 1, "2. una sola escritura atómica:", False),
        (None, None, f'estado = "{ultima["estado"]}"', None),
        (None, None, "historial = [ …las 2 de antes…, { LISTO, técnico } ]", None),
        (1, 1, "3. valida: EN_REPARACION → LISTO permitido,", "auto"),
        (None, None, "historial: mismas entradas + 1 al final, firmada", None),
        (None, None, "por quien escribe y con el estado nuevo", None),
        (1, 2, "4. guarda el documento", False),
        (2, 3, "5. dispara onDocumentWritten", False),
        (3, 4, "6. notificarOrden: crea la notificación", False),
        (None, None, "en usuarios/{cliente}/notificaciones", None),
        (3, 4, "7. notificarOrden: envía el push por FCM", False),
        (None, None, "a usuarios/{cliente}/tokensDispositivo (borra los inválidos)", None),
        (3, 4, "8. actividadPorOrden: sube", False),
        (None, None, "usuarios/{cliente}.ultimaActividad", None),
    ]
    y = y0 + 95
    for de, a, texto, tipo in mensajes:
        if de is None:
            c.texto(xs[ultimo_de] + 72 if ultimo_de == ultimo_a else (xs[ultimo_de] + xs[ultimo_a]) / 2, y - 26 + 18,
                    texto, 12.5, color="#334155", ancla="start" if ultimo_de == ultimo_a else "middle", halo=True)
            y += 18
            continue
        y += 22
        if de == a:
            x = xs[de]
            c.camino([(x, y - 8), (x + 60, y - 8), (x + 60, y + 60), (x + 4, y + 60)], grosor=1.5, flecha=True)
            c.texto(x + 72, y - 8, texto, 12.5, "bold", ancla="start", halo=True)
            y += 10
        else:
            c.camino([(xs[de], y), (xs[a], y)], grosor=1.5, flecha=True)
            c.texto((xs[de] + xs[a]) / 2, y - 12, texto, 12.5, "bold", halo=True)
        ultimo_de, ultimo_a = de, a
        y += 30
    return xs


def mapeo(c, x0, y0):
    filas = [
        ("Lógico: HISTORIAL DE ESTADO (EMB en ORDEN)", "Físico: Cloud Firestore"),
        ("un documento embebido", "un mapa dentro del arreglo ordenes/{id}.historial[]"),
        ("se identifica por su ORDEN y su fechaHora", "el documento de la orden + la posición en el arreglo"),
        ("pertenece a una ORDEN", "implícito: el arreglo vive dentro de su orden (no hay ordenId)"),
        ("REF estado → ESTADO DE ORDEN", "texto del enum: INGRESADO, EN_REPARACION, LISTO, ENTREGADO, ANULADA"),
        ("REF usuarioId → USUARIO", "usuarioId + usuarioNombre (copia desnormalizada: la firma)"),
        ("ORDEN.estado (copia a propósito)", "campo estado, igual a la última entrada; las reglas lo exigen"),
        ("agregar un elemento", "la misma escritura que cambia el estado agrega 1 entrada al final"),
        ("nunca se cambia ni se borra", "las reglas rechazan cambiar, quitar o reordenar entradas"),
    ]
    anchos = [370, 580]
    lh = 30
    y = y0
    for i, (a, b) in enumerate(filas):
        relleno = "#e2e8f0" if i == 0 else ("#fff" if i % 2 else "#f8fafc")
        c.formas.append(f'<rect x="{x0}" y="{y}" width="{sum(anchos)}" height="{lh}" fill="{relleno}" stroke="#cbd5e1"/>')
        c.formas.append(f'<line x1="{x0 + anchos[0]}" y1="{y}" x2="{x0 + anchos[0]}" y2="{y + lh}" stroke="#cbd5e1"/>')
        peso = "bold" if i == 0 else "normal"
        c.texto(x0 + 12, y + lh / 2, a, 13, peso, ancla="start", capa=c.formas)
        c.texto(x0 + anchos[0] + 12, y + lh / 2, b, 13, peso, ancla="start", capa=c.formas)
        y += lh
    return y


def razones(c, x0, y0):
    puntos = [
        ("Por qué embebido y no una subcolección", True),
        ("• Tiene tope: como máximo 4 entradas (INGRESADO → EN_REPARACION → LISTO → ENTREGADO, o → ANULADA).", False),
        ("• Se lee siempre con la orden: la línea de tiempo sale de la misma lectura, sin consultas extra.", False),
        ("• Se escribe junto con el estado: una sola escritura atómica, el estado y su entrada nunca se separan.", False),
        ("• Lejos del límite de 1 MiB por documento: cada entrada ocupa unos 150 bytes.", False),
        ("Si no tuviera tope (por ejemplo, cada nota del taller), iría a la subcolección ordenes/{id}/historial,", False),
        ("como los avances (ordenes/{id}/avances), que crecen sin límite.", False),
    ]
    y = y0
    for texto, titulo in puntos:
        c.texto(x0, y, texto, 15 if titulo else 13.5, "bold" if titulo else "normal", ancla="start")
        y += 30 if titulo else 25


ARBOL = [
    ("usuarios/{id}", "USUARIO (+ CLIENTE, JEFE DE TALLER, TÉCNICO por rol)", "col"),
    ("  ├─ tokensDispositivo/{token}", "DISPOSITIVO · token FCM real como id · vence con TTL", "sub"),
    ("  └─ notificaciones/{id}", "NOTIFICACIÓN · la crea notificarOrden · TTL", "sub"),
    ("dnis/{dni}", "reserva del DNI (se escribe con el USUARIO)", "apoyo"),
    ("vehiculos/{id}", "VEHÍCULO", "col"),
    ("placas/{placa}", "reserva de la placa (se escribe con el VEHÍCULO)", "apoyo"),
    ("solicitudes/{id}", "SOLICITUD", "col"),
    ("  ├─ sintomas[]", "SÍNTOMA DE SOLICITUD (embebido)", "emb"),
    ("  └─ fotos[]", "FOTO DE SOLICITUD (embebido) → Cloud Storage", "emb"),
    ("ordenes/{id}", "ORDEN", "col"),
    ("  ├─ historial[]", "HISTORIAL DE ESTADO (embebido, solo crece)", "emb"),
    ("  ├─ observacionesCliente[]", "OBSERVACIÓN (embebido)", "emb"),
    ("  ├─ conformidad{}", "CONFORMIDAD (embebido, máx. 1)", "emb"),
    ("  ├─ fotos[]", "FOTO DE ORDEN (embebido) → Cloud Storage", "emb"),
    ("  └─ avances/{id}", "AVANCE (subcolección)", "sub"),
    ("       └─ fotos[]", "FOTO DE AVANCE (embebido)", "emb"),
    ("numerosOrden/{numero}", "reserva del número (se escribe con la ORDEN)", "apoyo"),
    ("contadores/ordenes", "genera el siguiente número (transacción)", "apoyo"),
    ("codigosRestablecimiento/{id}", "solo el servidor · TTL", "apoyo"),
]


def arbol(c, x0, y0):
    """The whole Firestore tree: collections, subcollections, embedded details and support collections."""
    colores = {"col": "#dbeafe", "sub": "#fde68a", "emb": "#ffedd5", "apoyo": "#e5e7eb"}
    y = y0
    for ruta, que, tipo in ARBOL:
        c.formas.append(f'<rect x="{x0}" y="{y - 12}" width="16" height="16" fill="{colores[tipo]}" stroke="{TINTA}"/>')
        c.texto(x0 + 28, y - 4, ruta, 13.5, "bold" if tipo in ("col", "apoyo") else "normal", ancla="start", fam=MONO, capa=c.formas)
        c.texto(x0 + 400, y - 4, que, 13, color="#334155", ancla="start", capa=c.formas)
        y += 27
    leyenda = [("col", "colección"), ("sub", "subcolección: documentos bajo su dueño"), ("emb", "embebido: dentro del documento"),
               ("apoyo", "reserva o apoyo")]
    for i, (tipo, texto) in enumerate(leyenda):
        x = x0 + i * 300
        c.formas.append(f'<rect x="{x}" y="{y + 6}" width="16" height="16" fill="{colores[tipo]}" stroke="{TINTA}"/>')
        c.texto(x + 24, y + 14, texto, 12.5, ancla="start", capa=c.formas)


def ejemplos(c, x0, y0, orden_id, o):
    """Three worked examples with the seed's real values: sin repetir (reserva), subcolección, referencia y copia."""
    vid, cid, placa, dni, numero = o["vehiculoId"], o["clienteId"], o["vehiculo"]["placa"], o["cliente"]["dni"], o["numero"]
    OK, NO, NOTA = "#166534", "#b91c1c", "#475569"
    tarjetas = [
        ("Valores sin repetir: la reserva usa el valor como id", [
            ("m", "lote atómico (writeBatch):"),
            ("m", f'  1. set  vehiculos/{vid}  {{ placa: "{placa}", … }}'),
            ("m", f'  2. set  placas/{placa}  {{ vehiculoId: "{vid}", clienteId: "{cid}" }}'),
            ("", ""),
            ("ok", "✔ las dos escrituras entran juntas"),
            ("no", f'✘ otro vehículo con "{placa}": placas/{placa} ya existe, el lote falla completo'),
            ("no", "✘ vehículo sin su reserva, o reserva sin su vehículo: las reglas lo deniegan"),
            ("", ""),
            ("n", "Igual con el DNI y con el número de orden:"),
            ("m", f'  dnis/{dni}  {{ usuarioId: "{cid}" }}'),
            ("m", f'  numerosOrden/{numero}  {{ ordenId: "{orden_id}" }}'),
            ("n", "El contador solo genera el siguiente número; el id de la reserva lo garantiza."),
        ]),
        ("Subcolección: la ruta dice de quién es", [
            ("m", f"usuarios/{cid}/notificaciones/n-1"),
            ("m", f'  {{ titulo, mensaje, leida, ordenId: "{orden_id}", expiraEn }}'),
            ("ok", f"✔ lee {cid}: la ruta le pertenece"),
            ("no", "✘ otro usuario o el Jefe: denegado por la ruta"),
            ("", ""),
            ("m", f"ordenes/{orden_id}/avances/a-1"),
            ("m", "  { tecnicoId, tipo, descripcion, fotos[], fechaHora }"),
            ("ok", f"✔ lee quien puede leer la orden {orden_id}: la regla mira a la orden padre"),
            ("no", "✘ el avance no copia acceso[]: si cambia el técnico de la orden, no hay nada que actualizar"),
            ("", ""),
            ("n", "Una subcolección no viaja con su padre: leer la orden no trae sus avances."),
        ]),
        ("Referencia y copia: Firestore no une documentos al leer", [
            ("m", f"ordenes/{orden_id}"),
            ("m", f'  vehiculoId: "{vid}"          ← REF: solo el id'),
            ("m", f'  vehiculo: {{ placa: "{placa}", marca: "{o["vehiculo"]["marca"]}",'),
            ("m", f'              modelo: "{o["vehiculo"]["modelo"]}" }}   ← copia (instantánea)'),
            ("", ""),
            ("no", f"✘ Solo con la REF: leer la orden y luego leer vehiculos/{vid} = 2 lecturas"),
            ("ok", "✔ Con la copia: 1 lectura llena la pantalla de la orden"),
            ("", ""),
            ("n", "Firestore no verifica la REF: si el vehículo no existiera, nada falla."),
            ("n", "Las reglas y los lotes atómicos cuidan que la referencia sea válida."),
            ("n", "La copia es la del día del ingreso: las reglas impiden cambiarla."),
        ]),
    ]
    ancho, hueco = 740, 20
    for i, (titulo, lineas) in enumerate(tarjetas):
        x = x0 + i * (ancho + hueco)
        c.formas.append(f'<rect x="{x}" y="{y0}" width="{ancho}" height="395" rx="8" fill="#fff" stroke="#94a3b8"/>')
        c.texto(x + 16, y0 + 26, titulo, 14.5, "bold", ancla="start", capa=c.formas)
        y = y0 + 58
        for tipo, t in lineas:
            if t:
                color = {"ok": OK, "no": NO, "n": NOTA}.get(tipo, TINTA)
                c.texto(x + 16, y, t, 12.5, "bold" if tipo in ("ok", "no") else "normal", color, ancla="start",
                        fam=MONO if tipo == "m" else SANS, estilo="italic" if tipo == "n" else "normal", capa=c.formas)
            y += 28


def dibujar(ordenes):
    orden_id, o = max(ordenes.items(), key=lambda kv: len(kv[1]["historial"]))
    c = Lienzo(2400, 2410)
    panel(c, 30, 30, 1010, 730, f"Documento real: así guarda Firestore la ORDEN {o['numero']} y su HISTORIAL DE ESTADO")
    documento(c, orden_id, o, 60, 75)
    panel(c, 1070, 30, 1300, 730, "Cómo funciona: el técnico marca la orden como LISTO")
    secuencia(c, 1100, 90, o)
    panel(c, 30, 790, 1010, 440, "Del modelo lógico al físico")
    mapeo(c, 60, 840)
    panel(c, 1070, 790, 1300, 440, "Decisión de diseño")
    razones(c, 1100, 850)
    # legend for the document highlights
    lx, ly = 1840, 1060
    for i, (color, texto) in enumerate([(ESTADO, "campo derivado: estado actual"), (EMBEBIDO, "entradas anteriores: no se modifican"),
                                        (NUEVA, "entrada nueva: la agrega el cambio de estado")]):
        c.formas.append(f'<rect x="{lx}" y="{ly + i * 32 - 11}" width="30" height="22" fill="{color}" stroke="{TINTA}"/>')
        c.texto(lx + 42, ly + i * 32, texto, 13.5, ancla="start")
    panel(c, 30, 1260, 2340, 630, "Árbol de Firestore: así se anidan todos los documentos")
    arbol(c, 70, 1320)
    panel(c, 30, 1920, 2340, 470, "Ejemplos paso a paso: cómo funciona cada decisión")
    ejemplos(c, 60, 1960, orden_id, o)
    c.texto(1300, 1330, "Reglas para anidar (por qué cada detalle vive donde vive):", 15, "bold", ancla="start")
    for i, t in enumerate(["• Embebido: tiene tope, se lee siempre con su padre y lo escribe el mismo flujo.",
                           "• Subcolección: crece sin tope o se consulta sola, pero pertenece a un dueño;",
                           "   la ruta dice de quién es y las reglas autorizan por esa ruta.",
                           "• Colección de reserva: Firestore no garantiza valores sin repetir;",
                           "   el id del documento ES el valor (placa, DNI, número de orden).",
                           "• Referencia por id (vehiculoId, tecnicoId…): Firestore no la verifica;",
                           "   la cumplen las reglas y los lotes atómicos.",
                           "• Copias a propósito (instantáneas, clienteId, estado): Firestore no combina colecciones al leer."]):
        c.texto(1300, 1370 + i * 28, t, 13.5, ancla="start")
    c.texto(lx - 740, ly + 100, "Las fechas son milisegundos desde 1970; al lado, la hora de Lima.", 12.5, color="#475569",
            estilo="italic", ancla="start")
    return c


def main():
    destino = sys.argv[1] if len(sys.argv) > 1 else "."
    with open(os.path.join(destino, "documentos", "ordenes.json"), encoding="utf-8") as f:
        ordenes = json.load(f)["documentos"]
    print(dibujar(ordenes).guardar(os.path.join(destino, "historial-fisico.svg")))


if __name__ == "__main__":
    main()
