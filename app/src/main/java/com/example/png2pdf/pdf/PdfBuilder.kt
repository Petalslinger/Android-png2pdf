package com.example.png2pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.png2pdf.data.EncodeOptions
import com.example.png2pdf.data.MediaItem
import com.example.png2pdf.data.PageBackground
import com.example.png2pdf.data.PageSettings
import com.example.png2pdf.data.PageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/** 已写入输出流的 PDF 内容信息（落盘位置由调用方 commit 之后才知道） */
data class PdfContent(val pageCount: Int)

/**
 * 把图片列表渲染成 PDF。
 *
 * 设计要点：
 * 1. **逐张解码、逐张回收**。任何时刻内存里只有当前这一张 Bitmap，
 *    所以几百张 4000×3000 的图也不会 OOM，代价是速度换内存。
 * 2. **版面计算与绘制分离**。版面由 [PageLayout] 这个纯函数算好，这里只负责按矩形画。
 * 3. **只管写流，不管落在哪**。输出流由调用方从 [com.example.png2pdf.util.PdfOutput] 拿到，
 *    写完由调用方 commit —— 这样"写到哪了"不经过任何共享状态，也不会把真实异常盖掉。
 * 4. 支持协程取消：每个图片边界检查一次 isActive，用户返回就立刻停手。
 */
class PdfBuilder(private val context: Context) {

    /**
     * 把 PDF 写进 [outputStream]。**不关闭流**，由调用方 commit。
     *
     * @param onProgress (已开始的页数, 总页数)
     */
    suspend fun writeTo(
        items: List<MediaItem>,
        settings: PageSettings,
        encode: EncodeOptions,
        outputStream: OutputStream,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): PdfContent = withContext(Dispatchers.IO) {
        require(items.isNotEmpty()) { "图片列表为空，没有可导出的内容" }

        val resolver = context.contentResolver

        // 第一遍：只读尺寸（不解码像素），用于版面计算
        val sizeCache = HashMap<Int, Pair<Int, Int>>(items.size)
        items.forEachIndexed { index, item ->
            sizeCache[index] = resolveSize(resolver, item)
        }

        val placements = PageLayout.compute(items.size, settings, sizeCache::get)
        check(placements.isNotEmpty()) { "没有可渲染的图片，请检查所选文件是否仍可读取" }

        // 用排版结果反推总页数，保证进度条分母和真正 startPage 的次数一致
        val pagesNeeded = placements.map { it.pageIndex }.distinct().size

        val document = PdfDocument()
        // 自己数页数：PdfDocument 没有公开的 pagesCount，而每一页都是我亲手 start 的
        var openedPages = 0

        try {
            // 相邻且 pageIndex 相同的摆放共用同一页，保证 startPage/finishPage 严格配对
            var startedPage: PdfDocument.Page? = null
            var openPageIndex = -1

            fun finishCurrent() {
                startedPage?.let { document.finishPage(it) }
                startedPage = null
            }

            for (placement in placements) {
                coroutineContext.ensureActive()

                if (startedPage == null || placement.pageIndex != openPageIndex) {
                    finishCurrent()
                    openedPages++
                    val info = PdfDocument.PageInfo.Builder(
                        placement.pageSize.width.toInt().coerceAtLeast(1),
                        placement.pageSize.height.toInt().coerceAtLeast(1),
                        openedPages,
                    ).create()
                    startedPage = document.startPage(info)
                    openPageIndex = placement.pageIndex
                    onProgress(openedPages, pagesNeeded)
                }

                val page = startedPage ?: continue
                try {
                    drawItem(page.canvas, items[placement.itemIndex], placement, settings, encode, resolver)
                } catch (t: Throwable) {
                    // 单张图坏了不该毁掉整份 PDF：记日志后跳过，继续下一张
                    android.util.Log.w(TAG, "跳过无法绘制的图片: ${items[placement.itemIndex].displayName}", t)
                }
            }
            finishCurrent()

            // 不 close：流的生命周期归调用方（PdfOutput.Handle）管
            document.writeTo(outputStream)
        } finally {
            runCatching { document.close() }
        }

        PdfContent(pageCount = openedPages)
    }

    private fun drawItem(
        canvas: Canvas,
        item: MediaItem,
        placement: PageLayout.Placement,
        settings: PageSettings,
        encode: EncodeOptions,
        resolver: android.content.ContentResolver,
    ) {
        val dest = placement.destRect
        if (dest.isEmpty) return

        // 透明背景时不填充，保持 PDF 页面透明
        if (settings.background != PageBackground.TRANSPARENT) {
            val pagePaint = Paint().apply { color = settings.background.argb }
            canvas.drawRect(0f, 0f, placement.pageSize.width, placement.pageSize.height, pagePaint)
        }

        val bitmap = decode(
            resolver = resolver,
            uri = item.uri,
            keepOriginalPixels = settings.pageSize == PageSize.IMAGE_SIZE,
            encode = encode,
        ) ?: return

        try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                isAntiAlias = true
                isFilterBitmap = true
                alpha = 255
            }
            // 源矩形用整像素 Rect（Bitmap 本来就是整像素），目标矩形用 RectF：
            // Canvas 只有 (Bitmap, Rect, RectF, Paint) 这个混合重载，两边都是 RectF 反而不存在
            val src = Rect(0, 0, bitmap.width, bitmap.height)
            val dst = RectF(dest.left, dest.top, dest.right, dest.bottom)
            canvas.drawBitmap(bitmap, src, dst, paint)
        } finally {
            // 立刻释放：下一次解码前内存就已经回收
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** 解码单张图，并按 EXIF 校正方向（比让 Canvas 每次做旋转矩阵更直观） */
    private fun decode(
        resolver: android.content.ContentResolver,
        uri: Uri,
        keepOriginalPixels: Boolean,
        encode: EncodeOptions,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.onFailure { return null }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val rotation = readRotation(resolver, uri)
        val rotate = rotation == 90 || rotation == 270
        val dispW = if (rotate) bounds.outHeight else bounds.outWidth
        val dispH = if (rotate) bounds.outWidth else bounds.outHeight

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            // 长图模式要求像素级还原；其他模式按 maxPixels 降采样以控制内存
            inSampleSize = if (keepOriginalPixels) 1 else calculateInSampleSize(dispW, dispH, encode.maxPixels)
        }

        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null

        if (rotation == 0) return decoded

        val matrix = Matrix().apply {
            when (rotation) {
                90 -> postRotate(90f)
                180 -> postRotate(180f)
                270 -> postRotate(270f)
            }
        }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }

    private fun readRotation(resolver: android.content.ContentResolver, uri: Uri): Int = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun resolveSize(resolver: android.content.ContentResolver, item: MediaItem): Pair<Int, Int> {
        if (item.width > 0 && item.height > 0) return item.width to item.height

        val rotation = readRotation(resolver, item.uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        val rotate = rotation == 90 || rotation == 270
        val w = if (rotate) bounds.outHeight else bounds.outWidth
        val h = if (rotate) bounds.outWidth else bounds.outHeight
        return w.coerceAtLeast(1) to h.coerceAtLeast(1)
    }

    companion object {
        private const val TAG = "PdfBuilder"

        /** 计算 2 的幂次采样率 */
        fun calculateInSampleSize(width: Int, height: Int, maxPixels: Int): Int {
            if (maxPixels <= 0) return 1
            var sample = 1
            var longest = maxOf(width, height)
            while (longest / 2 >= maxPixels) {
                longest /= 2
                sample *= 2
            }
            return sample
        }
    }
}
