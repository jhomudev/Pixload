package com.pixload.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri

internal object GalleryShare {
    @JvmStatic fun intent(items: List<GalleryDocuments.Item>): Intent =
        intent(items, "Descarga de Pixload")

    @JvmStatic fun intent(items: List<GalleryDocuments.Item>, label: String): Intent {
        require(items.isNotEmpty()) { "No hay archivos para compartir." }
        val uris = ArrayList<Uri>(items.size)
        var mime = items[0].mime
        if (mime.isNullOrEmpty()) mime = "*/*"
        val clip = ClipData.newRawUri(label, items[0].uri)
        for ((index, item) in items.withIndex()) {
            uris.add(item.uri)
            if (index > 0) {
                clip.addItem(ClipData.Item(item.uri))
                if (mime != item.mime) mime = "*/*"
            }
        }
        return Intent(if (items.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            if (items.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0])
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
