package com.dumpcs.filter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 图形人机验证（Google reCAPTCHA 风格，纯 Canvas 自绘，无 Emoji / 无网络 / 无后端）
 * - 每次挑战随机：目标图形、位置、颜色、旋转、大小全部随机
 * - 每台设备不同：以 ANDROID_ID 哈希为随机种子
 */
enum class ShapeType(val label: String) {
    STAR("五角星"), CIRCLE("圆形"), TRIANGLE("三角形"), SQUARE("正方形"),
    DIAMOND("菱形"), HEART("爱心"), MOON("月牙"), CROSS("十字"),
    PENTAGON("五边形"), HEXAGON("六边形"), ARROW("箭头"), DROP("水滴")
}

/** 九宫格中的一个图形（类型 + 随机颜色/旋转/缩放） */
data class GridShape(
    val type: ShapeType,
    val color: Color,
    val rotation: Float,
    val scale: Float
)

/** 一次完整挑战 */
data class CaptchaChallenge(
    val target: ShapeType,
    val grid: List<GridShape>,          // 9 个格子
    val targetIndices: Set<Int>         // 目标图形所在格子下标
)

/** 挑战生成器：设备种子 + 时间种子 → 每次、每台设备都不同 */
object CaptchaGenerator {

    // Google 风格柔和色板
    private val palette = listOf(
        Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853),
        Color(0xFFFF6D00), Color(0xFF9C27B0), Color(0xFF00ACC1), Color(0xFF5D4037),
        Color(0xFF1E88E5), Color(0xFFD81B60), Color(0xFFF4511E), Color(0xFF00897B)
    )

    fun generate(deviceSeed: Long, timeSeed: Long): CaptchaChallenge {
        val rnd = Random(deviceSeed * 31 + timeSeed)
        val target = ShapeType.entries[rnd.nextInt(ShapeType.entries.size)]
        // 目标数量 2~4 个
        val targetCount = rnd.nextInt(3) + 2
        val indices = (0 until 9).shuffled(rnd).take(targetCount).toSet()
        val grid = (0 until 9).map { i ->
            val type = if (i in indices) target else randomOther(rnd, target)
            GridShape(
                type = type,
                color = palette[rnd.nextInt(palette.size)],
                rotation = rnd.nextFloat() * 360f,
                scale = 0.8f + rnd.nextFloat() * 0.4f
            )
        }
        return CaptchaChallenge(target, grid, indices)
    }

    private fun randomOther(rnd: Random, target: ShapeType): ShapeType {
        var t = target
        while (t == target) t = ShapeType.entries[rnd.nextInt(ShapeType.entries.size)]
        return t
    }
}

/**
 * 在画布中绘制一个图形（中心对齐，支持旋转/缩放）
 * @param bg 格子背景色（月牙遮挡用）
 */
fun DrawScope.drawCaptchaShape(
    shape: GridShape,
    center: Offset,
    size: Float,
    bg: Color
) {
    rotate(shape.rotation, pivot = center) {
        scale(shape.scale, pivot = center) {
            when (shape.type) {
                ShapeType.CIRCLE -> drawCircle(shape.color, size / 2f, center)
                ShapeType.SQUARE -> drawRect(
                    shape.color,
                    topLeft = Offset(center.x - size / 2f, center.y - size / 2f),
                    size = Size(size, size)
                )
                ShapeType.TRIANGLE -> drawPath(
                    polygonPath(center, size / 2f, 3, 0f), shape.color
                )
                ShapeType.STAR -> drawPath(
                    starPath(center, size / 2f, size / 2f * 0.45f), shape.color
                )
                ShapeType.DIAMOND -> drawPath(
                    polygonPath(center, size / 2f, 4, 0f), shape.color
                )
                ShapeType.PENTAGON -> drawPath(
                    polygonPath(center, size / 2f, 5, 0f), shape.color
                )
                ShapeType.HEXAGON -> drawPath(
                    polygonPath(center, size / 2f, 6, 0f), shape.color
                )
                ShapeType.HEART -> drawHeart(shape.color, center, size)
                ShapeType.MOON -> drawMoon(shape.color, center, size, bg)
                ShapeType.CROSS -> drawCross(shape.color, center, size)
                ShapeType.ARROW -> drawPath(arrowPath(center, size), shape.color)
                ShapeType.DROP -> drawPath(dropPath(center, size), shape.color)
            }
        }
    }
}

/* ================= 图形路径 ================= */

private fun polygonPath(center: Offset, radius: Float, sides: Int, startAngle: Float): Path {
    val p = Path()
    val step = 2.0 * PI / sides
    var angle = startAngle.toDouble() - PI / 2.0
    p.moveTo(
        center.x + (radius * cos(angle)).toFloat(),
        center.y + (radius * sin(angle)).toFloat()
    )
    repeat(sides - 1) {
        angle += step
        p.lineTo(
            center.x + (radius * cos(angle)).toFloat(),
            center.y + (radius * sin(angle)).toFloat()
        )
    }
    p.close()
    return p
}

private fun starPath(center: Offset, outer: Float, inner: Float): Path {
    val p = Path()
    val step = PI / 5.0
    var angle = -PI / 2.0
    p.moveTo(
        center.x + (outer * cos(angle)).toFloat(),
        center.y + (outer * sin(angle)).toFloat()
    )
    repeat(10) { i ->
        val r = if (i % 2 == 0) inner else outer
        angle += step
        p.lineTo(
            center.x + (r * cos(angle)).toFloat(),
            center.y + (r * sin(angle)).toFloat()
        )
    }
    p.close()
    return p
}

private fun DrawScope.drawHeart(color: Color, center: Offset, size: Float) {
    // 两个圆 + 一个倒三角 拼成心形
    val r = size * 0.30f
    drawCircle(color, r, Offset(center.x - size * 0.20f, center.y - size * 0.10f))
    drawCircle(color, r, Offset(center.x + size * 0.20f, center.y - size * 0.10f))
    val tri = Path().apply {
        moveTo(center.x, center.y + size * 0.44f)
        lineTo(center.x - size * 0.52f, center.y - size * 0.02f)
        lineTo(center.x + size * 0.52f, center.y - size * 0.02f)
        close()
    }
    drawPath(tri, color)
}

private fun DrawScope.drawMoon(color: Color, center: Offset, size: Float, bg: Color) {
    drawCircle(color, size * 0.42f, center)
    // 用背景色偏移圆切出月牙
    drawCircle(bg, size * 0.38f, Offset(center.x + size * 0.16f, center.y - size * 0.12f))
}

private fun DrawScope.drawCross(color: Color, center: Offset, size: Float) {
    val w = size * 0.28f
    drawRoundRect(
        color,
        topLeft = Offset(center.x - w / 2f, center.y - size * 0.42f),
        size = Size(w, size * 0.84f),
        cornerRadius = CornerRadius(w / 2f, w / 2f)
    )
    drawRoundRect(
        color,
        topLeft = Offset(center.x - size * 0.42f, center.y - w / 2f),
        size = Size(size * 0.84f, w),
        cornerRadius = CornerRadius(w / 2f, w / 2f)
    )
}

private fun arrowPath(center: Offset, size: Float): Path {
    val p = Path()
    p.moveTo(center.x - size * 0.42f, center.y - size * 0.12f)
    p.lineTo(center.x + size * 0.12f, center.y - size * 0.12f)
    p.lineTo(center.x + size * 0.12f, center.y - size * 0.36f)
    p.lineTo(center.x + size * 0.44f, center.y)
    p.lineTo(center.x + size * 0.12f, center.y + size * 0.36f)
    p.lineTo(center.x + size * 0.12f, center.y + size * 0.12f)
    p.lineTo(center.x - size * 0.42f, center.y + size * 0.12f)
    p.close()
    return p
}

private fun dropPath(center: Offset, size: Float): Path {
    val p = Path()
    p.moveTo(center.x, center.y - size * 0.44f)
    p.cubicTo(
        center.x + size * 0.38f, center.y - size * 0.04f,
        center.x + size * 0.32f, center.y + size * 0.38f,
        center.x, center.y + size * 0.44f
    )
    p.cubicTo(
        center.x - size * 0.32f, center.y + size * 0.38f,
        center.x - size * 0.38f, center.y - size * 0.04f,
        center.x, center.y - size * 0.44f
    )
    p.close()
    return p
}

/** 3x3 九宫格挑战视图（谷歌 reCAPTCHA 风格） */
@Composable
fun ImageCaptchaGrid(
    challenge: CaptchaChallenge,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cellSize = 84.dp
    val spacing = 8.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        challenge.grid.chunked(3).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                items.forEachIndexed { col, shape ->
                    val idx = row * 3 + col
                    val isSel = idx in selected
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) Color(0xFFEDE7F6) else Color(0xFFF1F3F4))
                            .border(
                                width = if (isSel) 3.dp else 1.dp,
                                color = if (isSel) Color(0xFF7B1FA2) else Color(0xFFDADCE0),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onToggle(idx) }
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            val s = min(size.width, size.height)
                            drawCaptchaShape(
                                shape = shape,
                                center = Offset(size.width / 2f, size.height / 2f),
                                size = s,
                                bg = Color(0xFFF1F3F4)
                            )
                        }
                        // 选中态：右上角自绘蓝色对勾
                        if (isSel) {
                            Canvas(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .padding(3.dp)
                            ) {
                                val check = Path().apply {
                                    moveTo(size.width * 0.15f, size.height * 0.52f)
                                    lineTo(size.width * 0.42f, size.height * 0.78f)
                                    lineTo(size.width * 0.88f, size.height * 0.22f)
                                }
                                drawPath(
                                    check,
                                    Color(0xFF7B1FA2),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 目标图形预览（小画布） */
@Composable
fun CaptchaTargetPreview(challenge: CaptchaChallenge, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F3F4)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            drawCaptchaShape(
                shape = GridShape(challenge.target, Color(0xFF7B1FA2), 0f, 1f),
                center = Offset(size.width / 2f, size.height / 2f),
                size = min(size.width, size.height),
                bg = Color(0xFFF1F3F4)
            )
        }
    }
}