import sys

# Genera el grafo del diagrama de despliegue. Los nodos visibles son los artefactos;
# los nodos transparentes ("anclas") marcan los bordes de cada nodo de despliegue,
# de modo que las rutas salgan y lleguen exactamente al borde y sean rectas.
ESP_WS_DEV = float(sys.argv[2]) if len(sys.argv) > 2 else 4.7

T = 'style=filled, fillcolor="#FFFFFF00", color="#FFFFFF00"'
L = []
w = L.append

def artefacto(id_, texto, ancho, alto, grupo=None, forma="note", fondo="#FFF3E0", borde="#E65100"):
    g = f', group="{grupo}"' if grupo else ""
    w(f'  {id_} [shape={forma}, style=filled, fillcolor="{fondo}", color="{borde}", fixedsize=true, width={ancho}, height={alto}, label="{texto}"{g}];')

def ancla(id_, grupo=None, ancho=0.02, alto=0.02):
    g = f', group="{grupo}"' if grupo else ""
    w(f'  {id_} [shape=box, {T}, fixedsize=true, width={ancho}, height={alto}, label=""{g}];')

def fila(*ids):
    w("  { rank=same; " + " -> ".join(ids) + " [style=invis]; }")

w('digraph despliegue {')
w('  graph [rankdir=TB, splines=ortho, newrank=true, nodesep=0.45, ranksep=0.7, fontname="Arial", pad=0.4,')
w('         ];')
w('  node [fontname="Arial", fontsize=12];')
w('  edge [color="#424242", penwidth=1.2, arrowhead=none];')

# r1: artefactos de la estación y del teléfono
artefacto("A_BUILD", "Android Studio · Gradle\\nservicio-electrico-freddy.apk", 3.0, 0.75, "g_B")
w(f'  s_wd [shape=box, {T}, fixedsize=true, width={ESP_WS_DEV}, height=0.1, label=""];')
artefacto("APK", "servicio-electrico-freddy.apk\\nKotlin · MVVM", 2.9, 0.75)
ancla("M_dev", "g_eje")
artefacto("GPS", "Google Play services\\ncliente de mensajes push", 2.9, 0.75)
fila("A_BUILD", "s_wd", "APK", "M_dev", "GPS")

# r2: segundo artefacto de la estación y anclas inferiores del teléfono
artefacto("A_CLI", "Firebase CLI\\nfirebase deploy", 3.0, 0.75, "g_B")
for a, g in (("p2", "g_st"), ("p8", "g_fcm"), ("p4", "g_eje"), ("p3", "g_fs"), ("p1", "g_auth")):
    ancla(a, g)
fila("A_CLI", "p2", "p8", "p4", "p3", "p1")

# r3: borde inferior de la estación
ancla("A_WS", "g_B")
# r4: borde superior del proyecto Firebase
ancla("pB", "g_B")
# r5: bordes superiores de los servicios
for a, g in (("T_ST", "g_st"), ("T_FCM", "g_fcm"), ("M5", "g_eje"), ("T_FS", "g_fs"), ("T_AUTH", "g_auth")):
    ancla(a, g)
fila("T_ST", "T_FCM", "M5", "T_FS", "T_AUTH")

# r6: contenido de los servicios
artefacto("A_FOTOS", "Fotos JPG / PNG", 1.8, 0.6, "g_st")
artefacto("A_SRULES", "storage.rules", 1.7, 0.6)
w(f'  g1 [shape=box, {T}, fixedsize=true, width=0.35, height=0.1, label=""];')
w(f'  L_FCM [shape=box, {T}, fixedsize=true, width=2.6, height=0.6, label="", group="g_fcm"];')
w(f'  M6 [shape=box, {T}, fixedsize=true, width=0.5, height=0.1, label="", group="g_eje"];')
artefacto("DB", "BD NoSQL\\n(6 colecciones\\n+ contador de órdenes)", 2.3, 1.1, "g_fs", forma="cylinder", fondo="#E8F5E9", borde="#2E7D32")
artefacto("A_RULES", "firestore.rules", 1.8, 0.6)
w(f'  g2 [shape=box, {T}, fixedsize=true, width=0.35, height=0.1, label=""];')
artefacto("A_USERS", "Cuentas de usuario\\n(correo y contraseña)", 2.4, 0.8, "g_auth")
fila("A_FOTOS", "A_SRULES", "g1", "L_FCM", "M6", "DB", "A_RULES", "g2", "A_USERS")

# r7: bordes inferiores de los servicios
for a, g in (("Bb_FCM", "g_fcm"), ("M7", "g_eje"), ("Bb_FS", "g_fs"), ("Bb_AUTH", "g_auth")):
    ancla(a, g)
fila("Bb_FCM", "M7", "Bb_FS", "Bb_AUTH")

# r8: bordes superiores de Cloud Functions
for a, g in (("T7", "g_fcm"), ("T4", "g_eje"), ("T5", "g_fs"), ("T6", "g_auth")):
    ancla(a, g)
fila("T7", "T4", "T5", "T6")

# r9: artefacto de Cloud Functions
artefacto("A_FN", "index.js · Node.js 20 · firebase-admin\\ninvocables: crearTecnico, desactivarCuenta,\\nunirClienteConDni\\ndisparador de Firestore: notificarOrden\\n(Entregado no genera aviso)", 4.9, 1.45, "g_fs")

# alineación vertical (invisibles)
w('  edge [style=invis];')
w('  A_BUILD -> A_CLI -> A_WS;')
w('  M_dev -> p4; p4 -> M5 [minlen=3]; M5 -> M6 -> M7 -> T4; T5 -> A_FN;')
w('  T_ST -> A_FOTOS; T_FCM -> L_FCM -> Bb_FCM; T_FS -> DB -> Bb_FS; T_AUTH -> A_USERS -> Bb_AUTH;')
# rutas visibles
w('  edge [style=solid];')
w('  A_WS -> pB [weight=50];')
w('  p1 -> T_AUTH [minlen=3, weight=50]; p2 -> T_ST [minlen=3, weight=50]; p3 -> T_FS [minlen=3, weight=50]; p8 -> T_FCM [minlen=3, weight=50]; p4 -> T4 [minlen=6, weight=50];')
w('  Bb_FS -> T5 [weight=50]; Bb_AUTH -> T6 [weight=50]; Bb_FCM -> T7 [weight=50];')
w('}')
open(sys.argv[1], "w").write("\n".join(L))
