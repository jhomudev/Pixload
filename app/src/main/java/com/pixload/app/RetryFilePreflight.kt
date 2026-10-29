package com.pixload.app

/** Multi-page originals create a folder; reject unavailable files before creating it. */
internal object RetryFilePreflight {
    fun requiresProbe(type: String, originalPageCount: Int, onlyWifi: Boolean): Boolean =
        !onlyWifi && type == "image" && originalPageCount >= 2
}
