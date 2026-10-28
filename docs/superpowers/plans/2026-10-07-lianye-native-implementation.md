# 连页 V5 Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Parallel workers have disjoint file ownership, shared contracts below, and root integration/review. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 将用户确认的连页 V5 原型落地为真实 Android 应用，完成自动与无需无障碍的手动长截图、简洁结果编辑及六组系统帮助。

**Architecture:** 自动路径继续由无障碍服务拥有。手动路径由 MediaProjection 前台服务拥有，独立普通悬浮/通知控制；引擎通过稳定采样和干净画面的可靠重叠追加，单份草稿继续使用既有持久化与导出。Compose UI 采用确认品牌与流程，MainActivity 只协调授权、导航和草稿保护。

**Tech Stack:** Kotlin、Jetpack Compose、coroutines/StateFlow、MediaProjection/ImageReader、WindowManager、JUnit、Android instrumentation、Gradle/ADB。

---

## 基线与范围

用户确认 V5 交互方向并要求开始实施。名称连页、Logo 01、暖纸/石墨/橘色、简单够用；无新增遮盖功能。只有当前草稿，普通返回保留，只在新截图覆盖未保存内容时确认。已有大量未提交改动保留在 `feat/product-redesign`。开始前 `:app:testDebugUnitTest :app:assembleDebug` 已成功（部分任务使用现有缓存）。本次测试仅更新 `.debug` 包，保留正式 `org.scrollloom` 与数据，不清除调试版数据。

## 固定接口

Root 创建 `app/src/main/java/org/scrollloom/domain/model/CaptureMode.kt`：

```kotlin
enum class CaptureMode { AUTO, MANUAL }
enum class ManualPhase { IDLE, PREPARING, READY, TAKING_FIRST, RECORDING, FIRST_FAILED, GAP, FINISHING }
enum class ManualGuide { FULL, ROUTE, HIDDEN }
enum class ManualControl { OVERLAY, NOTIFICATION, UNAVAILABLE }
data class ManualCaptureState(
    val phase: ManualPhase = ManualPhase.IDLE,
    val acceptedFrames: Int = 0,
    val guide: ManualGuide = ManualGuide.HIDDEN,
    val control: ManualControl = ManualControl.UNAVAILABLE,
    val message: String? = null
)
```

Repository 增加 `captureMode: StateFlow<CaptureMode>`、`setCaptureMode(mode)` 和 `manualSession: StateFlow<ManualCaptureState>`、`updateManualSession(state)`。模式切换在采集中、手动会话非空闲时拒绝；导出期另由 Activity/UI 阻止。

引擎保持 `startWeaving` 自动 API，新增：

```kotlin
suspend fun startManualWeaving(
    maxFrames: Int = Int.MAX_VALUE,
    onProgress: (CaptureProgress) -> Unit = {},
    onIssue: (CaptureCompletion) -> Unit = {},
    keepSamplingOnUnmatched: Boolean = false,
    onUnmatched: (Boolean) -> Unit = {}
): List<TileMetadata>
```

`FrameSample` 为 `Available(frame: PixelSlice)`、`NoNewFrame`、`Failed(reason: CaptureCompletion)`。`FrameCapturer.sampleFrame()` 默认封装旧 `captureFrame()`；`captureCleanFrame(candidate: PixelSlice)` 默认返回候选。MediaProjection override：原始样本只做稳定性识别，只有首屏/稳定新候选确认时隐藏控件并取得干净新缓冲，避免静止期间周期闪烁；tile 永不写原始带控件样本。纯通知模式且本次从未显示自己的窗口、projection 有效、产品页不可见时，允许复用已干净的稳定候选，兼容静止画面没有新 VD 缓冲的设备。

实施调整：普通悬浮模式确认缺口后冻结可靠前段；通知模式遇到无法匹配的画面时保留锚点并继续等待，不追加、不提高计数，恢复可靠匹配才继续。在未匹配状态结束会返回保守的 `ALIGNMENT_FAILED`。这不宣称能识别任意系统通知面板；边界见验收记录。

会话服务固定公开接口：`startManual(context, resultCode, data, preferOverlay=true)`、`refreshManualControls(context)`、`finishManual(context)`、`cancelManual(context)`、`setProductVisible(visible)`。原 Android 10 自动 `start/stop` 保持。手动服务从 Repository 的手动状态发布进度及指导；MainActivity 观察 READY 且控制可用后延续任务回桌面。

`MainScreen` 保留旧回调，增加 `draft: Draft? = null`、`captureMode: CaptureMode = AUTO`、`showManualFallback: Boolean = false`、`isBusy: Boolean = false`、`onManualCapture`、`onSelectCaptureMode`、`onOpenAccessibilitySettings`。Preview 保留草稿编辑、导出、分享及视口接口，删除产品中的完成清理和遮盖入口。

## Task 1: 共用状态及 Activity 集成（root）

**Files:** `domain/model/CaptureMode.kt`、`domain/repository/LoomRepository.kt`、`ui/main/MainActivity.kt`、`App.kt`、`AndroidManifest.xml`；相关 repository/activity 测试。

- [x] 增加上述共享状态，保持原草稿同步/IO锁、导出身份绑定及自动流程。
- [x] MainActivity 记住所选模式；自动未就绪展示简洁准备面板及本机帮助，手动只请求本次 MediaProjection。取消授权不启用任何服务、不创建草稿。
- [x] 手动授权后等待服务 READY；无可见控制留在准备，只请求悬浮窗或通知中的一种；授权返回重新检查实际能力。通知同时检查应用及渠道，不能只看 runtime permission。
- [x] 控制可用后自动回桌面；目标应用由用户打开，普通服务不声称自动识别任意应用。返回本应用时隐藏控制，活跃采集保留可靠部分。
- [x] 普通返回保留草稿，删除 Finish 的清理确认；只有 new 覆盖未保存草稿时提供「保存并新建」「放弃并新建」。保存失败留在原草稿，导出期不能换模式/新建。
- [x] Manifest 增加 `SYSTEM_ALERT_WINDOW`、`POST_NOTIFICATIONS`；保持没有 INTERNET。启用 MediaProjection service 于全部支持版本，但自动 API30+ 不启动它。
- [x] 增加明确非导出的透明 `CaptureActionActivity`，`taskAffinity=""`、`excludeFromRecents=true`，仅供通知直接 Activity PendingIntent，保留正式 applicationId。

## Task 2: 手动稳定采集与可靠拼接（native_capture_engine）

**Owned files:** `engine/FrameCapturer.kt`、`engine/LoomEngine.kt`、`engine/OverlapMatcher.kt` 的必需适配、新 manual helper，以及 engine 单元测试；不改 UI/service/repository。

- [x] 加入固定 FrameSample / clean-frame 契约与独立手动入口。
- [x] 等待有效首屏，不提前计数；相邻样本稳定后，clean 候选与最后可靠 clean 锚点对齐。稳定判定不把同一候选重复放入原 temporal mask。
- [x] 静止、暂无新缓冲、重复、回滑不计数、不自动结束；禁用自动位移先验，允许高分辨率半屏搜索，队列有界。
- [x] 确认缺口冻结追加并发布 issue，保留前段并等待 End；中断、受保护内容、存储失败保留可靠内容。固定 footer 仅最终保留一次。
- [x] End 收尾已确认稳定内容，不强制采通知面板；取消及 first-frame/End 竞态不造画面、不执行自动手势。
- [x] 编写真实序列测试，覆盖原地/稳定/运动/回滑/高分辨率/缺口/末段/首屏/资源释放；运行原自动与 matcher 回归。

## Task 3: MediaProjection 会话、悬浮及通知（native_capture_session）

**Owned files:** `service/capture/LoomMediaProjectionService.kt`、`MediaProjectionFrameCapturer.kt`、新 `CaptureActionActivity.kt`、新 `ui/floating/ManualFloatingOverlayManager.kt`、相关必要 helper/测试。旧 accessibility overlay 只作必需兼容，不改主 UI/engine/repository/manifest/App。

- [x] 手动授权后仅准备、计数零；Start/Retry 才调用手动引擎，首屏成功后才显示数字。首屏失败可重试或取消，等待首屏时的 End 由原回调完成。
- [x] 同一 Android 14 授权的 VD/Reader 持续使用，engine releaseSession 不销毁手动拥有的会话，任务结束/cancel/onStop 才彻底释放。
- [x] 普通悬浮控件与不可触摸路线分窗。路线窗口 alpha 不超过系统允许遮挡阈值，关闭按钮独立小窗，避免底层触摸被拦；保存位置并可拖动换侧。
- [x] 指导初次 FULL，第一次实际追加后 ROUTE，主动关闭本次 HIDDEN；下次已有学习记录从 ROUTE 开始。错误/收尾/结果清除指导。
- [x] 采 clean 图前隐藏所有自己的窗口、等待合成、丢弃旧缓冲；不要用 FLAG_SECURE 的黑块替代排除。
- [x] 控制入口能力检查：普通 overlay 是否允许，通知 permission/app/channel 是否实际可见；均不可用不送往目标。准备可取消。
- [x] 通知 Start/End/Retry/Cancel 直接 PendingIntent.getActivity，透明 Activity 使系统收起通知栏；Start 在透明页退出并稳定后开始，End 等待结果并打开共同预览，避免 notification trampoline 及后台启动限制。
- [x] 系统撤销/锁屏/服务销毁先停止采集、完成可靠前段，再清资源/状态；不恢复过期授权。

官方边界：[MediaProjection](https://developer.android.com/media/grow/media-projection)、[普通窗口触摸规则](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE)、[Android 12 notification trampoline](https://developer.android.com/about/versions/12/behavior-changes-all)、[通知权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)。

## Task 4: 已确认品牌与精简 Compose UI（native_product_ui）

**Owned files:** `ui/main/MainScreen.kt`、`MainUiState.kt`、`ui/main/components/*`、`ui/common/theme/*`、`ui/preview/ScrollPreviewScreen.kt`、裁剪 helper、strings 与 drawable/vector 资源；不改 MainActivity、repository、engine、capture session、PreviewTileCache。

- [x] 根据 `design/brand/lianye/refined-01/assets` 将批准 Logo 转为 native vector、自适应与单色资产，应用名连页，资源中旧长卷称呼同步。
- [x] 首页大标题、一句描述、长图图形、文字帮助、一个主动作；草稿使用真实缩略图及状态，删冗余卡片/脚注。拒绝无障碍可见手动兜底。
- [x] 帮助三步、本机六 OEM 路径自动选择按需展开；高级应用信息/电池/ADB 折叠；模式选择在帮助。
- [x] 结果标题/保存状态/尽量宽长图，默认仅裁剪、保存、文字分享；查看菜单保留缩放/适宽/顶部/底部。大图分段作为必要导出保护而非常驻选项。
- [x] 四角裁剪、滚动及全图定位、局部撤销/重做/还原；取消不写草稿，应用才更新。删除新遮盖编辑但保留旧草稿数据兼容。
- [x] 浅深色、字号及触控目标检查；保留错误/进度行内反馈，不加成功确认弹窗。

## Task 5: 集成、验证及实机验收（root + workers）

- [x] 先统一编译，逐个修复集成差异，不丢弃既有改动。检查 MODE/API/状态字段所有权及授权清理。
- [x] 运行 ` .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain`；检查真实输出及 XML 测试汇总。
- [x] 更新已有 instrumentation 对新交互的断言，构建 `:app:assembleDebugAndroidTest`，仅更新测试包后运行 `adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner`，保留草稿/保存竞态/恢复与平台导出验证。
- [x] 仅 `adb install -r app/build/outputs/apk/debug/app-debug.apk`；不卸载正式包，不清除任何用户数据。连接手机为 Huawei STK-AL00/API29，初始无障碍服务列表为 null。
- [x] 临时按此前用户授权启用调试包屏幕捕获/无障碍测试；验收自动、拒绝无障碍手动、普通悬浮与通知，截图首屏、简化路线、关闭后继续、结束、草稿/裁剪/保存。
- [x] 检查导出图无控件/提示残留、重叠/重复/缺口，记录可证实的 Android29 实机范围。API31/34/35 若无设备则写明编译/单元/代码核验范围，不声称均实测。
- [x] 测试后停止捕获和调试无障碍、撤回临时窗口授权（若由测试临时启用），保持正式应用及数据。输出 APK、实机图及实施记录。

## 实施状态（2026-10-08）

已完成上述实施与 Huawei STK-AL00 / Android 10 实机范围：142 项单元测试、7 项 instrumentation 通过，构建成功，Lint 0 errors / 88 warnings。自动、无需无障碍的手动悬浮、纯通知三条路径均生成并检查了实际 PNG。没有 API31/34/35 设备，未将这些版本标记为实测。临时权限已恢复，正式包及数据保留。具体证据、限制和安装包位置见 [原生实施验收记录](../../verification/2026-10-08-lianye-native-qa.md)。
