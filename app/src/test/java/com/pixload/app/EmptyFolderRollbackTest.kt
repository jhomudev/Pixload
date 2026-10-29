package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyFolderRollbackTest {
    @Test fun failedFirstFileRemovesOnlyANewEmptyGroupedFolder() {
        var checked = 0
        var deleted = 0
        val empty = { checked++; true }
        val remove = { deleted++; true }
        EmptyFolderRollback.run(grouped = true, completedFile = false, isEmpty = empty, delete = remove)
        assertEquals(1, checked)
        assertEquals(1, deleted)
    }

    @Test fun completedPagesAndNonemptyOrFlatFoldersStayUntouched() {
        var checked = 0
        var deleted = 0
        val nonempty = { checked++; false }
        val remove = { deleted++; true }
        EmptyFolderRollback.run(grouped = true, completedFile = true, isEmpty = nonempty, delete = remove)
        EmptyFolderRollback.run(grouped = false, completedFile = false, isEmpty = nonempty, delete = remove)
        assertEquals(0, checked)
        EmptyFolderRollback.run(grouped = true, completedFile = false, isEmpty = nonempty, delete = remove)
        assertEquals(1, checked)
        assertEquals(0, deleted)
    }
}
