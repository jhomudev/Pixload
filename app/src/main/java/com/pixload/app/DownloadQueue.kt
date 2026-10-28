package com.pixload.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure queue bookkeeping. It has no Android or JSON dependencies on purpose so the limit,
 * ordering and state transitions can be covered by JVM tests.
 *
 * Only queued and active jobs count against the limit; finished, cancelled and interrupted
 * jobs release their slot immediately.
 */
internal class DownloadQueueBook {
    class Item @JvmOverloads constructor(
        @JvmField var id: String,
        @JvmField var title: String,
        @JvmField var thumbnail: String,
        @JvmField var state: String,
        @JvmField var progress: Int,
        @JvmField var max: Int,
        /** Pages or files the work will produce; 0 when unknown (legacy rows). */
        @JvmField var pages: Int = 0,
        /** Work type label shown in the queue row, empty for legacy rows. */
        @JvmField var kind: String = ""
    )

    private val items = ArrayList<Item>()

    @Synchronized fun items(): List<Item> = ArrayList(items)

    @Synchronized fun pending(): List<Item> = items.filter { isPending(it) }

    @Synchronized fun count(): Int = items.count { isPending(it) }

    @Synchronized fun isFull(): Boolean = count() >= LIMIT

    @JvmOverloads @Synchronized fun add(id: String, title: String, thumbnail: String,
                                        pages: Int = 0, kind: String = ""): Boolean {
        if (id.isEmpty() || isFull() || items.any { it.id == id && isPending(it) }) return false
        // A retry replaces the stale failed or interrupted row for the same work.
        items.removeAll { it.id == id }
        items.add(Item(id, title, thumbnail, QUEUED, 0, 0, pages, kind))
        return true
    }

    @Synchronized fun progress(id: String, progress: Int, max: Int): Boolean {
        val item = items.firstOrNull { it.id == id } ?: return false
        if (item.progress == progress && item.max == max) return false
        item.progress = progress
        item.max = max
        return true
    }

    @Synchronized fun state(id: String, state: String, progress: Int, max: Int): Boolean {
        val item = items.firstOrNull { it.id == id } ?: return false
        item.state = state
        item.progress = progress
        item.max = max
        return true
    }

    @Synchronized fun remove(id: String): Boolean = items.removeAll { it.id == id }

    @Synchronized fun load(restored: List<Item>) {
        items.clear()
        items.addAll(restored)
    }

    /** Jobs left behind by a dead process can never resume, so they stop counting as pending. */
    @Synchronized fun interruptPending(): Boolean {
        var changed = false
        for (item in items) {
            if (isPending(item)) {
                item.state = INTERRUPTED
                changed = true
            }
        }
        return changed
    }

    @Synchronized fun percent(): Int {
        val pending = pending()
        if (pending.isEmpty()) return 0
        var total = 0
        for (item in pending) total += if (item.max > 0) minOf(100, item.progress * 100 / item.max) else 0
        return total / pending.size
    }

    companion object {
        const val LIMIT = 5
        const val QUEUED = "queued"
        const val ACTIVE = "active"
        const val INTERRUPTED = "interrupted"
        const val ERROR = "error"

        @JvmStatic fun isPending(item: Item): Boolean = item.state == QUEUED || item.state == ACTIVE

        /**
         * Progress of the whole work: [index] finished pages plus [fraction] of the page in
         * flight, divided by the total page count. Keeps every row on one scale.
         */
        @JvmStatic fun overall(index: Int, count: Int, fraction: Int): Int {
            if (count <= 0) return 0
            val done = index.coerceAtLeast(0) * 100 + fraction.coerceIn(0, 100)
            return (done / count).coerceIn(0, 100)
        }
    }
}

/**
 * Process-wide queue shared by [MainActivity] and [DownloadService], persisted only on state
 * transitions so progress ticks never touch storage.
 */
internal object DownloadQueue {
    private const val PREFS = "download_queue"
    private const val KEY = "items"

    private var book: DownloadQueueBook? = null

    @JvmStatic @Synchronized fun load(context: Context): DownloadQueueBook {
        book?.let { return it }
        val created = DownloadQueueBook()
        created.load(read(context))
        if (created.interruptPending()) persist(context, created)
        book = created
        return created
    }

    private fun read(context: Context): List<DownloadQueueBook.Item> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val restored = ArrayList<DownloadQueueBook.Item>(array.length())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val state = item.optString("state", DownloadQueueBook.QUEUED)
                // A failed job belongs to the previous process; its card is still in DownloadStatus.
                if (state == DownloadQueueBook.ERROR) continue
                restored.add(DownloadQueueBook.Item(
                    item.optString("id", ""),
                    item.optString("title", ""),
                    item.optString("thumbnail", ""),
                    state,
                    item.optInt("progress", 0),
                    item.optInt("max", 0),
                    item.optInt("pages", 0),
                    item.optString("kind", "")))
            }
            restored
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(context: Context, queue: DownloadQueueBook) {
        val array = JSONArray()
        for (item in queue.items()) {
            array.put(JSONObject().put("id", item.id).put("title", item.title)
                .put("thumbnail", item.thumbnail).put("state", item.state)
                .put("progress", item.progress).put("max", item.max)
                .put("pages", item.pages).put("kind", item.kind))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).apply()
    }

    @JvmStatic fun items(context: Context): List<DownloadQueueBook.Item> = load(context).items()

    @JvmStatic fun pending(context: Context): List<DownloadQueueBook.Item> = load(context).pending()

    @JvmStatic fun count(context: Context): Int = load(context).count()

    @JvmStatic fun isFull(context: Context): Boolean = load(context).isFull()

    @JvmStatic fun percent(context: Context): Int = load(context).percent()

    @JvmOverloads @JvmStatic @Synchronized fun enqueue(context: Context, id: String, title: String, thumbnail: String,
                                          pages: Int = 0, kind: String = ""): Boolean {
        val queue = load(context)
        val added = queue.add(id, title, thumbnail, pages, kind)
        if (added) persist(context, queue)
        return added
    }

    @JvmStatic @Synchronized fun markActive(context: Context, id: String) {
        val queue = load(context)
        if (queue.state(id, DownloadQueueBook.ACTIVE, 0, 0)) persist(context, queue)
    }

    /** Progress stays in memory; the persisted snapshot only exists for interruption recovery. */
    @JvmStatic fun onProgress(context: Context, id: String, progress: Int, max: Int) {
        load(context).progress(id, progress, max)
    }

    @JvmStatic @Synchronized fun finish(context: Context, id: String) {
        val queue = load(context)
        if (queue.remove(id)) persist(context, queue)
        clearThumbnailsIfFinished(context, queue)
    }

    /** Keeps a failed job visible in the queue until the user dismisses it. */
    @JvmStatic @Synchronized fun fail(context: Context, id: String) {
        val queue = load(context)
        if (queue.state(id, DownloadQueueBook.ERROR, 0, 0)) persist(context, queue)
        clearThumbnailsIfFinished(context, queue)
    }

    /** Serialize admission with terminal transitions so a late image cannot repopulate an empty queue. */
    @JvmStatic @Synchronized fun cacheThumbnail(context: Context, url: String, bytes: ByteArray) {
        if (load(context).pending().none { it.thumbnail == url }) return
        thumbnailCache(context).put(url, bytes)
    }

    private fun clearThumbnailsIfFinished(context: Context, queue: DownloadQueueBook) {
        if (queue.count() != 0) return
        try { thumbnailCache(context).clear() }
        catch (_: Exception) { /* The app cache can be unavailable independently of the downloads. */ }
    }

    private fun thumbnailCache(context: Context) =
        QueueThumbnailCache(java.io.File(context.cacheDir, "queue-thumbnails"))
}
