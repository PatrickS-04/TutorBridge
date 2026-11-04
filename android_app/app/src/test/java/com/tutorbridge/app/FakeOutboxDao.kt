package com.tutorbridge.app

import com.tutorbridge.app.data.local.OutboxDao
import com.tutorbridge.app.data.local.OutboxEntity
import com.tutorbridge.app.data.local.OutboxState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory outbox table, standing in for Room in unit tests. */
class FakeOutboxDao : OutboxDao {
    private var nextId = 1L
    val rows = MutableStateFlow<List<OutboxEntity>>(emptyList())

    private fun change(edit: (List<OutboxEntity>) -> List<OutboxEntity>) { rows.value = edit(rows.value) }

    override suspend fun insert(item: OutboxEntity): Long {
        val saved = item.copy(id = nextId++)
        change { it + saved }
        return saved.id
    }

    override suspend fun pending(userId: String) =
        rows.value.filter { it.userId == userId && it.state == OutboxState.PENDING }.sortedBy { it.id }

    override fun observeAll(userId: String): Flow<List<OutboxEntity>> =
        rows.map { all -> all.filter { it.userId == userId }.sortedBy { it.id } }

    override suspend fun delete(id: Long) = change { all -> all.filterNot { it.id == id } }

    override suspend fun recordAttempt(id: Long, error: String) =
        change { all -> all.map { if (it.id == id) it.copy(attempts = it.attempts + 1, lastError = error) else it } }

    override suspend fun markFailed(id: Long, error: String) =
        change { all ->
            all.map { if (it.id == id) it.copy(state = OutboxState.FAILED, attempts = it.attempts + 1, lastError = error) else it }
        }

    override suspend fun retryFailed(userId: String) =
        change { all ->
            all.map {
                if (it.userId == userId && it.state == OutboxState.FAILED) it.copy(state = OutboxState.PENDING, lastError = null) else it
            }
        }

    override suspend fun deletePendingByKey(userId: String, key: String) =
        change { all -> all.filterNot { it.userId == userId && it.dedupeKey == key && it.state == OutboxState.PENDING } }

    override suspend fun clearUser(userId: String) = change { all -> all.filterNot { it.userId == userId } }
}
