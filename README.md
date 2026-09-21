# PNG 拼 PDF（Android）

把手机上多张 PNG / JPG 截图按顺序拼成一个 PDF 的原生 Android 应用骨架。
纯 Kotlin + Jetpack Compose，**不依赖任何第三方 PDF 库**，直接用系统 `android.graphics.pdf.PdfDocument`。

```
选择多张图  →  调整顺序 / 排版参数  →  逐张渲染成 PDF  →  存到「下载/PNG2PDF/」
```

## 快速开始

1. 用 **Android Studio**（**Narwhal 2025.1.1 或更新**，例如 Otter / Panda / Quail；AGP 8.13 不支持更旧的版本）
   打开本目录 `android-png2pdf`。
2. 首次打开时 AS 会提示 `Gradle wrapper 缺失`并自动创建（本仓库未包含 `gradle/wrapper/gradle-wrapper.jar`
   与 `gradlew` 脚本，因为生成它们需要联网下载）。若想手动补，装好 Gradle 后在项目根目录执行：
   ```bash
   gradle wrapper --gradle-version 8.14.3
   ```
3. 确认 SDK 位置：AS 会自动写 `local.properties`（`sdk.dir=...`）。命令行构建请参考 `local.properties.example`。
4. 运行：
   ```bash
   ./gradlew :app:installDebug          # 装机
   ./gradlew :app:testDebugUnitTest     # 跑排版逻辑单测（不需要模拟器）
   ./gradlew :app:assembleRelease       # 出正式包（未配置签名，产物是未签名 APK）
   ```

环境要求：JDK 17 或以上（AS 自带 JBR 即可）、Android SDK **Platform 36** + Build-Tools、`minSdk 26`。

## 用 GitHub Actions 编译（本地没装 SDK 时最省事）

`.github/workflows/android.yml` 已经配好，推到 GitHub 就会自动跑：

```bash
git init && git add -A && git commit -m "init"
git remote add origin git@github.com:<你>/<仓库>.git
git push -u origin main
```

它会依次做：装 JDK 17 → 装 Android SDK（Platform 36 + build-tools）→ 装 Gradle 8.14.3 →
生成 wrapper → 跑 `:app:testDebugUnitTest` → 跑 `:app:assembleDebug`，
最后把**测试报告**和 **debug APK** 作为 artifact 上传（在 Actions 运行页底部下载）。

两点注意：

- 工作流里用的是 **`gradle` 命令而不是 `./gradlew`**，因为仓库里没有 `gradle-wrapper.jar`
  （生成它需要联网，本地生成不了）。CI 会顺手把 wrapper 补齐，所以第一次跑完之后你可以把
  CI 生成的 `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` 拉回本地，
  之后本地命令行也能直接用 `./gradlew`。
- 编译失败时，点开 Actions 里红色那一步的日志，**整段复制给 AI 或贴进 issue**，
  一次就能看到全部错误，比在 IDE 里一条条编译快得多。

## 目录结构

```
.github/workflows/android.yml    # CI：装 SDK/Gradle、跑单测、编 debug APK、上传产物
local.properties.example         # 命令行构建所需的 SDK 路径模板
app/src/main/java/com/example/png2pdf/
├── MainActivity.kt              # 只做三件事：edge-to-edge、接收分享 Intent、挂 Compose
├── data/
│   ├── MediaItem.kt             # 图片模型 + 排序（自然序比较器）
│   ├── PageSettings.kt          # 纸张/方向/缩放/背景/编码参数（全部不可变 data class）
│   └── ImageMetaReader.kt       # 只读元数据，不解码像素（inJustDecodeBounds + EXIF）
├── pdf/
│   ├── PageLayout.kt            # ★ 纯函数排版计算，零 Android 依赖，可 JVM 单测
│   └── PdfBuilder.kt            # 逐张解码 → 画进 PdfDocument 页面 → 写输出流
├── ui/
│   ├── MergeViewModel.kt        # 唯一状态持有者：选图、排序、导出、分享
│   ├── MergeScreen.kt           # 主界面（列表 + 底栏 + 进度浮层）
│   ├── SettingsSheet.kt         # 排版设置 BottomSheet + 自定义纸张对话框 + 结果面板
│   └── theme/Theme.kt           # Material3 + 动态取色
└── util/
    ├── PdfOutput.kt             # MediaStore/文件落地、FileProvider 分享、显示路径
    └── UriPermissions.kt        # 持久化 Uri 读权限

app/src/test/java/.../PageLayoutTest.kt   # 12 个排版单测
```

## 几个关键设计点（也是这个骨架值得留的地方）

**1. 内存：任何时候只有一张 Bitmap 在内存里**
`inJustDecodeBounds` 先把所有图片的宽高读出来（不解码），排版算完后，再逐张 `decodeStream → 画到
Canvas → recycle()`。所以 300 张 4000×3000 的图也不会 OOM，代价是"慢一点"。`maxPixels`（默认长边
2400）控制降采样倍率。想改并发就得先想清楚峰值内存，别顺手 `map { decode(...) }`。

**2. 方向和宽高：EXIF 只读一次**
竖拍照片的像素是横的、靠 EXIF 标记"转 90°"。`ImageMetaReader` 读方向后把宽高交换再存进
`MediaItem`，所以列表里显示的尺寸就是最终 PDF 里的方向；`PdfBuilder` 解码后用 `Matrix` 真正旋一次。
不处理的话用户会看到"图片躺倒"。

**3. 权限：一个都不用申请**
- 选图走系统 **照片选择器**（`PickMultipleVisualMedia`），Android 13+ 是系统组件，低版本 AndroidX
  自动回退到 `ACTION_OPEN_DOCUMENT`，两条路都不需要存储权限。
- 输出走 **MediaStore Downloads**（Android 10+）或公共下载目录（8/9），也不需要写权限。
  清单里的 `READ_MEDIA_IMAGES` 只有在你要"自动扫相册"时才需要。

**4. 排版逻辑与绘制分离**
`PageLayout.compute()` 是纯函数：给"每张图的尺寸"返回"每张图放在哪一页的哪个矩形里"。
输出 PDF 那一步只做机械翻译。好处是这 200 行最容易算错的比例/居中/分格代码可以脱离模拟器测试
（`PageLayoutTest` 覆盖了等比缩放边界、cover 溢出、AUTO 换向、2×2 不重叠、等大长图模式）。

**5. 失败要局部化**
单张图损坏/被删掉，只在 `drawItem` 里 catch 并记日志，跳过它继续画后面的，
而不是让整份 PDF 报废；用户拿到的是"少了那一张"的结果 + 一条提示。

**6. 每个副作用都可撤销**
流创建用 `PdfOutput.Handle`（`commit` / `abort`），取消或异常时删掉半成品文件，
Android 10+ 的 `IS_PENDING=1` 也保证写入中途的坏文件不会出现在文件管理器里。

## 已实现 / 未实现

已实现：多选、列表排序（自然序文件名 / 时间）、手动上下移动、删除、清空；纸张 A4/A5/A3/Letter/
自定义毫米/跟图片等大；自动换向；每页 1/2/4 张；完整缩放/铺满裁切/拉伸；页边距；白/黑/透明背景；
长边上限与压缩质量；导出进度与取消；存到下载目录、系统分享、系统阅读器打开；从相册"分享到本应用"
批量导入；12 个排版单测。

留给你的下一步（骨架里已留好接口位置）：
- **拖拽排序**：`MergeViewModel.move(from, to)` 已经是最终形态，UI 换成 `LazyColumn` + `Modifier
  .pointerInput` 的 drag 即可，不用改数据层。
- **持久化上次选择**：`UriPermissions` 已经做了 `takePersistableUriPermission`，再加一层 DataStore
  存 Uri 字符串 + settings 就能"打开就是上次的列表"。
- **页码 / 水印**：加在 `PdfBuilder.drawItem` 末尾，用 `Canvas.drawText`。
- **扫描后自动分页**：`PageLayout` 是纯函数，替换成"按内容高度切页"就能做长图分页。
- **正式签名与图标**：`app/build.gradle.kts` 里加 `signingConfigs`，图标用 AS 的 Image Asset 重新
  生成（现在 `res/drawable/ic_launcher_foreground.xml` 是占位矢量图）。

## 常见坑

| 现象 | 原因 / 处理 |
| --- | --- |
| 打开 AS 报 `gradle-wrapper.jar 缺失` | 本仓库未含 wrapper 二进制，AS 会提示自动创建；或执行 `gradle wrapper` |
| `SDK location not found` | 缺 `local.properties`，见上文 |
| 导出的 PDF 在某些阅读器里显示空白背景 | 选了"透明"背景，部分阅读器渲染透明页为白/黑，属预期 |
| 图片多时进度条长时间停在 0 | 第一遍读元数据没有进度回调，可自行把 `onProgress` 前移 |
| 内存吃紧 | 降低 `maxPixels`；"跟图片等大"模式不降采样，别用它导几十张 4K 图 |
| 提示"PDF 已生成（存在应用内）" | 系统的 MediaStore 不让你往公共「下载」目录写（个别 ROM 不支持 `Downloads` 集合、盘满、被安全软件拦）。这是**正常兜底**，不是失败：文件在应用私有目录里，点结果面板的「分享」就能另存到文件管理器/网盘 |
| 导出失败时想看到真实原因 | 弹窗现在显示异常本身的信息（`异常类型: message`），完整堆栈在 logcat 里，过滤 tag `MergeViewModel` |
| 想进一步缩小 PDF | 图片目前是无损嵌入页面的，压缩质量滑块是预留项；调低"长边上限"最直接，或在 `PdfBuilder` 里把降采样后的 Bitmap 重编码成 JPEG 再贴入 |
