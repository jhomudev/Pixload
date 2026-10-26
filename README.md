# Pixload

**Guarda obras de Pixiv en tu Android y consúltalas desde una galería propia.** Descarga ilustraciones originales, páginas de manga, novelas y animaciones; elige dónde guardarlas y vuelve a verlas, compartirlas o eliminarlas sin salir de la app.

> Pixload es un proyecto independiente, no oficial ni afiliado a Pixiv. Respeta los derechos de los creadores y las condiciones de Pixiv al guardar contenido.

## Descargar e instalar

Descarga el APK de la [última versión de Pixload en GitHub Releases](https://github.com/jhomudev/Pixload/releases/latest). Requiere **Android 8.0 o posterior**. Abre el APK en el teléfono y, si Android lo solicita, permite temporalmente la instalación desde esa fuente. Las descargas se guardan en la carpeta que elijas con el selector de documentos de Android: no necesitas conceder acceso a todo el almacenamiento.

**Si instalaste v0.1.0:** aquella versión se distribuyó con una firma distinta. Android no permite instalar versiones firmadas desde v0.2.0 encima: tendrás que desinstalar la versión anterior. **Desinstalar borra los ajustes, la sesión y el historial de la galería**, aunque los archivos descargados permanecen en la carpeta que elegiste. Comprueba antes que los archivos que quieras conservar estén en la carpeta elegida; el historial interno no puede migrarse automáticamente. Las versiones firmadas a partir de v0.2.0 podrán actualizarse entre sí si se conserva la misma clave de firma.

## ¿Qué puedes hacer?

- **Abrir obras fácilmente:** pega un enlace de Pixiv, compártelo con Pixload desde otra aplicación o ábrelo directamente. La vista previa muestra título, autor, imágenes y páginas disponibles; en novelas, el texto cuando Pixiv lo proporciona.
- **Elegir qué guardar:** descarga todas las páginas originales o solo las que selecciones. Las novelas se guardan como TXT y las animaciones *ugoira* se convierten a GIF o MP4; para GIF puedes elegir Rápido, Equilibrado o Calidad. El formato ZIP original no se ofrece.
- **Decidir dónde guardar:** elige una carpeta principal y, si lo prefieres, confirma la carpeta en cada descarga. Las obras de una página, novelas y animaciones usan el título de la obra como nombre de archivo; las obras de varias páginas crean una carpeta con archivos numerados según su página original. La primera descarga usa solo el título (y la extensión en archivos), sin sufijos. Si el nombre ya existe, Pixload añade `(2)`, `(3)`, etc. para evitar sobrescribirlo. Si vuelves a descargar la misma obra en esa carpeta, puedes continuar o cancelar desde un aviso; en otra carpeta no se considera repetida.
- **Seguir las descargas:** consulta su avance en la app o en la notificación, cancela desde la notificación y abre el resultado en la galería al finalizar. Las descargas pueden continuar mientras la interfaz esté cerrada.
- **Disfrutar tu galería:** visualiza imágenes, GIF y vídeos, lee novelas, entra en pantalla completa y recorre las páginas con gestos y miniaturas. Comparte una descarga desde su ficha (incluidos los archivos de obras multipágina) o elimínala tras confirmar. Desliza horizontalmente para cambiar entre Descargar, Vista previa, Galería y Ajustes.
- **Cambiar idioma:** en Ajustes, elige Español o English. La interfaz de Pixload conserva el idioma elegido al cerrar y volver a abrir la app; los títulos y textos de Pixiv no se traducen.
- **Usar tu sesión cuando haga falta:** las obras públicas pueden consultarse sin iniciar sesión; para otras obras, inicia sesión en Pixiv dentro de Pixload. También puedes cerrar sesión desde Ajustes.

### Uso rápido

1. Abre Pixload y pega o comparte un enlace de obra de Pixiv.
2. Revisa la vista previa, selecciona las páginas y el formato de animación si corresponde.
3. Elige la carpeta de destino y pulsa **Descargar selección**.
4. Consulta el resultado en **Galería**; toca una obra para verla, compartirla o eliminarla.

## Privacidad y límites

Las descargas se escriben mediante el selector de documentos de Android. La sesión utiliza las cookies del WebView de Pixload; **no** comparte automáticamente la sesión de tu navegador externo. No se almacenan contraseñas en el proyecto. El último enlace consultado se conserva localmente para facilitar el reintento.

Pixload utiliza endpoints AJAX no oficiales de Pixiv: su funcionamiento depende de que Pixiv los mantenga disponibles. Iniciar sesión no garantiza acceso a obras restringidas; las obras privadas, eliminadas o no autorizadas no pueden descargarse. Una descarga interrumpida no se reanuda automáticamente después de reiniciar el teléfono. El GIF se reduce según el preset elegido (Rápido: 360 px, Equilibrado: 480 px, Calidad: 720 px de ancho máximo) y el MP4 (sin audio) a 1280 px; la conversión necesita espacio temporal en el dispositivo. La galería conserva las descargas registradas por la app mientras sus URI sigan accesibles; no indexa automáticamente archivos antiguos guardados fuera de ella.

## Para desarrolladores

La app Android sigue en `:app` (Java 17 y conversión GIF en Kotlin, SDK 35). La migración incremental hacia Android e iOS continúa en `:shared`, un módulo Kotlin Multiplatform con reglas compartidas y un transporte Ktor **solo para respuestas AJAX de texto**, todavía no conectado al cliente Android. Las imágenes y animaciones requieren una ruta binaria aparte. Todavía no existe una app iOS distribuible; compilarla y probarla requiere macOS con Xcode. En Linux solo se verificó la compilación de fuentes compartidas para iOS. Para compilar Android se necesita JDK 21, Android SDK 35 y Gradle Wrapper (incluido):

```bash
./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug
```

Para las pruebas instrumentadas de galería y conversión, compila con `./gradlew :app:assembleDebugAndroidTest` y ejecútalas en un dispositivo o emulador con Android SDK; las pruebas sintéticas no verifican las descargas reales de Pixiv ni el selector de almacenamiento. La firma de distribución se configura **solo mediante variables de entorno**, nunca mediante claves o contraseñas dentro del repositorio: `PIXLOAD_RELEASE_KEYSTORE`, `PIXLOAD_RELEASE_STORE_PASSWORD`, `PIXLOAD_RELEASE_KEY_ALIAS` y `PIXLOAD_RELEASE_KEY_PASSWORD`. Con ellas, `./gradlew :app:assembleRelease` genera el APK de distribución. Conserva la clave de firma de forma segura: sin ella no podrás actualizar la misma aplicación.

## Licencias

Pixload se publica bajo [licencia MIT](LICENSE). La tipografía Titillium Web, los iconos de Tabler y Square gifencoder (Apache 2.0) conservan sus licencias en [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/). El icono de la app procede de [`app/icon-source.png`](app/icon-source.png).
