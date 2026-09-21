package com.example.png2pdf.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.example.png2pdf.data.ImageMetaReader
import com.example.png2pdf.data.MediaItem
import com.example.png2pdf.data.PageSize
import com.example.png2pdf.data.SortKey
import com.example.png2pdf.pdf.PageLayout
import com.example.png2pdf.util.PdfOutput

/**
 * 应用主界面。
 *
 * 这一层只做两件事：把 [EditorState] 画出来、把用户操作转成 ViewModel 调用。
 * 没有 Context 业务逻辑，也没有解码/写文件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(state: EditorState, viewModel: MergeViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // 系统照片选择器：Android 13+ 是系统级 Picker，低版本自动回退到 ACTION_OPEN_DOCUMENT，都不需要权限
    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100),
    ) { uris -> viewModel.addUris(uris) }

    val launchPicker: () -> Unit = {
        runCatching {
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    // 一次性提示 -> Snackbar
    LaunchedEffect(state.message?.id) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        viewModel.consumeMessage(message.id)
    }

    // 导出完成 -> 结果面板
    state.lastResult?.let { result ->
        ExportResultDialog(
            result = result,
            onDismiss = viewModel::dismissResult,
            onOpen = { viewModel.openResult(result) },
            onShare = { viewModel.shareResult(result) },
        )
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PNG 拼 PDF", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = summaryText(state),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortKey.entries.forEach { key ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = key.label,
                                            fontWeight = if (key == state.sortKey) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        viewModel.applySort(key)
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = viewModel::clearAll,
                        enabled = state.items.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                    }
                },
            )
        },
        bottomBar = {
            if (state.items.isNotEmpty()) {
                BottomActionBar(
                    state = state,
                    onAddMore = launchPicker,
                    onOpenSettings = { showSettings = true },
                    onExport = viewModel::export,
                )
            }
        },
        floatingActionButton = {
            if (state.items.isEmpty() && !state.isBusy) {
                ExtendedFloatingActionButton(
                    onClick = launchPicker,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("选择图片") },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoadingSelection && state.items.isEmpty() -> LoadingBlock("正在读取图片信息…")
                state.items.isEmpty() -> EmptyState(onPick = launchPicker)
                else -> ImageList(
                    state = state,
                    onMove = viewModel::move,
                    onRemove = viewModel::removeAt,
                    onAddMore = launchPicker,
                )
            }

            state.progress?.let { progress ->
                ExportOverlay(progress = progress, onCancel = viewModel::cancelExport)
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            encode = state.encode,
            outputName = PdfOutput.newFileName(),
            onSettingsChange = { transform -> viewModel.updateSettings(transform) },
            onEncodeChange = { transform -> viewModel.updateEncode(transform) },
            onDismiss = { showSettings = false },
        )
    }
}

private fun summaryText(state: EditorState): String {
    if (state.items.isEmpty()) return "选择多张 PNG，导出成一个 PDF"
    val pages = if (state.settings.pageSize == PageSize.IMAGE_SIZE) {
        state.items.size
    } else {
        PageLayout.pagesNeeded(state.items.size, state.settings)
    }
    return "${state.items.size} 张图片 · 约 $pages 页 · ${state.settings.pageSize.shortLabel}"
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("还没有选择图片", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "点下面的按钮批量选择 PNG / JPG 截图，\n长按拖不动也没关系，列表里可以上下调整顺序。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("选择图片")
        }
    }
}

@Composable
private fun LoadingBlock(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImageList(
    state: EditorState,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = state.items,
            key = { _, item -> item.uri.toString() },
        ) { index, item ->
            MediaRow(
                index = index,
                item = item,
                isFirst = index == 0,
                isLast = index == state.items.lastIndex,
                onMoveUp = { onMove(index, index - 1) },
                onMoveDown = { onMove(index, index + 1) },
                onRemove = { onRemove(index) },
            )
        }
        item {
            TextButton(
                onClick = onAddMore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("继续添加图片")
            }
        }
    }
}

@Composable
private fun MediaRow(
    index: Int,
    item: MediaItem,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 页码角标
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.width(8.dp))

            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(item.dimensionText)
                        val size = ImageMetaReader.formatSize(item.sizeBytes)
                        if (size.isNotEmpty()) append(" · $size")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "移除")
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    state: EditorState,
    onAddMore: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddMore) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("添加")
                }
                TextButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("排版")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onExport, enabled = state.canExport) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("生成 PDF")
                }
            }
        }
    }
}

@Composable
private fun ExportOverlay(progress: ExportProgress, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("正在生成 PDF", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${progress.done} / ${progress.total} 页",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onCancel) { Text("取消") }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "图片逐张解码，大图较多时会慢一些",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
