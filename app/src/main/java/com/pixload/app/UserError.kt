package com.pixload.app

/** Only exact, app-authored diagnostics cross into UI. Provider and server messages are untrusted. */
internal object UserError {
    const val FALLBACK_ES = "No se pudo completar la operación. Inténtalo de nuevo."
    const val FALLBACK_EN = "Could not complete the action. Try again."

    private val known = setOf(
        "Introduce un enlace de pixiv.net.",
        "El enlace debe usar HTTP o HTTPS.",
        "Enlace de obra no reconocido.",
        "Esta obra es R-18. Pixiv no permite ver sus archivos sin una sesión autorizada y con el contenido R-18 habilitado en la cuenta.",
        "Pixiv no ha proporcionado el texto de esta novela.",
        "Pixiv no ha proporcionado el título de esta novela.",
        "La obra no tiene páginas disponibles.",
        "Esta obra requiere iniciar sesión o no está disponible para tu cuenta.",
        "Pixiv denegó el acceso.",
        "La carpeta ya no está disponible. Selecciona la carpeta de nuevo y descarga la obra desde su enlace.",
        "La obra o las páginas seleccionadas ya no están disponibles. Abre la obra de nuevo.",
        "Pixiv no ofrece los archivos seleccionados. Abre la obra de nuevo.",
        "No se puede acceder a la carpeta elegida.",
        "Se ha perdido el acceso a este archivo.",
        "Se ha perdido el acceso a esta carpeta.",
        "El archivo ya no está disponible.",
        "La carpeta ya no está disponible.",
        "No se pudo leer el historial de descargas.",
        "No se pudo guardar la galería.",
        "No se pudo actualizar el estado de la descarga.",
        "Android no permitió eliminar el archivo o la carpeta.",
        "No se pudo iniciar la descarga",
        "No se pudo escribir el GIF.",
        "No se pudo escribir el MP4."
    )

    fun safeSpanish(raw: String?): String = if (raw in known) raw!! else FALLBACK_ES

}
