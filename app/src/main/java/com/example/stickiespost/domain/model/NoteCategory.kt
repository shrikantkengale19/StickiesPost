package com.example.stickiespost.domain.model

enum class NoteCategory(val label: String) {
    ALL("All notes"),
    GROCERIES("Groceries"),
    VEGETABLES("Vegetables"),
    HOUSEHOLD("Household"),
    BEAUTY("Beauty"),
    WORK("Work"),
    ELECTRONICS("Electronics"),
    PERSONAL("Personal"),
    OTHER("Other");

    companion object {
        fun fromModelValue(value: String): NoteCategory =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Gemini returned an unknown note category.")
    }
}
