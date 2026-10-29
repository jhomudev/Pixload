package com.pixload.app

/** Admission is independent of whether Android considers the Wi-Fi connection metered. */
internal class WifiDownloadPolicy {
    fun admit(onlyWifi: Boolean, activeWifi: Boolean): Boolean = !onlyWifi || activeWifi
}
