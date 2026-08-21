package com.dumpcs.filter.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dumpcs.filter.data.ClassInfo
import com.dumpcs.filter.data.DumpMember
import com.dumpcs.filter.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(navController: NavController, viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val result = uiState.filterResult
    var selectedClass by remember { mutableStateOf<ClassInfo?>(null) }
    var selectedMemberIdx by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("筛选结果", fontWeight = FontWeight.Bold)
                        Text(
                            "${result?.classes?.size ?: 0} 个匹配类",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (result != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.Home, contentDescription = "重新搜索")
                        }
                        IconButton(onClick = { navController.navigate(com.dumpcs.filter.ui.Screen.History.route) }) {
                            Icon(Icons.Default.History, contentDescription = "历史")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在筛选 ${uiState.totalClasses} 个类...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            result == null -> {
                EmptyResults(
                    modifier = Modifier.padding(padding),
                    totalClasses = uiState.totalClasses,
                    keywords = emptyList(),
                    notFilteredYet = true
                )
            }
            result.classes.isEmpty() -> {
                EmptyResults(
                    modifier = Modifier.padding(padding),
                    totalClasses = uiState.totalClasses,
                    keywords = result.keywords
                )
            }
            else -> {
                ResultList(
                    result = result,
                    visibleCount = uiState.visibleCount,
                    onLoadMore = { viewModel.expandResults() },
                    modifier = Modifier.padding(padding),
                    onExplain = { cls ->
                        viewModel.prepareExplain(cls)
                        navController.navigate(com.dumpcs.filter.ui.Screen.AiChat.route)
                    },
                    onMemberClick = { cls, idx ->
                        selectedClass = cls
                        selectedMemberIdx = idx
                    }
                )
            }
        }
    }

    selectedClass?.let { cls ->
        selectedMemberIdx?.let { idx ->
            val member = viewModel.getDumpMemberById(cls.className, idx)
                ?: viewModel.getDumpMemberById(cls.fullName, idx)
            member?.let { m ->
                MemberDetailDialog(member = m, typeName = cls.className, onDismiss = { selectedMemberIdx = null })
            }
        }
        if (selectedMemberIdx == null) selectedClass = null
    }
}

@Composable
fun ResultList(
    result: com.dumpcs.filter.data.FilterResult,
    visibleCount: Int = 200,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
    onExplain: (ClassInfo) -> Unit = {},
    onMemberClick: (ClassInfo, Int) -> Unit = { _, _ -> }
) {
    val visible = result.classes.take(visibleCount)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ResultSummaryCard(result) }
        items(visible.size) { index ->
            val cls = visible[index]
            ClassCard(classInfo = cls, onExplain = { onExplain(cls) }, onMemberClick = onMemberClick)
        }
        if (visibleCount < result.classes.size) {
            item {
                FilledTonalButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) { Text("加载更多（还剩 ${result.classes.size - visibleCount} 条）") }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun ResultSummaryCard(result: com.dumpcs.filter.data.FilterResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("筛选概览", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoItem("关键词", result.keywords.joinToString(", "))
                InfoItem("匹配数", "${result.classes.size}")
            }
            if (result.excludedHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(result.excludedHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ClassCard(classInfo: ClassInfo, onExplain: (ClassInfo) -> Unit = {}, onMemberClick: (ClassInfo, Int) -> Unit = { _, _ -> }) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, animationSpec = tween(300))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(classInfo.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (classInfo.parentClass.isNotBlank()) "${classInfo.namespace.ifBlank { "(无命名空间)" }} : ${classInfo.parentClass}"
                        else classInfo.namespace.ifBlank { "(无命名空间)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BadgedBox(badge = { if (classInfo.methods.isNotEmpty()) Badge { Text("${classInfo.methods.size}") } }) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    FilledTonalButton(
                        onClick = { onExplain(classInfo) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 解释（猫娘讲解）", fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (classInfo.fields.isNotEmpty()) {
                        Text("字段（含偏移量）", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        classInfo.fields.forEach { field ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onMemberClick(classInfo, classInfo.methods.size + classInfo.fields.indexOf(field)) }) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("• ${field.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    if (field.offset.isNotBlank()) {
                                        Text(field.offset, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (classInfo.methods.isNotEmpty()) {
                        Text("方法（RVA / Offset / VA）", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        classInfo.methods.forEach { method ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onMemberClick(classInfo, classInfo.methods.indexOf(method)) }) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("• ${method.name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(method.rva, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                }
                                val addrParts = buildList {
                                    if (method.offset.isNotBlank()) add("Off ${method.offset}")
                                    if (method.va.isNotBlank()) add("VA ${method.va}")
                                }
                                if (addrParts.isNotEmpty()) {
                                    Text(addrParts.joinToString("  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemberDetailDialog(member: DumpMember, typeName: String, onDismiss: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    fun copyText(text: String) { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("成员详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("类型", typeName)
                InfoRow("种类", member.kind.name)
                InfoRow("名称", member.name)
                InfoRow("签名", member.signature.ifBlank { member.details })
                InfoRow("RVA", "0x${member.rva.toString(16)}", onCopy = { copyText("0x${member.rva.toString(16)}") })
                InfoRow("Offset", "0x${member.offset.toString(16)}", onCopy = { copyText("0x${member.offset.toString(16)}") })
                InfoRow("VA", "0x${member.va.toString(16)}", onCopy = { copyText("0x${member.va.toString(16)}") })
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        onCopy?.let { TextButton(onClick = it) { Text("复制") } }
    }
}

@Composable
fun EmptyResults(
    modifier: Modifier = Modifier,
    totalClasses: Int = 0,
    keywords: List<String> = emptyList(),
    notFilteredYet: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.SearchOff, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (notFilteredYet) "还没有筛选结果" else "未找到匹配结果",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (notFilteredYet) {
            Text("请先返回首页选择关键词或使用 AI 智能搜索", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outlineVariant, textAlign = TextAlign.Center)
        } else if (totalClasses > 0) {
            Text("已解析 $totalClasses 个类，关键词 [${keywords.joinToString(", ")}] 无匹配", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outlineVariant, textAlign = TextAlign.Center)
        } else {
            Text("请先返回首页加载 dump.cs 文件并筛选", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI 建议", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "回首页用「AI 智能搜索」输入中文描述试试，例如：攻击伤害、玩家、武器、宝箱、透视。AI 会自动扩展同义词，命中率更高。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
