# 长卷产品重构实施记录

目标：面向普通用户的温润现代离线长截图工具，完整覆盖开启、截图、编辑、草稿恢复、保存和分享。

已批准的产品选择：中文品牌长卷、英文 ScrollLoom；暖白墨色与克制蓝；一个持久草稿，成品交给系统相册。

- [x] 品牌、设计令牌、双语资源、首页和权限说明
- [x] 独立的服务能力与任务状态、真实进度、结束原因、持久草稿和恢复
- [x] 视口瓦片预览、裁剪、实色遮盖、撤销重做
- [x] 流式编辑导出、相册保存、多图临时分享及进度反馈
- [x] 悬浮窗状态、停止行为、位置记忆、Activity 生命周期集成
- [x] 单元测试、构建、静态检查、实机视觉与流程验证（当前可用的华为 Android 10）

共享模型见 domain/model/Draft.kt：编辑坐标为原图像素，矩形右与下边界不包含。草稿包含历史、单调修订号、保存修订号、查看位置和完成原因。

验收重点：授权取消和失效可恢复；截图中停止入口保持可见；后台与分享不丢草稿；预览和导出坐标一致；30000px 分图不漏行且分享所有分图；不声明网络权限；Android 10+ 与签名兼容。

## 验证记录（2026-10-03）

最终命令：`.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease lintDebug connectedDebugAndroidTest --offline --console=plain`，BUILD SUCCESSFUL。

- JVM：96 项，0 失败与错误。
- 华为 STK-AL00 / Android 10：6 项仪器测试通过，覆盖真实 MediaStore、FileProvider 多图分享、跨瓦片裁剪遮盖、30001px 精确分图、编辑与 Activity 重建、待应用编辑恢复、深色中文及 1.5 倍字号英文首页。
- Lint：0 错误、54 警告，主要为旧未用资源与依赖/目标 SDK 更新提示，详细报告位于 `app/build/reports/lint-results-debug.html`。
- 35 个 XML/SVG 文件解析成功；中英文 133 个字符串键一致；`git diff --check` 通过。
- Release 签名证书 SHA-256 与原正式版相同：`668646d9ddc4cabf26c1e8553f86a92bfcb3e3d533ff58479b10230d047c7b3a`。合并 Release Manifest 无 INTERNET 权限。

实机手动流程：系统授权、悬浮控件开始与停止、生成内容页面 28 段自动触底、预览、保存、打开系统相册、调起系统分享并取消、继续处理草稿。最终完整 PNG 为 1080×21076，首尾分别为第 1 与第 28 段，状态栏以下悬浮控件强调色像素为 0；手动结束保留 1080×7110 的部分结果。首帧污染问题通过延后创建 VirtualDisplay 至控件隐藏后解决，复测成品无该污染。

最后确认「无障碍已开启」权限说明分支及应用内停用服务入口。测试包可与原正式包共存；原正式包和原有相册图片保留。本次生成的三张验收图片已清理，验收副本保存在本地 `build/design-review/`。临时无障碍服务已停用，MediaProjection 活动会话为 null。

验收工具注意：`uiautomator dump` 默认会临时抑制其他无障碍服务，读取其输出时不能据此判断被测服务原先未启用。服务相关手动验收使用 `adb screencap` 和系统 dumpsys。参见 [Android UiAutomation 文档](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)。反馈中断与服务解绑也分别处理，参见 [AccessibilityService.onInterrupt](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onInterrupt())。

仍需更多实机覆盖 Android 11+、其他厂商、受限设置、动态或禁止截屏的页面、极低存储、横屏与折叠屏，以及长时间性能与 TalkBack 实际朗读。本次测试结果不代表这些场景均已实机验证。
