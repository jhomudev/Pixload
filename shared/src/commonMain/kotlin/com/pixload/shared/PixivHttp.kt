package com.pixload.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url

/** Pixiv AJAX text transport. The caller owns session acquisition; binary media needs a separate streaming path. */
class PixivHttp(engine: HttpClientEngine) {
    data class Response(val status: Int, val body: String)

    private val client = HttpClient(engine) {
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun get(url: String, cookie: String? = null): Response {
        val parsed = try {
            Url(url)
        } catch (cause: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid Pixiv URL", cause)
        }
        // Reject before creating a request; only ASCII host case folding is safe for the origin guard.
        val host = parsed.host.takeIf { it.all { char -> char.code < 128 } }?.lowercase()
        require(PixivOrigins.isAllowed(parsed.protocol.name, host) &&
            PixivOrigins.acceptsCookies(host) && parsed.encodedPath.startsWith("/ajax/")) { "Invalid Pixiv URL" }

        val response = client.get(parsed) {
            header(HttpHeaders.Referrer, "https://www.pixiv.net/")
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            if (cookie != null && PixivOrigins.acceptsCookies(host)) {
                header(HttpHeaders.Cookie, cookie)
            }
        }
        return Response(response.status.value, response.bodyAsText())
    }

    fun close() = client.close()
}
