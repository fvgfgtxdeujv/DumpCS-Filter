package com.dumpcs.filter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM filter_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM filter_history ORDER BY timestamp DESC LIMIT 100")
    fun getRecent(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entity: HistoryEntity)

    @Query("DELETE FROM filter_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM filter_history")
    suspend fun deleteAll()
}
