package com.dumpcs.filter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dumpcs.filter.data.ClassInfo
import com.dumpcs.filter.data.DumpMember
import com.dumpcs.filter.data.MemberKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBrowseScreen(
    navController: androidx.navigation.NavController,
    viewModel: MainViewModel,
    explorerViewModel: ExplorerViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestoreFile.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(0) }
    var aiQuery by remember { mutableStateOf("") }
    var aiHint by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var selectedDefaults by remember { mutableStateOf(setOf<String>()) }
    var showKeywordSheet by remember { mutableStateOf(false) }
    var downloadFiles by remember { mutableStateOf(viewModel.scanDownloadCsFiles()) }
    var recentKeywords by remember { mutableStateOf(viewModel.getRecentKeywords()) }
    var browseQuery by remember { mutableStateOf("") }
    var selectedBrowseMember by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val defaultKeywords = viewModel.getDefaultKeywords()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.loadFile(it, context)
            explorerViewModel.loadDump(it)
            downloadFiles = emptyList()
        }
    }

    fun loadPath(path: String) {
        scope.launch {
            viewModel.loadFileByPath(path)
            try {
                explorerViewModel.loadDumpByPath(path)
            } catch (_: Exception) {}
            downloadFiles = emptyList()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("DumpCS Filter", fontWeight = FontWeight.Bold)
                            Text(
                                "查找与浏览",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("查找") }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("浏览") }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePicker.launch("text/*") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "上传")
            }
        }
    ) { padding ->
        when (activeTab) {
            0 -> SearchTab(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                isLoading = isLoading,
                progress = progress,
                viewModel = viewModel,
                navController = navController,
                aiQuery = aiQuery,
                onAiQueryChange = { aiQuery = it },
                aiHint = aiHint,
                onAiHintChange = { aiHint = it },
                keywords = keywords,
                onKeywordsChange = { keywords = it },
                defaultKeywords = defaultKeywords,
                selectedDefaults = selectedDefaults,
                onToggleDefault = { kw ->
                    selectedDefaults = if (selectedDefaults.contains(kw)) selectedDefaults - kw else selectedDefaults + kw
                },
                onShowKeywordSheet = { showKeywordSheet = true },
                downloadFiles = downloadFiles,
                onDownloadFileClick = { path ->
                    loadPath(path)
                },
                onRecentSearchClick = { kw ->
                    aiQuery = kw
                    aiHint = "记忆恢复: $kw"
                    viewModel.aiSearch(kw)
                    recentKeywords = viewModel.getRecentKeywords()
                    navController.navigate(Screen.Results.route)
                }
            )
            1 -> BrowseTab(
                modifier = Modifier.padding(padding),
                explorerViewModel = explorerViewModel,
                allClasses = viewModel.getAllClasses(),
                query = browseQuery,
                onQueryChange = { browseQuery = it },
                selectedMember = selectedBrowseMember,
                onMemberClick = { selectedBrowseMember = it },
                filePicker = { filePicker.launch("*/*") },
                mainViewModel = viewModel
            )
        }
    }

    if (showKeywordSheet) {
        KeywordBottomSheet(
            keywords = defaultKeywords,
            selected = selectedDefaults,
            onToggle = { kw ->
                selectedDefaults = if (selectedDefaults.contains(kw)) selectedDefaults - kw else selectedDefaults + kw
            },
            onDismiss = { showKeywordSheet = false }
        )
    }

    if (isLoading && progress < 1f) {
        LoadingDialog(progress = progress)
    }

    pendingRestore?.let { path ->
        AlertDialog(
            onDismissRequest = { viewModel.confirmRestore(false) },
            title = { Text("恢复上次读取的文件？") },
            text = {
                Text(
                    "检测到上次读取的文件：\n${path.substringAfterLast('/')}\n\n是否加载？"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore(true) }) {
                    Text("加载", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmRestore(false) }) {
                    Text("不加载")
                }
            }
        )
    }
}

@Composable
fun SearchTab(
    modifier: Modifier,
    uiState: MainViewModel.UiState,
    isLoading: Boolean,
    progress: Float,
    viewModel: MainViewModel,
    navController: androidx.navigation.NavController,
    aiQuery: String,
    onAiQueryChange: (String) -> Unit,
    aiHint: String,
    onAiHintChange: (String) -> Unit,
    keywords: String,
    onKeywordsChange: (String) -> Unit,
    defaultKeywords: List<String>,
    selectedDefaults: Set<String>,
    onToggleDefault: (String) -> Unit,
    onShowKeywordSheet: () -> Unit,
    downloadFiles: List<java.io.File>,
    onDownloadFileClick: (String) -> Unit,
    onRecentSearchClick: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // 文件上传卡片
        item {
            UploadCardCompact(
                isLoaded = uiState.isFileLoaded,
                fileName = uiState.fileName,
                totalClasses = uiState.totalClasses,
                isLoading = isLoading && progress < 1f,
                progress = progress
            )
        }

        // Download 目录快速加载
        if (downloadFiles.isNotEmpty() && !uiState.isFileLoaded) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Download 目录的快速加载", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        downloadFiles.take(5).forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onDownloadFileClick(file.absolutePath) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text("${formatFileSize(file.length())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // AI 智能搜索
        if (uiState.isFileLoaded) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("AI 智能搜索", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${uiState.totalClasses} 个类", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = aiQuery,
                            onValueChange = { onAiQueryChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                             placeholder = { Text("说中文，AI 帮你找，如：找攻击和伤害相关的") },
                             leadingIcon = { Icon(Icons.Default.Psychology, null) },
                             trailingIcon = { if (aiQuery.isNotBlank()) IconButton(onClick = { onAiQueryChange("") }) { Icon(Icons.Default.Clear, null) } else null },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (aiQuery.isNotBlank()) {
                                    onAiHintChange(viewModel.describeQuery(aiQuery))
                                    viewModel.aiSearch(aiQuery)
                                    navController.navigate(Screen.Results.route)
                                }
                            },
                            enabled = aiQuery.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AI 搜索")
                        }
                        if (aiHint.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(aiHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (viewModel.getRecentKeywords().isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("最近搜索", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(viewModel.getRecentKeywords().take(6)) { kw ->
                                    InputChip(
                                        selected = false,
                                        onClick = { onRecentSearchClick(kw) },
                                        label = { Text(kw, maxLines = 1) },
                                        leadingIcon = { Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 关键词筛选
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("关键词筛选", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = keywords,
                            onValueChange = onKeywordsChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("输入关键词，用逗号分隔") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(defaultKeywords.take(6)) { kw ->
                                FilterChip(
                                    selected = selectedDefaults.contains(kw),
                                    onClick = { onToggleDefault(kw) },
                                    label = { Text(kw) }
                                )
                            }
                            item {
                                InputChip(
                                    selected = false,
                                    onClick = onShowKeywordSheet,
                                    label = { Text("更多") },
                                    trailingIcon = { Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val allKws = buildList {
                            if (keywords.isNotBlank()) addAll(keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                            addAll(selectedDefaults)
                        }
                        if (allKws.isNotEmpty()) {
                            Button(
                                onClick = {
                                    viewModel.filterClasses(allKws)
                                    navController.navigate(Screen.Results.route)
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FilterAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("开始筛选")
                            }
                        }
                    }
                }
            }
        }

        // 空状态
        if (!uiState.isFileLoaded && !isLoading) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.FileOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("暂无文件", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
                    Text("点击右下角按钮上传 dump.cs 文件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun UploadCardCompact(
    isLoaded: Boolean,
    fileName: String,
    totalClasses: Int,
    isLoading: Boolean,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(8.dp))
                Text("正在解析... ${"%d".format(progress * 100)}%", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp))
            } else if (isLoaded) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("$totalClasses 个类", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("点击右下角按钮上传 dump.cs", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BrowseTab(
    modifier: Modifier,
    explorerViewModel: ExplorerViewModel,
    allClasses: List<ClassInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedMember: Pair<String, Int>?,
    onMemberClick: (Pair<String, Int>?) -> Unit,
    filePicker: () -> Unit,
    mainViewModel: MainViewModel
) {
    val ui by explorerViewModel.ui.collectAsStateWithLifecycle()

    val classTreeRows = remember(allClasses) {
        val result = mutableListOf<BrowseRow>()
        val nsMap = mutableMapOf<String, MutableList<ClassInfo>>()
        allClasses.forEach { cls ->
            nsMap.getOrPut(cls.namespace) { mutableListOf() }.add(cls)
        }
        nsMap.toSortedMap().forEach { (ns, classes) ->
            val nsKey = "ns|$ns"
            result.add(BrowseRow(key = nsKey, indent = 0, title = ns.ifEmpty { "(default)" }, expandable = true))
            classes.sortedBy { it.className }.forEach { cls ->
                val typeKey = "$nsKey|${cls.className}"
                result.add(BrowseRow(key = typeKey, indent = 1, title = cls.className, subtitle = "${cls.methods.size} 方法 · ${cls.fields.size} 字段", expandable = true))
                cls.methods.forEachIndexed { i, m ->
                    val sig = m.rawLine.substringBefore("(").trim().split(" ").lastOrNull() ?: m.name
                    result.add(BrowseRow(key = "$typeKey|m|$i", indent = 2, title = sig, subtitle = "方法 · ${m.rva}", isMember = true, memberData = Pair(cls.className, i)))
                }
                cls.fields.forEachIndexed { i, f ->
                    result.add(BrowseRow(key = "$typeKey|f|$i", indent = 2, title = f.rawLine.trimEnd(';').split(" ").lastOrNull() ?: f.name, subtitle = "字段 · ${f.offset}", isMember = true, memberData = Pair(cls.className, cls.methods.size + i)))
                }
            }
        }
        result
    }

    val dumpTreeRows = remember(ui.types) {
        val result = mutableListOf<BrowseRow>()
        ui.types.forEach { t ->
            val asmKey = "asm|${t.assembly.ifBlank { "(unknown)" }}"
            result.add(BrowseRow(key = asmKey, indent = 0, title = t.assembly.ifBlank { "(unknown)" }, expandable = true))
            val nsKey = "$asmKey|${t.nameSpace}"
            result.add(BrowseRow(key = nsKey, indent = 1, title = t.nameSpace, expandable = true))
            val typeKey = "$nsKey|${t.name}"
            result.add(BrowseRow(key = typeKey, indent = 2, title = t.name, subtitle = "${t.members.size} 个成员", expandable = true))
            val methods = t.members.filter { it.kind == MemberKind.Method || it.kind == MemberKind.Ctor }
            val fields = t.members.filter { it.kind == MemberKind.Field || it.kind == MemberKind.EnumValue }
            val props = t.members.filter { it.kind == MemberKind.Property }
            val events = t.members.filter { it.kind == MemberKind.Event }
            if (methods.isNotEmpty()) {
                result.add(BrowseRow(key = "$typeKey|Methods", indent = 3, title = "Methods (${methods.size})", expandable = true))
                methods.withIndex().forEach { (i, m) ->
                    val sig = m.signature.ifBlank { m.details }
                    result.add(BrowseRow(key = "$typeKey|m|$i", indent = 4, title = sig, subtitle = "0x${m.rva.toString(16)}", isMember = true, memberData = Pair(t.name, i)))
                }
            }
            if (fields.isNotEmpty()) {
                result.add(BrowseRow(key = "$typeKey|Fields", indent = 3, title = "Fields (${fields.size})", expandable = true))
                fields.withIndex().forEach { (i, m) ->
                    val sig = m.signature.ifBlank { m.details }
                    result.add(BrowseRow(key = "$typeKey|f|$i", indent = 4, title = sig, subtitle = "0x${m.offset.toString(16)}", isMember = true, memberData = Pair(t.name, methods.size + i)))
                }
            }
            if (props.isNotEmpty()) {
                result.add(BrowseRow(key = "$typeKey|Props", indent = 3, title = "Properties (${props.size})", expandable = true))
                props.withIndex().forEach { (i, m) ->
                    result.add(BrowseRow(key = "$typeKey|p|$i", indent = 4, title = m.signature.ifBlank { m.details }, isMember = true, memberData = Pair(t.name, methods.size + fields.size + i)))
                }
            }
            if (events.isNotEmpty()) {
                result.add(BrowseRow(key = "$typeKey|Events", indent = 3, title = "Events (${events.size})", expandable = true))
                events.withIndex().forEach { (i, m) ->
                    result.add(BrowseRow(key = "$typeKey|e|$i", indent = 4, title = m.signature.ifBlank { m.details }, isMember = true, memberData = Pair(t.name, methods.size + fields.size + props.size + i)))
                }
            }
        }
        result
    }

    val listState = rememberLazyListState()
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }
    fun toggleKey(key: String) {
        expandedKeys = if (expandedKeys.contains(key)) expandedKeys - key else expandedKeys + key
    }

    val filteredClassRows = if (query.isBlank()) classTreeRows else classTreeRows.filter { row ->
        row.title.lowercase().contains(query.lowercase())
    }
    val filteredDumpRows = if (query.isBlank()) dumpTreeRows else dumpTreeRows.filter { row ->
        row.title.lowercase().contains(query.lowercase())
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (ui.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        ui.error?.let {
            Text("错误: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true,
            label = { Text("搜索类名 / 成员") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )

        if (ui.types.isEmpty() && allClasses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("尚未加载文件", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = filePicker) { Text("打开文件") }
                }
            }
        } else {
            val rows = if (ui.types.isNotEmpty()) filteredDumpRows else filteredClassRows
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(rows, key = { it.key }) { row ->
                    val isExp = expandedKeys.contains(row.key)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (row.indent * 14).dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                            .then(if (row.isMember) Modifier.clickable { onMemberClick(row.memberData) } else if (row.expandable) Modifier.clickable { toggleKey(row.key) } else Modifier)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (row.expandable) {
                            Text(if (isExp) "v" else ">", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                        } else {
                            Spacer(Modifier.width(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            row.subtitle?.let { sub ->
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedMember?.let { (typeName, memberIdx) ->
        val member = mainViewModel.getDumpMemberById(typeName, memberIdx)
        member?.let { m ->
            MemberDetailDialog(
                member = m,
                typeName = typeName,
                onDismiss = { onMemberClick(null) }
            )
        }
    }
}

data class BrowseRow(
    val key: String,
    val indent: Int,
    val title: String,
    val subtitle: String? = null,
    val expandable: Boolean = false,
    val isMember: Boolean = false,
    val memberData: Pair<String, Int>? = null
)

