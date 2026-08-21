package com.dumpcs.filter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dumpcs.filter.data.ScriptTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 脚本工具 ViewModel：后台生成（IO 线程）+ 输入/结果持久化。
 * 生成过程在后台协程执行，切换页面/操作其他东西不中断、不丢失，
 * 重新进入自动恢复上次输入与生成结果，无需重新输入路径。
 */
class ScriptViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("script_prefs", android.content.Context.MODE_PRIVATE)

    data class GenResult(val tool: String, val fileName: String, val path: String, val size: Long)

    private val _results = MutableStateFlow<List<GenResult>>(emptyList())
    val results: StateFlow<List<GenResult>> = _results.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastMessage = MutableStateFlow("")
    val lastMessage: StateFlow<String> = _lastMessage.asStateFlow()

    init {
        // 恢复上次生成结果（仅提示）
        val saved = prefs.getString("last_results", null)
        if (!saved.isNullOrBlank()) {
            _results.value = saved.split("\n").mapNotNull { line ->
                val p = line.split("|")
                if (p.size >= 4) GenResult(p[0], p[1], p[2], p[3].toLongOrNull() ?: 0L) else null
            }
        }
    }

    fun getStr(key: String, def: String): String = prefs.getString(key, def) ?: def
    fun setStr(key: String, v: String) { prefs.edit().putString(key, v).apply() }
    fun getBool(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
    fun setBool(key: String, v: Boolean) { prefs.edit().putBoolean(key, v).apply() }
    fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)
    fun setInt(key: String, v: Int) { prefs.edit().putInt(key, v).apply() }

    private fun addResult(r: GenResult) {
        _results.value = listOf(r) + _results.value.take(19)
        prefs.edit().putString("last_results", _results.value.joinToString("\n") { "${it.tool}|${it.fileName}|${it.path}|${it.size}" }).apply()
    }

    private fun saveScript(fileName: String, content: String): File? {
        return try {
            val root = File(android.os.Environment.getExternalStorageDirectory(), "DumpCSFilter")
            val dir = File(root, "lua功能")
            if (!dir.mkdirs() && !dir.exists()) return null
            val f = File(dir, fileName)
            f.writeText(content)
            f
        } catch (e: Exception) {
            try {
                val f = File(getApplication<Application>().cacheDir, fileName)
                f.writeText(content)
                f
            } catch (e2: Exception) { null }
        }
    }

    fun generateBase(module: String, chain: String, type: String, value: String, is64: Boolean, fileName: String) {
        viewModelScope.launch {
            _busy.value = true
            _lastMessage.value = "正在生成 $fileName ..."
            val script = withContext(Dispatchers.IO) { ScriptTools.genBaseScript(module, chain, type, value, is64) }
            val f = withContext(Dispatchers.IO) { saveScript(fileName, script) }
            if (f != null) {
                addResult(GenResult("基址写法", fileName, f.absolutePath, f.length()))
                _lastMessage.value = "✓ 已生成：${f.absolutePath}（${f.length()} 字节）"
            } else {
                _lastMessage.value = "✗ 保存失败（请检查存储权限）"
            }
            _busy.value = false
        }
    }

    fun generateFeature(dumpPath: String) {
        viewModelScope.launch {
            _busy.value = true
            _lastMessage.value = "正在解析 dump.cs 并生成功能脚本 ..."
            val result = withContext(Dispatchers.IO) {
                try {
                    val f = File(dumpPath)
                    if (!f.exists()) "✗ 文件不存在：$dumpPath"
                    else if (!f.canRead()) "✗ 无读取权限：$dumpPath"
                    else {
                        val map = f.bufferedReader(Charsets.UTF_8).use { ScriptTools.parseDumpCs(it) }
                        val found = map.size
                        val offsets = ScriptTools.targetMethods
                            .filter { it != "GetCurrentWeaponKillEffect" }
                            .map { map[it] ?: "0x0" }
                        val kill = map["GetCurrentWeaponKillEffect"]
                        saveScript("wuhai功能脚本.lua", ScriptTools.genFeatureScript(offsets, kill))
                        if (kill != null) saveScript("wuhai击杀特效.lua", ScriptTools.genKillScript(kill))
                        _results.value = listOfNotNull(
                            GenResult("功能生成", "wuhai功能脚本.lua", "${android.os.Environment.getExternalStorageDirectory()}/DumpCSFilter/lua功能/wuhai功能脚本.lua", 0),
                            kill?.let { GenResult("功能生成", "wuhai击杀特效.lua", "${android.os.Environment.getExternalStorageDirectory()}/DumpCSFilter/lua功能/wuhai击杀特效.lua", 0) }
                        ) + _results.value
                        "✓ 功能脚本已生成！找到 $found/10 个目标方法"
                    }
                } catch (e: Exception) { "✗ 解析失败：${e.message}" }
            }
            _lastMessage.value = result
            _busy.value = false
        }
    }

    /** 解析 dump.cs（供 UI 展示提取结果），后台执行 */
    fun parseDump(dumpPath: String, onDone: (Map<String, String>, String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _lastMessage.value = "正在解析 dump.cs（大文件可能需要几秒）..."
            val result = withContext(Dispatchers.IO) {
                try {
                    val f = File(dumpPath)
                    if (!f.exists()) emptyMap<String, String>() to "文件不存在：$dumpPath"
                    else if (!f.canRead()) emptyMap<String, String>() to "无读取权限：$dumpPath"
                    else {
                        val map = f.bufferedReader(Charsets.UTF_8).use { ScriptTools.parseDumpCs(it) }
                        val missing = ScriptTools.targetMethods.filter { !map.containsKey(it) }
                        val msg = if (missing.isEmpty()) "全部方法已找到" else "未找到：" + missing.joinToString(", ")
                        map to "找到 ${map.size}/10 个方法。$msg"
                    }
                } catch (e: Exception) { emptyMap<String, String>() to "解析失败：${e.message}" }
            }
            _lastMessage.value = "✓ 解析完成"
            _busy.value = false
            onDone(result.first, result.second)
        }
    }

    fun generateMenu(layers: Int, funcs: Int, mode: Int, fileName: String) {
        viewModelScope.launch {
            _busy.value = true
            _lastMessage.value = "正在生成菜单脚本 ..."
            val script = withContext(Dispatchers.IO) { ScriptTools.genMenuScript(layers, funcs, mode) }
            val f = withContext(Dispatchers.IO) { saveScript(fileName, script) }
            if (f != null) {
                addResult(GenResult("菜单生成", fileName, f.absolutePath, f.length()))
                _lastMessage.value = "✓ 已生成：${f.absolutePath}（${f.length()} 字节）"
            } else _lastMessage.value = "✗ 保存失败"
            _busy.value = false
        }
    }

    fun generateStatic(itemsText: String, newValue: String, freeze: Boolean, fileName: String) {
        viewModelScope.launch {
            _busy.value = true
            _lastMessage.value = "正在生成静态基址脚本 ..."
            val items = withContext(Dispatchers.IO) {
                itemsText.lines().mapNotNull { line ->
                    val parts = line.trim().split("|")
                    if (parts.size >= 4) {
                        ScriptTools.StaticItem(
                            module = parts[0].trim(),
                            offset = parts[1].trim().removePrefix("0x").toLongOrNull(16) ?: return@mapNotNull null,
                            flags = parts[2].trim().toIntOrNull() ?: 4,
                            value = parts[3].trim()
                        )
                    } else null
                }
            }
            if (items.isEmpty()) {
                _lastMessage.value = "✗ 地址列表为空或格式错误"
                _busy.value = false
                return@launch
            }
            val script = withContext(Dispatchers.IO) { ScriptTools.genStaticBaseScript(items, newValue.ifBlank { null }, freeze) }
            val f = withContext(Dispatchers.IO) { saveScript(fileName, script) }
            if (f != null) {
                addResult(GenResult("静态基址", fileName, f.absolutePath, f.length()))
                _lastMessage.value = "✓ 已生成：${f.absolutePath}（${f.length()} 字节）"
            } else _lastMessage.value = "✗ 保存失败"
            _busy.value = false
        }
    }

    fun generateOffsetPtr(
        name: String, mainValue: String, mainFlags: Int, mainRange: Int,
        subText: String, modText: String, mode: String, fileName: String
    ) {
        viewModelScope.launch {
            _busy.value = true
            _lastMessage.value = "正在生成偏移指针脚本 ..."
            val subs = withContext(Dispatchers.IO) {
                subText.lines().mapNotNull { l ->
                    val p = l.trim().split("|")
                    if (p.size >= 3) ScriptTools.Feature(p[0].trim(), p[1].trim().toIntOrNull() ?: 4, p[2].trim().toLongOrNull() ?: 0L) else null
                }
            }
            val mods = withContext(Dispatchers.IO) {
                modText.lines().mapNotNull { l ->
                    val p = l.trim().split("|")
                    if (p.size >= 4) ScriptTools.ModifyItem(p[0].trim(), p[2].trim().toLongOrNull() ?: 0L, p[1].trim().toIntOrNull() ?: 4, p[3].trim() == "true") else null
                }
            }
            if (mods.isEmpty()) {
                _lastMessage.value = "✗ 修改项不能为空"
                _busy.value = false
                return@launch
            }
            val script = withContext(Dispatchers.IO) {
                ScriptTools.genOffsetPtrScript(name, mainValue, mainFlags, mainRange, subs, mods, mode)
            }
            val f = withContext(Dispatchers.IO) { saveScript(fileName, script) }
            if (f != null) {
                addResult(GenResult("偏移指针", fileName, f.absolutePath, f.length()))
                _lastMessage.value = "✓ 已生成：${f.absolutePath}（${f.length()} 字节）"
            } else _lastMessage.value = "✗ 保存失败"
            _busy.value = false
        }
    }

    fun cleanComments(filePath: String) {
        viewModelScope.launch {
            _busy.value = true
            _lastMessage.value = "正在清理注释 ..."
            val r = withContext(Dispatchers.IO) {
                try {
                    val f = File(filePath)
                    if (!f.exists()) null
                    else {
                        val (backup, cleaned) = ScriptTools.backupAndClean(filePath)
                        "✓ 完成！注释已删除\n备份: $backup\n清理后大小: ${cleaned.length} 字符"
                    }
                } catch (e: Exception) { "✗ 失败：${e.message}" }
            }
            _lastMessage.value = r ?: "✗ 文件不存在"
            _busy.value = false
        }
    }

    fun clearResults() {
        _results.value = emptyList()
        prefs.edit().remove("last_results").apply()
    }
}