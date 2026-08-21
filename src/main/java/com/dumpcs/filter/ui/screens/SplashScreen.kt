package com.dumpcs.filter.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 全屏深空流星启动页动画（单次播放，播完回调 onFinished 进入主界面）。
 * 流程：
 *  0~1s    星空静止（星星缓慢明暗闪烁）
 *  1~1.8s  流星从右上向左下高速划过（头部高亮白光 + 渐变拖尾）
 *  1.7~2.4s 白色文本 wuhai 从左侧水平滑入居中
 *  2.4s    流星撞击文字瞬间：闪光 + 文字炸裂为大量随机粒子向四周扩散、渐隐
 *  2.4~3.8s 粒子消散
 *  3.8~4.1s 画面淡出，回调 onFinished
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val starCount = 150
    val meteorStartMs = 1000L          // 星空静止 1s
    val meteorDurationMs = 800L        // 流星 0.8s
    val textStartMs = meteorStartMs + meteorDurationMs - 100L
    val textSlideMs = 700L             // 文字滑入 0.7s
    val impactMs = meteorStartMs + meteorDurationMs + 100L // 撞击时刻
    val particleMs = 1400L             // 粒子消散 1.4s
    val fadeMs = 300L                  // 结尾淡出
    val totalMs = impactMs + particleMs + fadeMs

    // 星星（随机位置/大小/闪烁相位/速度）
    val stars = remember {
        List(starCount) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = 0.6f + Random.nextFloat() * 2.3f,
                phase = Random.nextFloat() * 2f * PI.toFloat(),
                speed = 1.2f + Random.nextFloat() * 3.0f
            )
        }
    }
    // 粒子（撞击时刻从文字中心炸出：随机方向/速度/大小/寿命）
    val particles = remember {
        List(200) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            Particle(
                dirX = cos(angle),
                dirY = sin(angle),
                speed = 130f + Random.nextFloat() * 460f,
                size = 1.2f + Random.nextFloat() * 3.6f,
                lifeMs = 450f + Random.nextFloat() * 950f
            )
        }
    }

    var elapsedMs by remember { mutableLongStateOf(0L) }
    val textMeasurer = rememberTextMeasurer()

    // 60fps 驱动（跟随 vsync），播完单次回调
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            elapsedMs = (now - start) / 1_000_000L
            if (elapsedMs >= totalMs) break
        }
        onFinished()
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF01030A))
    ) {
        val w = size.width
        val h = size.height
        val density = this.density

        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF01030A),
                0.5f to Color(0xFF060D22),
                1f to Color(0xFF0C1E42)
            )
        )

        val tSec = elapsedMs / 1000f
        stars.forEach { s ->
            val tw = 0.5f + 0.5f * sin(tSec * s.speed + s.phase)
            drawCircle(
                color = Color.White.copy(alpha = (0.18f + 0.72f * tw) * 0.95f),
                radius = s.size * density,
                center = Offset(s.x * w, s.y * h)
            )
        }

        if (elapsedMs >= meteorStartMs) {
            val mt = ((elapsedMs - meteorStartMs).toFloat() / meteorDurationMs).coerceIn(0f, 1f)
            val sx = w * 0.90f
            val sy = h * 0.08f
            val ex = w * 0.22f
            val ey = h * 0.90f
            val mx = sx + (ex - sx) * mt
            val my = sy + (ey - sy) * mt
            val angle = atan2(ey - sy, ex - sx)
            // 拖尾：沿运动反方向渐变拉长，透明度逐步衰减
            val tailLen = 300f * density
            val tsx = mx - cos(angle) * tailLen
            val tsy = my - sin(angle) * tailLen
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.5f)
                    ),
                    start = Offset(tsx, tsy),
                    end = Offset(mx, my)
                ),
                start = Offset(tsx, tsy),
                end = Offset(mx, my),
                strokeWidth = 7f * density
            )
            // 头部高亮白光（三层光晕）
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = 22f * density,
                center = Offset(mx, my)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = 9f * density,
                center = Offset(mx, my)
            )
            drawCircle(
                color = Color.White,
                radius = 3.2f * density,
                center = Offset(mx, my)
            )
        }

        val textStyle = TextStyle(color = Color.White, fontSize = 66.sp, fontWeight = FontWeight.Bold)
        val layout = textMeasurer.measure("wuhai", textStyle)
        val textW = layout.size.width.toFloat()
        val textH = layout.size.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        if (elapsedMs >= textStartMs && elapsedMs < impactMs + 120L) {
            val st = ((elapsedMs - textStartMs).toFloat() / textSlideMs).coerceIn(0f, 1f)
            val ease = 1f - (1f - st) * (1f - st) // easeOut
            val offX = w * (1f - ease)             // 从左侧滑入
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(cx - textW / 2f + offX, cy - textH / 2f),
                alpha = ease.coerceIn(0f, 1f)
            )
        }

        if (elapsedMs >= impactMs) {
            val pt = (elapsedMs - impactMs).toFloat()
            // 闪光（快速亮起 → 慢速衰减）
            val flashT = (pt / 420f).coerceIn(0f, 1f)
            val flashAlpha = if (flashT < 0.5f) flashT * 2f else ((1f - (flashT - 0.5f) / 0.5f)).coerceIn(0f, 1f)
            if (flashAlpha > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = flashAlpha * 0.95f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = 300f * density
                    ),
                    radius = 300f * density,
                    center = Offset(cx, cy)
                )
            }
            // 粒子：向四周扩散，慢慢淡化消失
            particles.forEach { p ->
                if (pt < p.lifeMs) {
                    val t = pt / p.lifeMs
                    val dist = p.speed * (pt / 1000f) * density
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                        radius = (p.size * density * (1f - t * 0.5f)).coerceAtLeast(0.4f * density),
                        center = Offset(cx + p.dirX * dist, cy + p.dirY * dist)
                    )
                }
            }
        }

        if (elapsedMs >= totalMs - fadeMs) {
            val ft = ((elapsedMs - (totalMs - fadeMs)).toFloat() / fadeMs).coerceIn(0f, 1f)
            drawRect(color = Color.Black.copy(alpha = ft))
        }
    }
}

private data class Star(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)
private data class Particle(val dirX: Float, val dirY: Float, val speed: Float, val size: Float, val lifeMs: Float)
