package com.example.stickiespost.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.model.NoteStatus

@Entity(tableName = "note_items")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val category: String,
    val createdAtMillis: Long,
    val reminderAtMillis: Long?,
    val status: String,
    val remoteId: String? = null,
    val ownerUserId: String? = null
) {
    fun toDomain() = NoteItem(
        id = id,
        text = text,
        category = NoteCategory.fromModelValue(category),
        createdAtMillis = createdAtMillis,
        reminderAtMillis = reminderAtMillis,
        status = NoteStatus.fromStorageValue(status)
    )

    companion object {
        fun fromDomain(
            item: NoteItem,
            remoteId: String? = null,
            ownerUserId: String? = null
        ) = NoteEntity(
            id = item.id,
            text = item.text,
            category = item.category.name,
            createdAtMillis = item.createdAtMillis,
            reminderAtMillis = item.reminderAtMillis,
            status = item.status.name,
            remoteId = remoteId,
            ownerUserId = ownerUserId
        )
    }
}
