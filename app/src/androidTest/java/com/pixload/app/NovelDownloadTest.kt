package com.pixload.app

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic payload/stream and intent contracts, not a Pixiv download or real SAF provider. */
@RunWith(AndroidJUnit4::class)
class NovelDownloadTest {
    private fun work() = PixivClient.Work().apply {
        title = "Café & 日本語 😀"
        author = "A < B"
        text = "\nTexto & <literal>\n\nFin\n"
        type = "novel"
    }

    @Test fun txtRoundTripAndLegacyJobKeepExactUtf8Output() {
        val work = work()
        val file = JSONObject(NovelDownload.file(work, false).toString())
        assertEquals("novela.txt", file.getString("name"))
        assertFalse(file.has("epub"))
        val output = ByteArrayOutputStream()
        NovelDownload.write(file, output)
        assertArrayEquals((work.title + "\n" + work.author + "\n\n" + work.text).toByteArray(Charsets.UTF_8), output.toByteArray())
        val legacy = ByteArrayOutputStream()
        NovelDownload.write(JSONObject().put("text", "texto antiguo 😀\r\n"), legacy)
        assertArrayEquals("texto antiguo 😀\r\n".toByteArray(Charsets.UTF_8), legacy.toByteArray())
    }

    @Test fun epubRoundTripKeepsRawTextAndProducesZipNotTxt() {
        val work = work()
        val file = JSONObject(NovelDownload.file(work, true).toString())
        assertEquals("novela.epub", file.getString("name"))
        assertEquals(work.title, file.getString("title"))
        assertEquals(work.author, file.getString("author"))
        assertEquals(work.text, file.getString("text"))
        val output = ByteArrayOutputStream()
        NovelDownload.write(file, output)
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            assertEquals("mimetype", zip.nextEntry.name)
            assertEquals("application/epub+zip", zip.readBytes().toString(Charsets.US_ASCII))
        }
    }

    @Test fun externalOpeningAndSharingKeepTheExactSafUriMimeAndReadGrant() {
        val uri = Uri.parse("content://example.documents/tree/books/document/books%2FCaf%C3%A9.epub")
        val open = GalleryViewer.epubIntent(uri)
        assertEquals(Intent.ACTION_VIEW, open.action)
        assertEquals(uri, open.data)
        assertEquals("application/epub+zip", open.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, open.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val share = GalleryShare.intent(listOf(GalleryDocuments.Item(uri, "Café.epub", open.type)))
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals(open.type, share.type)
        assertEquals(uri, share.clipData!!.getItemAt(0).uri)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
