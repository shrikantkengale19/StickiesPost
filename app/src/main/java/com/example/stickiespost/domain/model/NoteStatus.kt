package com.example.stickiespost.domain.model

enum class NoteStatus(val label: String) {
    OPEN("Open"),
    IN_PROGRESS("In progress"),
    CLOSED("Closed");

    fun next(): NoteStatus = when (this) {
        OPEN -> IN_PROGRESS
        IN_PROGRESS -> CLOSED
        CLOSED -> OPEN
    }

    companion object {
        fun fromStorageValue(value: String): NoteStatus =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("The saved note has an unknown status.")
    }
}
