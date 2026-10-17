# Rediseño visual vintage-minimalista (estilo Notion)
Objetivo: paleta sin rojo, estilo plano tipo Notion, imágenes y logos reales importados, todo visible sin desplazarse. Rama: main (sin commits: el usuario ejecuta scripts/entrega_git.sh). Pruebas: ./gradlew assembleDebug testDebugUnitTest.

## Specs
- S1: "la paleta de colores roja puede cambiar, mejorar la paleta para que se vea mas vintage y minimalista"
- S2: "mejora esta interfaz un poco mas del estilo notion" (pantalla de perfil como referencia)
- S3: "la idea es que no exista tanto ir para abajo, toda la interfaz debe estar en una sola pantalla"
- S4: "los iconos y diseño no tienen que ser o verse como IA ... utiliza y basate en una investigacion de imagenes logos que podamos importar para que se vea mejor, tanto para imagenes sin copy e iconos"
- S5: "hice click en la imagen y no me deja salir de esa pantalla, corrige eso tambien"

## Tasks
- T1 (S1,S2) Tokens: colores claro/oscuro, radios, botones, tipografía. [inline] hecho: paleta papel/verde/mostaza, tema claro y oscuro, radios 8-10 dp, serif Lora en títulos
- T2 (S4) Recursos importados: fotos libres y logos de marcas (simple-icons, CC0) con archivo de licencias. [inline] hecho: 6 fotos de Unsplash, 15 logos de marcas, docs/05-desarrollo/recursos-y-licencias.md
- T3 (S2) Perfil estilo Notion. [inline] hecho: perfil como documento con filas de propiedades y acciones
- T4 (S4) Reemplazar ilustraciones caricaturescas por fotos, logos y glifos de una sola tinta. [inline] hecho: se quitaron las ilustraciones caricaturescas; etapas con glifo en círculo, fotos y logos
- T5 (S3) Pantallas sin desplazamiento: estado de orden, detalles, comprobante, perfil. [inline] hecho en estado de orden, línea de tiempo, comprobante, hallazgos, detalle del técnico y del jefe, perfil; los formularios largos siguen desplazándose
- T6 (S5) Visor de fotos: cierre con X bajo la barra de estado, tocando la foto y con Atrás. [inline] verificado en emulador: la X baja de la barra de estado; X y toque en la foto cierran

- T7 (S1,S2,S4) Japandi: paleta salvia/lino/madera, logo en sello, login y registro con franja y hoja redondeada, perfil como lista agrupada, íconos Heroicons. [inline] hecho, verificado en emulador (claro y oscuro)
- T8 (S3,S4) Escena de viaje (auto recoloreado, carretera, sol/luna animados), formularios largos en pasos con Siguiente (registro, nueva orden, nueva solicitud), diálogos cerrables con Atrás, tecla Siguiente/Hecho. [inline] hecho, verificado en emulador
- T9 Paleta del Figma, logos de 82 marcas con iniciales de respaldo, nombres con formato propio, error de duplicado por campo, salida correcta de la nueva solicitud, aviso para agregar vehículo en la nueva orden, perfil sin rol, auto del registro completo. [inline] hecho, verificado en emulador salvo el error de duplicado del técnico (cubierto por prueba unitaria)

- T10 Hojas modales con superficie sólida (sin capa de elevación), menú de acciones propio (MenuAcciones) en los 6 menús, logo F de circuito sin rayo, transiciones de pantalla con deslizamiento y barra inferior animada, auto del login que sale completo de la escena. Verificado en emulador; 50 pruebas unitarias en verde.

- T11 Estado Entregado (HU-49): el Jefe marca la entrega de una orden lista, la orden pasa al historial, desbloquea eliminar cuenta y vehículo; HU documentada. Escena de día soleada y logo de dos trazos. Verificado en emulador; tests en verde.

## Log
- L1 2026-10-16: "mejora mucho mas la interfaz, utiliza mejores iconos ... utiliza imagenes, dibujos, para el proceso se vea mas bonito y no solo una linea con puntos"
- L2 2026-10-16: "bien pero la idea es que no exista tanto  ir para abajo, toda la interfaz debe estar en una sola pantalla, y los iconos y disen1o no tienen que ser o vers como IA, mejora eso, utiliza y basate en una investigacion de imagenes logos que podamos importar para que se vea mejor, tanto para imagenes sin copy e iconos"
- L3 2026-10-16: "hice click en la imagen y no me deja ssalir de esa pantalla, corrige eso tambien"
- L4 2026-10-16: "mejora esta interfaz un poco mas del estilo notion, y la paleta de colores roja puede cambiar, mejorar la paleta para que se vea mas vintage y minimalista"
- Decisión (supuesto): acento verde racing #2F5D4E, mostaza #B8862F, papel crema, tinta cálida; formas de 8-10 dp, líneas finas.
- L5 2026-10-16: "mejor aplica un diseno Japandi ... mejora esta interfaz el icono es horrible consigue otro o mejor hazlo como esto [login con franja y hoja] pero sin los iconos de google, apple y para el perfil hazlo algo asi [lista agrupada]"
- L6 2026-10-16: "y la libreria de icon que se debe usar es heroicon"
- L7 2026-10-16: "recolorea el auto con la paleta y que no sea el icono de un sol tambien que se vea una animacion y el sol o luna tiene que estar arrriba del auto que se vea como si estuviera de recorrdio por la carretera, ademas que los formulario que no entren en todo la pantalla, pasena un boton de siguiente y se siga rellenando la otra informacion, mejoras el ux tambien dale una revisada controla el simiulador"
- L8 2026-10-17: "usa los colores del figma mejor" (variables del Figma Proyecto app moviles, colecciones Tema y Tema oscuro)
- L9 2026-10-17: "mejora los colores de esos modales, se puede mejorar el diseno de eso tambien, otro logo mas distintos que no sea un rayo, y mejora las animaciones entre pantallas ... la animacion del auto tambien mejorala"
- L10 2026-10-17: "corrije eso se tiene que ver un manana soleada y el sol se debe ver bien no rojo ... este es el nuevo logo, al ver todo verifica si cumple todo de los diagrama y las historia de usuario"; luego "1. y agrega a mi md de hu, y dime en que parte de mi ppt colocarlo"
