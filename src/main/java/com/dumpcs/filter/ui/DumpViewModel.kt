package com.dumpcs.filter.ui

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dumpcs.filter.data.DumperConfig
import com.rodroid.il2cppdumper.utils.DumperBridge
import com.rodroid.il2cppdumper.utils.DumperCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Dump 流程状态 */
enum class DumpState { IDLE, DUMPING, DONE, ERROR }

/**
 * 引擎通过 DumpSink::request_input 向 UI 请求输入时，用此密封类描述输入语义。
 * 三种子类型对应 Rust 引擎的三处调用点：
 *   - DumpAddress         → "Dump base address (hex), or 0 to skip"
 *   - ManualAddresses     → "CodeRegistration (hex)" / "MetadataRegistration (hex)"（两步）
 *   - Architecture        → "Select target architecture (1..=N)"（Fat Mach-O）
 */
sealed class InputRequest {
    data class DumpAddress(val prompt: String, val default: String? = null) : InputRequest()
    data class ManualAddresses(
        val step: Step, // CODE_REG → META_REG
        val prompt: String,
        val default: String? = null
    ) : InputRequest() {
        enum class Step { CODE_REG, META_REG }
    }
    data class Architecture(val prompt: String, val default: String? = null) : InputRequest()
}

class DumpViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("dump_prefs", Context.MODE_PRIVATE)

    private val _binaryPath = MutableStateFlow<String?>(null)
    val binaryPath: StateFlow<String?> = _binaryPath.asStateFlow()

    private val _metadataPath = MutableStateFlow<String?>(null)
    val metadataPath: StateFlow<String?> = _metadataPath.asStateFlow()

    private val _binaryFormat = MutableStateFlow<String?>(null)
    val binaryFormat: StateFlow<String?> = _binaryFormat.asStateFlow()

    private val _metadataFormat = MutableStateFlow<String?>(null)
    val metadataFormat: StateFlow<String?> = _metadataFormat.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _state = MutableStateFlow(DumpState.IDLE)
    val state: StateFlow<DumpState> = _state.asStateFlow()

    private val _outputPath = MutableStateFlow<String?>(null)
    val outputPath: StateFlow<String?> = _outputPath.asStateFlow()

    private val _saveDir = MutableStateFlow("")
    val saveDir: StateFlow<String> = _saveDir.asStateFlow()

    private val _inputRequest = MutableStateFlow<InputRequest?>(null)
    val inputRequest: StateFlow<InputRequest?> = _inputRequest.asStateFlow()

    private val _settingsCollapsed = MutableStateFlow(prefs.getBoolean("settings_collapsed", true))
    val settingsCollapsed: StateFlow<Boolean> = _settingsCollapsed

    private val _hasCachedReg = MutableStateFlow(
        !prefs.getString("code_reg", null).isNullOrBlank() ||
        !prefs.getString("meta_reg", null).isNullOrBlank()
    )
    val hasCachedReg: StateFlow<Boolean> = _hasCachedReg

    /** ManualAddresses 两步流程中的中间变量：保存用户已输入的 CodeReg 值 */
    private var _pendingCodeReg: String? = null
    val pendingCodeReg: String? get() = _pendingCodeReg

    val config = DumperConfig()

    private val logBuffer = mutableListOf<String>()
    private val inputQueue = LinkedBlockingQueue<String>(1)

    init {
        // 恢复上次路径/目录/配置
        val savedBinary = prefs.getString("binary_path", null)
        val savedMetadata = prefs.getString("metadata_path", null)
        val savedDir = prefs.getString("output_dir", null)
        val savedConfig = prefs.getString("config_json", null)
        if (!savedBinary.isNullOrBlank() && File(savedBinary).exists()) _binaryPath.value = savedBinary
        if (!savedMetadata.isNullOrBlank() && File(savedMetadata).exists()) _metadataPath.value = savedMetadata
        _saveDir.value = savedDir ?: defaultSaveDir()
        config.loadDefaults()
        config.restore(savedConfig)
        if (!_binaryPath.value.isNullOrBlank()) detectBinary(_binaryPath.value!!)
        if (!_metadataPath.value.isNullOrBlank()) detectMetadata(_metadataPath.value!!)
        // 日志节流：高频 native 日志合并刷新，避免 UI 卡顿
        viewModelScope.launch {
            while (isActive) {
                delay(120)
                flushLogs()
            }
        }
    }

    /** 清空并重新填充 inputQueue，用于每次 dump 开始时重置 */
    private fun resetInputQueue() {
        inputQueue.clear()
        // 优先使用手动输入的 pendingCodeReg（本次会话），否则用持久化的 savedCodeReg
        val savedCodeReg = prefs.getString("code_reg", null)
        val savedMetaReg = prefs.getString("meta_reg", null)
        if (savedCodeReg != null) {
            _pendingCodeReg = savedCodeReg
            inputQueue.offer(savedCodeReg)
        }
        if (savedMetaReg != null) {
            inputQueue.offer(savedMetaReg)
        }
    }

    private fun flushLogs() {
        synchronized(logBuffer) {
            if (logBuffer.isEmpty()) return
            _logs.value = (_logs.value + logBuffer).takeLast(400)
            logBuffer.clear()
        }
    }

    private fun defaultSaveDir(): String {
        return try {
            val dir = File(Environment.getExternalStorageDirectory(), "DumpCSFilter/dump")
            dir.mkdirs()
            dir.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    fun setSaveDir(path: String) {
        _saveDir.value = path
        try { File(path).mkdirs() } catch (e: Exception) { /* ignore */ }
        prefs.edit().putString("output_dir", path).apply()
    }

    fun detectBinary(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fmt = DumperBridge.INSTANCE.detectFormat(path)
                _binaryFormat.value = fmt
            } catch (e: Throwable) {
                _binaryFormat.value = "格式错误"
            }
        }
    }

    fun detectMetadata(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fmt = DumperBridge.INSTANCE.detectFormat(path)
                _metadataFormat.value = fmt
            } catch (e: Throwable) {
                _metadataFormat.value = "格式错误"
            }
        }
    }

    fun setBinaryPath(path: String) {
        _binaryPath.value = path
        _binaryFormat.value = null
        _state.value = DumpState.IDLE
        _outputPath.value = null
        prefs.edit().putString("binary_path", path).apply()
        addLog("已选择二进制文件：$path")
        detectBinary(path)
        _binaryFormat.value?.let { addLog("文件格式：$it") }
        // 自动在同目录查找 global-metadata.dat
        autoResolveMetadata(path)
    }

    fun setMetadataPath(path: String) {
        _metadataPath.value = path
        _metadataFormat.value = null
        _state.value = DumpState.IDLE
        _outputPath.value = null
        prefs.edit().putString("metadata_path", path).apply()
        addLog("已选择 metadata 文件：$path")
        detectMetadata(path)
        _metadataFormat.value?.let { addLog("文件格式：$it") }
    }

    /** 在 binaryPath 同目录下扫描 global-metadata.dat，找到则自动填入 */
    private fun autoResolveMetadata(binaryPathStr: String) {
        val binDir = File(binaryPathStr).parentFile ?: return
        val candidates = binDir.listFiles { f ->
            f.isFile && f.name.lowercase().contains("global-metadata")
        } ?: emptyArray()
        if (candidates.isEmpty()) return
        val best = candidates.firstOrNull { it.name == "global-metadata.dat" }
            ?: candidates.firstOrNull()
        if (best != null) {
            val existing = _metadataPath.value
            if (existing.isNullOrBlank() || !File(existing).exists()) {
                _metadataPath.value = best.absolutePath
                prefs.edit().putString("metadata_path", best.absolutePath).apply()
                addLog("自动找到 metadata：${best.absolutePath}")
                detectMetadata(best.absolutePath)
            }
        }
    }

    fun toggleSettingsCollapsed() {
        val next = !_settingsCollapsed.value
        _settingsCollapsed.value = next
        prefs.edit().putBoolean("settings_collapsed", next).apply()
    }

    fun addLog(msg: String) {
        synchronized(logBuffer) {
            logBuffer.add(msg)
        }
    }

    fun clearLogs() {
        flushLogs()
        _logs.value = emptyList()
    }

    /** 清除已保存的 CodeRegistration / MetadataRegistration（换游戏时使用） */
    fun clearRegistrationCache() {
        prefs.edit().remove("code_reg").remove("meta_reg").apply()
        _pendingCodeReg = null
        inputQueue.clear()
        _hasCachedReg.value = false
        addLog("已清除已保存的注册地址")
    }

    fun startDump() {
        val binary = _binaryPath.value
        val metadata = _metadataPath.value
        if (binary.isNullOrBlank() || metadata.isNullOrBlank()) {
            addLog("请先选择 libil2cpp.so 和 global-metadata.dat")
            return
        }
        val outDir = _saveDir.value.ifBlank { defaultSaveDir() }
        try { File(outDir).mkdirs() } catch (e: Exception) { /* ignore */ }
        prefs.edit().putString("output_dir", outDir).apply()
        prefs.edit().putString("config_json", config.toJsonString()).apply()

        _state.value = DumpState.DUMPING
        _outputPath.value = null
        resetInputQueue()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val callback = object : DumperCallback {
                    override fun onLog(log: String) {
                        addLog(log)
                    }

                    override fun onRequestInput(request: String): String {
                        when {
                            request.contains("Dump base address") -> {
                                _inputRequest.value = InputRequest.DumpAddress(request, "0")
                            }
                            request.contains("CodeRegistration") -> {
                                _inputRequest.value = InputRequest.ManualAddresses(
                                    InputRequest.ManualAddresses.Step.CODE_REG, request, null
                                )
                            }
                            request.contains("MetadataRegistration") -> {
                                val codeReg = _pendingCodeReg ?: ""
                                _inputRequest.value = InputRequest.ManualAddresses(
                                    InputRequest.ManualAddresses.Step.META_REG, request, null
                                )
                                // 等用户提交后，由 submitInput 回调将 codeReg 一并发送
                                return codeReg // 先返回已保存的 CodeReg，MetaReg 等用户输入
                            }
                            request.contains("Select target architecture") -> {
                                _inputRequest.value = InputRequest.Architecture(request, "1")
                            }
                            else -> {
                                _inputRequest.value = InputRequest.DumpAddress(request, null)
                            }
                        }
                        val answer = inputQueue.poll(60, TimeUnit.SECONDS)
                        _inputRequest.value = null
                        return answer ?: ""
                    }
                }
                val configJson = config.toJsonString()
                addLog("开始 Dump：$binary")
                addLog("配置：$configJson")
                val result = DumperBridge.INSTANCE.dumpIl2Cpp(
                    binary, metadata, outDir, configJson, callback
                )
                flushLogs()
                if (result.isNullOrBlank() || result.startsWith("Error") || result.contains("fail", true)) {
                    _state.value = DumpState.ERROR
                    addLog("Dump 失败：$result")
                } else {
                    _state.value = DumpState.DONE
                    _outputPath.value = result
                    addLog("✔ Dump 完成，输出：$result")
                    val f = File(result)
                    if (f.exists()) addLog("文件大小：${f.length() / 1024} KB")
                }
            } catch (e: Throwable) {
                _state.value = DumpState.ERROR
                addLog("Dump 异常：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 引擎请求输入时由 UI 提交 */
    fun submitInput(text: String) {
        val current = _inputRequest.value
        if (current is InputRequest.ManualAddresses) {
            when (current.step) {
                InputRequest.ManualAddresses.Step.CODE_REG -> {
                    _pendingCodeReg = text
                    // 持久化，下次自动注入
                    prefs.edit().putString("code_reg", text).apply()
                }
                InputRequest.ManualAddresses.Step.META_REG -> {
                    // 持久化 MetadataRegistration
                    prefs.edit().putString("meta_reg", text).apply()
                }
            }
        }
        inputQueue.offer(text)
    }

    fun cancelInput() {
        _pendingCodeReg = null
        inputQueue.offer("")
    }
}