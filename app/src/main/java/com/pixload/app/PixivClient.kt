package com.pixload.app

import android.net.Network
import android.net.Uri
import android.webkit.CookieManager
import com.pixload.shared.PixivOrigins
import com.pixload.shared.PixivWorkLinks
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

internal class PixivClient private constructor() {
    class Work {
        @JvmField var id: String? = null
        @JvmField var title: String? = null
        @JvmField var author: String? = null
        @JvmField var type: String? = null
        @JvmField var thumbnail: String? = null
        @JvmField var text: String? = null
        @JvmField var animationUrl: String? = null
        @JvmField var frames: JSONArray? = null
        @JvmField val pages: MutableList<String> = ArrayList()
        @JvmField val pageThumbnails: MutableList<String> = ArrayList()
    }

    companion object {
        private const val BASE = "https://www.pixiv.net"
        private const val REFERER = "$BASE/"

        @JvmStatic
        fun isLoggedIn(): Boolean {
            return try {
                val connection = open("$BASE/ajax/user/extra")
                try {
                    if (connection.responseCode != 200) false
                    else connection.inputStream.use { stream ->
                        !JSONObject(String(read(stream), StandardCharsets.UTF_8)).optBoolean("error", true)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                false
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun load(input: String): Work {
            val uri = Uri.parse(input.trim { it <= ' ' })
            // Keep Android Uri parsing and query decoding, with host and scheme checked first.
            var validation = PixivWorkLinks.classify(uri.host, uri.scheme, uri.path, null)
            if (validation is PixivWorkLinks.Rejected) {
                when (validation.reason) {
                    PixivWorkLinks.Reason.HOST -> throw IOException("Introduce un enlace de pixiv.net.")
                    PixivWorkLinks.Reason.SCHEME -> throw IOException("El enlace debe usar HTTP o HTTPS.")
                    else -> Unit
                }
            }
            validation = PixivWorkLinks.classify(uri.host, uri.scheme, uri.path, uri.getQueryParameter("id"))
            if (validation is PixivWorkLinks.Rejected) throw IOException("Enlace de obra no reconocido.")
            val link = validation as PixivWorkLinks.Work
            val novel = link.novel
            val id = link.id

            val prefix = if (novel) "/ajax/novel/" else "/ajax/illust/"
            val body = api(prefix + id)
            val work = Work()
            work.id = id
            work.title = body.optString("title", "obra-$id")
            work.author = body.optString("userName", "")
            val previewUrls = body.optJSONObject("urls")
            var thumbnail = if (previewUrls == null || previewUrls.isNull("regular")) "" else previewUrls.optString("regular", "")
            // Restricted metadata can be visible even when Pixiv withholds original image URLs.
            if (!novel && body.optInt("xRestrict", 0) > 0 && thumbnail.isEmpty()) {
                throw IOException("Esta obra es R-18. Pixiv no permite ver sus archivos sin una sesión " +
                    "autorizada y con el contenido R-18 habilitado en la cuenta.")
            }
            if (novel) {
                work.type = "novel"
                thumbnail = body.optString("coverUrl", "")
                val text = body.optString("content", "")
                work.text = text
                if (text.isEmpty()) throw IOException("Pixiv no ha proporcionado el texto de esta novela.")
            } else if (body.optInt("illustType", 0) == 2) {
                work.type = "ugoira"
                val meta = api("/ajax/illust/$id/ugoira_meta")
                work.animationUrl = meta.getString("originalSrc")
                work.frames = meta.getJSONArray("frames")
            } else {
                work.type = "image"
                val pages = apiArray("/ajax/illust/$id/pages")
                for (i in 0 until pages.length()) {
                    val urls = pages.getJSONObject(i).getJSONObject("urls")
                    work.pages.add(urls.getString("original"))
                    val small = urls.optString("small", "")
                    work.pageThumbnails.add(if (small.isEmpty()) urls.optString("regular", "") else small)
                    if (thumbnail.isEmpty()) thumbnail = urls.optString("regular", "")
                }
                if (work.pages.isEmpty()) throw IOException("La obra no tiene páginas disponibles.")
            }
            work.thumbnail = thumbnail
            return work
        }

        private fun api(path: String): JSONObject = response(path).getJSONObject("body")

        private fun apiArray(path: String): JSONArray = response(path).getJSONArray("body")

        private fun response(path: String): JSONObject {
            val connection = open(BASE + path)
            try {
                val code = connection.responseCode
                if (code == 401 || code == 403) throw IOException("Esta obra requiere iniciar sesión o no está disponible para tu cuenta.")
                if (code != 200) throw IOException("Pixiv respondió con HTTP $code.")
                connection.inputStream.use { stream ->
                    val json = JSONObject(String(read(stream), StandardCharsets.UTF_8))
                    if (json.optBoolean("error")) throw IOException(json.optString("message", "Pixiv denegó el acceso."))
                    return json
                }
            } finally {
                connection.disconnect()
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun open(address: String): HttpURLConnection = openChecked(address, null)

        /** Only transfer callers supply a Network; a stale Wi-Fi never falls back to the default route. */
        @JvmStatic
        @Throws(IOException::class)
        fun open(address: String, network: Network): HttpURLConnection = openChecked(address, network)

        private fun openChecked(address: String, network: Network?): HttpURLConnection {
            val url = URL(address)
            val host = url.host.lowercase(Locale.getDefault())
            if (!PixivOrigins.isAllowed(url.protocol, host)) throw IOException("Origen de archivo no permitido.")
            val connection = (if (network == null) url.openConnection() else network.openConnection(url)) as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Referer", REFERER)
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            if (PixivOrigins.acceptsCookies(host)) {
                val cookie = CookieManager.getInstance().getCookie(BASE)
                if (cookie != null) connection.setRequestProperty("Cookie", cookie)
            }
            return connection
        }

        @JvmStatic
        @Throws(IOException::class)
        fun read(stream: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count == -1) break
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        @JvmStatic
        fun extension(address: String): String {
            val path = Uri.parse(address).path
            val name = if (path == null) "" else path.substring(path.lastIndexOf('/') + 1)
            val ext = if (name.contains('.')) name.substring(name.lastIndexOf('.') + 1).lowercase(Locale.getDefault()) else "jpg"
            return if (ext.matches(Regex("jpg|jpeg|png|gif|webp|zip"))) ext else "jpg"
        }
    }
}
