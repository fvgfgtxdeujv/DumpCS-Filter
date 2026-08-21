package com.dumpcs.filter.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dumpcs.filter.R
import com.dumpcs.filter.ui.components.CaptchaGenerator
import com.dumpcs.filter.ui.components.CaptchaTargetPreview
import com.dumpcs.filter.ui.components.ImageCaptchaGrid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 固定卡密（本地硬编码校验，无后端）——需要修改时改这里即可 */
const val FIXED_CARD_KEY = "WUHAI-8888"

/**
 * 谷歌风格卡密登录页：
 * - 输入卡密 → 弹图形人机验证（自绘九宫格，每台设备/每次都不同）
 * - 验证通过 → 校验卡密 → 播放登录音频（欢迎回来尊贵的VIP用户）→ 回调绑定
 */
@Composable
fun LoginScreen(onSuccess: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cardInput by remember { mutableStateOf("") }
    var showCaptcha by remember { mutableStateOf(false) }
    var challenge by remember {
        mutableStateOf(CaptchaGenerator.generate(deviceSeed(context), System.currentTimeMillis()))
    }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var verifyFailed by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    fun refreshChallenge() {
        challenge = CaptchaGenerator.generate(deviceSeed(context), System.currentTimeMillis())
        selected = emptySet()
        verifyFailed = false
    }

    fun playWelcome() {
        try {
            val mp = MediaPlayer.create(context, R.raw.vip_welcome)
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFDF7FF), Color(0xFFF3E8FF)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 自绘 Logo：蓝色圆底 + 白色盾牌
            Canvas(modifier = Modifier.size(88.dp)) {
                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFAB47BC), Color(0xFF7B1FA2))
                    ),
                    radius = size.minDimension / 2f
                )
                val shield = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.22f)
                    lineTo(size.width * 0.72f, size.height * 0.32f)
                    lineTo(size.width * 0.72f, size.height * 0.52f)
                    cubicTo(
                        size.width * 0.72f, size.height * 0.68f,
                        size.width * 0.62f, size.height * 0.76f,
                        size.width * 0.5f, size.height * 0.82f
                    )
                    cubicTo(
                        size.width * 0.38f, size.height * 0.76f,
                        size.width * 0.28f, size.height * 0.68f,
                        size.width * 0.28f, size.height * 0.52f
                    )
                    lineTo(size.width * 0.28f, size.height * 0.32f)
                    close()
                }
                drawPath(shield, Color.White)
                // 盾牌上的对勾
                val check = Path().apply {
                    moveTo(size.width * 0.42f, size.height * 0.52f)
                    lineTo(size.width * 0.49f, size.height * 0.60f)
                    lineTo(size.width * 0.62f, size.height * 0.42f)
                }
                drawPath(
                    check,
                    Color(0xFF7B1FA2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("DumpCS Filter", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
            Spacer(Modifier.height(6.dp))
            Text("请输入卡密以继续", fontSize = 14.sp, color = Color(0xFF5F6368))

            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = cardInput,
                onValueChange = { cardInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                label = { Text("输入卡密") },
                placeholder = { Text("XXXX-XXXX") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF5F6368)) }
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (cardInput.isBlank()) {
                        Toast.makeText(context, "请输入卡密", Toast.LENGTH_SHORT).show()
                    } else {
                        refreshChallenge()
                        showCaptcha = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
            ) {
                Text("登  录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCaptcha) {
        Dialog(
            onDismissRequest = { showCaptcha = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 任务描述
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CaptchaTargetPreview(challenge)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "请选择所有包含「${challenge.target.label}」的图片",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF202124)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ImageCaptchaGrid(
                        challenge = challenge,
                        selected = selected,
                        onToggle = { idx ->
                            selected = if (idx in selected) selected - idx else selected + idx
                            verifyFailed = false
                        }
                    )
                    if (verifyFailed) {
                        Spacer(Modifier.height(10.dp))
                        Text("验证失败，请重新选择", color = Color(0xFFD93025), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            refreshChallenge()
                            Toast.makeText(context, "已刷新验证", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("刷新")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (selected == challenge.targetIndices) {
                                    // 人机验证通过 → 校验卡密
                                    if (cardInput.trim() == FIXED_CARD_KEY) {
                                        showCaptcha = false
                                        playWelcome()
                                        showSuccess = true
                                        scope.launch {
                                            delay(1100)
                                            onSuccess()
                                        }
                                    } else {
                                        showCaptcha = false
                                        Toast.makeText(context, "卡密错误，请重新输入", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    verifyFailed = true
                                    selected = emptySet()
                                    Toast.makeText(context, "请选择所有目标图形", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
                        ) {
                            Text("验证")
                        }
                    }
                }
            }
        }
    }

    if (showSuccess) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF27B1FA2)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val check = Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.52f)
                        lineTo(size.width * 0.44f, size.height * 0.76f)
                        lineTo(size.width * 0.82f, size.height * 0.26f)
                    }
                    drawPath(
                        check,
                        Color.White,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "欢迎回来，尊贵的VIP用户",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 设备种子：ANDROID_ID 哈希 → 每台设备的验证图形不同 */
private fun deviceSeed(context: Context): Long {
    val id = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    } catch (_: Exception) {
        null
    } ?: "default-device"
    return id.hashCode().toLong()
}