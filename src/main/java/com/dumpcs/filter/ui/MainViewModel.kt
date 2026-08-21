package com.dumpcs.filter.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dumpcs.filter.data.AiKeywordEngine
import com.dumpcs.filter.data.AiExplainer
import com.dumpcs.filter.data.LlmClient
import com.dumpcs.filter.data.FilterEngine
import com.dumpcs.filter.data.AiChatEntity
import com.dumpcs.filter.data.AppDatabase
import com.dumpcs.filter.data.HistoryEntity
import com.dumpcs.filter.data.ClassInfo
import com.dumpcs.filter.data.FilterResult
import com.dumpcs.filter.data.DumpCSParser
import com.dumpcs.filter.data.AiSettingsManager
import com.dumpcs.filter.data.PrefsManager
import com.dumpcs.filter.data.StorageManager
import com.dumpcs.filter.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val parser = DumpCSParser()
    private val engine = FilterEngine()
    private val aiEngine = AiKeywordEngine()
    private val explainer = AiExplainer()
    private val storage = StorageManager(application)
    private val prefs = PrefsManager(application)
    val updateManager = UpdateManager(application).also { it.setCurrentVersion(2, "1.1.1") }
    private val aiSettings = AiSettingsManager(application)
    private val llmClient = LlmClient()
    private val historyDao = AppDatabase.getDatabase(application).historyDao()
    private val aiChatDao = AppDatabase.getDatabase(application).aiChatDao()

    // AI 设置（打字机速度 / 回复详细度 / 反馈开关 / 第三方 API 配置）
    val typingSpeedMs: StateFlow<Int> = aiSettings.typingSpeedMs.stateIn(viewModelScope, SharingStarted.Eagerly, 18)
    val replyDetail: StateFlow<String> = aiSettings.replyDetail.stateIn(viewModelScope, SharingStarted.Eagerly, "standard")
    val showFeedback: StateFlow<Boolean> = aiSettings.showFeedback.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val apiBaseUrl: StateFlow<String> = aiSettings.apiBaseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val apiKey: StateFlow<String> = aiSettings.apiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val modelName: StateFlow<String> = aiSettings.modelName.stateIn(viewModelScope, SharingStarted.Eagerly, "gpt-3.5-turbo")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val history: StateFlow<List<HistoryEntity>> = _history.asStateFlow()

    private val _exportedFiles = MutableStateFlow<List<File>>(emptyList())
    val exportedFiles: StateFlow<List<File>> = _exportedFiles.asStateFlow()

    private val _aiChats = MutableStateFlow<List<AiChatEntity>>(emptyList())
    val aiChats: StateFlow<List<AiChatEntity>> = _aiChats.asStateFlow()

    private val _currentExplainClass = MutableStateFlow<ClassInfo?>(null)
    val currentExplainClass: StateFlow<ClassInfo?> = _currentExplainClass.asStateFlow()

    // 待确认恢复的文件（进入应用后弹窗询问，避免每次自动加载）
    private val _pendingRestoreFile = MutableStateFlow<String?>(null)
    val pendingRestoreFile: StateFlow<String?> = _pendingRestoreFile.asStateFlow()

    // 已播完打字机动画的消息 id（退出页面再进入时不重播，内容不会"被撤回"）
    private val _playedAnimIds = MutableStateFlow<Set<Long>>(emptySet())
    val playedAnimIds: StateFlow<Set<Long>> = _playedAnimIds.asStateFlow()

    private var allClasses: List<ClassInfo> = emptyList()

    // 对话式筛选记忆：记住上次筛选结果与意图，支持"只要热透/去掉设置"这类细化指令
    private var lastFilterClasses: List<ClassInfo>? = null
    private var chatMemoryLabel: String? = null

    init {
        viewModelScope.launch {
            historyDao.getRecent().collect { _history.value = it }
        }
        viewModelScope.launch {
            aiChatDao.getAll().collect { _aiChats.value = it }
        }
        loadExportedFiles()
        // 自动恢复上次加载的文件（退出/重启后不用重新找）
        viewModelScope.launch {
            // 检测上次读取的文件：不自动加载，交给 UI 弹窗询问（是→加载，否→留在首页重新选）
            val lastPath = prefs.lastFilePath.first()
            if (!lastPath.isNullOrBlank() && !uiState.value.isFileLoaded) {
                val f = File(lastPath)
                if (f.exists()) {
                    _pendingRestoreFile.value = lastPath
                }
            }
        }
    }

    /** 用户确认是否恢复上次文件 */
    fun confirmRestore(load: Boolean) {
        val path = _pendingRestoreFile.value ?: return
        _pendingRestoreFile.value = null
        if (load) {
            loadFileByPath(path)
        }
    }

    fun loadFile(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _progress.value = 0f

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = uri.lastPathSegment ?: "unknown"

                inputStream?.use { stream ->
                    // 解析放 IO 线程，不卡 UI
                    allClasses = withContext(Dispatchers.IO) {
                        parser.parse(stream) { p ->
                            _progress.value = p
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    fileName = fileName,
                    totalClasses = allClasses.size,
                    isFileLoaded = true,
                    error = null
                )
                // file:// 方案可直接恢复；SAF(content://) 无法还原路径则跳过
                if (uri.scheme == "file") {
                    uri.path?.let { p -> prefs.saveLastFile(p, fileName) }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "解析失败: ${e.message}")
            } finally {
                _isLoading.value = false
                _progress.value = 1f
            }
        }
    }

    fun filterClasses(keywords: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = _uiState.value.copy(error = null)
            try {
                // CPU 密集型过滤放 Default 线程池 + 并行分块（多核加速）
                val result = withContext(Dispatchers.Default) {
                    engine.filterParallel(allClasses, keywords)
                }

                // 文件导出已在 IO 线程
                val outputPath = storage.exportResult(result)

                historyDao.insert(
                    HistoryEntity(
                        fileName = _uiState.value.fileName,
                        keywords = keywords.joinToString(", "),
                        resultCount = result.classes.size,
                        outputPath = outputPath
                    )
                )

                _uiState.value = _uiState.value.copy(
                    filterResult = result,
                    lastOutputPath = outputPath,
                    error = null
                )

                loadExportedFiles()
            } catch (e: Exception) {
                // 任何异常都不闪退，转成界面提示
                _uiState.value = _uiState.value.copy(error = "筛选失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadExportedFiles() {
        _exportedFiles.value = storage.getExportedFiles()
    }

    fun deleteFile(file: File) {
        storage.deleteFile(file)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            historyDao.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.deleteAll()
        }
    }

    fun getDefaultKeywords(): List<String> = engine.getDefaultKeywords()

    /** AI 识别提示：返回中文描述命中的意图词 */
    fun describeQuery(query: String): String = aiEngine.describe(query)

/**
     * AI 智能搜索：输入中文描述 → 自动扩展关键词 → 过滤 → 记录历史（记忆）
     */
    fun aiSearch(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = _uiState.value.copy(error = null)
            try {
                if (allClasses.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "请先加载 dump.cs 文件")
                    _isLoading.value = false
                    return@launch
                }
                val spec = aiEngine.expandQuerySpec(query)
                if (spec.keywords.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "没识别到意图，试试输入：攻击、玩家、伤害、武器 等")
                    _isLoading.value = false
                    return@launch
                }
                // 精准语义过滤：命中关键词 → 剔除连接/桥梁等无关类（红透热透强保留）→ 强关键词置顶
                val result = withContext(Dispatchers.Default) {
                    if (allClasses.size > 20_000) {
                        engine.filterSmartParallel(allClasses, spec)
                    } else {
                        engine.filterSmart(allClasses, spec)
                    }
                }
                val outputPath = storage.exportResult(result)
                historyDao.insert(
                    HistoryEntity(
                        fileName = _uiState.value.fileName,
                        keywords = "AI: ${query.trim()} → ${spec.keywords.joinToString(", ")}",
                        resultCount = result.classes.size,
                        outputPath = outputPath
                    )
                )
                _uiState.value = _uiState.value.copy(
                    filterResult = result,
                    lastOutputPath = outputPath,
                    error = null
                )
                loadExportedFiles()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "AI 搜索失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

/**
     * 扫描 Download 目录下的 dump.cs 文件（方便快速加载）
     */
    fun scanDownloadCsFiles(): List<File> = storage.scanDownloadCsFiles()

/**
     * 按文件路径直接加载（Download 目录快捷加载）
     */
    fun loadFileByPath(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _progress.value = 0f
            try {
                val file = File(path)
                if (!file.exists()) {
                    _uiState.value = _uiState.value.copy(error = "文件不存在: $path")
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    file.inputStream().use { stream ->
                        allClasses = parser.parse(stream) { p -> _progress.value = p }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    fileName = file.name,
                    totalClasses = allClasses.size,
                    isFileLoaded = true,
                    error = null
                )
                // 记住上次文件，退出/重启后自动恢复
                prefs.saveLastFile(file.absolutePath, file.name)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "读取失败: ${e.message}")
            } finally {
                _isLoading.value = false
                _progress.value = 1f
            }
        }
    }

/**
     * 最近搜索记忆（来自历史记录）
     */
    fun getRecentKeywords(): List<String> {
        return _history.value
            .sortedByDescending { it.timestamp }
            .take(8)
            .mapNotNull { h ->
                h.keywords.removePrefix("AI: ").substringBefore(" →").trim().takeIf { it.isNotEmpty() }
            }
            .distinct()
    }

/**
     * 进入 AI 解释聊天：设定当前解释的类，并自动生成第一轮问答（写历史记忆）
     */
    fun prepareExplain(cls: ClassInfo) {
        _currentExplainClass.value = cls
        viewModelScope.launch {
            val userMsg = "帮我解释一下 ${cls.className} 是干什么的喵~"
            val aiMsg = generateExplain(cls)
            aiChatDao.insert(
                AiChatEntity(
                    className = cls.className,
                    userMsg = userMsg,
                    aiMsg = aiMsg
                )
            )
        }
    }

/**
     * 用户在聊天页继续追问（AI 猫娘换个角度回答，同样写入历史记忆）。
     * 输入含"找/筛选/搜索"等意图时，自动转为对话式筛选（AI 帮找类）。
     */
    fun sendChat(userMsg: String) {
        if (userMsg.isBlank()) return
        // 筛选意图 → 对话式筛选（读取所有类，按用户想法筛出结果）
        if (isFilterIntent(userMsg)) {
            aiChatSearch(userMsg)
            return
        }
        val cls = _currentExplainClass.value ?: return
        viewModelScope.launch {
            val aiMsg = generateReply(cls, userMsg)
            aiChatDao.insert(
                AiChatEntity(
                    className = cls.className,
                    userMsg = userMsg.trim(),
                    aiMsg = aiMsg
                )
            )
        }
    }

/**
     * 对话式筛选：用户在 AI 聊天里直接说出想法（如"找透视相关的，不要连接服务器"），
     * 引擎读取所有类 → 智能过滤 → AI 猫娘回复命中摘要，并同步保存筛选结果（可去结果页看全部）。
     */
    fun aiChatSearch(query: String) {
        viewModelScope.launch {
            if (allClasses.isEmpty()) {
                aiChatDao.insert(
                    AiChatEntity(
                        className = "",
                        userMsg = query.trim(),
                        aiMsg = "喵~还没有加载 dump.cs 文件哦，先去首页加载文件，再让我帮你找吧~"
                    )
                )
                return@launch
            }
            val keepWord = aiEngine.extractKeepIntent(query)
            val removeWord = aiEngine.extractRemoveIntent(query)
            val isRefine = keepWord != null || removeWord != null

            var spec = aiEngine.expandQuerySpec(query)
            // 细化指令"只要热透"单独也能扩展出关键词（热透→thermal/heat/hot）
            if (spec.keywords.isEmpty()) {
                aiChatDao.insert(
                    AiChatEntity(
                        className = "",
                        userMsg = query.trim(),
                        aiMsg = "喵~没听懂你想找什么的 说。可以试试：找透视相关的、找武器伤害的、只要热透的~"
                    )
                )
                return@launch
            }
            // "去掉/不要 X"：把 X 的关键词加入排除词，从当前结果里剔除
            if (removeWord != null) {
                val rmSpec = aiEngine.expandQuerySpec(removeWord)
                spec = spec.copy(excludes = spec.excludes + rmSpec.keywords)
            }

            // 记忆：细化指令基于上次筛选结果继续筛，否则全量筛
            val baseClasses =
                if (isRefine && !lastFilterClasses.isNullOrEmpty()) lastFilterClasses!! else allClasses

            val result = if (baseClasses.size > 20_000) {
                engine.filterSmartParallel(baseClasses, spec)
            } else {
                engine.filterSmart(baseClasses, spec)
            }

            lastFilterClasses = result.classes
            chatMemoryLabel = when {
                keepWord != null -> (chatMemoryLabel ?: "上次筛选") + " →只要$keepWord"
                removeWord != null -> (chatMemoryLabel ?: "上次筛选") + " →去掉$removeWord"
                else -> query.trim()
            }

            // 保存筛选结果 → 用户可去结果页查看全部
            _uiState.value = _uiState.value.copy(filterResult = result, error = null)

            val top = result.classes.take(12)
            val aiMsg = buildString {
                if (isRefine && chatMemoryLabel != null) {
                    append("喵~记得你刚才在找【${chatMemoryLabel}】相关的，现在按你说的【${query.trim()}】继续筛，共命中 ${result.classes.size} 个！\n")
                } else {
                    append("喵~帮你找【${query.trim()}】相关的类，共命中 ${result.classes.size} 个！\n")
                }
                if (result.excludedHint.isNotBlank()) append("${result.excludedHint}。\n")
                if (top.isNotEmpty()) {
                    append("📋 最相关的几个：\n")
                    top.forEachIndexed { i, c -> append("${i + 1}. ${c.fullName}\n") }
                    if (result.classes.size > top.size) {
                        append("……还有 ${result.classes.size - top.size} 个，点右上角/返回结果页看全部喵~\n")
                    }
                }
                append("还可以继续说要求细化，比如：只要热透的、去掉设置相关的~✨")
            }
            aiChatDao.insert(
                AiChatEntity(
                    className = "筛选: ${query.trim()}",
                    userMsg = query.trim(),
                    aiMsg = aiMsg
                )
            )
        }
    }

    private fun isFilterIntent(msg: String): Boolean {
        val m = msg.lowercase()
        return listOf(
            "找", "筛选", "搜索", "查找", "帮我找", "过滤", "有哪些", "帮我筛", "我要",
            "只要", "只留", "就要", "光要", "去掉", "不要", "排除", "剔除", "过滤掉", "删掉",
            "热透", "红透", "继续", "再加上"
        ).any { m.contains(it) }
    }

/**
     * 生成 AI 解释：配置了第三方 API 则用真实大模型（失败自动回退离线引擎）
     */
    private suspend fun generateExplain(cls: ClassInfo): String {
        val detail = replyDetail.value
        val offline = explainer.explain(cls)
        val baseUrl = apiBaseUrl.value
        val key = apiKey.value
        if (baseUrl.isBlank() || key.isBlank()) return offline
        return try {
            val detailHint = when (detail) {
                "brief" -> "用 2-3 句话简洁回答，不要长篇大论"
                "detailed" -> "尽量详细展开，多讲细节和修改思路"
                else -> "条理清晰，中等详细"
            }
            llmClient.chat(
                baseUrl, key, modelName.value,
                "你是一个猫娘AI助手，用可爱猫娘语气（喵~、的说、哦~）解释游戏反编译 dump.cs 中的类。用中文回答，带少量emoji，简洁有条理。",
                "请解释这个类的用途和功能：\n类名=${cls.className}\n命名空间=${cls.namespace}\n父类=${cls.parentClass}\n方法=${cls.methods.take(8).joinToString(", ") { it.name }}\n字段=${cls.fields.take(8).joinToString(", ") { it.name }}\n$detailHint"
            )
        } catch (e: Exception) {
            offline
        }
    }

/**
     * 生成 AI 追问回复：LLM 优先，失败回退离线规则引擎
     */
    private suspend fun generateReply(cls: ClassInfo, userMsg: String): String {
        val offline = explainer.reply(cls, userMsg)
        val baseUrl = apiBaseUrl.value
        val key = apiKey.value
        if (baseUrl.isBlank() || key.isBlank()) return offline
        return try {
            llmClient.chat(
                baseUrl, key, modelName.value,
                "你是一个猫娘AI助手，用可爱猫娘语气（喵~、的说、哦~）回答用户关于游戏反编译类的问题。用中文回答，简洁有条理，带少量emoji。",
                "类名=${cls.className}\n命名空间=${cls.namespace}\n父类=${cls.parentClass}\n方法=${cls.methods.take(10).joinToString(", ") { it.name }}\n字段=${cls.fields.take(10).joinToString(", ") { it.name }}\n用户问：$userMsg"
            )
        } catch (e: Exception) {
            offline
        }
    }

    /** 加载更多：结果页分页每次 +200 条（状态存 ViewModel，离开页面再回来不丢） */
    fun expandResults() {
        _uiState.value = _uiState.value.copy(visibleCount = _uiState.value.visibleCount + 200)
    }

    /** 保存 AI 行为设置（打字机速度 / 回复详细度 / 显示反馈） */
    fun saveAiSettings(typingSpeed: Int, detail: String, showFeedback: Boolean) {
        viewModelScope.launch {
            aiSettings.setTypingSpeedMs(typingSpeed)
            aiSettings.setReplyDetail(detail)
            aiSettings.setShowFeedback(showFeedback)
        }
    }

    /** 保存第三方 AI 中转站配置 */
    fun saveApiConfig(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            aiSettings.setApiConfig(baseUrl, apiKey, model)
        }
    }

    /** 测试第三方 API 连通性：成功返回 true + 响应摘要，失败返回错误信息 */
    fun testApi(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val baseUrl = apiBaseUrl.value
            val key = apiKey.value
            if (baseUrl.isBlank() || key.isBlank()) {
                onResult(false, "请先填写 Base URL 和 API Key")
                return@launch
            }
            try {
                val reply = llmClient.chat(
                    baseUrl, key, modelName.value,
                    "你是一个简短的测试助手，只回复两个字：正常",
                    "这是一条连通性测试消息，请只回复：正常"
                )
                onResult(true, "连接成功！模型回复：${reply.take(60)}")
            } catch (e: Exception) {
                onResult(false, "连接失败：${e.message}")
            }
        }
    }

    /** 清空 AI 解释聊天历史 */
    /** 立即检查更新（绕过冷却期） */
    fun checkUpdateNow() {
        viewModelScope.launch { updateManager.checkUpdate(forceCheck = true) }
    }

    fun clearAiChats() {
        viewModelScope.launch {
            aiChatDao.deleteAll()
        }
    }

    /** 标记某条 AI 消息的打字机动画已播完（退出重进时不重播） */
    fun markTypingDone(id: Long) {
        if (id !in _playedAnimIds.value) {
            _playedAnimIds.value = _playedAnimIds.value + id
        }
    }

    data class UiState(
        val fileName: String = "",
        val totalClasses: Int = 0,
        val isFileLoaded: Boolean = false,
        val filterResult: FilterResult? = null,
        val lastOutputPath: String = "",
        val visibleCount: Int = 200,
        val error: String? = null
    )

    /** 返回已解析的全部类（供浏览页使用） */
    fun getAllClasses(): List<ClassInfo> = allClasses

    /** 按类名+方法/字段索引查找并返回 DumpMember，用于结果页详情面板 */
    fun getDumpMemberById(typeName: String, memberIndex: Int): com.dumpcs.filter.data.DumpMember? {
        return allClasses
            .firstOrNull { it.className == typeName || it.fullName == typeName }
            ?.let { cls ->
                val totalMembers = cls.methods.size + cls.fields.size
                val idx = memberIndex.coerceIn(0, totalMembers - 1)
                if (idx < cls.methods.size) {
                    val m = cls.methods[idx]
                    com.dumpcs.filter.data.DumpMember(
                        kind = com.dumpcs.filter.data.MemberKind.Method,
                        name = m.name,
                        signature = m.rawLine,
                        details = m.rawLine,
                        rva = m.rva.toULongOrNull() ?: 0u,
                        offset = m.offset.toULongOrNull() ?: 0u,
                        va = m.va.toULongOrNull() ?: 0u
                    )
                } else {
                    val f = cls.fields[idx - cls.methods.size]
                    com.dumpcs.filter.data.DumpMember(
                        kind = com.dumpcs.filter.data.MemberKind.Field,
                        name = f.name,
                        signature = f.rawLine,
                        details = f.rawLine,
                        rva = 0u,
                        offset = f.offset.toULongOrNull() ?: 0u,
                        va = 0u
                    )
                }
            }
    }
}
