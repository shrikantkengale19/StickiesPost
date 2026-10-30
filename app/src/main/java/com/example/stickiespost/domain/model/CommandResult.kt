package com.example.stickiespost.domain.model

data class CommandResult(
    val action: AssistantCommand.Action,
    val category: NoteCategory,
    val items: List<NoteItem>,
    val message: String
)
