package com.example.png2pdf.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** PDF 落盘结果。由 [PdfOutput.Handle.commit] 直接返回，不经过任何共享状态。 */
data class WrittenPdf(
    val uri: Uri,
    /** 展示给用户看的位置，例如「下载/PNG2PDF/xxx.pdf」 */
    val displayPath: String,
    /** -1 表示查不到大小，不影响使用 */
    val sizeBytes: Long,
)

/**
 * PDF 落地。
 *
 * 设计上刻意的两点：
 * 1. **没有共享可变状态**。以前用 `lastWritten` 这种字段把"写到哪了"从 commit 传给调用方，
 *    一旦 commit 里任何一步抛异常，调用方拿到 null，就报出"已生成但未能写入存储"这种
 *    把真实原因盖掉的错误。现在 [Handle.commit] 直接把 [WrittenPdf] 返回。
 * 2. **能退**。MediaStore 写不进去（部分 ROM 不支持 Downloads 集合、盘满、被安全软件拦）时，
 *    退到应用自己的 cache 目录，用户仍可通过「分享」另存到任意位置，而不是白干一场。
 */
object PdfOutput {

    const val MIME_PDF = "application/pdf"

    /**
     * 首选输出子目录，例如 "Download/PNG2PDF"。
     * 不能声明成 `const val`：Environment.DIRECTORY_DOWNLOADS 是 Java 的 static final String，
     * Kotlin 不把它当编译期常量，而 const val 只接受真正的常量表达式。
     */
    val RELATIVE_DIR: String = Environment.DIRECTORY_DOWNLOADS + "/PNG2PDF"

    /** cache 兜底目录名 */
    private const val FALLBACK_DIR = "output"

    /** 生成不重名的文件名，例如 PNG合并_20250612_143001.pdf */
    fun newFileName(prefix: String = "PNG合并"): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$stamp.pdf"
    }

    /**
     * 申请一个写入位置。
     *
     * 优先公共「下载」目录（Android 10+ 走 MediaStore，8/9 直接写文件），
     * 都不行则退到 cache/output。调用方通过 [Opened.isFallback] 决定要不要提示用户。
     */
    fun prepare(context: Context, fileName: String): Opened {
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            prepareViaMediaStore(context, fileName)
        } else {
            prepareViaPublicFile(context, fileName)
        }
        if (primary != null) return Opened(primary, isFallback = false)

        return Opened(prepareViaCache(context, fileName), isFallback = true)
    }

    /** 写入位置 + 是否走了兜底路径 */
    class Opened(val handle: Handle, val isFallback: Boolean)

    /** MediaStore 路径；任何一步失败都返回 null，让上层退到 cache */
    private fun prepareViaMediaStore(context: Context, fileName: String): Handle? = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_PDF)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching null
        val stream = resolver.openOutputStream(uri) ?: return@runCatching null

        Handle(context, uri, stream) {
            // 去掉 pending 标记，文件才会出现在「文件」App 里。
            // 这一步失败不影响文件内容（已经写完并 fsync 过），只影响可见性，所以只记录不抛。
            val visible = runCatching {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            if (visible.isFailure) {
                android.util.Log.w(TAG, "清除 IS_PENDING 失败，文件可能不出现在文件管理器里", visible.exceptionOrNull())
            }
            WrittenPdf(
                uri = uri,
                displayPath = "下载/PNG2PDF/$fileName",
                sizeBytes = querySize(context, uri),
            )
        }
    }.getOrNull()

    /** Android 8/9：直接写公共下载目录（需要 WRITE_EXTERNAL_STORAGE） */
    @Suppress("DEPRECATION")
    private fun prepareViaPublicFile(context: Context, fileName: String): Handle? = runCatching {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PNG2PDF",
        )
        if (!dir.exists() && !dir.mkdirs()) return@runCatching null
        val file = File(dir, fileName)
        val stream = file.outputStream().buffered()
        Handle(context, Uri.fromFile(file), stream) {
            // 通知媒体库刷新，否则文件管理器里可能看不到
            runCatching {
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), arrayOf(MIME_PDF), null,
                )
            }
            WrittenPdf(Uri.fromFile(file), "下载/PNG2PDF/$fileName", file.length())
        }
    }.getOrNull()

    /**
     * 兜底：写到应用私有 cache 目录。这个位置**不需要任何权限、几乎不会失败**，
     * 用户可以通过结果面板的「分享」把文件另存到网盘/微信/文件管理器。
     */
    private fun prepareViaCache(context: Context, fileName: String): Handle {
        val dir = File(context.cacheDir, FALLBACK_DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        val stream = file.outputStream().buffered()
        return Handle(context, Uri.fromFile(file), stream) {
            WrittenPdf(
                uri = Uri.fromFile(file),
                displayPath = file.absolutePath,
                sizeBytes = file.length(),
            )
        }
    }

    private fun querySize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }.getOrDefault(-1L)

    /**
     * 拿到一个"别的 App 能读"的 uri。
     * - file:// → 先拷进 cache/share 再走 FileProvider（file:// 从 Android 7 起不能跨进程传）
     * - content:// → 原样返回
     */
    fun shareableUri(context: Context, uri: Uri): Uri {
        if (uri.scheme != "file") return uri

        val source = uri.path?.let { File(it) }
        if (source != null && source.parentFile?.name == "share") {
            // 已经在 FileProvider 覆盖范围内
            return fileProviderUri(context, source)
        }

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val target = File(dir, source?.name ?: "merged.pdf")
        if (source != null && source.exists()) {
            runCatching { source.copyTo(target, overwrite = true) }
        }
        return fileProviderUri(context, target)
    }

    /** 系统阅读器用的 uri：content:// 直接给，file:// 需要先转 FileProvider */
    fun viewableUri(context: Context, uri: Uri): Uri = shareableUri(context, uri)

    private fun fileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    class Handle(
        private val context: Context,
        val uri: Uri,
        val outputStream: OutputStream,
        private val onCommit: () -> WrittenPdf,
    ) {
        private var finished = false

        /** 写完收尾。返回真正落盘的位置；这里抛出的异常就是"为什么没写成"。 */
        fun commit(): WrittenPdf {
            check(!finished) { "这个写入位置已经 commit 过了" }
            finished = true
            outputStream.flush()
            outputStream.close()
            return onCommit()
        }

        /** 中途失败/取消时清理半成品：MediaStore 里删掉 pending 条目，普通文件直接删 */
        fun abort() {
            finished = true
            runCatching { outputStream.close() }
            when (uri.scheme) {
                "file" -> runCatching { uri.path?.let { File(it).delete() } }
                "content" -> runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }

    private const val TAG = "PdfOutput"
}
