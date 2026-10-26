package com.pixload.shared

/** Classifies components already parsed by the platform URI parser; does not parse or fetch URLs. */
object PixivWorkLinks {
    sealed interface Result
    data class Work(val id: String, val novel: Boolean) : Result
    enum class Reason { HOST, SCHEME, ID }
    data class Rejected(val reason: Reason) : Result

    private val pathId = Regex("/(?:artworks|novels)/([0-9]+)(?:/|$)")
    private val digits = Regex("[0-9]+")

    fun classify(host: String?, scheme: String?, path: String?, queryId: String?): Result {
        if (!"pixiv.net".equals(host, ignoreCase = true) &&
            !"www.pixiv.net".equals(host, ignoreCase = true)) return Rejected(Reason.HOST)
        if (!"https".equals(scheme, ignoreCase = true) &&
            !"http".equals(scheme, ignoreCase = true)) return Rejected(Reason.SCHEME)

        val normalizedPath = path ?: ""
        val novel = normalizedPath.contains("novel") || normalizedPath.contains("novels")
        val id = queryId ?: pathId.find(normalizedPath)?.groupValues?.get(1)
        if (id == null || !digits.matches(id)) return Rejected(Reason.ID)
        return Work(id, novel)
    }
}
