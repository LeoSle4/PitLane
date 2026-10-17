# Recursos gráficos y licencias

Todo lo importado tiene licencia que permite usarlo en la app sin atribución obligatoria. Aun así se deja el registro del origen.

| Recurso | Dónde está | Origen | Licencia |
|---|---|---|---|
| Íconos de interfaz | `res/drawable/ic_*.xml` | Heroicons 2.2 (contorno y sólido), paquete `heroicons`. El ícono de auto no existe en la librería y se dibujó con el mismo trazo | MIT |
| Logos de marcas de autos (82 marcas, entre ellas Toyota, Hyundai, Jeep, Mercedes-Benz, Mazda, Chery, BYD, Geely, Great Wall, Haval, Changan y JAC) | `res/drawable-nodpi/marca_*.png` | Conjunto de datos `car-logos-dataset` de GitHub (imágenes `thumb`) | Los logos son marcas registradas de sus dueños y se muestran solo para identificar la marca del vehículo. Las marcas sin logo muestran sus iniciales |
| Fotos de demostración y cabecera | `res/drawable-nodpi/foto_*.jpg` | Unsplash | Licencia Unsplash (uso libre, sin atribución obligatoria) |
| Animación del auto de las pantallas de acceso | `res/raw/auto_viaje_carbon.json`, `auto_viaje_avena.json` (la misma animación recoloreada con la paleta: carbón para fondos claros, avena para fondos oscuros) | "Free Car Stop & Go Animation" de Carl McNeill, en LottieFiles | Lottie Simple License (uso libre, también comercial) |
| Reproductor de animaciones | dependencia `com.airbnb.android:lottie` 6.7.1 | Airbnb Lottie para Android | Apache 2.0 |
| Sol y luna del login | `res/drawable/ic_astro_sol.xml`, `ic_astro_luna.xml` | Heroicons 2.2 (sólido) | MIT |
| Tipografía de títulos | `res/font/lora.ttf` | Lora, Google Fonts | SIL OFL 1.1 |
| Tipografía de texto | `res/font/plus_jakarta_sans.ttf` | Plus Jakarta Sans, Google Fonts | SIL OFL 1.1 |

## Fotos de Unsplash usadas

Identificador de cada foto en `https://unsplash.com/photos/<id>` (el prefijo `photo-` forma parte de la URL de descarga):

| Archivo | Identificador de descarga |
|---|---|
| `foto_demo_frente.jpg` | `1441148345475-03a2e82f9719` |
| `foto_demo_lateral.jpg` | `1714225612411-4875533d561a` |
| `foto_demo_motor.jpg` | `1752774581629-464238ee6996` |
| `foto_demo_bateria.jpg` | `1597766325363-f5576d851d6a` |
| `foto_demo_tablero.jpg` | `1533630217389-3a5e4dff5683` |
| `foto_cabecera_taller.jpg` | `1771340012378-3c86cb649193` |

Las fotos se descargaron recortadas a 1000 px de ancho. Las fotos de demostración son solo datos de ejemplo: las reales las sube el taller.

## Colores y medidas

Los colores del tema claro y oscuro, los colores de estado y los radios (campo 14, tarjeta 20, modal 28) salen de las variables del archivo de Figma "Proyecto app moviles" (colecciones Tema, Tema oscuro y Geometría) y están en `res/values/colors.xml`, `res/values-night/colors.xml` y `res/values/dimens.xml`.
