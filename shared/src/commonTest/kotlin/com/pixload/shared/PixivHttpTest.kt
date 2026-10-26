package com.pixload.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PixivHttpTest {
    private fun engine(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): MockEngine =
        MockEngine(MockEngineConfig().apply { addHandler(handler) })

    @Test fun acceptedAjaxRequestCarriesHeadersAndReturnsStatusAndBody() = runBlocking {
        var requests = 0
        val transport = PixivHttp(engine { request ->
            requests++
            assertEquals("https://www.pixiv.net/ajax/illust/42", request.url.toString())
            assertEquals("https://www.pixiv.net/", request.headers[HttpHeaders.Referrer])
            assertEquals("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36", request.headers[HttpHeaders.UserAgent])
            assertEquals("session=secret", request.headers[HttpHeaders.Cookie])
            respond("{\"ok\":true}", HttpStatusCode.OK)
        })
        try {
            assertEquals(PixivHttp.Response(200, "{\"ok\":true}"), transport.get("https://www.pixiv.net/ajax/illust/42", "session=secret"))
            assertEquals(1, requests)
        } finally { transport.close() }
    }

    @Test fun invalidOriginsNeverReachEngine() = runBlocking {
        var requests = 0
        val transport = PixivHttp(engine { requests++; respond("unexpected") })
        try {
            for (url in listOf("not a url", "http://www.pixiv.net/ajax/illust/1", "https://evil.pixiv.net/ajax/illust/1", "https://www.pixiv.net.evil/ajax/illust/1", "https://i.pximg.net/image.jpg", "https://pixiv.net/image.jpg", "ftp://pixiv.net/ajax/illust/1")) {
                assertFailsWith<IllegalArgumentException> { transport.get(url, "session=secret") }
            }
            assertEquals(0, requests)
        } finally { transport.close() }
    }

    @Test fun ajaxWithoutSessionNeverAddsCookie() = runBlocking {
        val transport = PixivHttp(engine { request ->
            assertNull(request.headers[HttpHeaders.Cookie])
            respond("{}", HttpStatusCode.OK)
        })
        try {
            assertEquals(PixivHttp.Response(200, "{}"), transport.get("https://pixiv.net/ajax/user/extra"))
        } finally { transport.close() }
    }

    @Test fun redirectIsReturnedWithoutFollowingOrLeakingCookie() = runBlocking {
        var requests = 0
        val transport = PixivHttp(engine { request ->
            requests++
            assertEquals("session=secret", request.headers[HttpHeaders.Cookie])
            respond("redirect", HttpStatusCode.Found, headers = io.ktor.http.headersOf(HttpHeaders.Location, "https://evil.example/"))
        })
        try {
            assertEquals(PixivHttp.Response(302, "redirect"), transport.get("https://pixiv.net/ajax/user/extra", "session=secret"))
            assertEquals(1, requests)
        } finally { transport.close() }
    }
}
