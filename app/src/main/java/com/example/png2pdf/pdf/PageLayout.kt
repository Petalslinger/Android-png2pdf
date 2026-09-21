package com.example.png2pdf.pdf

import com.example.png2pdf.data.FitMode
import com.example.png2pdf.data.Orientation
import com.example.png2pdf.data.PageMode
import com.example.png2pdf.data.PageSize
import com.example.png2pdf.data.PageSettings
import com.example.png2pdf.data.SizePt

/**
 * 排版计算：**纯函数，不依赖任何 Android 类型**，因此可以直接跑 JVM 单测。
 * 这里产出的是"第几页、页多大、每张图放在页面的哪个矩形里"。
 */
object PageLayout {

    /**
     * 一张图片的落位结果。
     *
     * @param itemIndex  在待合并列表中的下标（0 起）
     * @param pageIndex  落在第几页（0 起）。**相邻且页码相同的摆放共用同一个 PDF 页面**
     * @param pageSize   该页尺寸（pt）
     * @param destRect   图片在页面坐标系里的目标矩形（pt）
     */
    data class Placement(
        val itemIndex: Int,
        val pageIndex: Int,
        val pageSize: SizePt,
        val destRect: PSnapshot,
    )

    /** 一个和 android.graphics.RectF 同构的小矩形，避免让布局层依赖 Android */
    data class PSnapshot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val isEmpty: Boolean get() = width <= 0f || height <= 0f
    }

    /** 每个 MediaItem 需要的页数 */
    fun pagesNeeded(itemCount: Int, settings: PageSettings): Int {
        if (itemCount <= 0) return 0
        if (settings.pageSize == PageSize.IMAGE_SIZE) return itemCount
        val perPage = settings.pageMode.perPage.coerceAtLeast(1)
        return (itemCount + perPage - 1) / perPage
    }

    /**
     * 计算全部排版结果。
     *
     * @param itemSizeAt 第 index 张图的**原始像素尺寸**（宽, 高），用于决定页面朝向和缩放；
     *                   调用方传 lambda，避免在计算布局时解码整张图。
     */
    fun compute(
        itemCount: Int,
        settings: PageSettings,
        itemSizeAt: (Int) -> Pair<Int, Int>?,
    ): List<Placement> {
        if (itemCount <= 0) return emptyList()

        val margin = PageSettings.mmToPt(settings.marginMm)

        // 长图模式：一图一页，页面尺寸 = 图片尺寸，零留白
        if (settings.pageSize == PageSize.IMAGE_SIZE) {
            return (0 until itemCount).mapNotNull { i ->
                val (w, h) = itemSizeAt(i) ?: return@mapNotNull null
                if (w <= 0 || h <= 0) return@mapNotNull null
                val page = applyOrientation(PSnapshot(0f, 0f, w.toFloat(), h.toFloat()), settings.orientation)
                val dest = when (settings.fitMode) {
                    FitMode.STRETCH -> PSnapshot(0f, 0f, page.width, page.height)
                    else -> centerInside(page, w.toFloat(), h.toFloat(), 0f)
                }
                Placement(i, pageIndex = i, pageSize = SizePt(page.width, page.height), destRect = dest)
            }
        }

        val perPage = settings.pageMode.perPage.coerceAtLeast(1)
        val result = ArrayList<Placement>(itemCount)
        var pageIndex = 0
        var i = 0
        while (i < itemCount) {
            val group = (i until minOf(i + perPage, itemCount)).toList()

            // 页面朝向：AUTO 时看本页第一张图是横构图还是竖构图（每页重新判断，
            // 这样横图竖图混排时每一页都能用足面积）
            val base = settings.pageSizePt
            val firstSize = group.firstNotNullOfOrNull { itemSizeAt(it) }
            val page = when (settings.orientation) {
                Orientation.PORTRAIT -> SizePt(minOf(base.width, base.height), maxOf(base.width, base.height))
                Orientation.LANDSCAPE -> SizePt(maxOf(base.width, base.height), minOf(base.width, base.height))
                Orientation.AUTO -> {
                    val landscapePage = firstSize != null && firstSize.first > firstSize.second
                    if (landscapePage) {
                        SizePt(maxOf(base.width, base.height), minOf(base.width, base.height))
                    } else {
                        SizePt(minOf(base.width, base.height), maxOf(base.width, base.height))
                    }
                }
            }

            val innerW = (page.width - margin * 2).coerceAtLeast(1f)
            val innerH = (page.height - margin * 2).coerceAtLeast(1f)

            // 多图分格：2 张时上下切；4 张时 2×2
            val cols = if (group.size >= 4) 2 else 1
            val rows = if (group.size >= 4) 2 else group.size.coerceAtLeast(1)
            val cellW = innerW / cols
            val cellH = innerH / rows

            group.forEachIndexed { slot, itemIdx ->
                val row = slot / cols
                val col = slot % cols
                val cellLeft = margin + col * cellW
                val cellTop = margin + row * cellH
                val cell = PSnapshot(cellLeft, cellTop, cellLeft + cellW, cellTop + cellH)
                val src = itemSizeAt(itemIdx)
                val dest = if (src == null || src.first <= 0 || src.second <= 0) {
                    cell
                } else {
                    placeInCell(cell, src.first.toFloat(), src.second.toFloat(), settings.fitMode)
                }
                result += Placement(
                    itemIndex = itemIdx,
                    pageIndex = pageIndex,
                    pageSize = page,
                    destRect = dest,
                )
            }

            pageIndex++
            i += perPage
        }
        return result
    }

    private fun placeInCell(
        cell: PSnapshot,
        srcW: Float,
        srcH: Float,
        fitMode: FitMode,
    ): PSnapshot = when (fitMode) {
        FitMode.STRETCH -> cell
        FitMode.CONTAIN -> centerInside(cell, srcW, srcH, 0f)
        FitMode.COVER -> {
            val scale = maxOf(cell.width / srcW, cell.height / srcH)
            val dw = srcW * scale
            val dh = srcH * scale
            PSnapshot(
                cell.left + (cell.width - dw) / 2f,
                cell.top + (cell.height - dh) / 2f,
                cell.left + (cell.width + dw) / 2f,
                cell.top + (cell.height + dh) / 2f,
            )
        }
    }

    /** 等比缩放后居中放进 [box] */
    private fun centerInside(box: PSnapshot, srcW: Float, srcH: Float, inset: Float): PSnapshot {
        if (srcW <= 0f || srcH <= 0f) return box
        val availW = (box.width - inset * 2).coerceAtLeast(1f)
        val availH = (box.height - inset * 2).coerceAtLeast(1f)
        val scale = minOf(availW / srcW, availH / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        return PSnapshot(
            box.left + (box.width - dw) / 2f,
            box.top + (box.height - dh) / 2f,
            box.left + (box.width + dw) / 2f,
            box.top + (box.height + dh) / 2f,
        )
    }

    /** 按方向整理页面矩形，返回左上角在原点的一个 [PSnapshot] */
    private fun applyOrientation(size: PSnapshot, orientation: Orientation): PSnapshot = when (orientation) {
        Orientation.PORTRAIT -> PSnapshot(0f, 0f, minOf(size.width, size.height), maxOf(size.width, size.height))
        Orientation.LANDSCAPE -> PSnapshot(0f, 0f, maxOf(size.width, size.height), minOf(size.width, size.height))
        Orientation.AUTO -> PSnapshot(0f, 0f, size.width, size.height)
    }
}
