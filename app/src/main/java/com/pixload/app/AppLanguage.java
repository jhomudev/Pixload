package com.pixload.app;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** App-owned locale, independent of the system language on Android 8–15. */
final class AppLanguage {
    private static final String PREFS = "app_language";
    private static final String KEY = "language";

    private AppLanguage() { }

    static String current(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "es").equals("en") ? "en" : "es";
    }

    static void select(Context context, String language) {
        if (!language.equals("es") && !language.equals("en")) throw new IllegalArgumentException("Unsupported language");
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).apply();
    }

    static Context wrap(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(current(context)));
        return context.createConfigurationContext(configuration);
    }

    static String stage(Context context, String value) {
        if (!current(context).equals("en")) return value;
        if (value.startsWith("Error: ")) return "Error: " + error(context, value.substring(7));
        if (value.startsWith("Descargando animación · Archivo "))
            return "Downloading animation · File " + value.substring("Descargando animación · Archivo ".length());
        if (value.startsWith("Archivo ")) return "File " + value.substring(8);
        for (String prefix : new String[]{"Descarga completada: ", "Descargando animación · ",
                "Convirtiendo ", "No se pudo crear ", "No se pudo escribir ",
                "No se pudo finalizar "}) {
            if (value.startsWith(prefix)) return label(context, prefix) + value.substring(prefix.length());
        }
        return label(context, value);
    }

    static String error(Context context, String message) {
        if (message == null || !current(context).equals("en")) return message;
        String exact = label(context, message);
        if (!exact.equals(message)) return exact;
        if (message.startsWith("Pixiv respondió con HTTP "))
            return "Pixiv responded with HTTP " + message.substring("Pixiv respondió con HTTP ".length());
        for (String prefix : new String[]{"No se pudo crear ", "No se pudo escribir ", "No se pudo finalizar "})
            if (message.startsWith(prefix)) return label(context, prefix) + message.substring(prefix.length());
        return label(context, message);
    }

    // Only pass app-authored labels here; never pass Pixiv titles, author names or novel text.
    static String label(Context context, String spanish) {
        if (!current(context).equals("en")) return spanish;
        switch (spanish) {
            case "Pixiv activo": return "Pixiv connected";
            case "Sin sesión": return "Not signed in";
            case "Ocultar estado de descarga": return "Hide download status";
            case "En curso": return "In progress";
            case "Completada": return "Completed";
            case "Cancelada": return "Cancelled";
            case "Abrir en galería": return "Open in gallery";
            case "La descarga no está disponible en la galería.": return "The download is not available in the gallery.";
            case "Tu galería": return "Your gallery";
            case "Vista previa": return "Preview";
            case "Vista previa no disponible": return "Preview unavailable";
            case "Vista previa\nno disponible": return "Preview\nunavailable";
            case "Cancelar": return "Cancel";
            case "Tus obras descargadas de Pixiv, reunidas en un solo lugar.": return "Your downloaded Pixiv works in one place.";
            case "Aún no hay descargas": return "No downloads yet";
            case "Las próximas obras que descargues aparecerán aquí.": return "Your next downloads will appear here.";
            case "Buscar una obra": return "Find a work";
            case "Fecha no registrada": return "Date unavailable";
            case "Calculando elementos…": return "Counting items…";
            case "Carpeta no disponible": return "Folder unavailable";
            case "Salir de pantalla completa": return "Exit full screen";
            case "Cargando archivos…": return "Loading files…";
            case "Esta carpeta está vacía.": return "This folder is empty.";
            case "Volver a la galería": return "Back to gallery";
            case "Carpeta": return "Folder";
            case "Ver en pantalla completa": return "View full screen";
            case "Archivo anterior": return "Previous file";
            case "Archivo siguiente": return "Next file";
            case "Contenido de la carpeta": return "Folder contents";
            case "Eliminar descarga": return "Delete download";
            case "Eliminar": return "Delete";
            case "Descarga eliminada": return "Download deleted";
            case "Compartir descarga": return "Share download";
            case "No hay ninguna aplicación disponible para compartir estos archivos.": return "No app is available to share these files.";
            case "SOLO PIXIV": return "PIXIV ONLY";
            case "Tus obras favoritas,\na tu manera.": return "Your favorite works,\nyour way.";
            case "Guarda ilustraciones, manga, novelas y ugoira de Pixiv.": return "Save illustrations, manga, novels and ugoira from Pixiv.";
            case "Ilustraciones": return "Illustrations";
            case "Novelas": return "Novels";
            case "Pega la URL de pixiv aquí": return "Paste a Pixiv URL here";
            case "Limpiar URL": return "Clear URL";
            case "Pegar URL del portapapeles": return "Paste URL from clipboard";
            case "Consultando Pixiv…": return "Checking Pixiv…";
            case "Previsualizar obra": return "Preview work";
            case "Hecho para tus obras": return "Made for your works";
            case "Imágenes originales": return "Original images";
            case "Selecciona las páginas que quieras descargar.": return "Select the pages you want to download.";
            case "GIF o MP4": return "GIF or MP4";
            case "Convierte las ugoira al formato que prefieras.": return "Convert ugoira to your preferred format.";
            case "Novelas en TXT": return "Novels in TXT";
            case "Guarda el texto proporcionado por Pixiv.": return "Save the text provided by Pixiv.";
            case "Las descargas continúan en segundo plano. Consulta la notificación para ver su progreso o cancelarlas.": return "Downloads continue in the background. Check the notification to track or cancel them.";
            case "El portapapeles está vacío.": return "The clipboard is empty.";
            case "El portapapeles no contiene un enlace de Pixiv.": return "The clipboard has no Pixiv link.";
            case "Introduce un enlace de obra de Pixiv.": return "Enter a Pixiv work link.";
            case "Obra lista para previsualizar.": return "Work ready to preview.";
            case "No hay una obra cargada": return "No work loaded";
            case "Introduce un enlace de Pixiv para ver sus detalles y elegir qué descargar.": return "Enter a Pixiv link to see its details and choose what to download.";
            case "Ir a descargar": return "Go to downloads";
            case "OBRA ENCONTRADA": return "WORK FOUND";
            case "La carpeta y el nombre final se determinarán al guardar. Algunas obras pueden requerir una sesión autorizada en Pixiv.": return "The final folder and filename are determined when saving. Some works may require an authorized Pixiv session.";
            case "Novela": return "Novel";
            case "Ilustración / manga": return "Illustration / manga";
            case "Ilustración": return "Illustration";
            case "Deseleccionar todas": return "Deselect all";
            case "Seleccionar todas": return "Select all";
            case "Formato de animación": return "Animation format";
            case "MP4 no incluye sonido. La conversión puede reducir el tamaño o la fidelidad de la animación.": return "MP4 has no sound. Conversion may reduce the animation’s size or fidelity.";
            case "Calidad del GIF": return "GIF quality";
            case "Rápido": return "Fast";
            case "Equilibrado": return "Balanced";
            case "Calidad": return "Quality";
            case "difusión de error": return "error diffusion";
            case "sin dithering": return "no dithering";
            case "Texto de la novela": return "Novel text";
            case "Formato: TXT · texto proporcionado por Pixiv": return "Format: TXT · text provided by Pixiv";
            case "Descargar selección": return "Download selection";
            case "Descargando...": return "Downloading...";
            case "Personaliza cómo se guardan tus obras de Pixiv.": return "Customize how your Pixiv works are saved.";
            case "Formato predeterminado de ugoira": return "Default ugoira format";
            case "También podrás cambiarlo al previsualizar cada obra.": return "You can also change it when previewing each work.";
            case "Ubicación de descargas": return "Download location";
            case "Carpeta principal": return "Default folder";
            case "Aún no se ha elegido una carpeta": return "No folder selected yet";
            case "Carpeta seleccionada mediante Android": return "Folder selected through Android";
            case "Cambiar carpeta": return "Change folder";
            case "Preguntar dónde guardar cada descarga": return "Ask where to save each download";
            case "Cuenta de Pixiv": return "Pixiv account";
            case "Sesión de Pixiv activa": return "Signed in to Pixiv";
            case "Sin sesión confirmada": return "Not signed in";
            case "Puedes consultar obras públicas sin iniciar sesión. El acceso a otras obras depende de los permisos de tu cuenta en Pixiv.": return "You can view public works without signing in. Access to other works depends on your Pixiv account permissions.";
            case "Abrir mi sesión de Pixiv": return "Open my Pixiv session";
            case "Iniciar sesión en Pixiv": return "Sign in to Pixiv";
            case "Cerrar sesión de Pixiv": return "Sign out of Pixiv";
            case "Acerca de Pixload": return "About Pixload";
            case "Pixload es una aplicación de desarrollo independiente; no es un servicio oficial de Pixiv.": return "Pixload is independently developed; it is not an official Pixiv service.";
            case "Se borrarán las cookies de Pixiv guardadas en Pixload. No se cerrará la sesión del navegador del teléfono ni se borrarán los archivos descargados.": return "Pixiv cookies saved in Pixload will be removed. This will not sign you out of your phone’s browser or delete downloaded files.";
            case "Cerrar sesión": return "Sign out";
            case "Sesión cerrada. Puedes entrar con otra cuenta de Pixiv.": return "Signed out. You can use another Pixiv account.";
            case "Selecciona al menos una página.": return "Select at least one page.";
            case "Carpeta principal seleccionada": return "Default folder selected";
            case "Preparando descarga…": return "Preparing download…";
            case "Descarga iniciada. Consulta la notificación para ver el avance.": return "Download started. Check the notification for progress.";
            case "No se pudo iniciar la descarga": return "Could not start the download";
            case "No se ha confirmado la sesión. Puedes seguir con contenido público.": return "Sign-in could not be confirmed. You can still view public works.";
            case "No se pudo abrir la obra: ": return "Could not open the work: ";
            case "Por ": return "By ";
            case "Versión ": return "Version ";
            case "Página ": return "Page ";
            case "Compartir ": return "Share ";
            case "Eliminar ": return "Delete ";
            case "Archivo ": return "File ";
            case "Hasta ": return "Up to ";
            case "No se pudo acceder a la descarga: ": return "Could not access the download: ";
            case "No se pudo compartir la descarga: ": return "Could not share the download: ";
            case "No se pudo eliminar: ": return "Could not delete: ";
            case "No se pudo iniciar la descarga: ": return "Could not start the download: ";
            case "Se eliminará «": return "This will delete ‘";
            case "» y sus archivos del almacenamiento. Esta acción no se puede deshacer.": return "’ and its files from storage. This cannot be undone.";
            case " descarga": return " download";
            case " descargas": return " downloads";
            case " elementos": return " items";
            case "1 archivo": return "1 file";
            case "1 novela · TXT": return "1 novel · TXT";
            case "1 animación · ": return "1 animation · ";
            case " de ": return " of ";
            case " páginas seleccionadas": return " pages selected";
            case "Páginas (": return "Pages (";
            case "Convirtiendo ": return "Converting ";
            case "Descargando animación · ": return "Downloading animation · ";
            case "Descarga completada": return "Download completed";
            case "Descarga completada: ": return "Download completed: ";
            case "Descarga cancelada": return "Download cancelled";
            case "Error: ": return "Error: ";
            case "Descargando animación · Archivo ": return "Downloading animation · File ";
            case "Descarga interrumpida": return "Download interrupted";
            case "Conversión interrumpida": return "Conversion interrupted";
            case "No hay archivos seleccionados.": return "No files selected.";
            case "No se pudo crear ": return "Could not create ";
            case "No se pudo escribir ": return "Could not write ";
            case "No se pudo finalizar ": return "Could not finalize ";
            case "No se pudo escribir el GIF.": return "Could not write the GIF.";
            case "No se pudo escribir el MP4.": return "Could not write the MP4.";
            case "Formato de animación no válido.": return "Invalid animation format.";
            case "Origen de archivo no permitido.": return "File source not allowed.";
            case "No se pudo crear una carpeta única.": return "Could not create a unique folder.";
            case "No se pudo crear un nombre de archivo único.": return "Could not create a unique filename.";
            case "No se puede acceder a la carpeta elegida.": return "Cannot access the selected folder.";
            case "Obra de Pixiv": return "Pixiv work";
            case "Descargas": return "Downloads";
            case "Descarga de Pixload": return "Pixload download";
            case "El archivo ya no está disponible.": return "The file is no longer available.";
            case "Se ha perdido el acceso a este archivo.": return "Access to this file was lost.";
            case "La carpeta ya no está disponible.": return "The folder is no longer available.";
            case "Se ha perdido el acceso a esta carpeta.": return "Access to this folder was lost.";
            case "No hay archivos para compartir.": return "There are no files to share.";
            case "No se pudo leer el historial de descargas.": return "Could not read download history.";
            case "No se pudo guardar la galería.": return "Could not save the gallery.";
            case "No se pudo actualizar el estado de la descarga.": return "Could not update download status.";
            case "Android no permitió eliminar el archivo o la carpeta.": return "Android did not allow deleting the file or folder.";
            case "No se pudo reproducir el vídeo.": return "Could not play the video.";
            case "Reproducir vídeo en bucle": return "Loop video";
            case "Abriendo texto…": return "Opening text…";
            case "Archivo no disponible": return "File unavailable";
            case "Texto truncado para la vista previa.": return "Text truncated for preview.";
            case "No se pudo leer el texto: ": return "Could not read text: ";
            case "GIF no compatible": return "Unsupported GIF";
            case "Imagen no disponible": return "Image unavailable";
            case "No se pudo mostrar este archivo.": return "Could not display this file.";
            case "Este tipo de archivo no tiene vista previa.": return "This file type has no preview.";
            case "GIF no disponible": return "GIF unavailable";
            case "GIF demasiado grande para previsualizar": return "GIF too large to preview";
            case "Introduce un enlace de pixiv.net.": return "Enter a pixiv.net link.";
            case "El enlace debe usar HTTP o HTTPS.": return "The link must use HTTP or HTTPS.";
            case "Enlace de obra no reconocido.": return "Unrecognized work link.";
            case "Esta obra es R-18. Pixiv no permite ver sus archivos sin una sesión autorizada y con el contenido R-18 habilitado en la cuenta.": return "This work is R-18. Pixiv does not allow access to its files without an authorized session with R-18 content enabled.";
            case "Pixiv no ha proporcionado el texto de esta novela.": return "Pixiv did not provide the text of this novel.";
            case "La obra no tiene páginas disponibles.": return "This work has no available pages.";
            case "Esta obra requiere iniciar sesión o no está disponible para tu cuenta.": return "This work requires signing in or is unavailable to your account.";
            case "Pixiv denegó el acceso.": return "Pixiv denied access.";
            default: return spanish;
        }
    }
}
