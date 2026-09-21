package com.example.png2pdf.data

import android.net.Uri

/**
 * 一张待合并的图片。只存 Uri 和元数据，绝不持有 Bitmap —— 列表滚动、重组时零大对象开销。
 */
data class MediaItem(
    val uri: Uri,
    /** 系统给出的显示名，用于导出 PDF 时排序 / 展示 */
    val displayName: String = "",
    /** 已按 EXIF 校正过的宽高（像素） */
    val width: Int = 0,
    val height: Int = 0,
    /** 拍摄/最后修改时间，毫秒 */
    val dateModified: Long = 0L,
    /** 文件大小，字节；-1 表示未知 */
    val sizeBytes: Long = -1L,
    /** EXIF 旋转角度，0/90/180/270 */
    val rotationDegrees: Int = 0,
) {
    /** 供 UI 显示的尺寸文本，例如 "1080 × 2340" */
    val dimensionText: String
        get() = if (width > 0 && height > 0) "$width × $height" else "未知尺寸"
}

/** 待合并列表的排序方式 */
enum class SortKey(val label: String) {
    SELECTION("选择顺序"),
    NAME_ASC("文件名 ↑"),
    NAME_DESC("文件名 ↓"),
    DATE_ASC("时间 旧→新"),
    DATE_DESC("时间 新→旧"),
    ;

    companion object {
        val Default = SELECTION
    }
}

/**
 * 按给定顺序排列，返回新列表。排序是纯函数，方便单测。
 * 文件名排序使用自然序（num2 < num10）。
 */
fun List<MediaItem>.sortedBy(key: SortKey): List<MediaItem> = when (key) {
    SortKey.SELECTION -> this
    SortKey.NAME_ASC -> sortedWith(NaturalNameComparator)
    SortKey.NAME_DESC -> sortedWith(NaturalNameComparator.reversed())
    SortKey.DATE_ASC -> sortedBy { it.dateModified }
    SortKey.DATE_DESC -> sortedByDescending { it.dateModified }
}

/** 自然序比较器：把字符串切成数字段与文本段分别比较 */
object NaturalNameComparator : Comparator<MediaItem> {
    override fun compare(a: MediaItem, b: MediaItem): Int =
        naturalCompare(a.displayName, b.displayName)

    private fun naturalCompare(x: String, y: String): Int {
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            val cx = x[i]
            val cy = y[j]
            if (cx.isDigit() && cy.isDigit()) {
                var si = i
                var sj = j
                while (si < x.length && x[si].isDigit()) si++
                while (sj < y.length && y[sj].isDigit()) sj++
                val nx = x.substring(i, si).trimStart('0')
                val ny = y.substring(j, sj).trimStart('0')
                if (nx.length != ny.length) return nx.length - ny.length
                val cmp = nx.compareTo(ny)
                if (cmp != 0) return cmp
                i = si
                j = sj
            } else {
                val cmp = cx.lowercaseChar().compareTo(cy.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (x.length - i) - (y.length - j)
    }
}
