package com.pixload.app

import java.io.File
import java.security.MessageDigest

/** Bounded, app-private cache of already sampled queue thumbnails. Cache failures never block downloads. */
internal class QueueThumbnailCache(private val directory: File, private val maxBytes: Long = 8L * 1024 * 1024) {
    fun get(url: String): ByteArray? = synchronized(lock) {
        try {
            val file = file(url)
            if (!file.isFile) return@synchronized null
            if (file.length() !in 1..maxBytes) { file.delete(); return@synchronized null }
            val bytes = file.readBytes()
            file.setLastModified(nextTime())
            bytes
        } catch (_: Exception) { null }
    }

    fun put(url: String, bytes: ByteArray) = synchronized(lock) {
        if (bytes.isEmpty() || bytes.size > maxBytes) return@synchronized
        var temp: File? = null
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return@synchronized
            val target = file(url)
            if (target.isFile) return@synchronized
            temp = File.createTempFile("thumb-", ".tmp", directory)
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) return@synchronized
            target.setLastModified(nextTime())
            trim()
        } catch (_: Exception) { /* A cache write must not affect the thumbnail. */ }
        finally { temp?.delete() }
    }

    fun remove(url: String) = synchronized(lock) { file(url).delete() }

    fun clear() = synchronized(lock) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isFile && (file.extension == "jpg" || file.extension == "tmp")) file.delete()
            }
        } catch (_: Exception) { /* Cleanup failure must not interrupt a finished download. */ }
    }

    private fun trim() {
        val files = directory.listFiles()?.filter { it.name.endsWith(".jpg") } ?: return
        var size = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (size <= maxBytes) break
            val length = file.length()
            if (file.delete()) size -= length
        }
    }

    private fun nextTime(): Long = maxOf(System.currentTimeMillis(),
        (directory.listFiles()?.maxOfOrNull { it.lastModified() } ?: 0L) + 1L)

    private fun file(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return File(directory, hash.joinToString("") { "%02x".format(it) } + ".jpg")
    }

    private companion object { val lock = Any() }
}
