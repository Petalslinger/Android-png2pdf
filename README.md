# PNG 拼 PDF

一款原生 Android 图片合并应用。将手机中的多张截图或照片按指定顺序合成为单个 PDF 文件，支持纸张、朝向、缩放、页边距、背景等排版参数。

纯 Kotlin + Jetpack Compose 实现，**不依赖任何第三方 PDF 库**，PDF 生成基于系统 `android.graphics.pdf.PdfDocument`。

```
选择图片  →  调整顺序与排版参数  →  逐张渲染为 PDF  →  保存至「下载/PNG2PDF/」
```

---

## 目录

- [功能](#功能)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [架构设计](#架构设计)
- [关键设计决策](#关键设计决策)
- [测试](#测试)
- [构建与发布](#构建与发布)
- [已知限制](#已知限制)
- [常见问题](#常见问题)

---

## 功能

### 导入

| 能力 | 说明 |
| --- | --- |
| 批量选择 | 通过系统文件选择器一次选入多张图片 |
| 分享导入 | 支持从相册或文件管理器通过「分享 / 打开方式」直接批量送图 |
| 去重 | 同一文件重复选入时自动忽略，并给出提示 |
| 容错 | 单张文件无法读取（损坏、已被删除）时跳过并提示，不影响其余图片 |

### 列表管理

| 能力 | 说明 |
| --- | --- |
| 排序 | 选择顺序、文件名升/降序（自然序，`num2 < num10`）、时间升/降序 |
| 拖拽排序 | 长按条目拖动调整顺序，贴近列表边缘时自动滚动 |
| 按钮微调 | 上移 / 下移 / 单条移除 / 清空 |

### 预览

全屏预览当前图片：双指捏合缩放（1×–6×）、双击在 1× 与 2.5× 间切换、放大后单指拖动平移。顶部信息条实时显示文件名、像素尺寸、文件体积与当前缩放倍率。

### 排版

| 参数 | 可选值 |
| --- | --- |
| 纸张 | A4 / A5 / A3 / Letter / 自定义毫米尺寸 / 跟图片等大 |
| 朝向 | 自动（逐页判断）/ 纵向 / 横向 |
| 每页张数 | 1 张 / 2 张（上下）/ 4 张（2×2） |
| 缩放方式 | 完整缩放（保持比例，可能留白）/ 铺满裁切 / 拉伸填满 |
| 页边距 | 0–30 mm |
| 背景 | 白色 / 黑色 / 透明 |
| 长边上限 | 800–4000 px（控制降采样倍率与内存占用） |

### 导出

- 输出至公共「下载 / PNG2PDF」目录；系统不允许写入时自动回退到应用私有目录，并提示用户通过「分享」另存
- 实时进度与取消
- Android 10+ 使用 `IS_PENDING` 标记，写入中途的残缺文件不会出现在文件管理器中
- 单张图片绘制失败时记录日志并跳过，不使整份 PDF 报废
- 导出完成后可通过系统分享面板发送，或调用系统 PDF 阅读器打开

---

## 环境要求

| 项目 | 版本 |
| --- | --- |
| JDK | 17 或更高（Android Studio 内置 JBR 即可） |
| Android SDK Platform | 36 |
| Android SDK Build-Tools | 36.0.0 |
| Gradle | 8.14.3（由 Wrapper 指定） |
| Android Studio | Narwhal 2025.1.1 或更新（AGP 8.13 的最低要求） |

应用自身配置：

| 项目 | 值 |
| --- | --- |
| applicationId | `com.example.png2pdf` |
| compileSdk / targetSdk | 36 |
| minSdk | 26（Android 8.0） |
| Java / Kotlin 目标 | JVM 17 |

依赖版本统一由 `gradle/libs.versions.toml` 管理：

| 依赖 | 版本 |
| --- | --- |
| Android Gradle Plugin | 8.13.0 |
| Kotlin | 2.2.0 |
| Compose BOM | 2025.06.01 |
| core-ktx | 1.16.0 |
| lifecycle | 2.9.1 |
| activity-compose | 1.10.1 |
| exifinterface | 1.4.1 |
| Coil | 2.7.0 |
| JUnit | 4.13.2 |

---

## 快速开始

### 1. 配置 SDK 路径

Android Studio 首次打开工程时会自动生成 `local.properties`。命令行构建需手动创建，内容参考 `local.properties.example`：

```properties
sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
```

> 路径中的反斜杠需写成 `\\`，冒号需转义为 `\:`，这是 Java Properties 的语法要求。

### 2. 配置 JDK

Wrapper 脚本依赖 `JAVA_HOME`。若未设置，需在构建前指定：

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Users\<用户名>\.jdks\jbr-21.0.11"
```

```bash
# macOS / Linux
export JAVA_HOME=/path/to/jdk-17
```

### 3. 构建与运行

```bash
./gradlew :app:assembleDebug        # 构建 debug APK
./gradlew :app:installDebug         # 构建并安装到已连接的设备
./gradlew :app:testDebugUnitTest    # 运行排版逻辑单元测试（纯 JVM，无需模拟器）
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

> **关于 Gradle Wrapper**：本仓库不包含 `gradle/wrapper/gradle-wrapper.jar` 与 `gradlew` / `gradlew.bat`
> 脚本（生成它们需要联网下载 Gradle 发行版）。若首次构建报 `gradlew: command not found`，
> 在已安装 Gradle 8.14.3 的环境下执行一次即可补齐，之后便可正常使用 `./gradlew`：
>
> ```bash
> gradle wrapper --gradle-version 8.14.3 --distribution-type bin
> ```

---

## 项目结构

```
.github/workflows/android.yml          CI：安装 SDK/Gradle、运行单测、构建 debug APK、上传产物
local.properties.example               命令行构建所需的 SDK 路径模板
gradle/libs.versions.toml              依赖与插件版本目录（Version Catalog）

app/src/main/java/com/example/png2pdf/
├── MainActivity.kt                    入口：edge-to-edge、处理分享 Intent、挂载 Compose
├── data/
│   ├── MediaItem.kt                   图片模型、排序枚举、自然序比较器
│   ├── PageSettings.kt                纸张/朝向/缩放/背景/编码参数（全部为不可变 data class）
│   └── ImageMetaReader.kt             只读元数据（inJustDecodeBounds + EXIF），不解码像素
├── pdf/
│   ├── PageLayout.kt                  排版计算纯函数，零 Android 依赖
│   └── PdfBuilder.kt                  逐张解码 → 绘制到 PdfDocument 页面 → 写入输出流
├── ui/
│   ├── MergeViewModel.kt              唯一状态持有者：选图、排序、导出、分享
│   ├── MergeScreen.kt                 主界面（列表、拖拽排序、底栏、进度浮层）
│   ├── SettingsSheet.kt               排版设置 BottomSheet、自定义纸张对话框、结果面板
│   ├── ImagePreviewDialog.kt          全屏图片预览（缩放 / 平移 / 双击）
│   └── theme/Theme.kt                 Material 3 主题与动态取色
└── util/
    ├── PdfOutput.kt                   MediaStore 落盘、FileProvider 分享、显示路径
    └── UriPermissions.kt              持久化 Uri 读权限

app/src/test/java/com/example/png2pdf/pdf/PageLayoutTest.kt    排版逻辑单元测试
```

---

## 架构设计

依赖方向单向，共四层：

```
UI (MergeScreen / SettingsSheet / ImagePreviewDialog)
        │  读取状态、转发用户意图
        ▼
状态 (MergeViewModel / EditorState)
        │  编排业务流程
        ▼
领域 (PageLayout / PdfBuilder)
        │  计算与绘制
        ▼
基础设施 (PdfOutput / UriPermissions / ImageMetaReader)
```

### 分层职责

- **UI 层**不持有 `Context`，不执行解码或文件写入，只负责渲染 `EditorState` 与把用户操作转为 ViewModel 调用。
- **状态层**为单一状态持有者。`EditorState` 是不可变 data class，所有耗时工作运行在 `viewModelScope` 中。
- **领域层**中 `PageLayout` 为纯函数，输入图片尺寸与排版参数，输出「每张图片落在第几页的哪个矩形」，不引用任何 Android 类型，因而可脱离设备进行单元测试。
- **基础设施层**封装存储、权限与元数据读取，对上暴露无共享状态的接口。

---

## 关键设计决策

### 1. 内存：任意时刻仅保留一张 Bitmap

第一遍通过 `inJustDecodeBounds` 读取所有图片的宽高（不解码像素），据此完成排版计算；第二遍逐张 `decodeStream → 绘制到 Canvas → recycle()`。

因此即使处理数百张 4000×3000 的图片也不会 OOM，代价是生成速度较慢。如需改为并发处理，必须先评估峰值内存，不可简单地对列表做 `map { decode(it) }`。

### 2. 方向与尺寸：EXIF 只读取一次

竖拍照片的像素数据是横向的，依靠 EXIF 标记声明「需旋转 90°」。`ImageMetaReader` 读取方向后将宽高互换再存入 `MediaItem`，因此列表展示的尺寸即为最终 PDF 中的方向；`PdfBuilder` 在解码后通过 `Matrix` 实际执行一次旋转。

若不处理，用户会看到图片在 PDF 中「躺倒」。

### 3. 权限：不申请任何运行时权限

- **导入**通过系统文件选择器（`ACTION_OPEN_DOCUMENT`），该机制按文件授予临时读取权限，无需存储权限。
- **导出**通过 MediaStore Downloads（Android 10+）或应用私有目录，同样无需写入权限。

清单中声明的 `READ_MEDIA_IMAGES` 仅在实现「自动扫描相册」类功能时才会用到。

> **为什么不用系统照片选择器（`PickVisualMedia`）**：照片选择器通过 `OpenableColumns.DISPLAY_NAME`
> 返回的是合成文件名（形如 `58.png`，其中数字为 MediaStore 的 `_id`），无法获得真实文件名；
> 且其返回的 Uri 不可持久化，`takePersistableUriPermission` 会静默失败。
> `ACTION_OPEN_DOCUMENT` 可提供真实文件名，且 Uri 支持持久化，两者均无需申请权限。

### 4. 排版计算与绘制分离

`PageLayout.compute()` 是纯函数：输入每张图片的尺寸，输出每张图片所在的页码与目标矩形。`PdfBuilder` 只负责按矩形机械绘制。

这样收益在于：最容易算错的比例、居中、分格逻辑可以脱离模拟器进行测试。`PageLayoutTest` 覆盖了等比缩放边界、铺满裁切溢出、自动换向、2×2 不重叠、等大长图模式等场景。

### 5. 失败局部化

单张图片损坏或被删除时，`PdfBuilder.drawItem` 捕获异常并记录日志后跳过该图，继续绘制后续图片，而非让整份 PDF 失败。用户得到的是「缺少该张」的成品以及一条提示。

### 6. 输出流生命周期由调用方管理

`PdfOutput.Handle` 提供 `commit()` / `abort()` 语义，取消或异常时清理半成品文件。`PdfBuilder.writeTo()` 只负责写入，不关闭流。

早期实现使用 `lastWritten` 之类的字段在 `commit` 与调用方之间传递「写到哪了」，一旦 `commit` 中途抛异常，调用方只能拿到 `null`，从而报出「已生成但未能写入存储」这类掩盖真实原因的错误。改为由 `commit()` 直接返回 `WrittenPdf` 后，异常不再经过共享状态。

---

## 测试

排版逻辑的单元测试为纯 JVM 测试，不依赖 Android 框架与模拟器：

```bash
./gradlew :app:testDebugUnitTest
```

当前覆盖范围：

| 用例 | 验证内容 |
| --- | --- |
| 一图一页时页数等于图片数 | 页数计算 |
| 每页两张时向上取整 | 页数计算边界 |
| 空列表不产生页面 | 空输入处理 |
| 等尺寸页面得到同一页号 | 分页归组 |
| 完整缩放后图片完全落在页内 | 等比缩放边界 |
| 保持宽高比 | 缩放比例正确性 |
| 铺满裁切会超出页面边界 | COVER 溢出行为 |
| 自动方向让横图用横向页面 | AUTO 朝向 |
| 竖图用纵向页面 | AUTO 朝向 |
| 每页四张时二乘二排布不重叠 | 多图分格 |
| 长图模式下每页尺寸等于图片尺寸 | IMAGE_SIZE 模式 |
| 毫米到点换算正确 | 单位换算 |

---

## 构建与发布

### 构建 debug APK

```bash
./gradlew :app:assembleDebug
```

### 构建 release APK

release 变体已开启 R8 代码混淆与资源压缩，但**尚未配置签名**，直接执行 `assembleRelease` 产出的是未签名 APK，无法安装。需要先补充签名配置。

**方式一：Android Studio 向导**

`Build` → `Generate Signed Bundle / APK` → 选择 `APK` → 首次使用选择 `Create new...` 创建 keystore → 选择 `release` 变体 → 勾选 `V1` 与 `V2` 签名方案。

**方式二：手动配置签名**

生成 keystore：

```bash
keytool -genkeypair -v \
  -keystore keystore/png2pdf.jks \
  -alias png2pdf \
  -keyalg RSA -keysize 2048 -validity 10000
```

在 `app/build.gradle.kts` 中补充配置：

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/png2pdf.jks")
            storePassword = "…"
            keyAlias = "png2pdf"
            keyPassword = "…"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // …
        }
    }
}
```

> keystore 与口令需妥善保管。丢失后将无法向已安装用户发布更新。同时建议将 `keystore/` 加入 `.gitignore`。

### 持续集成

`.github/workflows/android.yml` 配置了 GitHub Actions 流程，推送代码后自动执行：安装 JDK 17 与 Android SDK（Platform 36 + Build-Tools 36.0.0）→ 安装 Gradle 8.14.3 → 运行单元测试 → 构建 debug APK → 上传测试报告与 APK 作为构建产物。

### 验证 APK 的 ABI 完整性

若 APK 需要安装到 arm64 真机，应确认其包含 `arm64-v8a` 原生库，否则安装会失败并报 `INSTALL_FAILED_NO_MATCHING_ABIS`：

```bash
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | grep native-code
```

期望输出包含 `arm64-v8a`。若只有 `x86_64`，说明构建时被注入了 ABI 过滤（通常由 Android Studio 部署到模拟器导致），此时应改用命令行构建。

---

## 已知限制

| 限制 | 说明 |
| --- | --- |
| 图片无损嵌入 | 当前图片以无损方式嵌入 PDF 页面，未接入 JPEG 重编码，因此输出体积主要由「长边上限」决定。`EncodeOptions.jpegQuality` 与 `flattenToJpeg` 为预留项，尚未生效。 |
| **铺满裁切配合每页多张时图片会互相覆盖（缺陷）** | `PageLayout` 在 `FitMode.COVER` 下返回的目标矩形会刻意超出所属格子（用于实现裁切效果），但 `PdfBuilder.drawItem` **没有按格子裁剪**。一图一页时超出部分被页面边界自然裁掉，行为正确；每页 2 张或 4 张时，超出部分会溢出到相邻格子并覆盖其中的图片。现有「每页四张不重叠」用例使用的是 `STRETCH`，无法覆盖该组合。修复需在 `PageLayout.Placement` 中增加格子矩形，并在绘制时 `clipRect` 到该矩形。 |
| 降采样精度 | `BitmapFactory.inSampleSize` 仅支持 2 的幂次。当前实现的条件为「长边 ÷ 2 ≥ 上限才降一档」，因此实际解码后的长边**最坏可达设定值的约 2 倍**（例如上限 2400 px、原图长边 4799 px 时按原尺寸解码），峰值内存相应高于预期。修复方向是按 `ceil(长边 / 上限)` 取 2 的幂。 |
| 列表选择顺序不持久化 | 退出应用后不保留上次的选择。`UriPermissions` 已实现持久化读权限，但尚未接入 DataStore 存储 Uri 列表与排版参数。 |
| 排序依据 | `MediaItem.dateModified` 记录的是读取元数据的时刻，而非文件真实修改时间，因此「时间排序」的实际效果接近保持当前顺序。 |
| 长图模式内存占用 | 「跟图片等大」模式不降采样，且 EXIF 旋转需要额外一次 `Bitmap.createBitmap`，峰值内存约为图片本身的两倍。不建议用该模式连续导出多张 4K 图片，可改用较低的长边上限。 |
| 透明背景的渲染差异 | 选择「透明」背景时，部分 PDF 阅读器会将透明页渲染为白底或黑底，属预期行为差异。 |
| 每页多张的间距 | 2 张 / 4 张模式下各格紧邻，没有格间距或分隔线。 |
| 无页码与水印 | 尚未实现。`PdfBuilder.drawItem` 末尾是合适的扩展位置。 |

---

## 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 打开工程提示 `gradle-wrapper.jar 缺失` | 本仓库未包含 Wrapper 二进制文件。Android Studio 会提示自动创建，或手动执行 `gradle wrapper --gradle-version 8.14.3`。 |
| `SDK location not found` | 缺少 `local.properties`，参见[快速开始](#快速开始)。 |
| `JAVA_HOME is not set` 或 Wrapper 找不到 Java | 未配置 `JAVA_HOME`，参见[快速开始](#快速开始)第 2 步。 |
| 装到真机报 `INSTALL_FAILED_NO_MATCHING_ABIS` | APK 缺少 `arm64-v8a` 原生库，参见[验证 APK 的 ABI 完整性](#验证-apk-的-abi-完整性)。 |
| 导出的 PDF 在某些阅读器中背景显示为纯白或纯黑 | 选择了「透明」背景，属预期行为差异。 |
| 图片较多时进度条长时间停留在 0 | 读取元数据的第一遍没有进度回调。可将 `onProgress` 前移以反映该阶段进度。 |
| 内存吃紧 | 降低「长边上限」；若使用「跟图片等大」模式，请改用固定纸张尺寸。 |
| 提示「PDF 已生成（存在应用内）」 | 系统未允许写入公共「下载」目录（部分 ROM 不支持 Downloads 集合、存储空间不足、被安全软件拦截）。这是正常的回退路径而非失败：文件位于应用私有目录，可通过结果面板的「分享」另存到文件管理器或网盘。 |
| 导出失败时需查看真实原因 | 弹窗显示异常类型与消息，完整堆栈位于 logcat，过滤 tag `MergeViewModel`。 |
| 希望进一步压缩 PDF 体积 | 当前图片无损嵌入，最直接的方式是调低「长边上限」；或在 `PdfBuilder` 中将降采样后的 Bitmap 重编码为 JPEG 后再绘制。 |
