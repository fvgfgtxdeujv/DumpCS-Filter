package com.dumpcs.filter.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dumpcs.filter.music.MusicPlayer
import com.dumpcs.filter.ui.MainViewModel
import com.dumpcs.filter.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()

    // AI 设置（异步 Flow → 本地状态）
    val typingSpeed by viewModel.typingSpeedMs.collectAsStateWithLifecycle()
    val replyDetail by viewModel.replyDetail.collectAsStateWithLifecycle()
    val showFeedback by viewModel.showFeedback.collectAsStateWithLifecycle()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val modelName by viewModel.modelName.collectAsStateWithLifecycle()

    var speed by remember { mutableFloatStateOf(18f) }
    var detail by remember { mutableStateOf("standard") }
    var feedback by remember { mutableStateOf(true) }
    var baseUrlInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var savedTip by remember { mutableStateOf(false) }

    val musicContext = LocalContext.current
    val musicPrefs = remember {
        musicContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
    var musicEnabled by remember { mutableStateOf(musicPrefs.getBoolean("music_enabled", true)) }
    val musicTrack by MusicPlayer.currentTrack.collectAsStateWithLifecycle()
    val musicPlaying by MusicPlayer.isPlaying.collectAsStateWithLifecycle()
    val musicCount by MusicPlayer.trackCount.collectAsStateWithLifecycle()
    var importing by remember { mutableStateOf(false) }

    val importMusicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                val path = withContext(Dispatchers.IO) {
                    MusicPlayer.importMusic(musicContext, uri)
                }
                importing = false
                Toast.makeText(
                    musicContext,
                    if (path != null) "🎵 歌曲已导入并加入随机播放" else "导入失败，请重试",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun toggleMusic(on: Boolean) {
        musicEnabled = on
        musicPrefs.edit().putBoolean("music_enabled", on).apply()
        if (on) {
            scope.launch(Dispatchers.IO) {
                if (!MusicPlayer.loaded) MusicPlayer.loadTracks(musicContext)
                MusicPlayer.playRandom(musicContext)
            }
            Toast.makeText(musicContext, "背景音乐已开启，随机播放中 🎶", Toast.LENGTH_SHORT).show()
        } else {
            MusicPlayer.stop()
            Toast.makeText(musicContext, "背景音乐已关闭（之后不再自动播放）", Toast.LENGTH_SHORT).show()
        }
    }

    // Flow 值到达后同步到本地编辑状态
    LaunchedEffect(typingSpeed, replyDetail, showFeedback, apiBaseUrl, apiKey, modelName) {
        speed = typingSpeed.toFloat()
        detail = replyDetail
        feedback = showFeedback
        baseUrlInput = apiBaseUrl
        apiKeyInput = apiKey
        modelInput = modelName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
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
                SectionTitle("AI 设置（输出率 / 输中率 / 反馈）")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 打字机输出速度
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("打字机输出速度", fontWeight = FontWeight.Medium)
                                Text("数值越小字出得越快", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${speed.toInt()} ms/字", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = speed,
                            onValueChange = { speed = it },
                            valueRange = 4f..60f,
                            steps = 10
                        )

                        // 回复详细度
                        Text("回复详细度", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailChip("简洁", "brief", detail) { detail = it }
                            DetailChip("标准", "standard", detail) { detail = it }
                            DetailChip("详细", "detailed", detail) { detail = it }
                        }

                        // 反馈结果开关
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("显示反馈结果", fontWeight = FontWeight.Medium)
                                Text("筛选时显示命中关键词/自动剔除数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = feedback, onCheckedChange = { feedback = it })
                        }

                        // 保存
                        FilledTonalButton(
                            onClick = {
                                viewModel.saveAiSettings(speed.toInt(), detail, feedback)
                                savedTip = true
                                scope.launch { kotlinx.coroutines.delay(1500); savedTip = false }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("保存 AI 设置")
                        }
                        if (savedTip) {
                            Text("已保存喵~", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                SectionTitle("自定义 AI 中转站（第三方 API）")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "配置后 AI 解释/追问/筛选回复由真实大模型生成；不填则使用内置离线猫娘引擎。支持任意 OpenAI 兼容接口（中转站、自建、官方都行）喵~",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = baseUrlInput,
                            onValueChange = { baseUrlInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.openai.com 或中转站地址") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("模型名") },
                            placeholder = { Text("gpt-3.5-turbo / deepseek-chat / glm-4 等") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.saveApiConfig(baseUrlInput, apiKeyInput, modelInput)
                                    savedTip = true
                                    scope.launch { kotlinx.coroutines.delay(1500); savedTip = false }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(6.dp))
                                Text("保存配置")
                            }
                            Button(
                                onClick = {
                                    testing = true
                                    testResult = null
                                    viewModel.testApi { ok, msg ->
                                        testing = false
                                        testResult = (if (ok) "✅ " else "❌ ") + msg
                                    }
                                },
                                enabled = !testing,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (testing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudDone, null)
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(if (testing) "测试中" else "测试连接")
                            }
                        }
                        if (testResult != null) {
                            Text(
                                testResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testResult!!.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle("背景音乐")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("背景音乐", fontWeight = FontWeight.Medium)
                                Text(
                                    "进入应用自动随机播放，播完自动切下一首，每次进入顺序都不一样",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = musicEnabled, onCheckedChange = { toggleMusic(it) })
                        }

                        val currentMusic = musicTrack
                        if (musicEnabled && currentMusic != null) {
                            val track = currentMusic
                            // 当前播放卡片
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val art = track.albumArt
                                    if (art != null) {
                                        Image(
                                            bitmap = art.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    } else {
                                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        track.title,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { MusicPlayer.toggle() }) {
                                    Icon(
                                        if (musicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        if (musicPlaying) "暂停" else "继续"
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch(Dispatchers.IO) { MusicPlayer.playNext(musicContext) }
                                }) {
                                    Icon(Icons.Default.SkipNext, "下一首")
                                }
                            }
                        } else if (musicEnabled) {
                            Text(
                                if (musicCount > 0) "已加载 $musicCount 首歌曲，正在播放…" else "正在扫描设备音乐…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 导入歌曲
                        FilledTonalButton(
                            onClick = { importMusicLauncher.launch(arrayOf("audio/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !importing
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (importing) "导入中…" else "导入歌曲")
                        }
                    }
                }
            }

            item {
                SectionTitle("关于")
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AppShortcut,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("DumpCS Filter", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("版本 1.1.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row {
                                    OutlinedButton(
                                        onClick = { viewModel.checkUpdateNow() },
                                        enabled = viewModel.updateManager.status == UpdateStatus.IDLE || viewModel.updateManager.status == UpdateStatus.UP_TO_DATE
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("检查更新", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "一款用于筛选 Il2CppDumper 导出 dump.cs 文件中类名的工具。支持 AI 智能搜索、猫娘解释、对话式筛选、偏移量展示、第三方 AI 接口接入等功能。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "作者 by:wuhai",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle("功能说明")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingItem(
                            icon = Icons.Default.UploadFile,
                            title = "上传 dump.cs",
                            subtitle = "从文件管理器选择 Il2CppDumper 导出的文件"
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingItem(
                            icon = Icons.Default.Psychology,
                            title = "AI 智能搜索",
                            subtitle = "说中文自动找类，支持精准语义过滤（如透视→保留红透热透、剔除连接桥梁）"
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingItem(
                            icon = Icons.Default.Pets,
                            title = "猫娘 AI 聊天",
                            subtitle = "解释每个类 + 对话式筛选：直接说想法，AI 帮你筛出最终结果"
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingItem(
                            icon = Icons.Default.Save,
                            title = "自动导出",
                            subtitle = "筛选结果自动保存到 /sdcard/DumpCSFilter/"
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingItem(
                            icon = Icons.Default.History,
                            title = "历史记录",
                            subtitle = "本地数据库保存筛选历史，支持查看和删除"
                        )
                    }
                }
            }

            item {
                SectionTitle("提示")
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "• 首次使用需要授予存储权限\n• 筛选结果会自动导出到 /sdcard/DumpCSFilter/ 目录\n• 历史记录保存在本地数据库中\n• 大文件解析可能需要一些时间，请耐心等待\n• 退出应用后会自动恢复上次加载的文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun DetailChip(label: String, value: String, current: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) }
    )
}

@Composable
fun SettingItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}