import sys, json
# ---------- datos ----------
columnas = [  # (clave, vista, viewmodel, interfaz, repositorio, coleccion)
    ("PER", "PerfilView", "PerfilViewModel", "IPerfil\\nRepository", "PerfilRepository", "«usuarios»"),
    ("ACC", "AccesoView", "AuthViewModel", "IAuth\\nRepository", "AuthRepository", "«usuarios»"),
    ("CLI", "ClientesView", "ClienteViewModel", "ICliente\\nRepository", "ClienteRepository", "«usuarios»"),
    ("TEC", "TecnicosView", "TecnicoViewModel", "ITecnico\\nRepository", "TecnicoRepository", "«usuarios»"),
    ("VEH", "VehiculosView", "VehiculoViewModel", "IVehiculo\\nRepository", "VehiculoRepository", "«vehiculos»"),
    ("SOL", "SolicitudesView", "SolicitudViewModel", "ISolicitud\\nRepository", "SolicitudRepository", "«solicitudes» + fotos"),
    ("ORD", "OrdenesView", "OrdenViewModel", "IOrden\\nRepository", "OrdenRepository", "«ordenes» + fotos"),
    ("AVA", "AvancesView", "AvanceViewModel", "IAvance\\nRepository", "AvanceRepository", "«avances» + fotos"),
    ("NOT", "NotificacionesView", "NotificacionViewModel", "INotificacion\\nRepository", "NotificacionRepository", "«notificaciones» + token"),
]
orden_cols = ["ACC", "NOT", "PER", "CLI", "TEC", "VEH", "SOL", "ORD", "AVA"]
cfg = dict(ancho_vista=2.0, ancho_vm=2.4, ancho_iface=1.5, ancho_repo=2.4, alto=0.80, nodesep=0.40, ranksep=0.85)

SL = float(sys.argv[2]) if len(sys.argv) > 2 else 2.4
SR = float(sys.argv[3]) if len(sys.argv) > 3 else 2.4

def punto(id_):
    w(f'    {id_} [shape=box, style=filled, fillcolor="#FFFFFF00", color="#FFFFFF00", width=0.01, height=0.01, group="g_eje", label=""];')

def col(clave): return next(c for c in columnas if c[0] == clave)

L = []
w = L.append
w('digraph componentes {')
w('  graph [rankdir=TB, splines=ortho, newrank=true, nodesep=%.2f, ranksep=%.2f, fontname="Arial", pad=0.4,' % (cfg["nodesep"], cfg["ranksep"]))
w('         labelloc=t, fontsize=18,')
w('         label=<<B>Diagrama de Componentes</B><BR/>Aplicación móvil para la recepción y seguimiento de reparaciones de vehículos en Servicio Eléctrico Freddy · MVVM + Repository<BR/> <BR/> >];')
w('  node [shape=component, style=filled, fontname="Arial", fontsize=12, margin="0.20,0.10", fixedsize=true, color="#424242"];')
w('  edge [color="#424242", arrowsize=0.8, penwidth=1.1];')

def estilo(tipo):
    return {"view": ("#E3F2FD", "#1565C0"), "android": ("#E0F7FA", "#00838F"), "vm": ("#EDE7F6", "#4527A0"),
            "repo": ("#E8F5E9", "#2E7D32"), "ds": ("#F1F8E9", "#558B2F"), "lib": ("#FFF3E0", "#E65100"),
            "svc": ("#FAFAFA", "#616161")}[tipo]

def nodo(id_, estereotipo, nombre, tipo, ancho, extra="", grupo=None, alto=None, linea2=""):
    fondo, borde = estilo(tipo)
    cuerpo = nombre if not linea2 else nombre + "<BR/>" + linea2
    g = f', group="{grupo}"' if grupo else ""
    h = alto or cfg["alto"]
    w(f'    {id_} [label=<<I>«{estereotipo}»</I><BR/>{cuerpo}>, fillcolor="{fondo}", color="{borde}", width={ancho:.2f}, height={h:.2f}{g}{extra}];')

def invisible(id_, ancho, grupo=None, alto=0.05):
    g = f', group="{grupo}"' if grupo else ""
    w(f'    {id_} [shape=point, style=invis, width=0.01, height={alto}{g}, label=""];')

def espaciador(id_, ancho, alto=0.2, grupo=None):
    g = f', group="{grupo}"' if grupo else ""
    w(f'    {id_} [shape=box, style=filled, fillcolor="#FFFFFF00", color="#FFFFFF00", width={ancho:.2f}, height={alto}{g}, label=""];')

# ---------- VISTA ----------
w('    { rank=same;')
espaciador("sp_vista", SL, grupo="g_sp")
espaciador("spr_vista", SR, grupo="g_spr")
punto("M_vista")
# columnas con extras
secuencia = []
for c in orden_cols:
    secuencia.append(c)
for c in orden_cols:
    cl = col(c)
    ancho = cfg["ancho_vista"] + (0.5 if c == "NOT" else 0)
    nodo(f"V_{c}", "View", cl[1], "view", ancho, grupo=f"g_{c}")
nodo("V_NAV", "Android", "NavegacionView", "android", 2.6, linea2="actividades principal y de acceso")
nodo("V_MSG", "Android", "MensajesPushService", "android", 2.6, linea2="FirebaseMessagingService")
w('    }')

# ---------- VIEWMODEL ----------
w('    { rank=same;')
espaciador("sp_vm", SL, grupo="g_sp")
espaciador("spr_vm", SR, grupo="g_spr")
punto("M_vm")
for c in orden_cols:
    cl = col(c)
    ancho = cfg["ancho_vm"] + (0.9 if c in ("ACC",) else 0) + (1.7 if c == "NOT" else 0)
    nodo(f"VM_{c}", "ViewModel", cl[2], "vm", ancho, grupo=f"g_{c}")
w('    }')

# ---------- REPOSITORY ----------
w('    { rank=same;')
w(f'      sp_ifaz [shape=box, style=filled, fillcolor="#FFFFFF00", color="#FFFFFF00", width={SL}, height=0.4, group="g_sp", label=""];')
w(f'      spr_ifaz [shape=box, style=filled, fillcolor="#FFFFFF00", color="#FFFFFF00", width={SR}, height=0.4, group="g_spr", label=""];')
punto('M_ifaz')
for c in orden_cols:
    cl = col(c)
    w(f'      I_{c} [shape=box, style=filled, fillcolor="#FFFFFF00", color="#FFFFFF00", fixedsize=true, width={cfg["ancho_iface"]}, height=0.80, group="g_{c}", label=<<FONT POINT-SIZE="28" COLOR="#2E7D32">○</FONT><BR/><FONT POINT-SIZE="11">{cl[3].replace(chr(92)+"n", "<BR/>")}</FONT>>];')
w('    }')
w('    { rank=same;')
nodo("DEP", "Android", "Dependencias", "android", 2.2, grupo="g_sp", linea2="raíz de composición")
espaciador("spr_repo", SR, grupo="g_spr")
punto("M_repo")
for c in orden_cols:
    cl = col(c)
    nodo(f"R_{c}", "Repository", cl[4], "repo", cfg["ancho_repo"], grupo=f"g_{c}", linea2=cl[5])
w('    }')
w('    { rank=same; A_USU [shape=box, style=filled, fillcolor="#FFFFFF00", color="#FFFFFF00", width=0.3, height=0.05, label="", group="g_usu"]; A_DOC [shape=box, style=filled, fillcolor="#FFFFFF00", color="#FFFFFF00", width=0.3, height=0.05, label="", group="g_eje"]; A_FOT [shape=box, style=filled, fillcolor="#FFFFFF00", color="#FFFFFF00", width=0.3, height=0.05, label="", group="g_fot"]; }')

# ---------- FUENTES ----------
w('    { rank=same;')
espaciador("spl_ds", 1.8)
espaciador("spr_ds", 1.8)
nodo("D_USU", "DataSource", "UsuariosDataSource", "ds", 3.2, grupo="g_usu", linea2="Authentication + «usuarios»<BR/>+ funciones de cuenta", alto=1.0)
nodo("D_DOC", "DataSource", "DocumentosDataSource", "ds", 3.2, grupo="g_eje", linea2="Firestore · lotes y transacciones", alto=1.0)
nodo("D_FOT", "DataSource", "FotoStorage", "ds", 3.2, grupo="g_fot", linea2="valida y sube a Cloud Storage", alto=1.0)
w('    }')

# ---------- SDK ----------
w('  FSDK [shape=component, style=filled, fillcolor="#FFF3E0", color="#E65100", fixedsize=true, width=11.4, height=0.95, group="g_eje",')
w('        label=<<I>«library»</I><BR/>Firebase SDK para Android<BR/>firebase-auth · firebase-firestore · firebase-storage · firebase-functions · firebase-messaging>];')

# ---------- FIREBASE ----------
w('    { rank=same;')
espaciador("spl_nube", 1.8)
espaciador("spr_nube", 1.8)
nodo("C_ST", "service", "Cloud Storage", "svc", 2.4, linea2="+ Security Rules")
nodo("C_AUTH", "service", "Firebase Authentication", "svc", 2.4)
nodo("C_FS", "service", "Cloud Firestore", "svc", 2.4, linea2="+ Security Rules")
nodo("C_FCM", "service", "Firebase Cloud Messaging", "svc", 2.4)
punto("M_nube")
w('    }')
nodo("C_FN", "service", "Cloud Functions", "svc", 8.2, linea2="Admin SDK", grupo="g_eje")

# ---------- aristas ----------
def con_eje(pref, ini, fin, eje):
    cols = [f"{pref}{c}" for c in orden_cols]
    return [ini] + cols[:3] + [eje] + cols[3:] + [fin]
sec_vista = ["sp_vista", "V_ACC", "V_NAV", "V_NOT", "V_MSG"] + [f"V_{c}" for c in orden_cols[2:3]] + ["M_vista"] + [f"V_{c}" for c in orden_cols[3:]] + ["spr_vista"]
sec_vm = con_eje("VM_", "sp_vm", "spr_vm", "M_vm")
sec_if = con_eje("I_", "sp_ifaz", "spr_ifaz", "M_ifaz")
sec_rp = con_eje("R_", "DEP", "spr_repo", "M_repo")
for sec in (sec_vista, sec_vm, sec_if, sec_rp):
    w("  { rank=same; " + " -> ".join(sec) + " [style=invis]; }")
w('  edge [style=dashed, arrowhead=vee];')
for c in orden_cols:
    w(f'  V_{c} -> VM_{c};')
w('  V_NAV -> VM_ACC; V_NAV -> VM_NOT; V_MSG -> VM_NOT; V_MSG -> VM_ACC;')
w('  V_ORD -> VM_CLI; V_ORD -> VM_VEH; V_ORD -> VM_SOL; V_ORD -> VM_TEC; V_ORD -> VM_AVA;')
w('  V_CLI -> VM_VEH; V_SOL -> VM_VEH; V_VEH -> VM_CLI;')
w('  edge [style=solid, arrowhead=none];')
for c in orden_cols:
    w(f'  VM_{c} -> I_{c}; I_{c} -> R_{c};')
w('  edge [style=dashed, arrowhead=vee];')

for c in orden_cols:
    w(f'  R_{c} -> A_DOC [style=invis];') if False else None
w('  R_PER -> A_USU [style=invis]; R_ORD -> A_FOT [style=invis];')
w('  { rank=same; spl_ds -> D_USU -> D_DOC -> D_FOT -> spr_ds [style=invis]; }')
w('  { rank=same; A_USU -> A_DOC -> A_FOT [style=invis]; }')
w('  A_USU -> D_USU; A_DOC -> D_DOC; A_FOT -> D_FOT;')
w('  D_USU -> FSDK; D_DOC -> FSDK [weight=300]; D_FOT -> FSDK;')
w('  FSDK -> C_ST; FSDK -> C_AUTH; FSDK -> C_FS;')
w('  FSDK -> C_FCM [dir=both, arrowtail=vee];')
w('  FSDK -> C_FN;')
w('  C_FS -> C_FN [dir=both, arrowtail=vee];')
w('  C_FN -> C_AUTH; C_FN -> C_FCM;')
# orden horizontal
w('  { rank=same; spl_nube -> C_ST -> C_AUTH -> M_nube -> C_FS -> C_FCM -> spr_nube [style=invis]; }')
w('  M_vista -> M_vm -> M_ifaz -> M_repo -> A_DOC [style=invis];')
w('  FSDK -> M_nube [style=invis, weight=300]; M_nube -> C_FN [style=invis, weight=300];')
w('  edge [style=dashed, arrowhead=vee];')
w('  DEP -> R_ACC;')
w('}')
open(sys.argv[1], "w").write("\n".join(L))
