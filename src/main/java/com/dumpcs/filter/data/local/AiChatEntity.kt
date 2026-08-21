package com.dumpcs.filter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 解释聊天记录（用户 ↔ 猫娘 AI 的对话气泡历史）
 */
@Entity(tableName = "ai_chat")
data class AiChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val className: String,
    val userMsg: String,
    val aiMsg: String,
    val timestamp: Long = System.currentTimeMillis()
)
