package com.pixload.app

import com.pixload.shared.PixivLinks

internal object PixivLink {
    @JvmStatic fun extract(text: String?): String? = PixivLinks.extract(text)
}
