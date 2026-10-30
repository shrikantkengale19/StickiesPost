package com.example.stickiespost.domain.model

data class NoteItem(
    val id: Long = 0,
    val text: String,
    val category: NoteCategory,
    val createdAtMillis: Long,
    val reminderAtMillis: Long? = null,
    val status: NoteStatus = NoteStatus.OPEN
)
