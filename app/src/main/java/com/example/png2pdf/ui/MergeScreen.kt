package com.example.png2pdf.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.example.png2pdf.data.ImageMetaReader
import com.example.png2pdf.data.MediaItem
import com.example.png2pdf.data.PageSize
import com.example.png2pdf.data.SortKey
import com.example.png2pdf.pdf.PageLayout
import com.example.png2pdf.util.PdfOutput
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    // 正在全屏预览的图片下标；null 表示没在预览
    var previewIndex by remember { mutableIntStateOf(-1) }

    // 选图走系统文件选择器（ACTION_OPEN_DOCUMENT）而不是照片选择器（PickVisualMedia）。
    // 原因：照片选择器通过 DISPLAY_NAME 返回的是合成名（形如 "58.png"，数字就是 MediaStore 的 _id），
    // 拿不到真实文件名；而且它给的 Uri 不可持久化，takePersistableUriPermission 会静默失败。
    // OPEN_DOCUMENT 返回真实文件名，并且 Uri 可持久化，两条路都不需要申请任何权限。
    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.addUris(uris) }

    val launchPicker: () -> Unit = {
        runCatching {
            pickImages.launch(IMAGE_MIME_TYPES)
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
                    onPreview = { index -> previewIndex = index },
                )
            }

            // 图片预览：盖在所有内容之上（包括底栏）
            state.items.getOrNull(previewIndex)?.let { previewing ->
                ImagePreviewDialog(
                    item = previewing,
                    onDismiss = { previewIndex = -1 },
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
    return "${state.items.size} 张图片 · 约 $pages 页 · ${state.settings.pageSize.shortLabel}" +
        if (state.sortKey == SortKey.SELECTION) " · 长按可拖动排序" else ""
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
            text = "点下面的按钮批量选择 PNG / JPG 截图，\n长按列表里的条目可以上下拖动排序。",
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

/** 列表横向内边距，拖动判定要用到行高，和这里保持一致 */
private val ListHPad = 12.dp

/**
 * 文件选择器接受的 MIME 类型。用宽泛的 image 通配符而不是逐个枚举：
 * 部分导出工具生成的 PNG 会把 MIME 标成 application/octet-stream，
 * 枚举太严会把这类文件挡在外面（PdfBuilder 本来就是按内容解码，不依赖 MIME）。
 * 想只显示图片可换成 arrayOf("image/png", "image/jpeg")。
 */
private val IMAGE_MIME_TYPES = arrayOf("image/*")

/**
 * 图片列表：支持**长按拖动排序**、**点缩略图预览**，同时保留原来的上移/下移按钮。
 *
 * 拖动实现要点：
 * 1. 长按（约 500ms）后才进入拖动，避免和纵向滚动抢手势；
 * 2. 拖动量换算成"跨过几行"，跨过一行就把目标下标汇报给 [onMove]，
 *    列表顺序由 ViewModel 落定，UI 只负责画；
 * 3. 拖到列表上下边缘附近会自动滚动，长列表也能一路拖到底。
 */
@Composable
private fun ImageList(
    state: EditorState,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddMore: () -> Unit,
    onPreview: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // 单行总高 = 卡片高度 + item 间距，拖动多少像素就除以它得到跨了几行。
    // LazyColumn 还没测量出行高时（取不到值）记为 0，此时按 1 处理防止除零。
    val rowHeightPx = (listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0)
        .takeIf { it > 0 } ?: 1

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ListHPad,
            end = ListHPad,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "长按任意一条可上下拖动排序 · 点缩略图看大图",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }

        itemsIndexed(
            items = state.items,
            key = { _, item -> item.uri.toString() },
        ) { index, item ->
            MediaRow(
                index = index,
                item = item,
                isFirst = index == 0,
                isLast = index == state.items.lastIndex,
                isDragging = index == draggingIndex,
                dragOffsetY = if (index == draggingIndex) dragOffsetY else 0f,
                onMoveUp = { onMove(index, index - 1) },
                onMoveDown = { onMove(index, index + 1) },
                onRemove = { onRemove(index) },
                onPreview = { onPreview(index) },
                onDragStart = {
                    draggingIndex = index
                    dragTargetIndex = index
                    dragOffsetY = 0f
                },
                onDrag = { delta ->
                    dragOffsetY += delta
                    val current = draggingIndex
                    if (current < 0) return@MediaRow
                    // 用绝对值换算，向上拖（delta 为负）时也要能跨行
                    val crossed = (dragOffsetY / rowHeightPx).roundToInt()
                    val target = (current + crossed)
                        .coerceIn(0, state.items.lastIndex.coerceAtLeast(0))
                    if (target != dragTargetIndex) {
                        onMove(current, target)
                        dragTargetIndex = target
                    }
                    // 贴近上下边缘自动滚动，方便拖到看不见的位置
                    autoScroll(listState, dragOffsetY, rowHeightPx, scope)
                },
                onDragEnd = {
                    draggingIndex = -1
                    dragTargetIndex = -1
                    dragOffsetY = 0f
                },
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

/** 拖到可见区域上下边缘附近时自动滚动列表 */
private fun autoScroll(
    listState: LazyListState,
    dragOffsetY: Float,
    rowHeightPx: Int,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val info = listState.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return

    val first = info.visibleItemsInfo.first()
    val last = info.visibleItemsInfo.last()
    // 被拖的那一行当前大致在哪
    val draggedCenter = first.offset + first.size / 2f + dragOffsetY
    val edge = rowHeightPx * 1.5f

    when {
        draggedCenter < info.viewportStartOffset + edge ->
            scope.launch { listState.animateScrollBy(-rowHeightPx / 2f) }

        draggedCenter > info.viewportEndOffset - edge ->
            scope.launch { listState.animateScrollBy(rowHeightPx / 2f) }
    }
}

@Composable
private fun MediaRow(
    index: Int,
    item: MediaItem,
    isFirst: Boolean,
    isLast: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    // 拖动中不播动画（否则跟手会卡顿），松手后回落才播
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        label = "mediaRowScale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 10f else 1f,
        label = "mediaRowElevation",
    )
    // 行高在第一次测量后就知道，用它把拖动像素换算成"跨过几行"
    var measuredRowHeight by remember { mutableIntStateOf(0) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged {
                if (it.height > 0) measuredRowHeight = it.height
            }
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = animatedScale
                scaleY = animatedScale
                shadowElevation = elevation * density.density
                // 拖动中抢到最上层，别被相邻卡片盖住
                if (isDragging) clip = false
            }
            // 长按后进入拖动排序（与纵向滚动共存：必须长按才触发）
            .pointerInput(item.uri) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 页码角标（拖动中换成"抓手"提示）
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isDragging) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            ) {
                Text(
                    text = if (isDragging) "≡" else "${index + 1}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDragging) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }

            Spacer(Modifier.width(8.dp))

            // 点缩略图 = 全屏预览（点击只挂在这里，不挂整行，
            // 这样"长按整行拖动"和"点击看大图"不会互相抢手势）
            AsyncImage(
                model = item.uri,
                contentDescription = "预览 ${item.displayName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPreview),
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
