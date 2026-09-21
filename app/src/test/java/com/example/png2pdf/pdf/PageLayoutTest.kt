package com.example.png2pdf.pdf

import com.example.png2pdf.data.FitMode
import com.example.png2pdf.data.Orientation
import com.example.png2pdf.data.PageMode
import com.example.png2pdf.data.PageSettings
import com.example.png2pdf.data.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单测：`gradlew :app:testDebugUnitTest` 就能跑，不需要模拟器。
 * 这也是把排版逻辑抽成纯函数的主要收益。
 */
class PageLayoutTest {

    private val a4Portrait = PageSettings(pageSize = PageSize.A4, orientation = Orientation.PORTRAIT)
    private val a4Auto = PageSettings(pageSize = PageSize.A4, orientation = Orientation.AUTO)

    @Test
    fun `一图一页时页数等于图片数`() {
        assertEquals(10, PageLayout.pagesNeeded(10, a4Portrait))
    }

    @Test
    fun `每页两张时向上取整`() {
        val settings = a4Portrait.copy(pageMode = PageMode.TWO_IMAGES_PER_PAGE)
        assertEquals(3, PageLayout.pagesNeeded(5, settings))
        assertEquals(3, PageLayout.pagesNeeded(6, settings))
    }

    @Test
    fun `空列表不产生页面`() {
        assertEquals(0, PageLayout.pagesNeeded(0, a4Portrait))
        assertTrue(PageLayout.compute(0, a4Portrait) { 100 to 100 }.isEmpty())
    }

    @Test
    fun `等尺寸页面得到同一页号`() {
        val placements = PageLayout.compute(4, a4Portrait.copy(pageMode = PageMode.TWO_IMAGES_PER_PAGE)) { 800 to 600 }
        assertEquals(4, placements.size)
        assertEquals(listOf(0, 0, 1, 1), placements.map { it.pageIndex })
    }

    @Test
    fun `完整缩放后图片完全落在页内`() {
        val settings = a4Portrait.copy(fitMode = FitMode.CONTAIN, marginMm = 10f)
        val placement = PageLayout.compute(1, settings) { 4000 to 3000 }.single()
        val page = placement.pageSize
        val dest = placement.destRect
        assertTrue("left=${dest.left}", dest.left >= -0.01f)
        assertTrue("top=${dest.top}", dest.top >= -0.01f)
        assertTrue("right=${dest.right} page=${page.width}", dest.right <= page.width + 0.01f)
        assertTrue("bottom=${dest.bottom} page=${page.height}", dest.bottom <= page.height + 0.01f)
    }

    @Test
    fun `保持宽高比`() {
        val settings = a4Portrait.copy(fitMode = FitMode.CONTAIN, marginMm = 0f)
        val placement = PageLayout.compute(1, settings) { 3000 to 1000 }.single()
        val dest = placement.destRect
        // 横图放进竖版 A4：宽度顶满，高度按 1:3 缩放
        assertEquals(3f, dest.width / dest.height, 0.01f)
    }

    @Test
    fun `铺满裁切会超出页面边界`() {
        val settings = a4Portrait.copy(fitMode = FitMode.COVER)
        val placement = PageLayout.compute(1, settings) { 3000 to 1000 }.single()
        val dest = placement.destRect
        assertTrue("应至少有一条边超出页面", dest.width > placement.pageSize.width + 0.5f)
    }

    @Test
    fun `自动方向让横图用横向页面`() {
        val placement = PageLayout.compute(1, a4Auto) { 2000 to 1000 }.single()
        assertTrue(placement.pageSize.width > placement.pageSize.height)
    }

    @Test
    fun `竖图用纵向页面`() {
        val placement = PageLayout.compute(1, a4Auto) { 1000 to 2000 }.single()
        assertTrue(placement.pageSize.height > placement.pageSize.width)
    }

    @Test
    fun `每页四张时二乘二排布不重叠`() {
        val settings = a4Portrait.copy(pageMode = PageMode.FOUR_IMAGES_PER_PAGE, fitMode = FitMode.STRETCH)
        val placements = PageLayout.compute(4, settings) { 1000 to 1000 }
        assertEquals(1, placements.map { it.pageIndex }.distinct().size)
        val rects = placements.map { it.destRect }
        assertEquals(4, rects.size)
        // 一四格在左，二三格在右
        assertTrue(rects[0].right <= rects[1].left + 0.01f)
        assertTrue(rects[0].bottom <= rects[2].top + 0.01f)
    }

    @Test
    fun `长图模式下每页尺寸等于图片尺寸`() {
        val settings = PageSettings(pageSize = PageSize.IMAGE_SIZE, orientation = Orientation.PORTRAIT)
        val placements = PageLayout.compute(3, settings) { index -> 1080 to (2000 + index) }
        assertEquals(3, placements.size)
        assertEquals(1080f, placements[0].pageSize.width, 0.01f)
        assertEquals(2000f, placements[0].pageSize.height, 0.01f)
        assertEquals(2002f, placements[2].pageSize.height, 0.01f)
    }

    @Test
    fun `毫米到点换算正确`() {
        // A4 宽 210mm 约等于 595pt
        assertEquals(595.28f, PageSettings.mmToPt(210f), 0.5f)
    }
}
