package com.example.png2pdf.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.png2pdf.data.EncodeOptions
import com.example.png2pdf.data.ImageMetaReader
import com.example.png2pdf.data.MediaItem
import com.example.png2pdf.data.PageSettings
import com.example.png2pdf.data.SortKey
import com.example.png2pdf.data.sortedBy
import com.example.png2pdf.pdf.PdfBuilder
import com.example.png2pdf.util.PdfOutput
import com.example.png2pdf.util.UriPermissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一次性提示 */
data class UiMessage(val id: Long, val text: String)

/** 导出进度 */
data class ExportProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * 导出结果。
 *
 * [isFallback] 为 true 表示公共「下载」目录写不进去（部分 ROM 不支持、盘满、被拦截等），
 * 文件退到了应用私有目录 —— 这时界面要引导用户用「分享」另存。
 */
data class PdfDestination(
    val uri: Uri,
    val displayPath: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val isFallback: Boolean = false,
)

data class EditorState(
    val items: List<MediaItem> = emptyList(),
    val settings: PageSettings = PageSettings(),
    val encode: EncodeOptions = EncodeOptions(),
    val sortKey: SortKey = SortKey.Default,
    /** 正在读取选中的文件元数据 */
    val isLoadingSelection: Boolean = false,
    val progress: ExportProgress? = null,
    val lastResult: PdfDestination? = null,
    val message: UiMessage? = null,
) {
    val isBusy: Boolean get() = progress != null || isLoadingSelection
    val canExport: Boolean get() = items.isNotEmpty() && !isBusy
}

/**
 * 页面唯一的状态持有者。
 *
 * 约定：所有耗时工作（读元数据、生成 PDF）都在 viewModelScope 里跑，
 * UI 只读 [state] 和调用下面这些意图方法，不自己持 Context 做业务。
 */
class MergeViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val builder = PdfBuilder(app)
    private var exportJob: Job? = null

    /** 系统选择器 / 分享进来的 Uri 统一从这里进来 */
    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val clean = uris.distinct().filter { it.scheme != null }
        if (clean.isEmpty()) return

        UriPermissions.takeRead(getApplication(), clean)

        viewModelScope.launch {
            _state.update { it.copy(isLoadingSelection = true) }

            // 并发读取元数据：只解码文件头，几十张图也就百毫秒级
            val existing = _state.value.items.map { it.uri }.toSet()
            val toRead = clean.filterNot { it in existing }

            val loaded = toRead.map { uri ->
                runCatching { ImageMetaReader.read(getApplication(), uri) }.getOrNull()
            }
            val failed = loaded.count { it == null }

            _state.update { current ->
                current.copy(
                    items = current.items + loaded.filterNotNull(),
                    isLoadingSelection = false,
                    message = when {
                        loaded.isNotEmpty() && failed == 0 -> UiMessage(
                            System.nanoTime(),
                            "已添加 ${loaded.size} 张图片",
                        )

                        failed > 0 -> UiMessage(
                            System.nanoTime(),
                            "已添加 ${loaded.count { it != null }} 张，$failed 张无法读取（已跳过）",
                        )

                        else -> UiMessage(System.nanoTime(), "这些图片之前已经加过了")
                    },
                )
            }
        }
    }

    fun applySort(key: SortKey) {
        _state.update { it.copy(sortKey = key, items = it.items.sortedBy(key)) }
    }

    fun move(from: Int, to: Int) {
        _state.update { current ->
            val list = current.items.toMutableList()
            if (from !in list.indices || to !in list.indices) return@update current
            val item = list.removeAt(from)
            list.add(to, item)
            // 手动拖动之后就不再声称是"按名字/时间排好的"了
            current.copy(items = list, sortKey = SortKey.SELECTION)
        }
    }

    fun removeAt(index: Int) {
        _state.update { current ->
            if (index !in current.items.indices) return@update current
            val list = current.items.toMutableList()
            val removed = list.removeAt(index)
            UriPermissions.release(getApplication(), removed.uri)
            current.copy(items = list)
        }
    }

    fun clearAll() {
        val removed = _state.value.items.map { it.uri }
        _state.update { it.copy(items = emptyList(), lastResult = null, sortKey = SortKey.Default) }
        removed.forEach { UriPermissions.release(getApplication(), it) }
    }

    fun updateSettings(block: (PageSettings) -> PageSettings) {
        _state.update { it.copy(settings = block(it.settings)) }
    }

    fun updateEncode(block: (EncodeOptions) -> EncodeOptions) {
        _state.update { it.copy(encode = block(it.encode)) }
    }

    fun consumeMessage(id: Long) {
        _state.update { if (it.message?.id == id) it.copy(message = null) else it }
    }

    fun dismissResult() {
        _state.update { it.copy(lastResult = null) }
    }

    /** 把生成好的 PDF 通过系统分享面板发出去 */
    fun shareResult(result: PdfDestination) {
        val app = getApplication<Application>()
        runCatching {
            val shareUri = PdfOutput.shareableUri(app, result.uri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = PdfOutput.MIME_PDF
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.startActivity(
                Intent.createChooser(intent, "分享 PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            _state.update { current ->
                current.copy(message = UiMessage(System.nanoTime(), "分享失败：${it.message}"))
            }
        }
    }

    /** 用系统 PDF 阅读器打开 */
    fun openResult(result: PdfDestination) {
        val app = getApplication<Application>()
        runCatching {
            val viewUri = PdfOutput.viewableUri(app, result.uri)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(viewUri, PdfOutput.MIME_PDF)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        }.onFailure {
            _state.update { current ->
                current.copy(message = UiMessage(System.nanoTime(), "没有找到能打开 PDF 的应用"))
            }
        }
    }

    fun export() {
        val current = _state.value
        if (current.items.isEmpty()) {
            _state.update { it.copy(message = UiMessage(System.nanoTime(), "请先选择至少一张图片")) }
            return
        }
        if (exportJob?.isActive == true) return

        exportJob = viewModelScope.launch {
            val fileName = PdfOutput.newFileName()
            _state.update { it.copy(progress = ExportProgress(0, 1), lastResult = null) }

            var handle: PdfOutput.Handle? = null
            try {
                val opened = withContext(Dispatchers.IO) {
                    // 优先公共「下载」目录；系统不让写时会自动退到应用缓存目录并把原因带回来
                    PdfOutput.prepare(getApplication(), fileName)
                }
                handle = opened.handle

                val content = builder.writeTo(
                    items = current.items,
                    settings = current.settings,
                    encode = current.encode,
                    outputStream = opened.handle.outputStream,
                    onProgress = { done, all ->
                        _state.update { it.copy(progress = ExportProgress(done, all)) }
                    },
                )

                val destination = opened
                val written = withContext(Dispatchers.IO) { opened.handle.commit() }
                val result = PdfDestination(
                    uri = written.uri,
                    displayPath = written.displayPath,
                    pageCount = content.pageCount,
                    sizeBytes = written.sizeBytes,
                    isFallback = destination.isFallback,
                )
                _state.update {
                    it.copy(
                        progress = null,
                        lastResult = result,
                        // 只有兜底路径才额外弹一条提示；正常情况结果面板已经写清了位置
                        message = if (destination.isFallback) {
                            UiMessage(System.nanoTime(), "无法写入「下载」目录，已改存到应用私有目录")
                        } else {
                            null
                        },
                    )
                }
            } catch (c: CancellationException) {
                withContext(NonCancellable) { runCatching { handle?.abort() } }
                _state.update { it.copy(progress = null) }
                throw c
            } catch (t: Throwable) {
                runCatching { handle?.abort() }
                android.util.Log.e("MergeViewModel", "导出 PDF 失败", t)
                _state.update {
                    it.copy(
                        progress = null,
                        message = UiMessage(System.nanoTime(), "导出失败：${t.message ?: t::class.java.simpleName}"),
                    )
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _state.update { it.copy(progress = null, message = UiMessage(System.nanoTime(), "已取消导出")) }
    }

    /** 从 Intent 里取出分享进来的图片 */
    fun urisFromIntent(intent: Intent?): List<Uri> {
        intent ?: return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri,
            )

            Intent.ACTION_SEND_MULTIPLE -> @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()

            else -> emptyList()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MergeViewModel(app)
            }
        }
    }
}
