package com.pixload.app

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Host-side guards; the Android test exercises actual Activity construction. */
class PreviewImagesInitializationTest {
    @Test fun previewCacheDirectoryIsNotResolvedDuringConstruction() {
        val cache = PreviewImages::class.java.getDeclaredField("queueCache\$delegate")
        assertEquals(Lazy::class.java, cache.type)
    }

    @Test fun oldTwoArgumentViewRemainsCallableFromJava() {
        val method = PreviewImages::class.java.getDeclaredMethod("view", String::class.java,
            Int::class.javaPrimitiveType)
        assertEquals(android.view.View::class.java, method.returnType)
    }

    @Test fun twoWorkersWriteOneCompleteThumbnail() {
        val directory = Files.createTempDirectory("preview-two-workers").toFile()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val cache = QueueThumbnailCache(directory, 64)
            val start = CountDownLatch(1)
            val bytes = listOf(ByteArray(32) { 1 }, ByteArray(32) { 2 })
            val jobs = bytes.map { data -> pool.submit { start.await(); repeat(30) { cache.put("url", data) } } }
            start.countDown()
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
            assertTrue(bytes.any { it.contentEquals(cache.get("url")) })
            assertTrue(bytes.any { it.contentEquals(QueueThumbnailCache(directory, 64).get("url")) })
            assertEquals(1, directory.listFiles()!!.count { it.extension == "jpg" })
        } finally { pool.shutdownNow(); directory.deleteRecursively() }
    }

    @Test fun rejectedEntriesAndUnavailableDirectoryPreserveExistingBytes() {
        val directory = Files.createTempDirectory("preview-rejection").toFile()
        try {
            val cache = QueueThumbnailCache(directory, 8)
            cache.put("saved", byteArrayOf(1, 2, 3))
            val saved = directory.listFiles()!!.single()
            fun digest() = MessageDigest.getInstance("SHA-256").digest(saved.readBytes()).toList()
            val before = digest()
            cache.put("empty", byteArrayOf())
            cache.put("oversize", ByteArray(9))
            assertNull(cache.get("empty"))
            assertNull(cache.get("oversize"))
            assertEquals(before, digest())
            assertEquals(1, directory.listFiles()!!.size)
            val occupied = Files.createTempFile("preview-not-a-directory", ".bin").toFile()
            try {
                val original = occupied.readBytes().toList()
                val unavailable = QueueThumbnailCache(occupied, 8)
                assertNull(unavailable.get("missing"))
                unavailable.put("missing", byteArrayOf(1))
                assertEquals(original, occupied.readBytes().toList())
                assertNull(unavailable.get("missing"))
            } finally { occupied.delete() }
        } finally { directory.deleteRecursively() }
    }
}
