package com.dumpcs.filter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "filter_history")
data class HistoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val keywords: String,
    val resultCount: Int,
    val outputPath: String,
    val timestamp: Long = System.currentTimeMillis()
)
