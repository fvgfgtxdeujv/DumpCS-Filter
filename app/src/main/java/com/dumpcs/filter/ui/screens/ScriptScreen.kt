package com.dumpcs.filter.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dumpcs.filter.ui.viewmodel.ScriptViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptScreen(navController: NavHostController, vm: ScriptViewModel) {
    var expandedCard by remember { mutableStateOf(-1) }
    val results by vm.results.collectAsState()
    val busy by vm.busy.collectAsState()
    val lastMessage by vm.lastMessage.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("脚本工具", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("GameGuardian Lua 工具集（Kotlin 移植）", fontWeight = FontWeight.Bold)
                        Text(
                            "生成 GG 可执行的 .lua 脚本：基址写法 / 单机功能 / 菜单 / 静态基址 / 偏移指针 / 去注释。生成全程后台执行，切换页面不丢失。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // 状态条
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        if (busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text("正在后台生成，可继续操作其他功能 ...", style = MaterialTheme.typography.bodySmall)
                        }
                        if (lastMessage.isNotEmpty()) {
                            Text(lastMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (results.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("已生成 ${results.size} 个脚本", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { vm.clearResults() }) { Text("清空") }
                            }
                            results.take(5).forEach { r ->
                                Text(
                                    "· ${r.tool}：${r.fileName}（${r.size} 字节）",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                ScriptToolCard(
                    title = "① 基址写法",
                    subtitle = "生成 setPtr 指针修改脚本",
                    icon = Icons.Default.GpsFixed,
                    expanded = expandedCard == 0,
                    onToggle = { expandedCard = if (expandedCard == 0) -1 else 0 }
                ) { BaseWriteTool(vm) }
            }

            item {
                ScriptToolCard(
                    title = "② 单机功能生成器",
                    subtitle = "从 dump.cs 提取 RVA 生成热透/自瞄/范围/高跳/滑铲/无后/聚点/击杀脚本",
                    icon = Icons.Default.Build,
                    expanded = expandedCard == 1,
                    onToggle = { expandedCard = if (expandedCard == 1) -1 else 1 }
                ) { FeatureGeneratorTool(vm) }
            }

            item {
                ScriptToolCard(
                    title = "③ 菜单生成器",
                    subtitle = "生成单选/多选 GG 菜单脚本",
                    icon = Icons.Default.List,
                    expanded = expandedCard == 2,
                    onToggle = { expandedCard = if (expandedCard == 2) -1 else 2 }
                ) { MenuGeneratorTool(vm) }
            }

            item {
                ScriptToolCard(
                    title = "④ 静态基址扫描工具",
                    subtitle = "从地址列表生成静态基址脚本",
                    icon = Icons.Default.Search,
                    expanded = expandedCard == 3,
                    onToggle = { expandedCard = if (expandedCard == 3) -1 else 3 }
                ) { StaticBaseTool(vm) }
            }

            item {
                ScriptToolCard(
                    title = "⑤ 偏移指针工具",
                    subtitle = "主特征 + 副特征 + 修改项 → CZ 引擎脚本",
                    icon = Icons.Default.Share,
                    expanded = expandedCard == 4,
                    onToggle = { expandedCard = if (expandedCard == 4) -1 else 4 }
                ) { OffsetPtrTool(vm) }
            }

            item {
                ScriptToolCard(
                    title = "⑥ 去除 Lua 语言注释",
                    subtitle = "清理 -- 注释并自动备份 .h",
                    icon = Icons.Default.Delete,
                    expanded = expandedCard == 5,
                    onToggle = { expandedCard = if (expandedCard == 5) -1 else 5 }
                ) { RemoveCommentTool(vm) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ScriptToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
            ) {
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(Modifier.padding(bottom = 10.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun BaseWriteTool(vm: ScriptViewModel) {
    var module by remember { mutableStateOf(vm.getStr("base_module", "libunity.so")) }
    var chain by remember { mutableStateOf(vm.getStr("base_chain", "0x0")) }
    var type by remember { mutableStateOf(vm.getStr("base_type", "4")) }
    var value by remember { mutableStateOf(vm.getStr("base_value", "999999")) }
    var is64 by remember { mutableStateOf(vm.getBool("base_64", true)) }
    var fileName by remember { mutableStateOf(vm.getStr("base_file", "指针修改脚本.lua")) }

    OutlinedTextField(module, { module = it; vm.setStr("base_module", it) }, label = { Text("模块名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(chain, { chain = it; vm.setStr("base_chain", it) }, label = { Text("偏移链（逗号分隔）如 0x0,0x10,0x20") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(type, { type = it; vm.setStr("base_type", it) }, label = { Text("修改类型：4=Dword 16=Float 32=Qword") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(value, { value = it; vm.setStr("base_value", it) }, label = { Text("修改值") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(fileName, { fileName = it; vm.setStr("base_file", it) }, label = { Text("保存文件名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("64位/32位指针（64位=Qword）")
        Spacer(Modifier.weight(1f))
        Switch(checked = is64, onCheckedChange = { is64 = it; vm.setBool("base_64", it) })
    }
    Button(
        onClick = { vm.generateBase(module, chain, type, value, is64, fileName) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("后台生成并保存") }
}

@Composable
private fun FeatureGeneratorTool(vm: ScriptViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dumpPath by remember { mutableStateOf(vm.getStr("feature_path", "")) }
    var methodMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var missingText by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val p = withContext(Dispatchers.IO) { copyToCache(context, uri, "dumpcs") }
                if (p != null) {
                    dumpPath = p
                    vm.setStr("feature_path", p)
                    vm.parseDump(p) { map, msg ->
                        methodMap = map
                        missingText = msg
                    }
                } else {
                    Toast.makeText(context, "无法读取所选文件（权限或复制失败）", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = dumpPath,
            onValueChange = { dumpPath = it; vm.setStr("feature_path", it) },
            label = { Text("dump.cs 路径") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(onClick = { picker.launch("*/*") }) {
            Icon(Icons.Default.FolderOpen, contentDescription = "选择 dump.cs")
        }
        IconButton(onClick = {
            if (dumpPath.isNotBlank()) vm.parseDump(dumpPath) { map, msg ->
                methodMap = map
                missingText = msg
            }
        }) {
            Icon(Icons.Default.Refresh, contentDescription = "重新解析")
        }
    }
    if (methodMap.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
            Column(Modifier.padding(10.dp)) {
                Text("提取结果（RVA）：", fontWeight = FontWeight.Bold)
                methodMap.forEach { (k, v) ->
                    Text("$k → $v", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                if (missingText.isNotEmpty()) Text(missingText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            if (dumpPath.isBlank()) {
                Toast.makeText(context, "请先选择 dump.cs 文件", Toast.LENGTH_SHORT).show()
            } else vm.generateFeature(dumpPath)
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("后台生成功能脚本 + 击杀特效") }
}

@Composable
private fun MenuGeneratorTool(vm: ScriptViewModel) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(vm.getInt("menu_mode", 1)) }
    var layers by remember { mutableStateOf(vm.getStr("menu_layers", "3")) }
    var funcs by remember { mutableStateOf(vm.getStr("menu_funcs", "5")) }
    var fileName by remember { mutableStateOf(vm.getStr("menu_file", "菜单脚本.lua")) }

    Text("模式：")
    Row {
        listOf(1 to "单选(无圆)", 2 to "单选(有圆)", 3 to "多选").forEach { (m, label) ->
            FilterChip(
                selected = mode == m,
                onClick = { mode = m; vm.setInt("menu_mode", m) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(layers, { layers = it; vm.setStr("menu_layers", it) }, label = { Text("层数（主菜单选项个数）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(funcs, { funcs = it; vm.setStr("menu_funcs", it) }, label = { Text("功能数（每个子菜单功能数）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(fileName, { fileName = it; vm.setStr("menu_file", it) }, label = { Text("保存文件名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            val L = layers.toIntOrNull() ?: 0
            val N = funcs.toIntOrNull() ?: 0
            if (L < 1 || N < 1) {
                Toast.makeText(context, "请输入有效的正整数", Toast.LENGTH_SHORT).show()
            } else vm.generateMenu(L, N, mode, fileName)
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("后台生成并保存") }
}

@Composable
private fun StaticBaseTool(vm: ScriptViewModel) {
    var itemsText by remember { mutableStateOf(vm.getStr("static_items", "libunity.so|0x1234|4|0")) }
    var newValue by remember { mutableStateOf(vm.getStr("static_newvalue", "")) }
    var freeze by remember { mutableStateOf(vm.getBool("static_freeze", false)) }
    var fileName by remember { mutableStateOf(vm.getStr("static_file", "静态基址.lua")) }

    OutlinedTextField(
        itemsText, { itemsText = it; vm.setStr("static_items", it) },
        label = { Text("地址列表：每行 模块|偏移|类型|值") },
        modifier = Modifier.fillMaxWidth().height(120.dp)
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(newValue, { newValue = it; vm.setStr("static_newvalue", it) }, label = { Text("修改值（留空保持原值）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(fileName, { fileName = it; vm.setStr("static_file", it) }, label = { Text("保存文件名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("冻结")
        Spacer(Modifier.weight(1f))
        Switch(checked = freeze, onCheckedChange = { freeze = it; vm.setBool("static_freeze", it) })
    }
    Button(
        onClick = { vm.generateStatic(itemsText, newValue, freeze, fileName) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("后台生成并保存") }
}

@Composable
private fun OffsetPtrTool(vm: ScriptViewModel) {
    var name by remember { mutableStateOf(vm.getStr("ptr_name", "测试")) }
    var mainValue by remember { mutableStateOf(vm.getStr("ptr_mainvalue", "0")) }
    var mainFlags by remember { mutableStateOf(vm.getStr("ptr_mainflags", "4")) }
    var mainRange by remember { mutableStateOf(vm.getStr("ptr_mainrange", "2")) }
    var subText by remember { mutableStateOf(vm.getStr("ptr_sub", "")) }
    var modText by remember { mutableStateOf(vm.getStr("ptr_mod", "")) }
    var mode by remember { mutableStateOf(vm.getStr("ptr_mode", "修改")) }
    var fileName by remember { mutableStateOf(vm.getStr("ptr_file", "偏移指针.lua")) }

    OutlinedTextField(name, { name = it; vm.setStr("ptr_name", it) }, label = { Text("脚本名字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(mainValue, { mainValue = it; vm.setStr("ptr_mainvalue", it) }, label = { Text("主特征值") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(mainFlags, { mainFlags = it; vm.setStr("ptr_mainflags", it) }, label = { Text("类型") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(mainRange, { mainRange = it; vm.setStr("ptr_mainrange", it) }, label = { Text("范围码") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Text("范围码：Jh=2 Ch=1 Ca=4 Cd=8 Cb=16 Ps=262144 A=32 J=65536 S=64 As=524288 V=1048576 O=-2080896 B=131072 Xa=16384 Xs=32768", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(subText, { subText = it; vm.setStr("ptr_sub", it) }, label = { Text("副特征：每行 值|类型|相对偏移") }, modifier = Modifier.fillMaxWidth().height(80.dp))
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(modText, { modText = it; vm.setStr("ptr_mod", it) }, label = { Text("修改项：每行 新值|类型|相对偏移|冻结(true/false)") }, modifier = Modifier.fillMaxWidth().height(80.dp))
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(fileName, { fileName = it; vm.setStr("ptr_file", it) }, label = { Text("保存文件名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row {
        listOf("修改", "载入").forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { mode = m; vm.setStr("ptr_mode", m) },
                label = { Text(m) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            vm.generateOffsetPtr(name, mainValue, mainFlags.toIntOrNull() ?: 4, mainRange.toIntOrNull() ?: 2, subText, modText, mode, fileName)
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("后台生成并保存") }
}

@Composable
private fun RemoveCommentTool(vm: ScriptViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filePath by remember { mutableStateOf(vm.getStr("clean_path", "")) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val p = withContext(Dispatchers.IO) { copyToCache(context, uri, "lua") }
                if (p != null) {
                    filePath = p
                    vm.setStr("clean_path", p)
                } else {
                    Toast.makeText(context, "无法读取所选文件（权限或复制失败）", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = filePath,
            onValueChange = { filePath = it; vm.setStr("clean_path", it) },
            label = { Text("Lua 文件路径") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(onClick = { picker.launch("*/*") }) {
            Icon(Icons.Default.FolderOpen, contentDescription = "选择 Lua 文件")
        }
    }
    Spacer(Modifier.height(6.dp))
    Button(
        onClick = {
            if (filePath.isBlank()) {
                Toast.makeText(context, "请选择 Lua 文件", Toast.LENGTH_SHORT).show()
            } else vm.cleanComments(filePath)
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("后台去除注释并备份") }
}

/** 把 SAF 选中的文件复制到缓存目录，返回真实路径 */
private fun copyToCache(context: Context, uri: Uri, prefix: String): String? {
    return try {
        if (uri.scheme == "file") {
            val p = uri.path
            if (!p.isNullOrBlank() && File(p).exists()) return p
        }
        var name = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx) ?: ""
        }
        if (name.isBlank()) name = uri.lastPathSegment ?: "$prefix.bin"
        val cacheDir = File(context.cacheDir, "script_input").apply { mkdirs() }
        val target = File(cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                }
            }
        }
        target.absolutePath
    } catch (e: Exception) {
        null
    }
}