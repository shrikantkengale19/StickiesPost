package com.example.stickiespost.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTaskDao {
    @Query("SELECT * FROM recurring_tasks")
    fun observeAll(): Flow<List<RecurringTaskEntity>>

    @Query("SELECT * FROM recurring_tasks WHERE id = :id")
    suspend fun getById(id: Long): RecurringTaskEntity?

    @Query("SELECT * FROM recurring_tasks")
    suspend fun getAll(): List<RecurringTaskEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: RecurringTaskEntity): Long

    @Query("UPDATE recurring_tasks SET isFulfilled = :isFulfilled WHERE id = :id")
    suspend fun setFulfilled(id: Long, isFulfilled: Boolean): Int

    @Query(
        "UPDATE recurring_tasks SET nextReminderAtMillis = :nextReminderAtMillis, " +
            "isFulfilled = 0 WHERE id = :id"
    )
    suspend fun advanceOccurrence(id: Long, nextReminderAtMillis: Long): Int
}
