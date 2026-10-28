# 截图交互修复验收 · 2026-10-08

## 原因与修复

- 手动启动曾永久关闭自动悬浮球偏好；自动设置入口又没有明确选择 AUTO。现在手动会话只临时隐藏自动控件，自动设置明确切换模式，并释放未开始的手动准备会话。正在截图时继续禁止切换。
- 手动控件曾有独立实现，并在缺少悬浮窗权限时静默改用通知栏。现在手动、自动共同使用 `FloatingOverlayManager` 与 `LoomFloatingBubble`；手动默认申请悬浮窗，通知栏只在用户明确选择后使用。
- 通知栏会话曾错误标记滑动引导已学会。现在只记录实际悬浮球引导，使用独立偏好 `floating_guide_learned`。首次收下画面显示完整滑动提示；首次有效拼接后保留淡化起止位置和箭头，连续拼接仍保留。关闭仅影响当前会话；无法接上或到底时退出滑动引导。
- 已准备好的手动会话从帮助入口再次开始曾被状态检查挡住。现在恢复原准备会话，实际 Activity 路由回归覆盖此路径。
- 实机重复段落曾让匹配器选到错误位移。现在比较二维内容与竞争峰，对无法区分的重叠拒绝猜测。加入生成页面的实机帧回放，断言正确位移 1192 像素。

## 自动检查

最终功能改动后运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
git diff --check
```

- Gradle：`BUILD SUCCESSFUL`，构建与 Lint 通过。
- JVM 单元测试：151 项，0 失败、0 错误、0 跳过。
- Android 测试：`OK (13 tests)`，包括普通悬浮窗生命周期、模式路由和已准备帮助入口。
- 新增路由、帮助入口和通知栏引导回归均先确认旧实现失败，再验证修复通过。
- `git diff --check` 通过；只有现有文件换行转换提示。

## 实机结果

设备：华为 STK-AL00，Android 10 / API 29，1080 × 2340。

- 缺少悬浮窗权限时保留准备页，明确选择通知栏才进入通知栏控制。
- 手动悬浮球开始、持续滑动引导、结束与预览均实际操作；先后多次拼接后引导仍可见。
- 手动成图 1080 × 4730，生成页面第 1–6 节连续，第 7 节在停止位置部分保留；截图中未发现悬浮控件或引导，导出的引导颜色像素检查为 0。
- 手动结束后自动开始、自动结束后再次切换均验证；手动前后自动悬浮球偏好保持开启。
- 未开始的手动准备切换到 AUTO 后，旧 MediaProjection 释放。
- 完整自动成图 1080 × 21076，包含生成页面全部 28 节。
- 明确选择通知栏后的开始与结束可用。通知面板遮挡画面时，当前保守策略会保留已接上的前段并提示未能接上；本次未改变该策略。

本次实机覆盖上述华为 Android 10 设备；其他厂商与系统版本未在本次重新实测。

## 交付与恢复

- [实机截图与成图](../../../build/lianye-capture-fix/index.html)
- [最终测试 APK](../../../build/lianye-capture-fix/lianye-capture-fixed-debug.apk)
- [恢复后的首页](../../../build/lianye-capture-fix/restored-home.png)

APK SHA-256：`7BAF918068CBC516ED9BB620854F2E6E39DF9950FA43ED0A15EAC9D6CD4A67F1`。

手机保留最终修复的 `org.scrollloom.debug` 测试包。当前轮测试前备份的 3 个偏好文件逐字节恢复，草稿恢复为原来的 0 个。临时无障碍服务关闭、悬浮窗 AppOps 恢复为 `default`、MediaProjection 为 `null`。正式包 `org.scrollloom` 及其数据保持不变。
