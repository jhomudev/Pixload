# Pixload (Android)

Aplicación Android nativa en Java para guardar obras de Pixiv. **Versión 0.1.0 de desarrollo**, probada parcialmente en un Motorola Edge 30 Pro. No es una app oficial de Pixiv.

La [primera versión](https://github.com/jhomudev/Pixload/releases/tag/v0.1.0) incluye un APK de depuración para probarla. No es un paquete de producción ni está firmado para distribución en tiendas.

## Funcionalidad disponible

- Abrir enlaces de obras mediante pegado, «Compartir» o «Abrir con». Al pegar o compartir texto con título y URL, la app extrae solo el enlace de Pixiv.
- Previsualizar título, autor, portada, miniaturas de páginas y texto de novelas cuando Pixiv lo proporciona. Las miniaturas se cargan a resolución reducida y no afectan a la descarga de originales.
- Descargar páginas originales seleccionadas de ilustraciones y manga; novelas en TXT; ugoira en GIF animado o MP4 (no se ofrece ZIP original).
- Usar Pixiv sin iniciar sesión para obras públicas o abrir el inicio de sesión de Pixiv en una WebView para usar las cookies de la cuenta. La app comprueba la sesión, vuelve a Pixload tras la autenticación y reintenta el último enlace. También permite cerrar la sesión borrando las cookies de la app para entrar con otra cuenta; no afecta al navegador externo.
- Elegir carpeta principal con el selector de documentos de Android y, opcionalmente, preguntar en cada descarga. Animaciones, novelas e ilustraciones de una página se guardan directamente con el título de la obra como `título.ext` (caracteres incompatibles reemplazados); si ya existe ese nombre se añade un sufijo alfanumérico de cuatro caracteres. Una obra de dos o más páginas crea una carpeta `título-xxxx` con las páginas originales `001.ext`, `002.ext`, etc. Incluso al seleccionar solo una página de una obra multipágina se conserva su número y su carpeta.
- Descargas en primer plano de servicio (continúan mientras la interfaz no esté abierta) con progreso en la app y en la notificación (título de la obra como encabezado y etapa/avance como descripción); cancelación desde la notificación. Al finalizar se limpia la URL y la obra previsualizada correspondientes si no se ha comenzado a trabajar en otra; «Abrir en galería» muestra el resultado dentro de Pixload y oculta el aviso de progreso tras pulsarlo. Archivos `.part` incompletos se eliminan ante error.
- Galería interna de descargas nuevas (y la última anterior si conserva su URI), con miniaturas, título, fecha, número de elementos para carpetas y eliminación confirmada de los archivos reales. Permite recorrer las páginas de ilustraciones y manga, reproducir GIF y MP4 (en bucle para animaciones cortas) y leer TXT. Si una carpeta deja de estar accesible, su ficha muestra el error, sin inventar archivos.
- Interfaz oscura con inicio para enlaces de Pixiv, vista previa con selección de páginas o formato de ugoira, galería y ajustes de carpeta, sesión y formato predeterminado. La pestaña de vista previa no es una cola persistente; el progreso continúa en la notificación.

## Pendiente antes de considerar la app completa

- GIF usa un codificador rápido con paleta global de 256 colores y tramado ordenado; los degradados pueden perder algo de fidelidad. Puede reducir la resolución a 960 píxeles. MP4 es silencioso, codificado a 30 fps y puede reducir la resolución a 1280 píxeles; el código MP4 es independiente del codificador GIF. Los tiempos originales se aproximan al fotograma de vídeo más cercano. La conversión requiere espacio temporal en el almacenamiento privado de la app.
- Recuperar de forma fiable la cola después de reiniciar el teléfono o tras una terminación forzada del proceso; reintentos y descargas reanudables.
- Reproducción de la animación en la vista previa, pruebas con cuenta y sin cuenta, mejoras de accesibilidad e internacionalización.
- Verificar en un Android real la compatibilidad con Pixiv, que utiliza interfaces web no garantizadas como API pública. Obras privadas, eliminadas o no autorizadas no pueden descargarse.
- Verificar el inicio de sesión con una cuenta real; una sesión iniciada en el navegador externo del teléfono no se comparte automáticamente con Pixload. Las obras R-18 requieren una cuenta autorizada con ese contenido habilitado por Pixiv.
- Las conversiones GIF/MP4 se probaron en el Motorola Edge 30 Pro con un ZIP sintético de dos fotogramas. Falta comprobarlas con obras reales y archivos grandes.

## Compilación

Requiere JDK 21 para ejecutar Gradle (el código usa Java 17) y Android SDK con plataforma 35. Ejecutar `./gradlew assembleDebug` en la raíz del proyecto. El APK aparecerá en `app/build/outputs/apk/debug/`.

También puede abrirse la carpeta raíz del proyecto en Android Studio y ejecutar el módulo `app`. Los archivos locales (`local.properties`, `.idea`, historial de Engram y APK generados) no se publican en Git.

No se almacenan contraseñas: la sesión se gestiona con las cookies de WebView. El último enlace se guarda localmente para facilitar el reintento. Las peticiones a la API de Pixiv usan `www.pixiv.net`; las imágenes se reciben de `i.pximg.net` con la cabecera `Referer` de Pixiv.

La tipografía Titillium Web incluida en `app/src/main/assets/fonts/` se distribuye bajo SIL Open Font License; licencia en `app/src/main/assets/licenses/titillium_web_OFL.txt`.

Los iconos de la interfaz son adaptaciones vectoriales de **Tabler Icons** (licencia MIT en `app/src/main/assets/licenses/tabler_icons_MIT.txt`); sus colores se aplican en Android según el estado de cada control. El icono de notificación sigue siendo monocromo.

El icono de la app se genera a partir de `app/icon-source.png` (archivo original). Los recursos de lanzamiento están en `app/src/main/res/mipmap-*` y las capas del icono adaptable en `app/src/main/res/drawable-nodpi/`.

## Licencia

El código de Pixload se publica bajo [licencia MIT](LICENSE). Titillium Web y Tabler Icons conservan sus licencias incluidas en `app/src/main/assets/licenses/`.
