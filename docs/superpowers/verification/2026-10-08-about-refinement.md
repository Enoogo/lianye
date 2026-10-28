# 关于区验收

## 结果

帮助页底部只保留“关于”。应用标记复用首页几何和主题颜色，名称、真实版本上下排列；说明缩为“开源，无广告。截图仅在本机处理。”；“查看更新”为完整宽度的轻量操作行，右侧外链图标表明离开应用。原 GitHub Releases 地址和浏览器缺失提示保留。未修改首页、设备适配、权限导航或截图逻辑。

## 构建与原生界面检查

最终产品变更的命令：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline
```

`BUILD SUCCESSFUL in 10s`，禁止网络依赖为 0。仅调整测试容器后再次执行 `:app:assembleDebugAndroidTest :app:lintDebug --offline`，`BUILD SUCCESSFUL in 3s`。最终 Lint 为 **0 errors、0 fatal、90 warnings**，与任务前一致。

```powershell
adb shell am instrument -w -e class org.scrollloom.ui.main.AboutUiReviewTest org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

最终 **OK (1 test)，6.701 秒**。一个场景循环覆盖中文浅色、英文深色、中文浅色大字体、英文深色大字体。大字体为 1.8 倍，实际组件宽度断言为 320dp。版本、说明、展开操作与更新按钮均可滚动访问；更新按钮四次命中精确的 HTTPS GitHub 发布页 Intent 拦截器，没有真正打开浏览器。

首次裸 Box 测试容器未提供 Material 内容颜色，导致深色截图中的默认文字为黑色。关于应用名改为明确的主题文字色，测试宿主改为 Surface；另用 Box 保留子内容的宽度上限，避免 Surface 的最小约束使窄屏检查失真。最终四张截图逐张复核，关于内容无截断，深色对比度正常。英文大字体场景滚动后顶部说明离开视口，属于正常纵向滚动。

测试在 Huawei STK-AL00、Android 10、1080 × 2340、density 3 上运行。语言、深色和字体大小为 Compose 配置注入；系统状态栏保留手机原设置。没有改动用户系统主题或字号。本次为关于区的针对性 UI 验证，没有重跑与这次变更无关的截图算法完整回归。

## 数据和设备状态

使用本任务重新生成的备份，包含 **9 个文件、15,255,281 字节**。恢复后以及最终实际展开关于区后，所有文件 SHA-256 与备份完全相同。相册原有 **5 个 PNG 文件名一致**。

初次设备读取时，无障碍为 `org.scrollloom.debug/org.scrollloom.service.LoomAccessibilityService`、开启值 `1`；屏幕捕获为 `null`。为安装执行 force-stop 后，该设备的服务配置读数变为 `null/0`，因此早期的 `accessibility-before.txt` 实际记录的是停止后的状态，原始读数单独记录在 `accessibility-initial.json`。

原生自动化结束后曾留下系统的待连接记录：Enabled services 包含连页、Bound services 为空、Binding services 包含连页；仅更新设置或在系统界面重新开关没有消除记录。最终停止并重新启动本调试包后，该待连接记录清空，再恢复任务开始时的无障碍配置。最后的系统读数为 **Enabled 连页、Bound 连页、Binding 为空**，实际帮助页显示“已开启 / 无障碍服务已连接”。不把只有设置值为 1 当作连接成功。没有为此修改产品权限逻辑。

最终屏幕捕获仍为 `null`，没有申请捕获或生成新的长图。删除辅助包 `org.scrollloom.debug.test`。QA 图片目录解析为调试包的 `/storage/emulated/0/Android/data/org.scrollloom.debug/files/about-review` 后清理，外部 files 目录为空；本任务手机临时备份、截图和 XML 已删除。手机停在实际应用的“关于”展开页。正式版未更新，未提交或发布。

## 证据

本任务产物位于 `build/about-refinement/`：

- `about-device.png`：最终实际应用帮助页，关于展开、无障碍已连接。
- `renders-verified/`：四种最终原生渲染配置。
- `build-final.txt`、`test-build-verified.txt`、`native-tests-verified.txt`：最终构建与原生验证输出。
- `verification-metrics.json`：Lint 和相册对比。
- `backup-manifest.json`、`restoration-check.json`：文件哈希和最终完整恢复结果。

`git diff --check` 使用仓库原有换行配置检查；新增组件、原生视觉检查、矢量和本任务文档另行扫描尾部空白。没有要求用户选择额外设置或重新确认已授权的测试步骤。
