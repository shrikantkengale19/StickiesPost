package com.example.stickiespost

import com.example.stickiespost.domain.model.NoteStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteStatusTest {
    @Test
    fun advancesFromOpenToInProgressToClosedAndBackToOpen() {
        assertEquals(NoteStatus.IN_PROGRESS, NoteStatus.OPEN.next())
        assertEquals(NoteStatus.CLOSED, NoteStatus.IN_PROGRESS.next())
        assertEquals(NoteStatus.OPEN, NoteStatus.CLOSED.next())
    }

    @Test
    fun parsesStoredStatusesWithoutCaseSensitivity() {
        assertEquals(NoteStatus.IN_PROGRESS, NoteStatus.fromStorageValue("in_progress"))
    }
}
