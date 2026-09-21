package com.example.png2pdf.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 从系统 Uri 读取图片元数据。
 *
 * 关键点：**只读元数据，不解码像素**。inJustDecodeBounds 让 BitmapFactory 只填宽高后立刻返回，
 * 处理 200 张图也不会 OOM。真正的解码发生在导出阶段，且是逐张、可回收的。
 */
object ImageMetaReader {

    suspend fun read(context: Context, uri: Uri): MediaItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        var name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        var size = -1L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }

        // 1) 先拿原始像素尺寸
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }

        // 2) 再读 EXIF 方向（只读取文件头部若干字节，开销很小）
        var rotation = 0
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90,
                    ExifInterface.ORIENTATION_TRANSPOSE,
                    -> 90

                    ExifInterface.ORIENTATION_ROTATE_180,
                    ExifInterface.ORIENTATION_FLIP_VERTICAL,
                    -> 180

                    ExifInterface.ORIENTATION_ROTATE_270,
                    ExifInterface.ORIENTATION_TRANSVERSE,
                    -> 270

                    else -> 0
                }
            }
        }

        // 3) 旋转 90/270 时宽高互换，这样列表里显示的尺寸和最终 PDF 里的方向一致
        val rawW = bounds.outWidth.coerceAtLeast(0)
        val rawH = bounds.outHeight.coerceAtLeast(0)
        val (w, h) = if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH

        if (name.isBlank()) {
            name = "image_${System.currentTimeMillis()}.png"
        }

        MediaItem(
            uri = uri,
            displayName = name,
            width = w,
            height = h,
            dateModified = System.currentTimeMillis(),
            sizeBytes = size,
            rotationDegrees = rotation,
        )
    }

    /** 人类可读的文件大小 */
    fun formatSize(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
