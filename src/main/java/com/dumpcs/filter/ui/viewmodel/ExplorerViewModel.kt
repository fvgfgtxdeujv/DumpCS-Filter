package com.dumpcs.filter.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dumpcs.filter.data.explorer.DumpMember
import com.dumpcs.filter.data.explorer.DumpTreeParser
import com.dumpcs.filter.data.explorer.DumpType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 浏览页收藏 / Hook 模板专用 DataStore */
private val Context.favoritesDataStore by preferencesDataStore(name = "explorer_favs")

data class ExplorerUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val types: List<DumpType> = emptyList()
)

/**
 * dump.cs 浏览页 ViewModel（移植自 DumpCsExplorer-Mobile）：
 * - 流式解析 dump.cs → 结构化类型树
 * - 收藏类型 / 成员（DataStore 持久化）
 * - Hook 模板（方法/字段/属性/事件，可编辑可重置）
 */
class ExplorerViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(ExplorerUiState())
    val ui = _ui.asStateFlow()

    private val dataStore = app.favoritesDataStore

    private val favTypesKey = stringSetPreferencesKey("favorite_types")
    private val favMembersKey = stringSetPreferencesKey("favorite_members")

    private val hookTemplateMethodKey = stringPreferencesKey("hook_template_method")
    private val hookTemplateFieldKey = stringPreferencesKey("hook_template_field")
    private val hookTemplatePropertyKey = stringPreferencesKey("hook_template_property")
    private val hookTemplateEventKey = stringPreferencesKey("hook_template_event")

    private val defaultHookTemplateMethod = """
auto img = BNM::Image("${'$'}{assemblyName}");
auto klass = img.GetClass("${'$'}{namespace}", "${'$'}{className}");
auto method = klass.GetMethod("${'$'}{methodName}", ${'$'}{parameterCount});
auto offset = method.GetOffset();

// TODO: Replace return/arg types
using t_${'$'}{safeMethodName} = void(*)(void* __this);
static t_${'$'}{safeMethodName} orig_${'$'}{safeMethodName} = nullptr;

static void hook_${'$'}{safeMethodName}(void* __this) {
    // TODO: your code
    return orig_${'$'}{safeMethodName}(__this);
}

// DobbyHook(reinterpret_cast<void*>(offset), reinterpret_cast<void*>(hook_${'$'}{safeMethodName}), reinterpret_cast<void**>(&orig_${'$'}{safeMethodName}));
""".trim()

    private val defaultHookTemplateField = """
auto img = BNM::Image("${'$'}{assemblyName}");
auto klass = img.GetClass("${'$'}{namespace}", "${'$'}{className}");
auto field = klass.GetField("${'$'}{memberName}");
""".trim()

    private val defaultHookTemplateProperty = """
auto img = BNM::Image("${'$'}{assemblyName}");
auto klass = img.GetClass("${'$'}{namespace}", "${'$'}{className}");
auto prop = klass.GetProperty("${'$'}{memberName}");
""".trim()

    private val defaultHookTemplateEvent = """
auto img = BNM::Image("${'$'}{assemblyName}");
auto klass = img.GetClass("${'$'}{namespace}", "${'$'}{className}");
auto evt = klass.GetEvent("${'$'}{memberName}");
""".trim()

    val favoriteTypes = dataStore.data
        .map { it[favTypesKey] ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val favoriteMembers = dataStore.data
        .map { it[favMembersKey] ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val hookTemplateMethod = dataStore.data
        .map { prefs -> prefs[hookTemplateMethodKey] ?: defaultHookTemplateMethod }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultHookTemplateMethod)

    val hookTemplateField = dataStore.data
        .map { prefs -> prefs[hookTemplateFieldKey] ?: defaultHookTemplateField }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultHookTemplateField)

    val hookTemplateProperty = dataStore.data
        .map { prefs -> prefs[hookTemplatePropertyKey] ?: defaultHookTemplateProperty }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultHookTemplateProperty)

    val hookTemplateEvent = dataStore.data
        .map { prefs -> prefs[hookTemplateEventKey] ?: defaultHookTemplateEvent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultHookTemplateEvent)

    fun setHookTemplateMethod(value: String) {
        viewModelScope.launch { dataStore.edit { prefs -> prefs[hookTemplateMethodKey] = value } }
    }

    fun setHookTemplateField(value: String) {
        viewModelScope.launch { dataStore.edit { prefs -> prefs[hookTemplateFieldKey] = value } }
    }

    fun setHookTemplateProperty(value: String) {
        viewModelScope.launch { dataStore.edit { prefs -> prefs[hookTemplatePropertyKey] = value } }
    }

    fun setHookTemplateEvent(value: String) {
        viewModelScope.launch { dataStore.edit { prefs -> prefs[hookTemplateEventKey] = value } }
    }

    fun resetHookTemplatesToDefault() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(hookTemplateMethodKey)
                prefs.remove(hookTemplateFieldKey)
                prefs.remove(hookTemplatePropertyKey)
                prefs.remove(hookTemplateEventKey)
            }
        }
    }

    fun typeFavoriteKey(asm: String, ns: String, typeName: String): String {
        return "type|$asm|$ns|$typeName"
    }

    fun memberFavoriteKey(asm: String, ns: String, typeName: String, member: DumpMember): String {
        val sig = member.signature.ifBlank { member.details }
        return "member|$asm|$ns|$typeName|${member.kind.name}|0x${member.rva.toString(16)}|0x${member.offset.toString(16)}|0x${member.va.toString(16)}|$sig"
    }

    fun toggleTypeFavorite(asm: String, ns: String, typeName: String) {
        val key = typeFavoriteKey(asm, ns, typeName)
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val cur = prefs[favTypesKey] ?: emptySet()
                prefs[favTypesKey] = if (cur.contains(key)) cur - key else cur + key
            }
        }
    }

    fun toggleMemberFavorite(asm: String, ns: String, typeName: String, member: DumpMember) {
        val key = memberFavoriteKey(asm, ns, typeName, member)
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val cur = prefs[favMembersKey] ?: emptySet()
                prefs[favMembersKey] = if (cur.contains(key)) cur - key else cur + key
            }
        }
    }

    fun loadDump(uri: Uri) {
        _ui.value = ExplorerUiState(isLoading = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cr = getApplication<Application>().contentResolver
                cr.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法打开文件" }
                    input.bufferedReader().use { reader ->
                        val parsed = DumpTreeParser.parse(reader)
                        _ui.value = ExplorerUiState(isLoading = false, types = parsed)
                    }
                }
            } catch (t: Throwable) {
                _ui.value = ExplorerUiState(isLoading = false, error = t.message ?: "解析失败")
            }
        }
    }

    /** 从本地路径加载（支持 Dump 页产出的 dump.cs 直接浏览） */
    fun loadDumpByPath(path: String) {
        _ui.value = ExplorerUiState(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = java.io.File(path).bufferedReader().use { DumpTreeParser.parse(it) }
                _ui.value = ExplorerUiState(isLoading = false, types = parsed)
            } catch (t: Throwable) {
                _ui.value = ExplorerUiState(isLoading = false, error = t.message ?: "解析失败")
            }
        }
    }

    fun clear() {
        _ui.value = ExplorerUiState()
    }
}
