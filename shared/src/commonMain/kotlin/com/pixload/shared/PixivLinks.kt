package com.pixload.shared

object PixivLinks {
    // Match ASCII case only, like Java Pattern.CASE_INSENSITIVE without UNICODE_CASE.
    private val url = Regex("""[hH][tT][tT][pP][sS]?://(?:[wW]{3}\.)?[pP][iI][xX][iI][vV]\.[nN][eE][tT]/[^\s　<>"'“”‘’]+""")
    private const val trailing = ".,;:!?)]}»、。？！」"

    fun extract(text: String?): String? {
        val match = text?.let { url.find(it) } ?: return null
        return match.value.trimEnd { it in trailing }
    }
}
