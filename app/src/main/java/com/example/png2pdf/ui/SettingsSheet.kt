package com.example.png2pdf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.png2pdf.data.EncodeOptions
import com.example.png2pdf.data.FitMode
import com.example.png2pdf.data.Orientation
import com.example.png2pdf.data.PageBackground
import com.example.png2pdf.data.PageMode
import com.example.png2pdf.data.PageSettings
import com.example.png2pdf.data.PageSize
import com.example.png2pdf.util.PdfOutput
import kotlin.math.roundToInt

/**
 * 排版设置面板。做成 BottomSheet 而不是独立页面：
 * 改一项设置就能立刻从后面的顶栏摘要看到页数变化。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: PageSettings,
    encode: EncodeOptions,
    outputName: String,
    onSettingsChange: ((PageSettings) -> PageSettings) -> Unit,
    onEncodeChange: ((EncodeOptions) -> EncodeOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCustomSize by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("排版设置", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "输出到 ${PdfOutput.RELATIVE_DIR}/$outputName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("纸张尺寸")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PageSize.entries.forEach { size ->
                    FilterChip(
                        selected = settings.pageSize == size,
                        onClick = {
                            if (size == PageSize.CUSTOM) showCustomSize = true
                            onSettingsChange { it.copy(pageSize = size) }
                        },
                        label = { Text(size.shortLabel) },
                    )
                }
            }
            if (settings.pageSize == PageSize.IMAGE_SIZE) {
                HintText("每一页的尺寸等于该图片的像素尺寸，导出后没有任何留白，适合把长截图拼成一份文档。")
            }
            if (settings.pageSize == PageSize.CUSTOM) {
                HintText("当前自定义尺寸：${settings.customWidthMm.roundToInt()} × ${settings.customHeightMm.roundToInt()} mm")
                TextButton(onClick = { showCustomSize = true }) { Text("修改尺寸") }
            }

            SectionHeader("方向")
            EnumRadioRow(
                options = Orientation.entries,
                selected = settings.orientation,
                label = { it.label },
                onSelect = { value -> onSettingsChange { it.copy(orientation = value) } },
            )

            SectionHeader("每页张数")
            EnumRadioRow(
                options = PageMode.entries.toList(),
                selected = settings.pageMode,
                enabled = { settings.pageSize.supportsMultiPerPage },
                label = { it.label },
                onSelect = { value -> onSettingsChange { it.copy(pageMode = value) } },
            )
            if (!settings.pageSize.supportsMultiPerPage) {
                HintText("「跟图片等大」模式下固定为一图一页。")
            }

            SectionHeader("缩放方式")
            Column(modifier = Modifier.selectableGroup()) {
                FitMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.fitMode == mode,
                                onClick = { onSettingsChange { it.copy(fitMode = mode) } },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.fitMode == mode, onClick = null)
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                mode.hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionHeader("页边距：${settings.marginMm.roundToInt()} mm")
            Slider(
                value = settings.marginMm,
                onValueChange = { value -> onSettingsChange { it.copy(marginMm = value) } },
                valueRange = 0f..30f,
                steps = 29,
                enabled = settings.fitMode != FitMode.STRETCH,
            )

            SectionHeader("背景")
            EnumRadioRow(
                options = PageBackground.entries,
                selected = settings.background,
                label = { it.label },
                onSelect = { value -> onSettingsChange { it.copy(background = value) } },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text("输出质量", style = MaterialTheme.typography.titleMedium)

            SectionHeader("图片长边上限：${encode.maxPixels} px")
            Slider(
                value = encode.maxPixels.toFloat(),
                onValueChange = { value ->
                    onEncodeChange { it.copy(maxPixels = (value / 100f).roundToInt() * 100) }
                },
                valueRange = 800f..4000f,
                steps = 31,
            )
            HintText("降低这个值能显著缩小 PDF 体积、加快生成速度，代价是放大后变模糊。截图建议 1600～2400。")

            SectionHeader("PNG 压缩质量：${encode.jpegQuality}（预留）")
            Slider(
                value = encode.jpegQuality.toFloat(),
                onValueChange = { value -> onEncodeChange { it.copy(jpegQuality = value.roundToInt()) } },
                valueRange = 40f..100f,
                steps = 59,
                // 当前实现走系统 PdfDocument 的无损图像流，体积由上面的「长边上限」决定。
                // 想真正用上这个参数，需要在 PdfBuilder 里把降采样后的 Bitmap 重新编码成 JPEG 再贴入页面。
                enabled = false,
            )
            HintText("暂未接入：现在图片是无损嵌入 PDF 的，控制体积请调上面的「长边上限」。")
        }
    }

    if (showCustomSize) {
        CustomSizeDialog(
            widthMm = settings.customWidthMm,
            heightMm = settings.customHeightMm,
            onConfirm = { w, h ->
                onSettingsChange { it.copy(pageSize = PageSize.CUSTOM, customWidthMm = w, customHeightMm = h) }
                showCustomSize = false
            },
            onDismiss = { showCustomSize = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> EnumRadioRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { option ->
            val isEnabled = enabled(option)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == option,
                        enabled = isEnabled,
                        onClick = { onSelect(option) },
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == option,
                    onClick = null,
                    enabled = isEnabled,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomSizeDialog(
    widthMm: Float,
    heightMm: Float,
    onConfirm: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var widthText by remember { mutableStateOf(widthMm.roundToInt().toString()) }
    var heightText by remember { mutableStateOf(heightMm.roundToInt().toString()) }
    val widthValue = widthText.toFloatOrNull()
    val heightValue = heightText.toFloatOrNull()
    val valid = widthValue != null && heightValue != null &&
        widthValue in 20f..2000f && heightValue in 20f..2000f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义纸张（毫米）") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("宽") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("高") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (valid) {
                        val w = PageSettings.mmToPt(widthValue!!)
                        val h = PageSettings.mmToPt(heightValue!!)
                        "约 ${w.roundToInt()} × ${h.roundToInt()} pt"
                    } else {
                        "请输入 20～2000 之间的数值"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (valid) onConfirm(widthValue!!, heightValue!!) },
                enabled = valid,
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 生成成功后的结果面板 */
@Composable
fun ExportResultDialog(
    result: PdfDestination,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result.isFallback) "PDF 已生成（存在应用内）" else "PDF 已生成") },
        text = {
            Column {
                Text("共 ${result.pageCount} 页", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = result.displayPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.sizeBytes > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "体积 ${formatBytes(result.sizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.isFallback) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "系统不允许写入公共「下载」目录，文件暂时放在应用私有目录里。" +
                            "点「分享」可以另存到文件管理器、网盘或直接发给别人。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpen) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("打开")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("分享")
                }
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
