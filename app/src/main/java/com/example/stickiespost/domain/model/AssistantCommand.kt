package com.example.stickiespost.domain.model

data class AssistantCommand(
    val action: Action,
    val category: NoteCategory,
    val items: List<String> = emptyList(),
    val reminderAtMillis: Long? = null,
    val reply: String = ""
) {
    enum class Action {
        SAVE,
        LIST
    }
}
