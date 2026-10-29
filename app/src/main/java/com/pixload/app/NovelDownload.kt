package com.pixload.app

import java.io.OutputStream
import org.json.JSONObject

/** Novel job payloads retain their format through serialization; legacy text-only jobs stay TXT. */
internal object NovelDownload {
    fun file(work: PixivClient.Work, epub: Boolean): JSONObject {
        val file = JSONObject().put("name", if (epub) "novela.epub" else "novela.txt")
        if (epub) {
            file.put("epub", true).put("title", work.title ?: "").put("author", work.author ?: "")
                .put("text", work.text ?: "")
        } else {
            file.put("text", work.title + "\n" + work.author + "\n\n" + work.text)
        }
        return file
    }

    fun write(file: JSONObject, output: OutputStream) {
        val text = file.getString("text")
        output.write(if (file.optBoolean("epub", false))
            EpubNovel.bytes(file.optString("title"), file.optString("author"), text)
        else text.toByteArray(Charsets.UTF_8))
    }
}
