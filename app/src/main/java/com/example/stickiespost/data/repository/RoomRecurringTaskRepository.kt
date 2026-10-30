package com.example.stickiespost.data.repository

import com.example.stickiespost.data.local.RecurringTaskDao
import com.example.stickiespost.data.local.RecurringTaskEntity
import com.example.stickiespost.domain.model.RecurringTask
import com.example.stickiespost.domain.repository.RecurringTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRecurringTaskRepository(
    private val taskDao: RecurringTaskDao
) : RecurringTaskRepository {
    override fun observeAll(): Flow<List<RecurringTask>> =
        taskDao.observeAll().map { tasks -> tasks.map(RecurringTaskEntity::toDomain) }

    override suspend fun getById(id: Long): RecurringTask? =
        taskDao.getById(id)?.toDomain()

    override suspend fun getAll(): List<RecurringTask> =
        taskDao.getAll().map(RecurringTaskEntity::toDomain)

    override suspend fun add(task: RecurringTask): RecurringTask {
        val id = taskDao.insert(RecurringTaskEntity.fromDomain(task))
        return task.copy(id = id)
    }

    override suspend fun setFulfilled(id: Long, isFulfilled: Boolean) {
        check(taskDao.setFulfilled(id, isFulfilled) == 1) {
            "Could not find this task to update its status."
        }
    }

    override suspend fun advanceOccurrence(id: Long, nextReminderAtMillis: Long) {
        check(taskDao.advanceOccurrence(id, nextReminderAtMillis) == 1) {
            "Could not find this task to schedule its next occurrence."
        }
    }
}
