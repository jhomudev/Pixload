package com.pixload.app

/**
 * Decides what the end of a download clears on the preview screen. The queue finishes in its own
 * order while only the last finished status is kept, so the decision compares the work that is on
 * screen against the last queued work instead of the job that wrote the status last.
 */
internal object PreviewClear {
    /** The preview belongs to the last queued work, whichever job finished last. */
    @JvmStatic fun preview(queuedId: String?, workId: String?): Boolean =
        !queuedId.isNullOrEmpty() && queuedId == workId

    /** The link box still holds the queued link, so the user did not type another work. */
    @JvmStatic fun link(queuedLink: String?, link: String?): Boolean =
        !queuedLink.isNullOrEmpty() && queuedLink == link
}
