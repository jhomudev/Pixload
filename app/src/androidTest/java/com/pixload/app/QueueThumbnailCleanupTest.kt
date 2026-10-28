package com.pixload.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Isolated preferences/cache; never touches actual downloaded files. */
@RunWith(AndroidJUnit4::class)
class QueueThumbnailCleanupTest {
    private fun isolated(action: (Context, QueueThumbnailCache) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "queue_cleanup_${System.nanoTime()}_"
        val root = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getCacheDir(): File = root
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences(prefix + name, mode)
        }
        val field = DownloadQueue::class.java.getDeclaredField("book").apply { isAccessible = true }
        val previous = field.get(null)
        try {
            field.set(null, null)
            action(context, QueueThumbnailCache(File(root, "queue-thumbnails")))
        } finally {
            field.set(null, previous)
            base.deleteSharedPreferences(prefix + "download_queue")
            root.deleteRecursively()
        }
    }

    @Test fun finishingOneJobKeepsCacheUntilTheLastJobEnds() = isolated { context, cache ->
        assertTrue(DownloadQueue.enqueue(context, "a", "A", "url-a"))
        assertTrue(DownloadQueue.enqueue(context, "b", "B", "url-b"))
        DownloadQueue.cacheThumbnail(context, "url-a", byteArrayOf(1))
        DownloadQueue.cacheThumbnail(context, "url-b", byteArrayOf(2))
        DownloadQueue.finish(context, "a")
        assertNotNull(cache.get("url-a"))
        assertNotNull(cache.get("url-b"))
        DownloadQueue.finish(context, "b")
        assertEquals(0, DownloadQueue.count(context))
        assertNull(cache.get("url-a"))
        assertNull(cache.get("url-b"))
        DownloadQueue.cacheThumbnail(context, "url-b", byteArrayOf(2))
        assertNull(cache.get("url-b"))
    }

    @Test fun failureCleansCacheEvenThoughTheErrorRowRemains() = isolated { context, cache ->
        DownloadQueue.enqueue(context, "a", "A", "url-a")
        DownloadQueue.cacheThumbnail(context, "url-a", byteArrayOf(1))
        DownloadQueue.fail(context, "a")
        assertEquals(DownloadQueueBook.ERROR, DownloadQueue.items(context).single().state)
        assertNull(cache.get("url-a"))
        DownloadQueue.cacheThumbnail(context, "url-a", byteArrayOf(1))
        assertNull(cache.get("url-a"))
    }

    @Test fun concurrentLateWritesCannotRecreateTheCacheAfterCancellation() = isolated { context, cache ->
        DownloadQueue.enqueue(context, "a", "A", "url-a")
        val pool = Executors.newSingleThreadExecutor()
        try {
            val job = pool.submit { repeat(50) { DownloadQueue.cacheThumbnail(context, "url-a", byteArrayOf(1)) } }
            DownloadQueue.finish(context, "a") // Cancellation uses the same terminal transition.
            job.get(10, TimeUnit.SECONDS)
            assertNull(cache.get("url-a"))
            DownloadQueue.enqueue(context, "b", "B", "url-b")
            DownloadQueue.cacheThumbnail(context, "url-a", byteArrayOf(1))
            assertNull(cache.get("url-a"))
            DownloadQueue.cacheThumbnail(context, "url-b", byteArrayOf(2))
            assertArrayEquals(byteArrayOf(2), cache.get("url-b"))
        } finally { pool.shutdownNow() }
    }

    @Test fun terminalOrdersRejectLateWritesAndPreserveProtectedFiles() {
        for (failure in listOf(false, true)) {
            for (reverse in listOf(false, true)) isolated { context, cache ->
                val root = context.cacheDir
                val download = File(root, "downloads/work.jpg").apply {
                    parentFile!!.mkdirs()
                    writeText("download")
                }
                val gallery = File(root, "gallery.json").apply { writeText("gallery") }
                fun hashes() = root.walkTopDown().filter { it.isFile }.associate {
                    it.relativeTo(root).path to java.security.MessageDigest
                        .getInstance("SHA-256").digest(it.readBytes()).toList()
                }
                val protected = hashes()
                for (id in listOf("a", "b")) {
                    assertTrue(DownloadQueue.enqueue(context, id, id, "url-$id"))
                    DownloadQueue.cacheThumbnail(context, "url-$id", byteArrayOf(1))
                }
                DownloadQueue.markActive(context, "a")
                val prefs = context.getSharedPreferences("download_queue", Context.MODE_PRIVATE)
                val persisted = prefs.getString("items", "")
                val before = hashes()
                assertFalse(DownloadQueue.enqueue(context, "a", "duplicate", "other"))
                DownloadQueue.finish(context, "unknown")
                DownloadQueue.fail(context, "unknown")
                DownloadQueue.cacheThumbnail(context, "unrelated", byteArrayOf(1))
                DownloadQueue.cacheThumbnail(context, "url-a", byteArrayOf())
                DownloadQueue.cacheThumbnail(context, "url-a", ByteArray(8 * 1024 * 1024 + 1))
                assertEquals(before, hashes())
                assertEquals(persisted, prefs.getString("items", ""))

                val order = if (reverse) listOf("b", "a") else listOf("a", "b")
                for ((index, id) in order.withIndex()) {
                    if (failure) DownloadQueue.fail(context, id) else DownloadQueue.finish(context, id)
                    if (index == 0) {
                        assertNotNull(cache.get("url-a"))
                        assertNotNull(cache.get("url-b"))
                    }
                }
                assertEquals(0, DownloadQueue.count(context))
                assertNull(cache.get("url-a"))
                assertNull(cache.get("url-b"))
                val empty = hashes()
                DownloadQueue.cacheThumbnail(context, "url-a", byteArrayOf(1))
                assertEquals(empty, hashes())
                assertTrue(DownloadQueue.enqueue(context, "new", "new", "url-new"))
                DownloadQueue.cacheThumbnail(context, "url-new", byteArrayOf(2))
                val newQueue = hashes()
                DownloadQueue.cacheThumbnail(context, "url-a", byteArrayOf(1))
                assertEquals(newQueue, hashes())
                assertArrayEquals(byteArrayOf(2), cache.get("url-new"))
                assertTrue(protected.all { (name, hash) -> hashes()[name] == hash })
                assertEquals("download", download.readText())
                assertEquals("gallery", gallery.readText())
            }
        }
    }

    @Test fun racingWritesCannotSurviveLastFinishOrFailure() = isolated { context, cache ->
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(100) { number ->
                val id = number.toString()
                assertTrue(DownloadQueue.enqueue(context, id, id, "url-$id"))
                val start = java.util.concurrent.CountDownLatch(1)
                val writer = pool.submit {
                    start.await()
                    repeat(20) { DownloadQueue.cacheThumbnail(context, "url-$id", byteArrayOf(1)) }
                }
                val terminal = pool.submit {
                    start.await()
                    if (number % 2 == 0) DownloadQueue.finish(context, id) else DownloadQueue.fail(context, id)
                }
                start.countDown()
                writer.get(10, TimeUnit.SECONDS)
                terminal.get(10, TimeUnit.SECONDS)
                assertNull(cache.get("url-$id"))
                assertEquals(0, DownloadQueue.count(context))
            }
        } finally { pool.shutdownNow() }
    }
}
