package com.aicode.feature.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aicode.feature.agent.data.local.entity.TodoItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TodoItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TodoItemEntity>)

    @Query("SELECT * FROM todo_items WHERE sessionId = :sessionId ORDER BY `order` ASC, priority DESC")
    fun getBySession(sessionId: String): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE sessionId = :sessionId ORDER BY `order` ASC, priority DESC")
    suspend fun getBySessionOnce(sessionId: String): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items")
    suspend fun getAllOnce(): List<TodoItemEntity>

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM todo_items WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("UPDATE todo_items SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("SELECT MAX(`order`) FROM todo_items WHERE sessionId = :sessionId")
    suspend fun getMaxOrder(sessionId: String): Int?
}
