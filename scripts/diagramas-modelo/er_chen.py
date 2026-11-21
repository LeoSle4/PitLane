#!/usr/bin/env python3
"""ER (Chen) of PitLane: technology-free conceptual model with relationship attributes.

Usage: python3 -I scripts/diagramas-modelo/er_chen.py docs/04-arquitectura/er-chen
Writes er-chen.svg/.png (with attributes and the Firebase legend) and er-chen-vista.svg/.png (entities and
relationships only). Firebase details belong to the logical model (logico.py), not here.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dibujo import Lienzo  # noqa: E402

CX = 1300                           # vertical axis: USUARIO, the history relationship and ORDEN
Y_SUB, Y_USU, Y_REL, Y_ORD, Y_BAJO = 175, 340, 640, 1000, 1600
X_CLI, X_TEC = 350, 2250            # subtype columns (and their lanes)
X_OBS, X_CONF = 880, 1080           # lanes of "observa" (outer) and "da conformidad" (inner)
X_ATI = 2550                        # outer lane of "atiende"


def polar(cx, cy, r, grados):
    a = math.radians(grados)
    return cx + r * math.cos(a), cy - r * math.sin(a)


def dibujar(con_atributos):
    c = Lienzo(2640, 2170 if con_atributos else 1720)

    # ---- entities ----
    c.entidad("JEF", CX, 70, "JEFE DE TALLER")
    c.entidad("CLI", X_CLI, Y_SUB, "CLIENTE")
    c.entidad("TEC", X_TEC, Y_SUB, "TÉCNICO")
    c.jerarquia("ISA", CX, Y_SUB)
    c.entidad("USU", CX, Y_USU, "USUARIO", ancho=230)
    c.entidad("CUE", 760, Y_USU, "CUENTA")
    c.entidad("DIS", 1840, Y_USU, "DISPOSITIVO", debil=True)
    c.entidad("EST", 1700, Y_REL, "ESTADO DE ORDEN")
    c.entidad("ORD", CX, Y_ORD, "ORDEN", ancho=270, alto=96)
    c.entidad("NOT", 1840, 540, "NOTIFICACIÓN", debil=True)
    c.entidad("VEH", X_CLI, Y_ORD, "VEHÍCULO")
    c.entidad("AVA", X_TEC, Y_ORD, "AVANCE", debil=True)
    c.entidad("SOL", X_CLI, Y_BAJO, "SOLICITUD")

    # ---- relationships ----
    c.relacion("r_aut", 1030, Y_USU, "se autentica con")
    c.relacion("r_dis", 1570, Y_USU, "registra", identificadora=True)
    c.relacion("r_his", CX, Y_REL, "cambia de estado", ancho=200, alto=84)
    c.relacion("r_obs", X_OBS, Y_REL, "observa")
    c.relacion("r_con", X_CONF, Y_REL, "da conformidad")
    c.relacion("r_gen", 1700, 900, "genera")
    c.relacion("r_rec", 1600, 540, "recibe", identificadora=True)
    c.relacion("r_pos", X_CLI, Y_REL, "posee")
    c.relacion("r_red", X_TEC, Y_REL, "redacta")
    c.relacion("r_ing", 760, Y_ORD, "ingresa en")
    c.relacion("r_doc", 1780, Y_ORD, "documenta", identificadora=True)
    c.relacion("r_rep", X_CLI, 1300, "se reporta en")
    c.relacion("r_ori", 800, Y_BAJO, "origina")
    c.relacion("r_ate", 1700, Y_BAJO, "atiende")

    usu_b = Y_USU + 30
    ord_t, ord_b = Y_ORD - 48, Y_ORD + 48

    # ---- hierarchy: total (double line) and disjoint (d) ----
    c.camino([(CX, Y_SUB), (CX, 70)])
    c.camino([(CX, Y_SUB), (X_CLI, Y_SUB)])
    c.camino([(CX, Y_SUB), (X_TEC, Y_SUB)])
    c.camino([(CX, Y_SUB), (CX, Y_USU)], doble=True)

    # ---- relationship lines (orthogonal); doble = total participation ----
    c.camino([(760, Y_USU), (1030, Y_USU)], doble=True)              # every CUENTA belongs to a USUARIO
    c.camino([(1030, Y_USU), (CX, Y_USU)])
    c.cardinalidad(850, Y_USU - 16, "1")
    c.cardinalidad(1170, Y_USU - 16, "1")
    c.camino([(CX, Y_USU), (1570, Y_USU)])
    c.camino([(1570, Y_USU), (1840, Y_USU)], doble=True)
    c.cardinalidad(1430, Y_USU - 16, "1")
    c.cardinalidad(1745, Y_USU - 16, "N")

    # history: ternary ORDEN–ESTADO–USUARIO
    c.camino([(CX, usu_b), (CX, Y_REL)])
    c.camino([(CX, Y_REL), (1700, Y_REL)])
    c.camino([(CX, Y_REL), (CX, ord_t)], doble=True)                 # every order has at least INGRESADO
    c.cardinalidad(CX + 14, usu_b + 20, "1")
    c.cardinalidad(1590, Y_REL - 16, "M")
    c.cardinalidad(CX + 14, ord_t - 20, "N")

    # observa (outer lane) and da conformidad (inner lane), nested so they never cross
    c.camino([(CX - 80, usu_b), (CX - 80, 440), (X_OBS, 440), (X_OBS, Y_REL)])
    c.camino([(X_OBS, Y_REL), (X_OBS, 870), (CX - 75, 870), (CX - 75, ord_t)])
    c.cardinalidad(CX - 94, usu_b + 20, "M")
    c.cardinalidad(CX - 89, ord_t - 20, "N")
    c.camino([(CX - 40, usu_b), (CX - 40, 480), (X_CONF, 480), (X_CONF, Y_REL)])
    c.camino([(X_CONF, Y_REL), (X_CONF, 830), (CX - 40, 830), (CX - 40, ord_t)])
    c.cardinalidad(CX - 54, usu_b + 20, "1")
    c.cardinalidad(CX - 54, ord_t - 20, "N")

    # genera: every NOTIFICACIÓN comes from one ORDEN (total, not identifying)
    c.camino([(CX + 60, ord_t), (CX + 60, 900), (1700, 900)])
    c.camino([(1700, 900), (1840, 900), (1840, 563)], doble=True)
    c.cardinalidad(CX + 74, ord_t - 20, "1")
    c.cardinalidad(1854, 583, "N")
    # recibe: the NOTIFICACIÓN belongs to the USUARIO who receives it (identifying: it lives under that user)
    c.camino([(CX + 40, usu_b), (CX + 40, 540), (1600, 540)])
    c.camino([(1600, 540), (1840, 540)], doble=True)
    c.cardinalidad(CX + 54, usu_b + 20, "1")
    c.cardinalidad(1730, 524, "N")

    # client side
    c.camino([(X_CLI, Y_SUB), (X_CLI, Y_REL)])
    c.camino([(X_CLI, Y_REL), (X_CLI, Y_ORD)], doble=True)
    c.cardinalidad(X_CLI + 14, Y_SUB + 43, "1")
    c.cardinalidad(X_CLI + 14, Y_ORD - 46, "N")
    c.camino([(X_CLI, Y_ORD), (760, Y_ORD)])
    c.camino([(760, Y_ORD), (CX, Y_ORD)], doble=True)
    c.cardinalidad(460, Y_ORD - 16, "1")
    c.cardinalidad(1150, Y_ORD - 16, "N")
    c.camino([(X_CLI, Y_ORD), (X_CLI, 1300)])
    c.camino([(X_CLI, 1300), (X_CLI, Y_BAJO)], doble=True)
    c.cardinalidad(X_CLI + 14, Y_ORD + 46, "1")
    c.cardinalidad(X_CLI + 14, Y_BAJO - 46, "N")
    c.camino([(X_CLI, Y_BAJO), (800, Y_BAJO)])
    c.camino([(800, Y_BAJO), (CX - 30, Y_BAJO), (CX - 30, ord_b)])
    c.cardinalidad(460, Y_BAJO - 16, "1")
    c.cardinalidad(CX - 44, ord_b + 20, "1")

    # technician side
    c.camino([(X_TEC, Y_SUB), (X_TEC, Y_REL)])
    c.camino([(X_TEC, Y_REL), (X_TEC, Y_ORD)], doble=True)
    c.cardinalidad(X_TEC + 14, Y_SUB + 43, "1")
    c.cardinalidad(X_TEC + 14, Y_ORD - 46, "N")
    c.camino([(CX, Y_ORD), (1780, Y_ORD)])
    c.camino([(1780, Y_ORD), (X_TEC, Y_ORD)], doble=True)
    c.cardinalidad(1460, Y_ORD - 16, "1")
    c.cardinalidad(2140, Y_ORD - 16, "N")
    c.camino([(X_TEC + 75, Y_SUB), (X_ATI, Y_SUB), (X_ATI, Y_BAJO), (1700, Y_BAJO)])
    c.camino([(1700, Y_BAJO), (CX + 30, Y_BAJO), (CX + 30, ord_b)], doble=True)
    c.cardinalidad(X_TEC + 92, Y_SUB - 16, "1")
    c.cardinalidad(CX + 44, ord_b + 20, "N")

    if con_atributos:
        a = c.atributo
        # USUARIO (composite name)
        a("USU", 1180, 250, "DNI", clave="solido")
        a("USU", 1420, 250, "teléfono")
        a("USU", 1545, 238, "activo")
        nom = a("USU", 1500, 430, "nombre completo")
        a(nom, 1670, 398, "nombres")
        a(nom, 1670, 462, "apellidos")
        # CUENTA
        a("CUE", 640, 262, "correo", clave="solido")
        a("CUE", 760, 232, "contraseña")
        a("CUE", 885, 262, "fecha de registro")
        # DISPOSITIVO
        a("DIS", 1780, 262, "token", clave="punteado")
        # ESTADO (catalog)
        a("EST", 1590, 770, "código", clave="solido")
        a("EST", 1680, 810, "nombre")
        a("EST", 1765, 770, "secuencia")
        # history attributes live on the relationship
        a("r_his", 1440, 592, "fecha y hora", clave="punteado")
        a("r_his", 1460, 712, "motivo")
        # observa / da conformidad
        a("r_obs", 775, 735, "fecha y hora", clave="punteado")
        a("r_obs", 775, 805, "texto")
        a("r_obs", 985, 770, "acuerdo")
        a("r_con", 980, 560, "fecha y hora")
        # NOTIFICACIÓN
        a("NOT", 2075, 455, "título")
        a("NOT", 2110, 505, "mensaje")
        a("NOT", 2120, 555, "fecha y hora", clave="punteado")
        a("NOT", 2105, 605, "leída")
        a("NOT", 2045, 655, "tipo")
        # ingresa en: the vehicle as it entered the shop (above the diamond, under the "observa" line)
        a("r_ing", 525, 915, "fecha de ingreso")
        a("r_ing", 665, 905, "kilometraje")
        a("r_ing", 800, 915, "combustible")
        a("r_ing", 985, 915, "observaciones de ingreso")
        # ORDEN: a symmetric fan from the bottom corners, outside the "origina" and "atiende" lines
        a("ORD", 990, 1105, "número", clave="solido", desde=(CX - 135, ord_b - 5))
        a("ORD", 1030, 1195, "falla", desde=(CX - 110, ord_b))
        a("ORD", 1110, 1285, "motivo de anulación", derivado=True, desde=(CX - 75, ord_b))
        a("ORD", 1610, 1105, "estado actual", derivado=True, desde=(CX + 135, ord_b - 5))
        a("ORD", 1570, 1195, "último aviso", derivado=True, desde=(CX + 110, ord_b))
        fo = a("ORD", 1490, 1275, "fotos", multivaluado=True, desde=(CX + 75, ord_b))
        a(fo, 1420, 1380, "archivo")
        a(fo, 1580, 1380, "fecha y hora")
        # VEHÍCULO
        for (ang, nombre, k, r) in [(150, "placa", "solido", "#fff"), (166, "marca", None, "#fff"),
                                    (182, "modelo", None, "#fff"), (198, "año", None, "#fff"),
                                    (214, "color", None, "#fff"), (230, "activo", None, "#fff")]:
            x, y = polar(X_CLI, Y_ORD, 265, ang)
            a("VEH", x, y, nombre, clave=k, relleno=r)
        # SOLICITUD
        for (ang, nombre, k, m) in [(130, "código", "solido", False), (150, "descripción", None, False),
                                    (170, "síntomas", None, True), (190, "fecha de llegada", None, False),
                                    (210, "estado", None, False), (230, "fecha de creación", None, False)]:
            x, y = polar(X_CLI, Y_BAJO, 265, ang)
            a("SOL", x, y, nombre, clave=k, multivaluado=m)
        sf = a("SOL", X_CLI, 1760, "fotos", multivaluado=True)
        a(sf, 520, 1850, "archivo")
        a(sf, 510, 1700, "fecha y hora")
        # AVANCE (weak: partial key)
        a("AVA", 2110, 1150, "fecha y hora", clave="punteado")
        a("AVA", 2250, 1190, "tipo")
        a("AVA", 2390, 1150, "descripción")
        af = a("AVA", 2250, 1300, "fotos", multivaluado=True)
        a(af, 2150, 1400, "archivo")
        a(af, 2350, 1400, "fecha y hora")
        leyenda(c, 1930)
    return c


def leyenda(c, y0):
    """Chen notation only: Firebase details are shown in the logical model."""
    c.fondos.append(f'<rect x="40" y="{y0}" width="2560" height="190" rx="10" fill="#f8fafc" stroke="#94a3b8" stroke-width="1.4"/>')
    c.texto(70, y0 + 30, "Notación de Chen", 17, "bold", ancla="start")
    columnas = [
        ["Rectángulo: entidad", "Doble rectángulo: entidad débil", "Rombo: relación (puede tener atributos)"],
        ["Doble rombo: relación identificadora", "Línea doble: participación total", "Círculo d: jerarquía disjunta"],
        ["Subrayado: clave; punteado: clave parcial", "Elipse punteada: atributo derivado",
         "Elipse doble: atributo multivaluado"],
        ["1, N, M: cardinalidad", "Ternaria: cambia de estado (el historial)",
         "NOTIFICACIÓN: la recibe un USUARIO y la genera una ORDEN",
         "JEFE DE TALLER actúa como USUARIO: cambia de estado, observa, da conformidad"],
    ]
    for k, col in enumerate(columnas):
        for i, t in enumerate(col):
            c.texto(70 + k * 640, y0 + 68 + i * 30, t, 13.5, color="#334155", ancla="start")


def main():
    destino = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(destino, exist_ok=True)
    for nombre, attrs in (("er-chen", True), ("er-chen-vista", False)):
        print(dibujar(attrs).guardar(os.path.join(destino, f"{nombre}.svg")))


if __name__ == "__main__":
    main()
