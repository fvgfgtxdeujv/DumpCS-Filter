package com.dumpcs.filter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {
    @Query("SELECT * FROM ai_chat ORDER BY timestamp ASC")
    fun getAll(): Flow<List<AiChatEntity>>

    @Query("SELECT * FROM ai_chat WHERE className = :className ORDER BY timestamp ASC")
    fun getByClass(className: String): Flow<List<AiChatEntity>>

    @Insert
    suspend fun insert(entity: AiChatEntity)

    @Query("DELETE FROM ai_chat")
    suspend fun deleteAll()
}
