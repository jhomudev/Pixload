package com.pixload.app

/** Activity-local preflight feedback; it never changes the persisted queue. Main thread only. */
internal class RetryProgress {
    private val active = HashSet<String>()
    fun begin(id: String): Boolean = active.add(id)
    fun end(id: String) { active.remove(id) }
    fun active(id: String): Boolean = id in active
}

/** Selection only: never persist Pixiv URLs, novel text, frames or a byte offset for retry. */
internal data class RetryDownload(
    val id: String, val type: String, val folder: String, val pages: List<Int>,
    val format: String, val preset: String
) {
    fun validated(workId: String?, workType: String?, pageCount: Int): List<Int>? {
        if (id.isBlank() || id != workId || folder.isBlank() || type != workType) return null
        return when (type) {
            "image" -> if (format.isEmpty() && preset.isEmpty() && pages.isNotEmpty() &&
                pages.distinct().size == pages.size && pages.all { it in 0 until pageCount }) pages else null
            "ugoira" -> if (pages.isEmpty() && (format == "mp4" && preset.isEmpty() ||
                format == "gif" && preset in listOf("fast", "balanced", "quality"))) pages else null
            "novel" -> if (pages.isEmpty() && format in listOf("txt", "epub") && preset.isEmpty()) pages else null
            else -> null
        }
    }
}
