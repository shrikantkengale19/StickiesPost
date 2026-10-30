package com.example.stickiespost.domain.reminder

import com.example.stickiespost.domain.model.NoteItem

interface ReminderScheduler {
    fun schedule(item: NoteItem)
}
