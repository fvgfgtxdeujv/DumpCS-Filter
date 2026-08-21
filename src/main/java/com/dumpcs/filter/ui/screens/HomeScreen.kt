package com.dumpcs.filter.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.dumpcs.filter.ui.navigation.Screen
import com.dumpcs.filter.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestoreFile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var keywords by remember { mutableStateOf("") }
    var selectedDefaults by remember { mutableStateOf(setOf<String>()) }
    var showKeywordSheet by remember { mutableStateOf(false) }
    var aiQuery by remember { mutableStateOf("") }
    var aiHint by remember { mutableStateOf("") }
    var downloadFiles by remember { mutableStateOf(viewModel.scanDownloadCsFiles()) }
    var recentKeywords by remember { mutableStateOf(viewModel.getRecentKeywords()) }

    val defaultKeywords = viewModel.getDefaultKeywords()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFile(it, context) }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("DumpCS Filter", fontWeight = FontWeight.Bold)
                        Text(
                            "筛选 dump.cs 类名",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.History.route) }) {
                        Icon(Icons.Default.History, contentDescription = "历史记录")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 错误提示（筛选/解析失败时显示，不闪退）
            uiState.error?.let { err ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Download 目录快速加载（一键加载 dump.cs）
            if (downloadFiles.isNotEmpty() && !uiState.isFileLoaded) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Download 目录的 dump.cs（点击快速加载）",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            downloadFiles.take(6).forEach { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.loadFileByPath(file.absolutePath)
                                            downloadFiles = emptyList()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            formatSize(file.length()),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 文件上传卡片
            item {
                Spacer(modifier = Modifier.height(8.dp))
                UploadCard(
                    isLoaded = uiState.isFileLoaded,
                    fileName = uiState.fileName,
                    totalClasses = uiState.totalClasses,
                    onClick = { filePicker.launch("text/*") },
                    isLoading = isLoading && progress < 1f,
                    progress = progress
                )
            }

            // AI 智能搜索（中文描述 → 自动找关键词，有记忆）
            if (uiState.isFileLoaded) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "AI 智能搜索",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "共 ${uiState.totalClasses} 个类",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "用中文描述你想找的，例如：攻击伤害、玩家、武器、宝箱",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = aiQuery,
                                onValueChange = { aiQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("说中文，AI 帮你找，如：找攻击和伤害相关的") },
                                leadingIcon = { Icon(Icons.Default.Psychology, null) },
                                trailingIcon = {
                                    if (aiQuery.isNotBlank()) {
                                        IconButton(onClick = { aiQuery = "" }) {
                                            Icon(Icons.Default.Clear, null)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    aiHint = viewModel.describeQuery(aiQuery)
                                    viewModel.aiSearch(aiQuery)
                                    recentKeywords = viewModel.getRecentKeywords()
                                    navController.navigate(Screen.Results.route)
                                },
                                enabled = aiQuery.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Search, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI 搜索", fontWeight = FontWeight.Bold)
                            }
                            if (aiHint.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    aiHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            // 最近搜索记忆（可点击重搜）
                            if (recentKeywords.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "最近搜索（记忆）",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(recentKeywords) { kw ->
                                        InputChip(
                                            selected = false,
                                            onClick = {
                                                aiQuery = kw
                                                aiHint = "记忆恢复: $kw"
                                                viewModel.aiSearch(kw)
                                                navController.navigate(Screen.Results.route)
                                            },
                                            label = { Text(kw, maxLines = 1) },
                                            leadingIcon = { Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 关键词输入区域
            if (uiState.isFileLoaded) {
                item {
                    AnimatedVisibility(
                        visible = uiState.isFileLoaded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        KeywordSection(
                            keywords = keywords,
                            onKeywordsChange = { keywords = it },
                            defaultKeywords = defaultKeywords,
                            selectedDefaults = selectedDefaults,
                            onToggleDefault = { kw ->
                                selectedDefaults = if (selectedDefaults.contains(kw)) {
                                    selectedDefaults - kw
                                } else {
                                    selectedDefaults + kw
                                }
                            },
                            onShowMore = { showKeywordSheet = true }
                        )
                    }
                }

                // 筛选按钮
                item {
                    AnimatedVisibility(
                        visible = keywords.isNotBlank() || selectedDefaults.isNotEmpty(),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val allKeywords = buildList {
                                    if (keywords.isNotBlank()) {
                                        addAll(keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                                    }
                                    addAll(selectedDefaults)
                                }
                                viewModel.filterClasses(allKeywords)
                                navController.navigate(Screen.Results.route)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.FilterAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始筛选", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 空状态提示
            if (!uiState.isFileLoaded && !isLoading) {
                item {
                    EmptyStateHint()
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showKeywordSheet) {
        KeywordBottomSheet(
            keywords = defaultKeywords,
            selected = selectedDefaults,
            onToggle = { kw ->
                selectedDefaults = if (selectedDefaults.contains(kw)) {
                    selectedDefaults - kw
                } else {
                    selectedDefaults + kw
                }
            },
            onDismiss = { showKeywordSheet = false }
        )
    }

    if (isLoading && progress < 1f) {
        LoadingDialog(progress = progress)
    }

    // 上次读取文件恢复确认弹窗（不选就不加载，需重新选 .cs）
    pendingRestore?.let { path ->
        AlertDialog(
            onDismissRequest = { viewModel.confirmRestore(false) },
            title = { Text("恢复上次读取的文件？") },
            text = {
                Text(
                    "检测到上次读取的文件：\n${path.substringAfterLast('/')}\n\n是否加载？\n选「不加载」则留在首页重新选择 .cs 文件。"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore(true) }) {
                    Text("加载", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmRestore(false) }) { Text("不加载") }
            }
        )
    }
}

@Composable
fun UploadCard(
    isLoaded: Boolean,
    fileName: String,
    totalClasses: Int,
    onClick: () -> Unit,
    isLoading: Boolean,
    progress: Float
) {
    val scale by animateFloatAsState(
        targetValue = if (isLoading) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(enabled = !isLoading) { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "正在解析文件... ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else if (isLoaded) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "共 $totalClasses 个类",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClick) {
                    Text("重新选择文件")
                }
            } else {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "点击上传 dump.cs 文件",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "支持从文件管理器选择",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun KeywordSection(
    keywords: String,
    onKeywordsChange: (String) -> Unit,
    defaultKeywords: List<String>,
    selectedDefaults: Set<String>,
    onToggleDefault: (String) -> Unit,
    onShowMore: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "关键词筛选",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = keywords,
            onValueChange = onKeywordsChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入关键词，用逗号分隔...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "预设关键词",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(defaultKeywords.take(8)) { keyword ->
                FilterChip(
                    selected = selectedDefaults.contains(keyword),
                    onClick = { onToggleDefault(keyword) },
                    label = { Text(keyword) },
                    leadingIcon = if (selectedDefaults.contains(keyword)) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
            item {
                InputChip(
                    selected = false,
                    onClick = onShowMore,
                    label = { Text("更多") },
                    trailingIcon = { Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp)) }
                )
            }
        }
    }
}

@Composable
fun EmptyStateHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.FileOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "暂无文件",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            "请先上传 dump.cs 文件开始筛选",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordBottomSheet(
    keywords: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "选择预设关键词",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            keywords.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { keyword ->
                        FilterChip(
                            selected = selected.contains(keyword),
                            onClick = { onToggle(keyword) },
                            label = { Text(keyword) },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (selected.contains(keyword)) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                    if (row.size < 3) {
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun LoadingDialog(progress: Float) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        dismissButton = {},
        title = {
            Text("正在解析", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("已解析 ${(progress * 100).toInt()}%")
            }
        }
    )
}

fun formatSize(size: Long): String {
    return when {
        size >= 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
        size >= 1024 -> "%.1f KB".format(size / 1024.0)
        else -> "$size B"
    }
}
