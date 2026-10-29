package com.pixload.app

/** Multi-page originals create a folder; reject unavailable files before creating it. */
internal object RetryFilePreflight {
    fun requiresProbe(type: String, originalPageCount: Int): Boolean =
        type == "image" && originalPageCount >= 2
}
