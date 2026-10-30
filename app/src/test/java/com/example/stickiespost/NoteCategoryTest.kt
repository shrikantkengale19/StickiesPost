package com.example.stickiespost

import com.example.stickiespost.domain.model.NoteCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCategoryTest {
    @Test
    fun parsesNewHouseholdCategoriesFromInterpreterValues() {
        assertEquals(NoteCategory.BEAUTY, NoteCategory.fromModelValue("BEAUTY"))
        assertEquals(NoteCategory.VEGETABLES, NoteCategory.fromModelValue("VEGETABLES"))
        assertEquals(NoteCategory.HOUSEHOLD, NoteCategory.fromModelValue("HOUSEHOLD"))
    }
}
