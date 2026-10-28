# 自适应无障碍帮助 Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task-by-task in the existing authorized working branch. No commits or publication are authorized.

**Goal:** 自动显示当前手机的无障碍帮助和实际权限状态，设置入口失败自动回退，无系统选择器。

**Architecture:** DeviceVendorDetector 识别品牌并提供集中资源映射。AccessibilityHelpState 纯函数决定显示状态，RestrictionStatus 保留未知结果。AccessibilitySettingsNavigator 负责有序 Intent 回退，Activity 在恢复时读取系统状态，Compose 帮助卡负责简洁呈现。

**Tech Stack:** Kotlin、Jetpack Compose、Android Settings / AccessibilityManager、JUnit、Android 原生实机测试。

## Task 1: 设备与权限模型

- [x] 扩展 `app/src/test/java/org/scrollloom/platform/DeviceVendorDetectorTest.kt`：荣耀制造商为 Huawei 时品牌 HONOR 优先；OPPO 制造商的一加/realme 品牌优先；POCO、Redmi、iQOO 和仅品牌字段能识别；未知为空安全回退。
- [x] 在 `app/src/main/java/org/scrollloom/platform/DeviceVendorDetector.kt` 增加 HONOR/ONEPLUS/REALME，集中短提示和路径资源映射，所有旧调用使用同一映射。
- [x] 新增 `AccessibilityHelpState.kt` 及测试：`resolve(connected, enabled, restriction, sdkInt)` 按 CONNECTED → NOT_CONNECTED → RESTRICTED → OFF 决策；仅 sdkInt >= 33 且未连接可显示受限说明。
- [x] `RestrictedSettingsHelper.kt` 新增明确三态探测；MODE_ALLOWED 为 ALLOWED，MODE_IGNORED/MODE_ERRORED 为 BLOCKED，MODE_DEFAULT/异常为 UNKNOWN；API < 33 为 NOT_APPLICABLE。
- [x] 运行 `.\gradlew.bat :app:testDebugUnitTest --offline`，确认上述行为测试通过。

## Task 2: 自动设置导航与实时状态

- [x] 新增 `app/src/main/java/org/scrollloom/platform/AccessibilitySettingsNavigator.kt`，按服务详情 → 定位列表 → 普通列表 → Settings 顺序尝试；逐项捕获 ActivityNotFoundException/SecurityException，全部失败返回 UNAVAILABLE，不查询或要求用户选择入口。
- [x] 在 `MainActivity.kt` 的 onResume 读取本服务是否已在 AccessibilityManager 的 enabledServices 列表中、限制状态；MainScreen / 帮助卡接收实时状态。跳转全部失败时给短提示，不崩溃；返回时不误判开启成功。
- [x] 原生导航测试校验参数确实是本包组件、定位参数正确、SecurityException 和 ActivityNotFoundException 会依序回退、全部失败有返回结果。

## Task 3: 帮助与准备页面

- [x] 新增 `DeviceAccessibilityHelp.kt`：设备名/Android 版本、实际服务状态、一个主操作、折叠三步指引和受限排障；系统版本未知时保守回退，不猜测 ROM 版本。
- [x] `AboutBottomSheet.kt` 接入帮助卡，移除旧的重复固定路径和通用受限段落，手动页显示无需无障碍。帮助可一键回到自己滑动，使用现有模式切换回调，不直接启动会话。
- [x] 中英文资源补充品牌和短步骤，旧提示和准备页复用集中映射。`PreFlightGuideBottomSheet.kt` 去除重复 when 分支。
- [x] 原生帮助测试覆盖品牌注入不出现其他品牌、状态切换、API29 不显示受限菜单、API33 受限和未知区别、已连接不显示开启排障、手动模式无无障碍设置按钮、大字体英文可滚动触达。

## Task 4: 验收与恢复

- [x] 为本任务新建 `build/adaptive-accessibility-help/`，先对调试版 `files/drafts` 和 `shared_prefs` 新备份，记录 SHA-256、已有相册文件、无障碍和投影状态。
- [x] 执行 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline`；检查 Lint 和测试报告中的真实错误。
- [x] 安装调试包和辅助包，运行完整 `adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner`，读取 JUnit 输出中的最终 OK 及失败信息。
- [x] 查看帮助页的实机截图与状态/深色/大字体截图，真实点击帮助内前往设置验证 Huawei 入口与返回刷新，不开启权限。
- [x] 按本次备份恢复，逐文件哈希核对，相册和原有权限保持一致，清除本次 QA 文件和测试包，手机留下更新后的应用。`git diff --check` 通过。
- [x] 将命令、结果、截图、品牌实机验证边界和恢复证据写入 `docs/superpowers/verification/2026-10-08-adaptive-accessibility-help.md`。
