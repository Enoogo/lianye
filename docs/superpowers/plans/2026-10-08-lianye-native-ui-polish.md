# 连页实机 UI 精修 Implementation Plan

> **For agentic workers:** Use executing-plans for inline implementation and review checkpoints. Steps use checkbox syntax for tracking.

**Goal:** 统一连页原生 UI 的比例、字重、控件、草稿预览和弹窗，以真实手机截图完成视觉验收。

**Architecture:** Compose 共用主题及轻量按钮定义统一视觉；首页缩略图仍使用有界 PreviewTileCache，仅解码可见首段。Activity 保留状态所有权，覆盖确认拆为独立 Dialog。普通悬浮为 native View，手工对齐同一组颜色/圆角。

**Tech Stack:** Kotlin / Compose / vector drawable / Android instrumentation / Gradle / ADB。

## 1. 主题与共享组件

- [x] `ui/common/theme/Type.kt` 使用 14sp 操作字、Medium 标题和 28sp 主标题；`Theme.kt` 使用暖灰 surfaceContainer，统一圆角 12/18/20dp。新增 `ui/common/LianyeControls.kt`，主按钮 `heightIn(min=52.dp)`、`shape=RoundedCornerShape(12.dp)`，辅助图标动作最少 48dp。
- [x] 新增 native vector `ic_action_crop.xml` / `ic_action_save.xml` / `ic_action_share.xml` / `ic_chevron_down.xml`，24 viewport、1.7 stroke。

## 2. 首页与草稿

- [x] 修改 `ui/main/components/MainHeader.kt`：Logo 30dp、品牌 titleMedium 18sp、帮助 bodyMedium、统一 56dp 顶栏。
- [x] 修改 `ui/main/MainScreen.kt`：用 BoxWithConstraints 计算草稿图片区域；缩略图 `pixelScale=width/crop.width`，`bottom=min(crop.bottom,crop.top+height/pixelScale)`，只读取限定首段；渲染先 clip，再施加已有 masks。卡片可继续编辑，状态单行；统一底部按钮。
- [x] 调整长图示意的细线、橘色与留白，不加入动画或新的说明文字。

## 3. 任务页、帮助和弹窗

- [x] `ui/preview/ScrollPreviewScreen.kt` 统一工具栏、菜单、状态及操作条；保留所有坐标与编辑逻辑，只让滚动条视觉更细。
- [x] `ui/main/components/AboutBottomSheet.kt` 和 `CapturePreparationSheets.kt` 统一 14sp 文字、48/52dp 操作与折叠箭头。保留六 OEM 路径和权限分支。
- [x] 新建 `ui/main/components/ReplaceDraftDialog.kt`，纵向三个动作，`MainActivity.kt` 替换 AlertDialog 布局；原保存/覆盖/取消回调完全保留。
- [x] `ui/floating/LoomFloatingBubble.kt` / `ManualFloatingOverlayManager.kt` 对齐颜色、16dp 外框/10dp 操作圆角、文字和轻量按钮，保持触摸及 capture 排除逻辑。

## 4. 真实渲染和回归

- [x] 保存 `run-as` tar 备份当前调试包 files/drafts 与 shared_prefs。添加 `UiVisualReviewTest.kt`，真实渲染帮助/准备/覆盖确认；检查大字号时覆盖确认三个动作可达，取消回调只有一次；截图均为测试内容。
- [x] 构建运行 ` .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain`，读取 XML 和 Lint 结果。
- [x] 仅 install -r 调试包与测试包，运行 `adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner`。拉取截图、逐张查看并处理必要迭代。
- [x] 在测试进程结束、调试包 force-stop 后，从备份恢复实际草稿和偏好。查看手机上的首页/预览/弹窗，核实原草稿仍在。必要的临时截图权限全部撤回。
- [x] 输出精修 APK、真实截图及验证记录，不提交整个脏工作区。
