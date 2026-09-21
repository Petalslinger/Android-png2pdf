package com.example.png2pdf.data

/**
 * 页面与排版设置。全部是不可变值对象，改一项就 copy 一份交给 UI，
 * 这样 Compose 的重组判断和状态回滚都很简单。
 */
data class PageSettings(
    val pageSize: PageSize = PageSize.A4,
    /** 仅当 pageSize == CUSTOM 时生效，单位毫米 */
    val customWidthMm: Float = 210f,
    val customHeightMm: Float = 297f,
    val orientation: Orientation = Orientation.AUTO,
    val pageMode: PageMode = PageMode.ONE_IMAGE_PER_PAGE,
    val fitMode: FitMode = FitMode.CONTAIN,
    val marginMm: Float = 0f,
    val background: PageBackground = PageBackground.WHITE,
) {
    /** 解析后的页面尺寸（pt）；AUTO 方向会在生成时逐页翻转 */
    val pageSizePt: SizePt
        get() = when (pageSize) {
            PageSize.CUSTOM -> SizePt(mmToPt(customWidthMm), mmToPt(customHeightMm))
            else -> pageSize.sizePt
        }

    companion object {
        const val PT_PER_INCH = 72f
        const val MM_PER_INCH = 25.4f

        fun mmToPt(mm: Float): Float = mm / MM_PER_INCH * PT_PER_INCH
    }
}

data class SizePt(val width: Float, val height: Float)

/** A4/A5/A3/Letter 按 72dpi 换算成 pt */
enum class PageSize(val label: String, val shortLabel: String, val sizePt: SizePt) {
    A4("A4 (210 × 297 mm)", "A4", SizePt(595.28f, 841.89f)),
    A5("A5 (148 × 210 mm)", "A5", SizePt(419.53f, 595.28f)),
    A3("A3 (297 × 420 mm)", "A3", SizePt(841.89f, 1190.55f)),
    LETTER("Letter (8.5 × 11 in)", "Letter", SizePt(612f, 792f)),

    /** 页面尺寸完全等于图片像素尺寸：截图拼长图专用，导出后无留白 */
    IMAGE_SIZE("跟图片等大（长图模式）", "等大", SizePt(0f, 0f)),

    /** 自定义毫米尺寸 */
    CUSTOM("自定义尺寸", "自定义", SizePt(0f, 0f)),
    ;

    /** 每页放几张 */
    val supportsMultiPerPage: Boolean
        get() = this != IMAGE_SIZE
}

enum class Orientation(val label: String) {
    AUTO("自动"),
    PORTRAIT("纵向"),
    LANDSCAPE("横向"),
}

enum class PageMode(val label: String, val perPage: Int) {
    ONE_IMAGE_PER_PAGE("每页 1 张", 1),
    TWO_IMAGES_PER_PAGE("每页 2 张（上下）", 2),
    FOUR_IMAGES_PER_PAGE("每页 4 张（2×2）", 4),
}

enum class FitMode(val label: String, val hint: String) {
    CONTAIN("完整缩放", "整张图缩放到页内，保持比例，可能留白"),
    COVER("铺满裁切", "铺满整页，超出部分被裁掉，无留白"),
    STRETCH("拉伸填满", "强制拉满整页，比例会变形"),
}

enum class PageBackground(val label: String, val argb: Int) {
    WHITE("白色", 0xFFFFFFFF.toInt()),
    BLACK("黑色", 0xFF000000.toInt()),
    TRANSPARENT("透明", 0),
}

/** 导出的编码参数 */
data class EncodeOptions(
    /** 单边像素上限；超过则降采样。0 表示不限制（内存风险自负） */
    val maxPixels: Int = 2400,
    /** JPEG 质量，100 = 不重编码 */
    val jpegQuality: Int = 88,
    val flattenToJpeg: Boolean = false,
)
