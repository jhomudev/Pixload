package com.pixload.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PixivPayloadsTest {
    private fun envelope(body: String) = """{"error":false,"body":$body}"""
    private fun failure(detail: String, message: String) {
        assertEquals(message, assertFailsWith<IllegalStateException> {
            PixivPayloads.parse("42", false, detail)
        }.message)
    }

    @Test fun imagePagesKeepOriginalOrderAndPreviewFallbacks() {
        val detail = envelope("""{"title":"Tít ulo","userName":"作者","urls":{"regular":"cover"}}""")
        val pages = envelope("""[{"urls":{"original":"one.png","small":"one-small","regular":"one-regular"}},
            {"urls":{"original":"two.jpg","small":"","regular":"two-regular"}}]""")
        assertEquals(Work("42", "Tít ulo", "作者", "image", "cover", pages = listOf("one.png", "two.jpg"),
            pageThumbnails = listOf("one-small", "two-regular")), PixivPayloads.parse("42", false, detail, pages))
        assertEquals(Work("42", "obra-42", "", "image", "from-page", pages = listOf("a"),
            pageThumbnails = listOf("from-page")), PixivPayloads.parse("42", false,
            envelope("{}"), envelope("""[{"urls":{"original":"a","regular":"from-page"}}]""")))
    }

    @Test fun previewSkipsPagesWithoutRegularUrl() {
        val detail = envelope("{}")
        val pages = envelope("""[{"urls":{"original":"first.jpg","regular":""}},
            {"urls":{"original":"second.jpg","regular":"second-preview"}}]""")
        assertEquals("second-preview", PixivPayloads.parse("42", false, detail, pages).thumbnail)
    }

    @Test fun nullRequiredOriginalIsRejected() {
        assertFailsWith<Exception> {
            PixivPayloads.parse("42", false, envelope("{}"),
                envelope("""[{"urls":{"original":null}}]"""))
        }
    }

    @Test fun nullRequiredUgoiraFieldsAreRejected() {
        val detail = envelope("""{"illustType":2}""")
        assertFailsWith<Exception> {
            PixivPayloads.parse("42", false, detail,
                ugoiraJson = envelope("""{"originalSrc":null,"frames":[]}"""))
        }
        assertFailsWith<Exception> {
            PixivPayloads.parse("42", false, detail,
                ugoiraJson = envelope("""{"originalSrc":"zip","frames":[{"file":null,"delay":80}]}"""))
        }
    }

    @Test fun novelUsesCoverAndContentWithoutPages() {
        assertEquals(Work("42", "Poem", "Writer", "novel", "novel-cover", text = "Line 1\nLine 2"),
            PixivPayloads.parse("42", true,
                envelope("""{"title":"Poem","userName":"Writer","coverUrl":"novel-cover","content":"Line 1\nLine 2"}""")))
    }

    @Test fun ugoiraKeepsZipAndFrameFileDelay() {
        val work = PixivPayloads.parse("42", false,
            envelope("""{"illustType":2,"urls":{"regular":"preview"}}"""),
            ugoiraJson = envelope("""{"originalSrc":"zip-url","frames":[{"file":"0001.jpg","delay":80},{"file":"0002.jpg","delay":120}]}"""))
        assertEquals(Work("42", "obra-42", "", "ugoira", "preview", animationUrl = "zip-url",
            frames = listOf(Work.Frame("0001.jpg", 80), Work.Frame("0002.jpg", 120))), work)
    }

    @Test fun restrictedAndEmptyBodiesKeepExistingErrors() {
        failure(envelope("""{"xRestrict":1,"urls":{}}"""),
            "Esta obra es R-18. Pixiv no permite ver sus archivos sin una sesión autorizada y con el contenido R-18 habilitado en la cuenta.")
        assertEquals("Pixiv no ha proporcionado el texto de esta novela.", assertFailsWith<IllegalStateException> {
            PixivPayloads.parse("42", true, envelope("""{"content":""}"""))
        }.message)
        assertEquals("La obra no tiene páginas disponibles.", assertFailsWith<IllegalStateException> {
            PixivPayloads.parse("42", false, envelope("{}"), envelope("[]"))
        }.message)
        assertEquals("image", PixivPayloads.parse("42", false,
            envelope("""{"xRestrict":1,"urls":{"regular":"available"}}"""),
            envelope("""[{"urls":{"original":"a"}}]""")).type)
    }

    @Test fun ajaxErrorUsesMessageOrFallbackBeforeParsing() {
        failure("""{"error":true,"message":"No access","body":{}}""", "No access")
        failure("""{"error":true,"body":{}}""", "Pixiv denegó el acceso.")
        assertEquals("Denied pages", assertFailsWith<IllegalStateException> {
            PixivPayloads.parse("42", false, envelope("{}"), """{"error":true,"message":"Denied pages"}""")
        }.message)
        assertEquals("Denied meta", assertFailsWith<IllegalStateException> {
            PixivPayloads.parse("42", false, envelope("""{"illustType":2}"""),
                ugoiraJson = """{"error":true,"message":"Denied meta"}""")
        }.message)
    }
}
