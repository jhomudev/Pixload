package com.pixload.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int

/** Portable snapshot of the fields Android's PixivClient.load currently consumes. */
data class Work(
    val id: String,
    val title: String,
    val author: String,
    val type: String,
    val thumbnail: String,
    val text: String = "",
    val animationUrl: String = "",
    val frames: List<Frame> = emptyList(),
    val pages: List<String> = emptyList(),
    val pageThumbnails: List<String> = emptyList(),
) {
    data class Frame(val file: String, val delay: Int)
}

/** Parses already-fetched Pixiv AJAX envelopes; link validation and HTTP status handling belong to the caller. */
object PixivPayloads {
    fun parse(id: String, novel: Boolean, detailJson: String, pagesJson: String? = null, ugoiraJson: String? = null): Work {
        val detail = body(detailJson) as JsonObject
        val title = detail.string("title", "obra-$id")
        val author = detail.string("userName")
        val preview = (detail["urls"] as? JsonObject)?.string("regular").orEmpty()
        if (!novel && (detail.string("xRestrict").toIntOrNull() ?: 0) > 0 && preview.isEmpty()) {
            error("Esta obra es R-18. Pixiv no permite ver sus archivos sin una sesión " +
                "autorizada y con el contenido R-18 habilitado en la cuenta.")
        }
        if (novel) {
            val text = detail.string("content")
            if (text.isEmpty()) error("Pixiv no ha proporcionado el texto de esta novela.")
            return Work(id, title, author, "novel", detail.string("coverUrl"), text = text)
        }
        if (detail.string("illustType").toIntOrNull() == 2) {
            val meta = body(requireNotNull(ugoiraJson) { "Missing ugoira metadata" }) as JsonObject
            val frames = (meta.getValue("frames") as JsonArray).map { frame ->
                val entry = frame as JsonObject
                Work.Frame(entry.requiredString("file"),
                    (entry.getValue("delay") as JsonPrimitive).int)
            }
            return Work(id, title, author, "ugoira", preview,
                animationUrl = meta.requiredString("originalSrc"), frames = frames)
        }
        val pages = if (pagesJson == null) emptyList() else (body(pagesJson) as JsonArray).map { page ->
            val urls = (page as JsonObject).getValue("urls") as JsonObject
            val original = urls.requiredString("original")
            val small = urls.string("small")
            Triple(original, if (small.isEmpty()) urls.string("regular") else small, urls.string("regular"))
        }
        if (pages.isEmpty()) error("La obra no tiene páginas disponibles.")
        return Work(id, title, author, "image", preview.ifEmpty { pages.firstOrNull { it.third.isNotEmpty() }?.third.orEmpty() },
            pages = pages.map { it.first }, pageThumbnails = pages.map { it.second })
    }

    private fun body(json: String): JsonElement {
        val envelope = Json.parseToJsonElement(json) as JsonObject
        if (envelope.string("error") == "true") error(envelope.string("message", "Pixiv denegó el acceso."))
        return envelope.getValue("body")
    }

    private fun JsonObject.requiredString(key: String): String =
        (getValue(key) as JsonPrimitive).takeUnless { it === JsonNull }?.content
            ?: error("Missing $key")

    private fun JsonObject.string(key: String, default: String = ""): String =
        (this[key] as? JsonPrimitive)?.takeUnless { it === JsonNull }?.content ?: default
}
