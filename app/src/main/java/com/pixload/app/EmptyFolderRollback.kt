package com.pixload.app

/** A failed first file may remove only the new, still-empty grouped folder. */
internal object EmptyFolderRollback {
    fun run(grouped: Boolean, completedFile: Boolean, isEmpty: () -> Boolean, delete: () -> Boolean) {
        if (grouped && !completedFile && isEmpty()) delete()
    }
}
