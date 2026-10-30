package com.example.stickiespost.data.remote

import com.example.stickiespost.data.local.NoteDao
import com.example.stickiespost.data.local.NoteEntity
import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseNoteSync(
    private val supabase: SupabaseClient,
    private val noteDao: NoteDao
) {
    @OptIn(SupabaseExperimental::class)
    suspend fun run() {
        supabase.from(TABLE)
            .selectAsFlow<SupabaseNote, Long>(SupabaseNote::id)
            .collect { remoteNotes ->
                noteDao.replaceSupabaseNotes(remoteNotes.map(SupabaseNote::toEntity))
            }
    }

    @Serializable
    private data class SupabaseNote(
        val id: Long,
        val text: String,
        val category: String,
        @SerialName("created_at_millis") val createdAtMillis: Long,
        @SerialName("reminder_at_millis") val reminderAtMillis: Long? = null,
        val status: String
    ) {
        fun toEntity(): NoteEntity {
            require(id > 0) { "Supabase note IDs must be positive integers." }
            require(NoteCategory.fromModelValue(category) != NoteCategory.ALL) {
                "Supabase notes cannot use the ALL category."
            }
            NoteStatus.fromStorageValue(status)
            return NoteEntity(
                id = -id,
                text = text,
                category = category,
                createdAtMillis = createdAtMillis,
                reminderAtMillis = reminderAtMillis,
                status = status
            )
        }
    }

    private companion object {
        const val TABLE = "note_items"
    }
}
