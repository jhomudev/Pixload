package com.pixload.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a self-contained EPUB 3 package for the text Pixiv delivers, using only java.util.zip.
 * It carries an EPUB 2 NCX fallback so older readers still find the navigation, and it escapes the
 * delivered text instead of interpreting it: whatever Pixiv sends is what the reader shows, the
 * same rule the TXT output follows.
 */
internal object EpubNovel {
    private const val MIMETYPE = "application/epub+zip"

    /** Pixiv does not report the novel language; BCP 47 "und" states that honestly. */
    private const val LANGUAGE = "und"

    private const val CHAPTER = "OEBPS/novela.xhtml"

    @JvmStatic
    @Throws(IOException::class)
    fun bytes(title: String, author: String, text: String,
              modified: Long = System.currentTimeMillis()): ByteArray {
        val bookTitle = title
        val bookAuthor = author.takeIf { xml(it).isNotBlank() } ?: ""
        val bookText = text
        if (xml(bookTitle).isBlank()) throw IOException("Pixiv no ha proporcionado el título de esta novela.")
        if (xml(bookText).isBlank()) throw IOException("Pixiv no ha proporcionado el texto de esta novela.")
        val identifier = "urn:uuid:" + UUID.nameUUIDFromBytes(
            (bookTitle + "\u0000" + bookAuthor + "\u0000" + bookText).toByteArray(StandardCharsets.UTF_8))
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            // The OCF container requires the MIME type as the first, uncompressed entry.
            stored(zip, "mimetype", MIMETYPE.toByteArray(StandardCharsets.US_ASCII))
            deflated(zip, "META-INF/container.xml", container())
            deflated(zip, "OEBPS/content.opf", packageDocument(identifier, bookTitle, bookAuthor, timestamp(modified)))
            deflated(zip, "OEBPS/nav.xhtml", navigation(bookTitle))
            deflated(zip, "OEBPS/toc.ncx", ncx(identifier, bookTitle))
            deflated(zip, CHAPTER, chapter(bookTitle, bookAuthor, bookText))
        }
        return output.toByteArray()
    }

    private fun stored(zip: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        entry.method = ZipEntry.STORED
        entry.size = data.size.toLong()
        entry.compressedSize = data.size.toLong()
        val crc = CRC32()
        crc.update(data)
        entry.crc = crc.value
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    private fun deflated(zip: ZipOutputStream, name: String, document: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(document.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun container(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n")
        append("  <rootfiles>\n")
        append("    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n")
        append("  </rootfiles>\n")
        append("</container>\n")
    }

    private fun packageDocument(identifier: String, title: String, author: String, modified: String): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"pub-id\"")
            append(" xml:lang=\"").append(LANGUAGE).append("\">\n")
            append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
            append("    <dc:identifier id=\"pub-id\">").append(xml(identifier)).append("</dc:identifier>\n")
            append("    <dc:title>").append(xml(title)).append("</dc:title>\n")
            append("    <dc:language>").append(LANGUAGE).append("</dc:language>\n")
            if (author.isNotEmpty()) append("    <dc:creator>").append(xml(author)).append("</dc:creator>\n")
            append("    <meta property=\"dcterms:modified\">").append(modified).append("</meta>\n")
            append("  </metadata>\n")
            append("  <manifest>\n")
            append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            append("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
            append("    <item id=\"chapter\" href=\"novela.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
            append("  </manifest>\n")
            append("  <spine toc=\"ncx\">\n")
            append("    <itemref idref=\"chapter\"/>\n")
            append("  </spine>\n")
            append("</package>\n")
        }

    private fun navigation(title: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"")
        append(" xml:lang=\"").append(LANGUAGE).append("\" lang=\"").append(LANGUAGE).append("\">\n")
        append("  <head>\n")
        append("    <meta charset=\"utf-8\"/>\n")
        append("    <title>").append(xml(title)).append("</title>\n")
        append("  </head>\n")
        append("  <body>\n")
        append("    <nav epub:type=\"toc\" id=\"toc\">\n")
        append("      <h1>").append(xml(title)).append("</h1>\n")
        append("      <ol>\n")
        append("        <li><a href=\"novela.xhtml\">").append(xml(title)).append("</a></li>\n")
        append("      </ol>\n")
        append("    </nav>\n")
        append("  </body>\n")
        append("</html>\n")
    }

    private fun chapter(title: String, author: String, text: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"").append(LANGUAGE)
        append("\" lang=\"").append(LANGUAGE).append("\">\n")
        append("  <head>\n")
        append("    <meta charset=\"utf-8\"/>\n")
        append("    <title>").append(xml(title)).append("</title>\n")
        append("  </head>\n")
        append("  <body>\n")
        append("    <h1>").append(xml(title)).append("</h1>\n")
        if (author.isNotEmpty()) append("    <p>").append(xml(author)).append("</p>\n")
        // Keep spacing and Pixiv markup literally, without injecting HTML or fetching images.
        append("    <div id=\"text\" style=\"white-space: pre-wrap\">").append(xml(text)).append("</div>\n")
        append("  </body>\n")
        append("</html>\n")
    }

    private fun ncx(identifier: String, title: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
          <head>
            <meta name="dtb:uid" content="${xml(identifier)}"/>
            <meta name="dtb:depth" content="1"/>
            <meta name="dtb:totalPageCount" content="0"/>
            <meta name="dtb:maxPageNumber" content="0"/>
          </head>
          <docTitle><text>${xml(title)}</text></docTitle>
          <navMap><navPoint id="chapter" playOrder="1">
            <navLabel><text>${xml(title)}</text></navLabel>
            <content src="novela.xhtml"/>
          </navPoint></navMap>
        </ncx>
    """.trimIndent()

    /** Escapes [value] for XML and drops the characters XML 1.0 forbids, keeping Unicode intact. */
    internal fun xml(value: String): String {
        val escaped = StringBuilder(value.length + 16)
        var index = 0
        while (index < value.length) {
            val code = value.codePointAt(index)
            index += Character.charCount(code)
            // Lone surrogates and control characters fall outside these ranges and are dropped.
            if (code != 0x9 && code != 0xA && code != 0xD && code !in 0x20..0xD7FF &&
                code !in 0xE000..0xFFFD && code !in 0x10000..0x10FFFF) continue
            when (code) {
                '&'.code -> escaped.append("&amp;")
                '<'.code -> escaped.append("&lt;")
                '>'.code -> escaped.append("&gt;")
                '"'.code -> escaped.append("&quot;")
                '\''.code -> escaped.append("&#39;")
                else -> escaped.appendCodePoint(code)
            }
        }
        return escaped.toString()
    }

    private fun timestamp(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(millis))
    }
}
