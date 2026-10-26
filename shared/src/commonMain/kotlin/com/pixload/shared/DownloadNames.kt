package com.pixload.shared

object DownloadNames {
    fun startOrdinal(duplicate: Boolean): Int = if (duplicate) 2 else 1

    fun folderName(title: String, ordinal: Int): String =
        if (ordinal <= 1) title else "$title ($ordinal)"

    fun collisionName(desired: String, ordinal: Int): String {
        if (ordinal <= 1) return desired
        val dot = desired.lastIndexOf('.')
        return desired.substring(0, dot) + " ($ordinal)" + desired.substring(dot)
    }

    fun flatFileName(title: String, pageName: String): String =
        title + pageName.substring(pageName.lastIndexOf('.'))

    fun titleName(title: String): String {
        // Match Java's default ASCII \s and String.trim(), not Kotlin's wider whitespace set.
        val clean = title.replace(Regex("[\\p{Cntrl}\\p{Cf}/\\\\:*?\"<>|]"), " ")
            .replace(Regex("[ \t\n\u000B\u000C\r]+"), " ")
            .trim { it <= ' ' }
            .replace(Regex("^[. ]+|[. ]+$"), "")
        if (clean.isEmpty()) return "Obra"
        val name = StringBuilder()
        var bytes = 0
        var index = 0
        while (index < clean.length) {
            val next = if (clean[index].isHighSurrogate() && index + 1 < clean.length &&
                clean[index + 1].isLowSurrogate()) index + 2 else index + 1
            val character = clean.substring(index, next)
            val size = character.encodeToByteArray().size
            if (bytes + size > 180) break // Reserve room for extension, collision suffix and .part.
            name.append(character)
            bytes += size
            index = next
        }
        val shortened = name.toString().replace(Regex("[. ]+$"), "")
        return shortened.ifEmpty { "Obra" }
    }
}
