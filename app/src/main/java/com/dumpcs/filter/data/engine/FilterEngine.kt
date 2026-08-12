package com.dumpcs.filter.data.engine

import com.dumpcs.filter.data.ai.AiKeywordEngine
import com.dumpcs.filter.data.model.ClassInfo
import com.dumpcs.filter.data.model.FilterResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FilterEngine {

    fun filter(classes: List<ClassInfo>, keywords: List<String>): FilterResult {
        val lowerKeywords = keywords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val filtered = classes.filter { it.containsKeyword(lowerKeywords) }
        return FilterResult(
            fileName = "",
            keywords = keywords,
            classes = filtered
        )
    }

/**
     * 智能过滤：命中关键词 + 剔除排除词类（连接/桥梁等），强关键词类名强制保留。
     * 例：搜"透视"→ 保留 esp/visible/thermal 相关类，剔除 ConnectionBridge 这类连接桥梁。
     */
    fun filterSmart(
        classes: List<ClassInfo>,
        spec: AiKeywordEngine.SearchSpec
    ): FilterResult {
        val keywords = spec.keywords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val excludes = spec.excludes.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val strong = spec.strongKeywords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) {
            return FilterResult(fileName = "", keywords = spec.keywords, classes = emptyList())
        }

        var excludedCount = 0
        val kept = mutableListOf<ClassInfo>()
        for (cls in classes) {
            if (!cls.containsKeyword(keywords)) continue
            // 命中排除词：除非类名/完整路径本身含强关键词（红透/热透），否则剔除
            if (excludes.isNotEmpty() && cls.containsExclude(excludes)) {
                if (strong.isEmpty() || !cls.fullNameLower.containsAny(strong)) {
                    excludedCount++
                    continue
                }
            }
            kept.add(cls)
        }

        // 排序：强关键词置顶 → 相关度降序（类名命中 > 命名空间/父类命中 > 仅方法/字段命中）
        // UI设置容器类（Template/Toggle/Slider/Container 等）降级，连接服务器等弱相关沉底
        if (strong.isNotEmpty()) {
            kept.sortWith(
                compareByDescending<ClassInfo> { if (it.fullNameLower.containsAny(strong)) 1 else 0 }
                    .thenByDescending { it.relevanceScore(keywords) }
            )
        } else {
            kept.sortByDescending { it.relevanceScore(keywords) }
        }

        return FilterResult(
            fileName = "",
            keywords = spec.keywords,
            classes = kept,
            excludedCount = excludedCount,
            excludedHint = if (excludedCount > 0) "已自动剔除 $excludedCount 个连接/桥梁等无关类" else ""
        )
    }

/**
     * 并行过滤：按 CPU 核数分块，多线程同时筛选（大文件速度翻倍）
     */
    suspend fun filterParallel(classes: List<ClassInfo>, keywords: List<String>): FilterResult =
        coroutineScope {
            val lowerKeywords = keywords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
            if (classes.size < 20000 || lowerKeywords.isEmpty()) {
                // 小数据量直接单线程，避免线程开销
                return@coroutineScope filter(classes, lowerKeywords)
            }
            val cores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            val chunkSize = (classes.size / cores) + 1
            val result = classes.chunked(chunkSize)
                .map { chunk ->
                    async {
                        chunk.filter { it.containsKeyword(lowerKeywords) }
                    }
                }
                .awaitAll()
                .flatten()
            FilterResult(
                fileName = "",
                keywords = keywords,
                classes = result
            )
        }

/**
     * 并行智能过滤（大文件 + 排除逻辑）
     */
    suspend fun filterSmartParallel(
        classes: List<ClassInfo>,
        spec: AiKeywordEngine.SearchSpec
    ): FilterResult = coroutineScope {
        if (classes.size < 20000) {
            return@coroutineScope filterSmart(classes, spec)
        }
        val keywords = spec.keywords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val excludes = spec.excludes.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val strong = spec.strongKeywords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) {
            return@coroutineScope FilterResult(fileName = "", keywords = spec.keywords, classes = emptyList())
        }
        val cores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val chunkSize = (classes.size / cores) + 1
        var excludedCount = 0
        val kept = mutableListOf<ClassInfo>()
        classes.chunked(chunkSize)
            .map { chunk ->
                async {
                    var excl = 0
                    val local = mutableListOf<ClassInfo>()
                    for (cls in chunk) {
                        if (!cls.containsKeyword(keywords)) continue
                        if (excludes.isNotEmpty() && cls.containsExclude(excludes)) {
                            if (strong.isEmpty() || !cls.fullNameLower.containsAny(strong)) {
                                excl++
                                continue
                            }
                        }
                        local.add(cls)
                    }
                    excl to local
                }
            }
            .awaitAll()
            .forEach { (excl, local) ->
                excludedCount += excl
                kept.addAll(local)
            }
        // 强关键词置顶 → 相关度降序（与 filterSmart 一致）
        if (strong.isNotEmpty()) {
            kept.sortWith(
                compareByDescending<ClassInfo> { if (it.fullNameLower.containsAny(strong)) 1 else 0 }
                    .thenByDescending { it.relevanceScore(keywords) }
            )
        } else {
            kept.sortByDescending { it.relevanceScore(keywords) }
        }
        FilterResult(
            fileName = "",
            keywords = spec.keywords,
            classes = kept,
            excludedCount = excludedCount,
            excludedHint = if (excludedCount > 0) "已自动剔除 $excludedCount 个连接/桥梁等无关类" else ""
        )
    }

    fun getDefaultKeywords(): List<String> = listOf(
        "aim", "recoil", "jump", "slide", "range", "damage", "speed",
        "wall", "visible", "occlusion", "render", "camera", "player",
        "weapon", "bullet", "hit", "range", "scope", "crosshair",
        "health", "armor", "shield", "fly", "gravity", "teleport",
        "esp", "radar", "map", "loot", "item", "box", "chest"
    )
}

/** 完整类名转小写（用于强关键词判断） */
private val ClassInfo.fullNameLower: String
    get() = fullName.lowercase()

/** 完整类名是否包含任一关键词 */
private fun String.containsAny(words: List<String>): Boolean =
    words.any { contains(it) }

/**
 * 相关度评分（越高越相关）：
 * 3 = 完整类名（命名空间.类名）直接命中关键词
 * 2 = 仅命名空间 / 父类命中
 * 1 = 仅方法名 / 字段名命中（弱相关，如连接服务器的类恰好有 jump 字段）
 * 类名含 UI 设置容器弱词（Template/Toggle/Slider/Container/Window 等）时降 1 级，
 * 避免"搜跳跃时设置开关容器排最前"的观感问题。
 */
private val WEAK_WORDS = listOf(
    "template", "toggle", "slider", "setting", "container",
    "window", "option", "config", "preference", "button", "panel"
)

private fun ClassInfo.relevanceScore(keywords: List<String>): Int {
    val fn = fullNameLower
    val score = when {
        keywords.any { fn.contains(it) } -> 3
        keywords.any { namespace.lowercase().contains(it) || parentClass.lowercase().contains(it) } -> 2
        else -> 1 // containsKeyword 已保证命中（方法/字段）
    }
    return if (score >= 2 && WEAK_WORDS.any { fn.contains(it) }) score - 1 else score
}
