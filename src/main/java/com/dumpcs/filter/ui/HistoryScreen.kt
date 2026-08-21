package com.dumpcs.filter.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dumpcs.filter.data.HistoryEntity
import com.dumpcs.filter.ui.Screen
import com.dumpcs.filter.ui.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController, viewModel: MainViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val files by viewModel.exportedFiles.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var sortBy by remember { mutableStateOf("newest") }
    var filterKeyword by remember { mutableStateOf("") }

    val filteredHistory = remember(history, sortBy, filterKeyword) {
        var list = history
        if (filterKeyword.isNotBlank()) {
            list = list.filter { h ->
                h.keywords.contains(filterKeyword, ignoreCase = true) ||
                    h.fileName.contains(filterKeyword, ignoreCase = true)
            }
        }
        list = when (sortBy) {
            "newest" -> list.sortedByDescending { it.timestamp }
            "oldest" -> list.sortedBy { it.timestamp }
            "results" -> list.sortedByDescending { it.resultCount }
            else -> list.sortedByDescending { it.timestamp }
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史记录", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("筛选历史") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("导出文件") })
            }

            if (selectedTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filterKeyword,
                        onValueChange = { filterKeyword = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("搜索关键词/文件名") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showSortMenu = true },
                            label = { Text("排序: $sortBy") },
                            leadingIcon = { Icon(Icons.Default.Sort, null, modifier = Modifier.size(16.dp)) }
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf("newest" to "最新优先", "oldest" to "最早优先", "results" to "结果最多").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        sortBy = value
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                HistoryList(history = filteredHistory, viewModel = viewModel, navController = navController)
            } else {
                FileList(files = files, navController = navController, viewModel = viewModel)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清空") },
            text = { Text("确定要清空所有历史记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun HistoryList(
    history: List<HistoryEntity>,
    viewModel: MainViewModel,
    navController: NavController
) {
    if (history.isEmpty()) {
        EmptyHistory(message = "暂无筛选历史")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history, key = { it.id }) { item ->
                HistoryItemCard(item = item, onDelete = { viewModel.deleteHistoryItem(item.id) })
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun HistoryItemCard(item: HistoryEntity, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.fileName,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        dateFormat.format(Date(item.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("删除记录") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                onDelete()
                                showMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "关键词: ${item.keywords}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${item.resultCount} 个结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun FileList(files: List<File>, navController: NavController, viewModel: MainViewModel) {
    if (files.isEmpty()) {
        EmptyHistory(message = "暂无导出文件")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(files) { file ->
                FileItemCard(file = file, navController = navController, viewModel = viewModel)
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun FileItemCard(file: File, navController: NavController, viewModel: MainViewModel) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val encodedPath = java.net.URLEncoder.encode(file.absolutePath, "UTF-8")
                navController.navigate(Screen.FileViewer.createRoute(encodedPath))
            },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${dateFormat.format(Date(file.lastModified()))} · ${formatFileSize(file.length())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                viewModel.deleteFile(file)
                viewModel.loadExportedFiles()
            }) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun EmptyHistory(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size >= 1024 * 1024 -> "%.2f MB".format(size / (1024.0 * 1024.0))
        size >= 1024 -> "%.2f KB".format(size / 1024.0)
        else -> "$size B"
    }
}
