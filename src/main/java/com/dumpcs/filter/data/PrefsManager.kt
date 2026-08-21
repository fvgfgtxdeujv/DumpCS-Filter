package com.dumpcs.filter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dumpcs_prefs")

/**
 * 轻量偏好存储：记住上次加载的文件，退出/重启后自动恢复，不用重新找文件
 */
class PrefsManager(private val context: Context) {

    companion object {
        private val KEY_LAST_FILE_PATH = stringPreferencesKey("last_file_path")
        private val KEY_LAST_FILE_NAME = stringPreferencesKey("last_file_name")
    }

    val lastFilePath: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_FILE_PATH]
    }

    val lastFileName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_FILE_NAME]
    }

    suspend fun saveLastFile(path: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_FILE_PATH] = path
            prefs[KEY_LAST_FILE_NAME] = name
        }
    }

    suspend fun clearLastFile() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_LAST_FILE_PATH)
            prefs.remove(KEY_LAST_FILE_NAME)
        }
    }
}