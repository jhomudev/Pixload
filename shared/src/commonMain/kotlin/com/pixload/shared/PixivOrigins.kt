package com.pixload.shared

/** Checks URL components after the platform has parsed the URL and lowercased its host. */
object PixivOrigins {
    fun acceptsCookies(host: String?): Boolean = host == "www.pixiv.net" || host == "pixiv.net"

    fun isAllowed(protocol: String?, host: String?): Boolean =
        "https".equals(protocol, ignoreCase = true) &&
            (acceptsCookies(host) || host == "i.pximg.net")
}
