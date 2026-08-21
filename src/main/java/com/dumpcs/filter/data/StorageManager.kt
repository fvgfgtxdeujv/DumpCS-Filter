package com.dumpcs.filter.data

import android.content.Context
import android.os.Environment
import com.dumpcs.filter.data.ClassInfo
import com.dumpcs.filter.data.FilterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StorageManager(private val context: Context) {

    // 公共目录（需"所有文件访问"权限）
    private val publicFolder: File by lazy {
        File(Environment.getExternalStorageDirectory(), "DumpCSFilter")
    }

    // 应用私有外部目录（无需任何权限）
    private val privateFolder: File by lazy {
        File(context.getExternalFilesDir(null), "exports")
    }

    fun ensureFolder(): File {
        // 优先公共目录，无权限时自动降级私有目录
        return if (canWritePublic()) {
            if (!publicFolder.exists()) publicFolder.mkdirs()
            publicFolder
        } else {
            if (!privateFolder.exists()) privateFolder.mkdirs()
            privateFolder
        }
    }

    private fun canWritePublic(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportResult(result: FilterResult): String = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Filter_${timestamp}.txt"
        val targetDir = ensureFolder()
        val file = File(targetDir, fileName)

        file.bufferedWriter().use { writer ->
            writer.write("// DumpCS Filter Result\n")
            writer.write("// Keywords: ${result.keywords.joinToString(", ")}\n")
            writer.write("// Total Classes: ${result.classes.size}\n")
            writer.write("// Timestamp: ${Date(result.timestamp)}\n")
            writer.write("// ================================\n\n")

            result.classes.forEach { cls ->
                writer.write("// Namespace: ${cls.namespace}\n")
                writer.write("public class ${cls.className}\n")
                writer.write("{\n")

                cls.fields.forEach { field ->
                    writer.write("    ${field.rawLine}\n")
                }
                if (cls.fields.isNotEmpty() && cls.methods.isNotEmpty()) {
                    writer.write("\n")
                }

                cls.methods.forEach { method ->
                    writer.write("    ${method.rawLine}\n")
                }

                writer.write("}\n\n")
            }
        }

        file.absolutePath
    }

    fun getExportedFiles(): List<File> {
        val files = mutableListOf<File>()
        try {
            publicFolder.listFiles { f -> f.extension == "txt" }?.let { files.addAll(it) }
        } catch (e: Exception) { /* 无权限时忽略 */ }
        try {
            privateFolder.listFiles { f -> f.extension == "txt" }?.let { files.addAll(it) }
        } catch (e: Exception) { /* ignore */ }
        return files.sortedByDescending { it.lastModified() }
    }

/**
     * 扫描 Download 目录下的 .cs 文件（dump.cs 快捷加载）
     */
    fun scanDownloadCsFiles(): List<File> {
        val dir = File(Environment.getExternalStorageDirectory(), "Download")
        return try {
            dir.listFiles { f ->
                f.isFile && (f.extension.equals("cs", true) || f.name.contains("dump", true))
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun readFileContent(file: File): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            "无法读取文件内容: ${e.message}"
        }
    }

    fun deleteFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }
}
