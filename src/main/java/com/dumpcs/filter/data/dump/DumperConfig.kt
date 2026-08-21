package com.dumpcs.filter.data.dump

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.rodroid.il2cppdumper.utils.DumperBridge
import org.json.JSONObject

/**
 * Il2CppDumper 配置（与原版 DumperConfig 对齐）
 * 用 JSONObject 承载，默认值来自引擎 getDefaultConfig()，保证引擎行为一致
 *
 * 响应式：任何 set* 都会自增 version，触发 Compose 重组；
 * 所有 get* 先读取 version 建立依赖，保证 UI 读到最新值。
 */
class DumperConfig {

    var json: JSONObject = JSONObject()
        private set

    private var version by mutableIntStateOf(0)

    private fun bump() { version++ }

    /** 从 native 引擎加载默认配置 */
    fun loadDefaults() {
        val newJson = try {
            val raw = DumperBridge.INSTANCE.getDefaultConfig()
            if (!raw.isNullOrBlank()) JSONObject(raw) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        // 兜底关键开关（引擎没给默认时）
        fun ensureBool(key: String, def: Boolean) { if (!newJson.has(key)) newJson.put(key, def) }
        fun ensureInt(key: String, def: Int) { if (!newJson.has(key)) newJson.put(key, def) }
        fun ensureDouble(key: String, def: Double) { if (!newJson.has(key)) newJson.put(key, def) }
        fun ensureString(key: String, def: String) { if (!newJson.has(key)) newJson.put(key, def) }
        ensureBool("dumpMethod", true)
        ensureBool("dumpField", true)
        ensureBool("dumpProperty", true)
        ensureBool("dumpAttribute", true)
        ensureBool("dumpMethodOffset", true)
        ensureBool("dumpFieldOffset", true)
        ensureBool("dumpTypeDefIndex", true)
        ensureBool("dumpAssemblyName", false)
        ensureBool("generateStruct", false)
        ensureBool("generateDummyDll", false)
        ensureBool("dummyDllAddToken", true)
        ensureBool("forceIl2cppVersion", false)
        ensureDouble("forceVersion", 0.0)
        ensureBool("forceDump", false)
        ensureBool("noRedirectedPointer", false)
        ensureBool("splitDumpPerType", false)
        ensureBool("generateGenericsDump", false)
        ensureBool("dumpDisassembly", false)
        ensureInt("dumpDisassemblyTarget", 0)
        ensureInt("maxDisassemblyInstructions", 300)
        ensureBool("generateCppScaffold", false)
        ensureBool("mangleNames", true)
        ensureBool("enhancedIdaMetadata", false)
        ensureBool("generateUnityHeaders", false)
        ensureString("compilerLayout", "")
        ensureBool("useTopologicalSort", false)
        ensureBool("codm", false)
        json = newJson
        bump()
    }

    fun getBool(key: String): Boolean { version; return json.optBoolean(key, false) }
    fun setBool(key: String, v: Boolean) { json.put(key, v); bump() }
    fun getInt(key: String): Int { version; return json.optInt(key, 0) }
    fun setInt(key: String, v: Int) { json.put(key, v); bump() }
    fun getDouble(key: String): Double { version; return json.optDouble(key, 0.0) }
    fun setDouble(key: String, v: Double) { json.put(key, v); bump() }
    fun getString(key: String): String { version; return json.optString(key, "") }
    fun setString(key: String, v: String) { json.put(key, v); bump() }

    fun toJsonString(): String = json.toString()

    /** 从持久化 JSON 恢复配置（触发重组） */
    fun restore(jsonStr: String?) {
        if (jsonStr.isNullOrBlank()) return
        try {
            json = JSONObject(jsonStr)
            bump()
        } catch (e: Exception) { /* 用默认 */ }
    }
}