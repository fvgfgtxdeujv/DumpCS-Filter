package com.dumpcs.filter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dumpcs.filter.data.AiChatEntity
import com.dumpcs.filter.ui.MainViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(navController: NavController, viewModel: MainViewModel) {
    val chats by viewModel.aiChats.collectAsStateWithLifecycle()
    val currentCls by viewModel.currentExplainClass.collectAsStateWithLifecycle()
    val playedAnimIds by viewModel.playedAnimIds.collectAsStateWithLifecycle()
    val typingSpeed by viewModel.typingSpeedMs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    // 新消息到达时自动滚到底部（流畅跟随）
    val lastCount = chats.size
    LaunchedEffect(lastCount) {
        if (lastCount > 0) {
            listState.animateScrollToItem(lastCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            currentCls?.className ?: "AI 解释",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            "猫娘 AI 讲解 · ${chats.size} 条记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空记录")
                    }
                }
            )
        },
        bottomBar = {
            // 底部输入栏：继续追问 AI
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("问猫娘...如：找透视相关的 / 它有什么用？") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (input.isNotBlank()) {
                                    viewModel.sendChat(input)
                                    input = ""
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.sendChat(input)
                                input = ""
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    ) { padding ->
        if (chats.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Pets,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "还没有聊天记录喵~",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    "可以直接说出想法让我帮找类，比如：找透视相关的、找武器伤害的；\n也可以在筛选结果里点某个类的「AI 解释」，猫娘就来啦~",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                    ChatBubble(
                        chat = chat,
                        typingSpeedMs = typingSpeed,
                        animateAi = index == chats.lastIndex && chat.id !in playedAnimIds,
                        onTypingDone = { viewModel.markTypingDone(it) }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空解释记录？") },
            text = { Text("会把所有猫娘解释的聊天记录都删掉喵~确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAiChats()
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

/** 单条聊天记录：用户气泡（右/主题色）+ AI 气泡（左/次级色 + 猫头像 + 打字机动画） */
@Composable
fun ChatBubble(
    chat: AiChatEntity,
    typingSpeedMs: Int = 18,
    animateAi: Boolean = false,
    onTypingDone: (Long) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 用户消息：右侧气泡
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn(tween(250))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Bubble(
                    text = chat.userMsg,
                    isUser = true,
                    bubbleColor = MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    typing = false
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // AI 猫娘消息：左侧气泡 + 头像 + 打字机效果
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // 猫娘头像
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Pets,
                    contentDescription = "猫娘",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "猫娘 AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Bubble(
                    text = chat.aiMsg,
                    isUser = false,
                    typingSpeedMs = typingSpeedMs,
                    bubbleColor = MaterialTheme.colorScheme.secondaryContainer,
                    textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    typing = animateAi,
                    onTypingDone = { onTypingDone(chat.id) }
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            formatTime(chat.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.align(
                if (chat.userMsg.startsWith("帮我解释")) Alignment.End else Alignment.Start
            )
        )
    }
}

/** 气泡本体：支持打字机逐字显示（仅最新一条 AI 消息自动播放，历史记录直接完整显示） */
@Composable
fun Bubble(
    text: String,
    isUser: Boolean,
    bubbleColor: Color,
    textColor: Color,
    typingSpeedMs: Int = 18,
    typing: Boolean,
    onTypingDone: () -> Unit = {}
) {
    val maxLen = text.length
    // key 绑定 text+typing：切换时立即重置状态（不会出现"上一条停在半截"的 bug）
    var revealed by remember(text, typing) {
        mutableStateOf(if (isUser || !typing) maxLen else 0)
    }

    // 打字机：逐字 reveal（速度可在设置里调，默认 18ms/字符），播完回调标记完成
    LaunchedEffect(text, typing) {
        if (!isUser && typing) {
            revealed = 0
            while (revealed < maxLen) {
                delay(typingSpeedMs.toLong())
                revealed = (revealed + 1).coerceAtMost(maxLen)
            }
            onTypingDone()
        } else {
            revealed = maxLen
        }
    }

    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(200),
        label = "bubbleAlpha"
    )

    val shown = if (isUser) text else text.substring(0, revealed.coerceIn(0, maxLen))
    val isTypingNow = !isUser && revealed < maxLen

    Box(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                )
            )
            .background(bubbleColor.copy(alpha = alpha))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = shown + if (isTypingNow) "▍" else "",
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp
        )
    }
}

private fun formatTime(ts: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
}