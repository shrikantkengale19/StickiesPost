package com.example.stickiespost.domain.repository

import com.example.stickiespost.domain.model.RecurringTask
import kotlinx.coroutines.flow.Flow

interface RecurringTaskRepository {
    fun observeAll(): Flow<List<RecurringTask>>
    suspend fun getById(id: Long): RecurringTask?
    suspend fun getAll(): List<RecurringTask>
    suspend fun add(task: RecurringTask): RecurringTask
    suspend fun setFulfilled(id: Long, isFulfilled: Boolean)
    suspend fun advanceOccurrence(id: Long, nextReminderAtMillis: Long)
}
