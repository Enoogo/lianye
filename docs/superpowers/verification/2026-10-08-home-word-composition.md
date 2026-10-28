# 首页图文字组验收

撤掉空首页的旧 slogan“一屏之外，完整留下。”及两种模式的副说明。首页视觉文字改为“连成”“长图”，英文为“One long”“image”，不含标点。通过字号、字重、字距及与插画的并排关系组织页面；没有新增选项或解释性文案。

新增 HomeEmptyHero，将修长页面示意和短字组组成居中的整体。普通宽度及字号采用左右构图；320dp 窄屏及大字体采用上下构图。字组作为一个读屏标题，系统字体缩放保留。LongCaptureIllustration 改为 148dp × 264dp 的两段页面，细线和短橘色连接沿用现有主题。草稿标题、缩略图、必要状态反馈及底部操作保持现有内容与路由。

## 检查结果

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

构建成功，35 秒。Lint 零错误、90 项既有警告；禁止的网络依赖为零。`git diff --check` 退出码 0。

完整 Android 实机测试 **OK (24 tests)，55.871 秒**。测试结果在 `build/home-word-composition/native-tests.txt`。本轮没有新增针对视觉样式数值的单元测试，也没有修改测试源码。

在 Huawei STK-AL00 / Android 10 / 1080 × 2340 / density 3 上检查了：浅色自动与手动首页、中文普通字号深色、中文 1.8 倍字体浅色 320dp、英文 1.8 倍字体深色 320dp，以及带已解码缩略图的草稿首页。控件可见且主操作可点击；大字体图文改为上下构图，无文字截断。深色及字号组合为原生测试注入，系统状态栏维持当前设备设置。

证据位于 `build/home-word-composition/`：

- `before-home.png`、`after-home.png`：实际应用修改前后截图；最终截图在本次备份恢复并重新启动后生成。
- `native-final/`：最终原生浅色、深色、两种模式及大字体截图。
- `draft-final/home-with-draft.png`：既有草稿页面。
- `native-tests.txt`：完整实机回归输出。

## 恢复核对

本次新备份含 **3 个文件、586 字节**，保存当前偏好和草稿目录；当时没有保留的草稿文件。恢复后以及最终应用启动后，全部文件的 SHA-256 与本次新备份完全一致。没有恢复其他任务的旧备份。

相册的 4 个已有文件名未改变。无障碍服务仍为 `null`，开启状态 `0`，屏幕捕获 `null`；本次未开启这些权限。测试辅助包、手机 QA 截图目录及临时备份、UI dump 已移除，调试版已更新并停留在新首页。正式包未修改，没有提交或发布。
