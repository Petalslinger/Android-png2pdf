package com.example.png2pdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.png2pdf.data.ImageMetaReader
import com.example.png2pdf.data.MediaItem
import kotlin.math.max
import kotlin.math.min

/**
 * 全屏图片预览。
 *
 * 手势：
 * - 双指捏合缩放（1x ～ 6x）
 * - 双击在 1x / 2.5x 之间切换
 * - 放大后单指拖动平移（1x 时不动，避免和"下滑关闭"抢手势）
 * - 右上角关闭，或点空白处关闭
 *
 * 说明：为了不引入额外的图片加载库，这里直接用已有的 Coil [AsyncImage]，
 * 缩放平移用 [graphicsLayer] 做，不重新解码原图。
 */
@Composable
fun ImagePreviewDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember(item.uri) { mutableFloatStateOf(1f) }
        var offsetX by remember(item.uri) { mutableFloatStateOf(0f) }
        var offsetY by remember(item.uri) { mutableFloatStateOf(0f) }

        fun reset() {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.96f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // ---- 图片本体：缩放 / 平移 / 双击缩放 ----
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(item.uri) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                scale = next
                                // 1x 时锁定位移，避免图片被拖出屏幕
                                if (next > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .pointerInput(item.uri) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) reset() else scale = 2.5f
                                },
                                // 单击刻意不关闭：否则"双击缩放"的第一下会先把预览关掉，
                                // 手势互相打架。退出统一走右上角的关闭按钮。
                                onTap = { },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                    )
                }

                // ---- 顶部信息条 ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.displayName.ifBlank { "图片预览" },
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = buildString {
                                    append(item.dimensionText)
                                    val size = ImageMetaReader.formatSize(item.sizeBytes)
                                    if (size.isNotEmpty()) append("  ·  $size")
                                    append("  ·  ${"%.1f".format(scale)}x")
                                },
                                color = Color.White.copy(alpha = 0.75f),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭预览",
                                tint = Color.White,
                            )
                        }
                    }
                }

                // ---- 底部手势提示 ----
                Text(
                    text = "双指缩放 · 双击放大 / 还原 · 右上角关闭",
                    color = Color.White.copy(alpha = 0.6f),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                )
            }
        }
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f

/** 供调用方做边界保护用（当前未使用，保留给后续拖拽关闭等交互） */
private fun clampScale(value: Float): Float = min(MAX_SCALE, max(MIN_SCALE, value))
