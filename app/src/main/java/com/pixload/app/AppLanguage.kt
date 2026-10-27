package com.pixload.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** App-owned locale, independent of the system language on Android 8–15. */
internal object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY = "language"

    @JvmStatic fun current(context: Context): String =
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "es") == "en") "en" else "es"

    @JvmStatic fun select(context: Context, language: String) {
        if (language != "es" && language != "en") throw IllegalArgumentException("Unsupported language")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
    }

    @JvmStatic fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(current(context)))
        return context.createConfigurationContext(configuration)
    }

    @JvmStatic fun stage(context: Context, value: String?): String? {
        if (current(context) != "en") return value
        val text = value!!
        if (text.startsWith("Error: ")) return "Error: " + error(context, text.substring(7))
        if (text.startsWith("Descargando animación · Archivo "))
            return "Downloading animation · File " + text.substring("Descargando animación · Archivo ".length)
        if (text.startsWith("Archivo ")) return "File " + text.substring(8)
        for (prefix in arrayOf("Descarga completada: ", "Descargando animación · ",
            "Convirtiendo ", "No se pudo crear ", "No se pudo escribir ", "No se pudo finalizar ")) {
            if (text.startsWith(prefix)) return label(context, prefix) + text.substring(prefix.length)
        }
        return label(context, text)
    }

    @JvmStatic fun error(context: Context, message: String?): String? {
        if (message == null || current(context) != "en") return message
        val exact = label(context, message)
        if (exact != message) return exact
        if (message.startsWith("Pixiv respondió con HTTP "))
            return "Pixiv responded with HTTP " + message.substring("Pixiv respondió con HTTP ".length)
        for (prefix in arrayOf("No se pudo crear ", "No se pudo escribir ", "No se pudo finalizar "))
            if (message.startsWith(prefix)) return label(context, prefix) + message.substring(prefix.length)
        return label(context, message)
    }

    // Only pass app-authored labels here; never pass Pixiv titles, author names or novel text.
    @JvmStatic fun label(context: Context, spanish: String?): String? {
        if (current(context) != "en") return spanish
        return when (spanish!!) {
            "Pixiv activo" -> "Pixiv connected"
            "Sin sesión" -> "Not signed in"
            "Ocultar estado de descarga" -> "Hide download status"
            "En curso" -> "In progress"
            "Completada" -> "Completed"
            "Cancelada" -> "Cancelled"
            "Abrir en galería" -> "Open in gallery"
            "La descarga no está disponible en la galería." -> "The download is not available in the gallery."
            "Tu galería" -> "Your gallery"
            "Vista previa" -> "Preview"
            "Vista previa no disponible" -> "Preview unavailable"
            "Vista previa\nno disponible" -> "Preview\nunavailable"
            "Miniatura de la obra de Pixiv" -> "Pixiv work thumbnail"
            "Miniatura de archivo guardado" -> "Saved file thumbnail"
            "Cancelar" -> "Cancel"
            "Tus obras descargadas de Pixiv, reunidas en un solo lugar." -> "Your downloaded Pixiv works in one place."
            "Aún no hay descargas" -> "No downloads yet"
            "Las próximas obras que descargues aparecerán aquí." -> "Your next downloads will appear here."
            "Buscar una obra" -> "Find a work"
            "Fecha no registrada" -> "Date unavailable"
            "Calculando elementos…" -> "Counting items…"
            "Carpeta no disponible" -> "Folder unavailable"
            "Salir de pantalla completa" -> "Exit full screen"
            "Cargando archivos…" -> "Loading files…"
            "Esta carpeta está vacía." -> "This folder is empty."
            "Volver a la galería" -> "Back to gallery"
            "Carpeta" -> "Folder"
            "Ver en pantalla completa" -> "View full screen"
            "Archivo anterior" -> "Previous file"
            "Archivo siguiente" -> "Next file"
            "Contenido de la carpeta" -> "Folder contents"
            "Eliminar descarga" -> "Delete download"
            "Eliminar" -> "Delete"
            "Descarga eliminada" -> "Download deleted"
            "Compartir descarga" -> "Share download"
            "No hay ninguna aplicación disponible para compartir estos archivos." -> "No app is available to share these files."
            "Tus obras favoritas,\na tu manera." -> "Your favorite works,\nyour way."
            "Guarda ilustraciones, manga, novelas y ugoira de Pixiv." -> "Save illustrations, manga, novels and ugoira from Pixiv."
            "Ilustraciones" -> "Illustrations"
            "Novelas" -> "Novels"
            "Pega la URL de pixiv aquí" -> "Paste a Pixiv URL here"
            "Limpiar URL" -> "Clear URL"
            "Pegar URL del portapapeles" -> "Paste URL from clipboard"
            "Consultando Pixiv…" -> "Checking Pixiv…"
            "Previsualizar obra" -> "Preview work"
            "Hecho para tus obras" -> "Made for your works"
            "Imágenes originales" -> "Original images"
            "Selecciona las páginas que quieras descargar." -> "Select the pages you want to download."
            "GIF o MP4" -> "GIF or MP4"
            "Convierte las ugoira al formato que prefieras." -> "Convert ugoira to your preferred format."
            "Novelas en TXT" -> "Novels in TXT"
            "Guarda el texto proporcionado por Pixiv." -> "Save the text provided by Pixiv."
            "Las descargas continúan en segundo plano. Consulta la notificación para ver su progreso o cancelarlas." -> "Downloads continue in the background. Check the notification to track or cancel them."
            "El portapapeles está vacío." -> "The clipboard is empty."
            "El portapapeles no contiene un enlace de Pixiv." -> "The clipboard has no Pixiv link."
            "Introduce un enlace de obra de Pixiv." -> "Enter a Pixiv work link."
            "Obra lista para previsualizar." -> "Work ready to preview."
            "No hay una obra cargada" -> "No work loaded"
            "Introduce un enlace de Pixiv para ver sus detalles y elegir qué descargar." -> "Enter a Pixiv link to see its details and choose what to download."
            "Ir a descargar" -> "Go to downloads"
            "OBRA ENCONTRADA" -> "WORK FOUND"
            "La carpeta y el nombre final se determinarán al guardar. Algunas obras pueden requerir una sesión autorizada en Pixiv." -> "The final folder and filename are determined when saving. Some works may require an authorized Pixiv session."
            "Novela" -> "Novel"
            "Ilustración / manga" -> "Illustration / manga"
            "Ilustración" -> "Illustration"
            "Deseleccionar todas" -> "Deselect all"
            "Seleccionar todas" -> "Select all"
            "Formato de animación" -> "Animation format"
            "MP4 no incluye sonido. La conversión puede reducir el tamaño o la fidelidad de la animación." -> "MP4 has no sound. Conversion may reduce the animation’s size or fidelity."
            "Calidad del GIF" -> "GIF quality"
            "Rápido" -> "Fast"
            "Equilibrado" -> "Balanced"
            "Calidad" -> "Quality"
            "difusión de error" -> "error diffusion"
            "sin dithering" -> "no dithering"
            "Texto de la novela" -> "Novel text"
            "Formato: TXT · texto proporcionado por Pixiv" -> "Format: TXT · text provided by Pixiv"
            "Descargar selección" -> "Download selection"
            "Descargando..." -> "Downloading..."
            "Personaliza cómo se guardan tus obras de Pixiv." -> "Customize how your Pixiv works are saved."
            "Formato predeterminado de ugoira" -> "Default ugoira format"
            "También podrás cambiarlo al previsualizar cada obra." -> "You can also change it when previewing each work."
            "Ubicación de descargas" -> "Download location"
            "Carpeta principal" -> "Default folder"
            "Aún no se ha elegido una carpeta" -> "No folder selected yet"
            "Carpeta seleccionada mediante Android" -> "Folder selected through Android"
            "Cambiar carpeta" -> "Change folder"
            "Preguntar dónde guardar cada descarga" -> "Ask where to save each download"
            "Cuenta de Pixiv" -> "Pixiv account"
            "Sesión de Pixiv activa" -> "Signed in to Pixiv"
            "Sin sesión confirmada" -> "Not signed in"
            "Puedes consultar obras públicas sin iniciar sesión. El acceso a otras obras depende de los permisos de tu cuenta en Pixiv." -> "You can view public works without signing in. Access to other works depends on your Pixiv account permissions."
            "Abrir mi sesión de Pixiv" -> "Open my Pixiv session"
            "Iniciar sesión en Pixiv" -> "Sign in to Pixiv"
            "Cerrar sesión de Pixiv" -> "Sign out of Pixiv"
            "Acerca de Pixload" -> "About Pixload"
            "Pixload es una aplicación de desarrollo independiente; no es un servicio oficial de Pixiv." -> "Pixload is independently developed; it is not an official Pixiv service."
            "Se borrarán las cookies de Pixiv guardadas en Pixload. No se cerrará la sesión del navegador del teléfono ni se borrarán los archivos descargados." -> "Pixiv cookies saved in Pixload will be removed. This will not sign you out of your phone’s browser or delete downloaded files."
            "Cerrar sesión" -> "Sign out"
            "Sesión cerrada. Puedes entrar con otra cuenta de Pixiv." -> "Signed out. You can use another Pixiv account."
            "Selecciona al menos una página." -> "Select at least one page."
            "Carpeta principal seleccionada" -> "Default folder selected"
            "Preparando descarga…" -> "Preparing download…"
            "Descarga iniciada. Consulta la notificación para ver el avance." -> "Download started. Check the notification for progress."
            "No se pudo iniciar la descarga" -> "Could not start the download"
            "No se ha confirmado la sesión. Puedes seguir con contenido público." -> "Sign-in could not be confirmed. You can still view public works."
            "No se pudo abrir la obra: " -> "Could not open the work: "
            "Por " -> "By "
            "Versión " -> "Version "
            "Página " -> "Page "
            "Compartir " -> "Share "
            "Eliminar " -> "Delete "
            "Archivo " -> "File "
            "Hasta " -> "Up to "
            "No se pudo acceder a la descarga: " -> "Could not access the download: "
            "No se pudo compartir la descarga: " -> "Could not share the download: "
            "No se pudo eliminar: " -> "Could not delete: "
            "No se pudo iniciar la descarga: " -> "Could not start the download: "
            "Se eliminará «" -> "This will delete ‘"
            "» y sus archivos del almacenamiento. Esta acción no se puede deshacer." -> "’ and its files from storage. This cannot be undone."
            " descarga" -> " download"
            " descargas" -> " downloads"
            " elementos" -> " items"
            "1 archivo" -> "1 file"
            "1 novela · TXT" -> "1 novel · TXT"
            "1 animación · " -> "1 animation · "
            " de " -> " of "
            " páginas seleccionadas" -> " pages selected"
            "Páginas (" -> "Pages ("
            "Convirtiendo " -> "Converting "
            "Descargando animación · " -> "Downloading animation · "
            "Descarga completada" -> "Download completed"
            "Descarga completada: " -> "Download completed: "
            "Descarga cancelada" -> "Download cancelled"
            "Error: " -> "Error: "
            "Descargando animación · Archivo " -> "Downloading animation · File "
            "Descarga interrumpida" -> "Download interrupted"
            "Conversión interrumpida" -> "Conversion interrupted"
            "No hay archivos seleccionados." -> "No files selected."
            "No se pudo crear " -> "Could not create "
            "No se pudo escribir " -> "Could not write "
            "No se pudo finalizar " -> "Could not finalize "
            "No se pudo escribir el GIF." -> "Could not write the GIF."
            "No se pudo escribir el MP4." -> "Could not write the MP4."
            "Formato de animación no válido." -> "Invalid animation format."
            "Origen de archivo no permitido." -> "File source not allowed."
            "No se pudo crear una carpeta única." -> "Could not create a unique folder."
            "No se pudo crear un nombre de archivo único." -> "Could not create a unique filename."
            "No se puede acceder a la carpeta elegida." -> "Cannot access the selected folder."
            "Obra de Pixiv" -> "Pixiv work"
            "Descargas" -> "Downloads"
            "Descargas en curso" -> "Downloads in progress"
            "Cola de descargas" -> "Download queue"
            "No hay descargas en curso" -> "No downloads in progress"
            "Las obras que descargues aparecerán acá con su progreso y podrás cancelarlas una por una." ->
                "The works you download appear here with their progress, and you can cancel them one by one."
            "3 a la vez" -> "3 at a time"
            "Máximo 5" -> "Max 5"
            "Manga" -> "Manga"
            "Obra" -> "Work"
            "En espera" -> "Waiting"
            " página" -> " page"
            " páginas" -> " pages"
            " archivo" -> " file"
            " archivos" -> " files"
            "Todavía no hay descargas para abrir." -> "There are no downloads to open yet."
            "No hay descargas en curso." -> "No downloads in progress."
            "Cancelar descarga" -> "Cancel download"
            "Quitar" -> "Remove"
            "Máximo 5 descargas en la cola." -> "At most 5 downloads in the queue."
            "Esta obra ya se está descargando." -> "This work is already downloading."
            "Cola llena" -> "Queue full"
            "Descarga interrumpida" -> "Download interrupted"
            "Interrumpida" -> "Interrupted"
            "Ver cola" -> "View queue"
            "Hasta 3 a la vez · máximo 5 en cola" -> "Up to 3 at once · 5 queued max"
            " en curso" -> " in progress"
            "Descarga de Pixload" -> "Pixload download"
            "El archivo ya no está disponible." -> "The file is no longer available."
            "Se ha perdido el acceso a este archivo." -> "Access to this file was lost."
            "La carpeta ya no está disponible." -> "The folder is no longer available."
            "Se ha perdido el acceso a esta carpeta." -> "Access to this folder was lost."
            "No hay archivos para compartir." -> "There are no files to share."
            "No se pudo leer el historial de descargas." -> "Could not read download history."
            "No se pudo guardar la galería." -> "Could not save the gallery."
            "No se pudo actualizar el estado de la descarga." -> "Could not update download status."
            "Android no permitió eliminar el archivo o la carpeta." -> "Android did not allow deleting the file or folder."
            "No se pudo reproducir el vídeo." -> "Could not play the video."
            "Reproducir vídeo en bucle" -> "Loop video"
            "Abriendo texto…" -> "Opening text…"
            "Archivo no disponible" -> "File unavailable"
            "Texto truncado para la vista previa." -> "Text truncated for preview."
            "No se pudo leer el texto: " -> "Could not read text: "
            "GIF no compatible" -> "Unsupported GIF"
            "Imagen no disponible" -> "Image unavailable"
            "No se pudo mostrar este archivo." -> "Could not display this file."
            "Este tipo de archivo no tiene vista previa." -> "This file type has no preview."
            "GIF no disponible" -> "GIF unavailable"
            "GIF demasiado grande para previsualizar" -> "GIF too large to preview"
            "Introduce un enlace de pixiv.net." -> "Enter a pixiv.net link."
            "El enlace debe usar HTTP o HTTPS." -> "The link must use HTTP or HTTPS."
            "Enlace de obra no reconocido." -> "Unrecognized work link."
            "Esta obra es R-18. Pixiv no permite ver sus archivos sin una sesión autorizada y con el contenido R-18 habilitado en la cuenta." -> "This work is R-18. Pixiv does not allow access to its files without an authorized session with R-18 content enabled."
            "Pixiv no ha proporcionado el texto de esta novela." -> "Pixiv did not provide the text of this novel."
            "La obra no tiene páginas disponibles." -> "This work has no available pages."
            "Esta obra requiere iniciar sesión o no está disponible para tu cuenta." -> "This work requires signing in or is unavailable to your account."
            "Pixiv denegó el acceso." -> "Pixiv denied access."
            else -> spanish
        }
    }
}
