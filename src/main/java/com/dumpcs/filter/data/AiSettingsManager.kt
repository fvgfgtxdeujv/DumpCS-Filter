package com.dumpcs.filter.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aiDataStore by preferencesDataStore(name = "ai_settings")

/**
 * AI 设置管理（DataStore 持久化）：
 * - 打字机输出速度 / 回复详细度 / 反馈结果开关（输出率、输中率、反馈结果等）
 * - 自定义第三方 AI 中转站配置（Base URL + API Key + 模型名）
 */
class AiSettingsManager(private val context: Context) {

    companion object {
        private val KEY_TYPING_SPEED_MS = intPreferencesKey("typing_speed_ms")
        private val KEY_REPLY_DETAIL = stringPreferencesKey("reply_detail") // brief / standard / detailed
        private val KEY_SHOW_FEEDBACK = booleanPreferencesKey("show_feedback")
        private val KEY_API_BASE_URL = stringPreferencesKey("api_base_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL_NAME = stringPreferencesKey("model_name")
    }

    val typingSpeedMs: Flow<Int> = context.aiDataStore.data.map { it[KEY_TYPING_SPEED_MS] ?: 18 }
    val replyDetail: Flow<String> = context.aiDataStore.data.map { it[KEY_REPLY_DETAIL] ?: "standard" }
    val showFeedback: Flow<Boolean> = context.aiDataStore.data.map { it[KEY_SHOW_FEEDBACK] ?: true }
    val apiBaseUrl: Flow<String> = context.aiDataStore.data.map { it[KEY_API_BASE_URL] ?: "" }
    val apiKey: Flow<String> = context.aiDataStore.data.map { it[KEY_API_KEY] ?: "" }
    val modelName: Flow<String> = context.aiDataStore.data.map { it[KEY_MODEL_NAME] ?: "gpt-3.5-turbo" }

    suspend fun setTypingSpeedMs(v: Int) {
        context.aiDataStore.edit { it[KEY_TYPING_SPEED_MS] = v.coerceIn(4, 60) }
    }

    suspend fun setReplyDetail(v: String) {
        context.aiDataStore.edit { it[KEY_REPLY_DETAIL] = v }
    }

    suspend fun setShowFeedback(v: Boolean) {
        context.aiDataStore.edit { it[KEY_SHOW_FEEDBACK] = v }
    }

    suspend fun setApiConfig(baseUrl: String, apiKey: String, model: String) {
        context.aiDataStore.edit {
            it[KEY_API_BASE_URL] = baseUrl.trim()
            it[KEY_API_KEY] = apiKey.trim()
            it[KEY_MODEL_NAME] = model.trim().ifBlank { "gpt-3.5-turbo" }
        }
    }
}
