package com.pixload.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class QueueThumbnailCacheTest {
    @Test fun clearRemovesOnlyQueueCacheFilesAndSurvivesReopening() {
        val directory = Files.createTempDirectory("thumb-cache-test").toFile()
        try {
            val cache = QueueThumbnailCache(directory, 32)
            cache.put("a", byteArrayOf(1, 2, 3))
            cache.put("b", byteArrayOf(4, 5, 6))
            val unfinished = java.io.File(directory, "thumb-unfinished.tmp")
            unfinished.writeBytes(byteArrayOf(7))
            val unrelated = java.io.File(directory.parentFile, directory.name + "-download.jpg")
            try {
                unrelated.writeBytes(byteArrayOf(9))
                cache.clear()
                assertNull(QueueThumbnailCache(directory, 32).get("a"))
                assertNull(QueueThumbnailCache(directory, 32).get("b"))
                assertFalse(unfinished.exists())
                assertEquals(0, directory.listFiles()!!.size)
                assertArrayEquals(byteArrayOf(9), unrelated.readBytes())
                cache.clear()
            } finally { unrelated.delete() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun survivesNewInstanceWithoutExposingTheUrlInFileNames() {
        val directory = Files.createTempDirectory("thumb-cache-test").toFile()
        try {
            val url = "https://i.pximg.net/img-original/private/123.jpg"
            QueueThumbnailCache(directory, 32).put(url, byteArrayOf(1, 2, 3))
            assertArrayEquals(byteArrayOf(1, 2, 3), QueueThumbnailCache(directory, 32).get(url))
            assertEquals(1, directory.listFiles()!!.size)
            assertFalse(directory.listFiles()!![0].name.contains("private"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun trimsLeastRecentlyUsedAndIgnoresOversizedEntries() {
        val directory = Files.createTempDirectory("thumb-cache-test").toFile()
        try {
            val cache = QueueThumbnailCache(directory, 6)
            cache.put("a", byteArrayOf(1, 2, 3))
            cache.put("b", byteArrayOf(4, 5, 6))
            cache.put("c", byteArrayOf(7, 8, 9))
            assertNull(cache.get("a"))
            assertArrayEquals(byteArrayOf(7, 8, 9), cache.get("c"))
            cache.put("large", ByteArray(7))
            assertNull(cache.get("large"))
            assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 6L)
        } finally { directory.deleteRecursively() }
    }

    @Test fun brokenOrEmptyFilesAreDiscardedForRedownload() {
        val directory = Files.createTempDirectory("thumb-cache-test").toFile()
        try {
            val cache = QueueThumbnailCache(directory, 32)
            cache.put("broken", byteArrayOf(1))
            directory.listFiles()!!.single().writeBytes(byteArrayOf())
            assertNull(cache.get("broken"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun parallelWritersLeaveOnlyCompleteBoundedEntries() {
        val directory = Files.createTempDirectory("thumb-cache-test").toFile()
        val pool = Executors.newFixedThreadPool(3)
        try {
            val start = CountDownLatch(1)
            val cache = QueueThumbnailCache(directory, 60)
            val jobs = (1..3).map { number -> pool.submit {
                start.await()
                repeat(20) { cache.put("item-$number", ByteArray(20) { number.toByte() }) }
            } }
            start.countDown()
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
            assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 60L)
            assertTrue(directory.listFiles()!!.all { it.extension == "jpg" && it.length() == 20L })
            (1..3).forEach { number ->
                assertArrayEquals(ByteArray(20) { number.toByte() }, cache.get("item-$number"))
            }
        } finally {
            pool.shutdownNow()
            directory.deleteRecursively()
        }
    }
}
