package com.pixload.app

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class EpubNovelTest {
    private val dc = "http://purl.org/dc/elements/1.1/"
    private val opf = "http://www.idpf.org/2007/opf"
    private val html = "http://www.w3.org/1999/xhtml"

    @Test fun mimetypeIsFirstStoredExactAndWithoutExtraFields() {
        val bytes = EpubNovel.bytes("Título", "Autor", "Texto", 0)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val first = zip.nextEntry
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            assertEquals("application/epub+zip", zip.readBytes().toString(Charsets.US_ASCII))
            assertTrue(first.extra == null || first.extra.isEmpty())
        }
        // Local ZIP header: no preamble, mimetype name length 8, extra field length 0.
        assertArrayEquals(byteArrayOf(0x50, 0x4b, 3, 4), bytes.copyOfRange(0, 4))
        assertEquals(8, bytes[26].toInt())
        assertEquals(0, bytes[28].toInt())
        assertEquals(0, bytes[29].toInt())
    }

    @Test fun allPackageNavigationAndSpineTargetsExist() {
        val files = unzip(EpubNovel.bytes("Título & 日本語 😀", "A < B", "Texto", 0))
        assertEquals(6, files.size)
        val container = parse(files.getValue("META-INF/container.xml"))
        val root = container.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:container", "rootfile")
            .item(0) as Element
        val pack = parse(files.getValue(root.getAttribute("full-path")))
        assertEquals("3.0", pack.documentElement.getAttribute("version"))
        assertEquals("Título & 日本語 😀", pack.getElementsByTagNameNS(dc, "title").item(0).textContent)
        assertEquals("A < B", pack.getElementsByTagNameNS(dc, "creator").item(0).textContent)
        assertEquals("und", pack.getElementsByTagNameNS(dc, "language").item(0).textContent)
        assertEquals("1970-01-01T00:00:00Z", pack.getElementsByTagNameNS(opf, "meta").item(0).textContent)
        val items = pack.getElementsByTagNameNS(opf, "item")
        val ids = HashSet<String>()
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            ids.add(item.getAttribute("id"))
            assertTrue(item.getAttribute("href"), files.containsKey("OEBPS/" + item.getAttribute("href")))
        }
        val spine = pack.getElementsByTagNameNS(opf, "itemref").item(0) as Element
        assertTrue(spine.getAttribute("idref") in ids)
        val navItem = (0 until items.length).map { items.item(it) as Element }
            .single { it.getAttribute("properties") == "nav" }
        val nav = parse(files.getValue("OEBPS/" + navItem.getAttribute("href")))
        val toc = nav.getElementsByTagNameNS(html, "nav").item(0) as Element
        assertEquals("toc", toc.getAttributeNS("http://www.idpf.org/2007/ops", "type"))
        val link = nav.getElementsByTagNameNS(html, "a").item(0) as Element
        assertTrue(files.containsKey("OEBPS/" + link.getAttribute("href")))
        val ncx = parse(files.getValue("OEBPS/toc.ncx"))
        val uid = ncx.getElementsByTagNameNS("http://www.daisy.org/z3986/2005/ncx/", "meta").item(0) as Element
        assertEquals(pack.getElementsByTagNameNS(dc, "identifier").item(0).textContent, uid.getAttribute("content"))
        files.filterKeys { it != "mimetype" }.values.forEach { parse(it) }
    }

    @Test fun textAndWhitespaceArePreservedAndMarkupStaysLiteral() {
        val text = "\n  Café 日本語 😀 & <script>alert('x')</script>\n\n[ruby:漢字 > かんじ]\n Fin  \n"
        val files = unzip(EpubNovel.bytes("Título", "Autor", text, 0))
        val chapter = parse(files.getValue("OEBPS/novela.xhtml"))
        val bodyText = chapter.getElementsByTagNameNS(html, "div").item(0)
        assertEquals(text, bodyText.textContent)
        assertEquals(0, chapter.getElementsByTagNameNS(html, "script").length)
        assertEquals(0, chapter.getElementsByTagNameNS(html, "img").length)
    }

    @Test fun illegalXmlCharactersAreDroppedButSupplementaryUnicodeSurvives() {
        val text = "a\u0000\u0001\uD800b\uDC00\uFFFE\uFFFF😀\t\n"
        val files = unzip(EpubNovel.bytes("T\u0000😀", "A\uD800", text, 0))
        files.filterKeys { it != "mimetype" }.values.forEach { parse(it) }
        val chapter = parse(files.getValue("OEBPS/novela.xhtml"))
        assertEquals("ab😀\t\n", chapter.getElementsByTagNameNS(html, "div").item(0).textContent)
    }

    @Test fun missingAuthorIsNotInventedAndIdentifierIsStableForSameContent() {
        fun metadata(text: String) = parse(unzip(EpubNovel.bytes("T", "", text, 0)).getValue("OEBPS/content.opf"))
        val one = metadata("Texto")
        assertEquals(0, one.getElementsByTagNameNS(dc, "creator").length)
        val id = one.getElementsByTagNameNS(dc, "identifier").item(0).textContent
        assertEquals(id, metadata("Texto").getElementsByTagNameNS(dc, "identifier").item(0).textContent)
        assertNotEquals(id, metadata("Otro texto").getElementsByTagNameNS(dc, "identifier").item(0).textContent)
    }

    @Test(expected = IOException::class) fun absentTextIsRejected() {
        EpubNovel.bytes("T", "A", " \n\t", 0)
    }

    @Test(expected = IOException::class) fun xmlIllegalTextCannotBecomeAnEmptyBook() {
        EpubNovel.bytes("T", "A", "\u0000\uD800", 0)
    }

    @Test(expected = IOException::class) fun missingTitleIsRejectedRatherThanInvented() {
        EpubNovel.bytes(" \u0000", "A", "Texto", 0)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertNull("duplicate ${entry.name}", files.put(entry.name, zip.readBytes()))
            }
        }
        return files
    }

    private fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
}
