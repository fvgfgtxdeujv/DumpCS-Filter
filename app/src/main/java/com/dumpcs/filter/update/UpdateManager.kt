package com.dumpcs.filter.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** 版本信息 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String = "",
    val forceUpdate: Boolean = false
) {
    val isUpdateAvailable: Boolean get() = versionCode > getCurrentVersionCode()
}

/** 更新状态 */
enum class UpdateStatus { IDLE, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, DOWNLOADING, DOWNLOAD_COMPLETE, DOWNLOAD_FAILED, INSTALLATION_READY }

class UpdateManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val MIN_CHECK_INTERVAL_MS = 6 * 3600 * 1000L
        private const val DEFAULT_CHECK_URL = "https://raw.githubusercontent.com/fvgfgtxdeujv/DumpCS-Filter/main/update.json"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private var currentDownloadId: Long? = null
    private var _status = UpdateStatus.IDLE
    val status: UpdateStatus get() = _status

    private var _updateInfo: UpdateInfo? = null
    val updateInfo: UpdateInfo? get() = _updateInfo

    private var _downloadProgress = 0f
    val downloadProgress: Float get() = _downloadProgress

    private var savedApkPath: String? = null

    fun setCurrentVersion(versionCode: Int, versionName: String) {
        prefs.edit()
            .putInt("current_version_code", versionCode)
            .putString("current_version_name", versionName)
            .apply()
    }

    private fun getCurrentVersionCode(): Int = prefs.getInt("current_version_code", 0)

    suspend fun checkUpdate(forceCheck: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!forceCheck) {
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
            if (System.currentTimeMillis() - lastCheck < MIN_CHECK_INTERVAL_MS) return@withContext null
        }
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
        doCheckUpdate(DEFAULT_CHECK_URL)
    }

    private suspend fun doCheckUpdate(url: String): UpdateInfo? = withContext(Dispatchers.IO) {
        _status = UpdateStatus.CHECKING
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                _status = UpdateStatus.IDLE
                return@withContext null
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val jsonStr = buildString {
                var c = reader.read()
                while (c >= 0) { append(c.toChar()); c = reader.read() }
            }
            reader.close()
            conn.disconnect()
            val json = JSONObject(jsonStr)
            val info = UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                downloadUrl = json.getString("downloadUrl"),
                changelog = json.optString("changelog", ""),
                forceUpdate = json.optBoolean("forceUpdate", false)
            )
            _updateInfo = info
            _status = if (info.isUpdateAvailable) UpdateStatus.UPDATE_AVAILABLE else UpdateStatus.UP_TO_DATE
            info
        } catch (e: Exception) {
            _status = UpdateStatus.IDLE
            null
        }
    }

    fun startDownload(): Boolean {
        val info = _updateInfo ?: return false
        _status = UpdateStatus.DOWNLOADING
        val dm = ContextCompat.getSystemService(context, DownloadManager::class.java) ?: return false
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("DumpCS Filter 更新")
            .setDescription("正在下载最新版本 ${info.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "DumpCSFilter-${info.versionName}.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        currentDownloadId = dm.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, currentDownloadId!!).apply()
        savedApkPath = null
        return true
    }

    fun queryDownloadProgress(): Float? {
        val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId < 0) return null
        val dm = ContextCompat.getSystemService(context, DownloadManager::class.java) ?: return null
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query) ?: return null
        if (!cursor.moveToFirst()) { cursor.close(); return null }
        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val progressCol = cursor.getColumnIndex(DownloadManager.COLUMN_PROGRESS)
        val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
        val status = cursor.getInt(statusCol)
        when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val uri = dm.getUriForDownloadedFile(downloadId)
                if (uri != null) {
                    val srcFile = File(uri.path ?: "")
                    val destFile = File(context.cacheDir, "latest_update.apk")
                    srcFile.copyTo(destFile, overwrite = true)
                    savedApkPath = destFile.absolutePath
                }
                _status = UpdateStatus.DOWNLOAD_COMPLETE
                cursor.close()
                return 1f
            }
            DownloadManager.STATUS_FAILED -> {
                _status = UpdateStatus.DOWNLOAD_FAILED
                cursor.close()
                currentDownloadId = null
                prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                return null
            }
            DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                val total = cursor.getLong(totalCol)
                val progress = cursor.getLong(progressCol)
                cursor.close()
                if (total <= 0) return null
                _downloadProgress = progress.toFloat() / total
                return _downloadProgress
            }
        }
        cursor.close()
        return null
    }

    fun installApk() {
        val apkPath = savedApkPath ?: return
        val file = File(apkPath)
        if (!file.exists()) return
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _status = UpdateStatus.INSTALLATION_READY
    }

    fun cancelDownload() {
        val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId > 0) {
            ContextCompat.getSystemService(context, DownloadManager::class.java)?.remove(downloadId)
        }
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        savedApkPath = null
        _status = UpdateStatus.IDLE
        _downloadProgress = 0f
    }

    fun resetDismiss() {
        if (_status == UpdateStatus.UPDATE_AVAILABLE || _status == UpdateStatus.DOWNLOAD_COMPLETE) {
            // 保持状态，不重置
        }
    }
}
