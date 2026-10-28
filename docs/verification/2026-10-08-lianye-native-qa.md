# 连页 V5 原生实施与验收

验收日期：2026-10-07 至 2026-10-08。分支：`feat/product-redesign`。实现按 [原生实施计划](../superpowers/plans/2026-10-07-lianye-native-implementation.md) 落地，保留工作区原有改动。

## 交付内容

- 使用已批准的「连页」名称和 01 精密取景 Logo，暖纸、石墨、橘色主题；原生首页、帮助、准备面板、结果页与四角裁剪。
- 自动滚动继续使用无障碍；手动模式独立使用本次屏幕捕获授权，不要求无障碍。普通悬浮和通知均能开始、结束；两者不可用时留在准备页。
- 手动先准备，点击开始后等待真实首屏；稳定画面可靠拼接后才增加计数。首次完整滑动指引，第一次成功追加后仅收起文字，路线继续存在；关闭指引只影响本次。
- 普通返回保留当前草稿，新截图覆盖未保存内容时才确认。裁剪取消不修改草稿，应用后一次提交；保存/分享保留草稿和编辑状态。兼容旧草稿的遮盖数据，没有新增遮盖入口。
- 六组厂商帮助继续保留：小米、OPPO、vivo、华为/荣耀、三星、原生 Android。当前厂商路径按需展开，应用信息、电池和 ADB 帮助折叠。
- 全页面处理系统安全边距，保存目录为 `Pictures/Lianye`，分享使用系统选择器。

## 自动验证

最终命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

结果：`BUILD SUCCESSFUL`。实际读取 21 份 JUnit XML，142 tests、0 failures、0 errors。Lint 为 0 errors、88 warnings；现存警告没有被整体压制或作为依赖升级任务处理。

单元测试覆盖手动稳定序列、无新缓冲、原地/回滑、高分辨率位移、缺口冻结、通知遮挡恢复、End 竞态、受保护画面、可靠前段保留、干净候选边界、指引状态、草稿持久化/保存身份、PNG 编辑导出及原自动流程回归。

Android 测试包构建与实际设备运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

最终结果：`OK (7 tests)`，15.949 秒。包括 3 项平台导出、1 项首页深色/中文/大字号英文可达性、1 项应用草稿与 Activity 重建、2 项裁剪恢复/取消/局部历史。安全边距调整后运行通过。

## 实机验证

设备为 Huawei STK-AL00，Android 10 / API29，1080×2340。测试页面由 instrumentation 包生成，仅含 28 个编号段落。以下结果均为真实系统授权、真实应用操作及实际保存的 PNG。

| 路径 | 已验证行为 | 实际导出 |
| --- | --- | --- |
| 自动滚动 | 悬浮开始、连续自动滚动、约 4.1 屏时主动结束、共同预览、保存 | [1080×11085 PNG](../../build/lianye-native-qa/auto-export.png)，段落 1–14 连续，15 为末尾部分 |
| 手动普通悬浮 | 无障碍关闭；准备计数 0，开始后首屏 1；慢滑后 2、指引文字收起但路线保留；关闭指引后继续到 3；结束释放捕获 | [1080×4848 PNG](../../build/lianye-native-qa/manual-export.png)，段落 1–6 连续，7 为末尾部分 |
| 纯通知 | 无障碍关闭且普通悬浮未授权；静止首屏成功；展开通知栏不增加计数；回到页面并滑动能恢复可靠追加；通知 End 打开预览并释放捕获 | [1080×3598 PNG](../../build/lianye-native-qa/notification-export.png)，段落 1–4 连续，5 为末尾部分 |
| 控制均不可用 | 真实关闭通知渠道且普通悬浮不可用；停留准备面板，取消后投屏为空 | [准备页截图](../../build/lianye-native-qa/controls-unavailable.png) |

三张导出图已逐张查看：编号内容连续，未发现重复段、断层、自己的按钮/滑动路线或通知面板残留。原始系统状态栏保留在首屏，不是本产品控件。

界面证据：

- [首页](../../build/lianye-native-qa/auto-home.png)
- [停止临时权限并重启后保留的已保存草稿](../../build/lianye-native-qa/home-final.png)
- [首次完整指引](../../build/lianye-native-qa/manual-guide-full.png)
- [成功追加后保留路线](../../build/lianye-native-qa/manual-guide-route.png)
- [主动关闭后继续](../../build/lianye-native-qa/manual-guide-dismissed.png)
- [华为完整帮助路径](../../build/lianye-native-qa/help-huawei-expanded.png)
- [原生结果页](../../build/lianye-native-qa/auto-result.png)
- [大字号英文操作区](../../build/lianye-native-qa/instrumentation-final/home-large-en-action.png)
- [裁剪结果](../../build/lianye-native-qa/instrumentation-final/preview-cropped.png)

## 通知模式的保守边界

普通悬浮模式确认缺口后冻结可靠前段。通知模式不能依赖公开接口判断任意系统通知面板，因而对稳定但无法匹配的画面保留原可靠锚点，不追加、不提高计数，并等待恢复可靠匹配。

实机已验证展开通知栏不污染导出，回到目标页面能继续。当在仍未匹配的画面（包括打开的通知栏）直接结束时，结果可能显示「未能接上，已保留前段」。这是一项保守提示，并不证明目标页面真的缺失。实现没有加入通知栏识别猜测，也没有要求额外敏感权限。

纯通知且本次从未挂载产品悬浮窗口、projection 有效、产品 Activity 不可见时，稳定候选本身可作为干净候选，解决静止 VD 没有新缓冲的问题；任何一次悬浮窗口历史都会关闭这条优化。对应条件有单元测试。

## 权限与数据清理

测试只更新 `org.scrollloom.debug` 和其 instrumentation 包。正式 `org.scrollloom` 仍安装，未卸载、清除或覆盖正式数据。

收尾实际检查：

```text
dumpsys media_projection: null
enabled_accessibility_services: null
accessibility_enabled: 0
SYSTEM_ALERT_WINDOW: default
POST_NOTIFICATION: default
debug loom_projection_channel: importance=2
```

临时启用的测试无障碍和普通悬浮已恢复；屏幕捕获已结束。用于不可用分支验收的通知渠道恢复可用。生成的测试长图和当前已保存草稿保留供检查。

## 交付与尚未实测范围

安装包：[lianye-v5-debug.apk](../../build/lianye-native-qa/lianye-v5-debug.apk)。包名 `org.scrollloom.debug`，版本 `0.1.0-debug`，大小 26,056,772 字节，可与正式版并存。

SHA256：`D1AE33C32068EBA964D1B3FB5B1DF76AA5A00FA1C1767BCE01464D93CB4C52EE`。

本轮没有 Android 12/14/15 和其他厂商实机。相关版本分支、MediaProjection 回调/会话所有权、通知直接 Activity PendingIntent、触摸窗口边界以及系统安全边距已实现并经过编译、适用单元测试和代码核验；不将其描述为全系统实机验收完成。下一轮跨设备验证应覆盖现代 MediaProjection 授权、通知权限、悬浮触摸规则和厂商设置路径。

系统安全边距依据：[Android Compose insets](https://developer.android.com/develop/ui/compose/system/insets)。
