# About Refinement Implementation Plan

> **For agentic workers:** Execute inline using the executing-plans workflow and verify each task before claiming completion. User authorization covers this reversible UI refinement; do not commit or publish the inherited worktree.

**Goal:** 将“隐私与关于”精简为一个排版清晰、符合现有品牌风格的“关于”区。

**Architecture:** `AppAboutSection.kt` 封装折叠内容、版本查询和原有外链行为；`AboutBottomSheet.kt` 只组合此区。复用 `MainHeader.kt` 的品牌标记。原生视觉检查使用配置注入和 Intent 拦截。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Android instrumentation、ADB。

## Task 1: 内容与版式

- [x] 新建 `app/src/main/java/org/scrollloom/ui/main/components/AppAboutSection.kt`。使用 `ExpandableSection(uiText("关于", "About"))`，42dp 品牌标记，应用名和真实版本上下排列，16dp 内容间距，说明为设计文档中的一句话。
- [x] `MainHeader.kt` 的 `BrandMark` 改为 `internal`，几何和颜色不变。新建原生 `ic_open_external.xml`，用于完整宽度、最小 48dp 的“查看更新”行。
- [x] `AboutBottomSheet.kt` 删除旧关于内容和不再需要的版本、外链 imports，改为调用 `AppAboutSection()`。外链继续使用 `ACTION_VIEW`、`https://github.com/gxwane/scroll-loom/releases` 和 `FLAG_ACTIVITY_NEW_TASK`，浏览器缺失仍短提示。

## Task 2: 验证

- [x] 新建 `app/src/androidTest/java/org/scrollloom/ui/main/AboutUiReviewTest.kt`，渲染现有帮助内容，覆盖中文浅色、英文深色，以及各自 320dp/1.8 倍字体配置；滚动展开 About，确保版本、说明与更新按钮可达并生成截图。
- [x] 用 `IntentFilter` 精确拦截 HTTPS GitHub 发布页的 `ACTION_VIEW`，点击后确认 monitor 命中且仍可操作关于区；不真正启动浏览器或产生联网访问。
- [x] 执行 `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline`，预期 `BUILD SUCCESSFUL`、零 Lint errors、零禁止网络依赖。
- [x] 对本次重新备份的调试包数据生成 SHA-256 清单；安装后执行 `adb shell am instrument -w -e class org.scrollloom.ui.main.AboutUiReviewTest org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner`，预期 `OK (1 test)`，检查四张截图。

## Task 3: 实机与收尾

- [x] 恢复本次备份并核对全部文件哈希和相册清单。进入实际应用帮助页展开“关于”，检查版本、间距、完整宽度操作和截图控制区的实际组合，保存 `build/about-refinement/about-device.png`。
- [x] 确认无障碍设置与本次开始时一致、屏幕捕获未启用，移除辅助测试包和本次手机临时文件，只清理已确认属于调试包的 QA 图片目录。
- [x] 运行 `git diff --check`，记录构建、原生验证、实际截图与数据恢复证据至 `docs/superpowers/verification/2026-10-08-about-refinement.md`，完成全部计划勾选。
