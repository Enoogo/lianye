# 手动拼接与恢复实机验收 · 2026-10-08

修复包已安装到华为 STK-AL00（Android 10 / API 29，1080 × 2340）。手动会话暂未接上时保留可靠锚点并继续观察；恢复重叠后续接、恢复滑动指引。继续使用现有悬浮球，没有增加用户选项。

## 修复内容

- `ManualFrameAnalyzer` 按实际固定边缘和可靠重叠边界匹配滚动内容；标题冲突或存在近似候选接缝时仍拒绝拼接。
- `ManualFrameStability` 识别静止布局中的局部动画，时间变化区域不会压倒仍可靠的内容。有效证据不能少于视口十分之一。
- `LoomEngine` 移除两次失败后的永久冻结。失败帧不写入、不增加进度；结束时只提交可靠前段。
- `MediaProjectionFrameCapturer` 在控件隐藏完成后排空中间缓冲，再等新的画面。实机曾捕获到的球与关闭按钮残影已消失。
- 隐藏控件时保留透明点击区域，结束点击不会落到下面的应用；可靠重叠下边界辅助裁掉半透明固定底栏，使底栏只保留一次。

## 自动验证

最终源代码验证命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

结果：24 个单元测试套件，161 测试，0 失败，0 错误；Android 实机 20 测试通过（35.846 秒）。APK 构建成功；lint 0 错误、88 警告，未将既有警告写成“零警告”。

新增/更新回归覆盖：大导航栏、局部与大面积动画、时间变化徽标、折叠导航栏、重复段落的独特标题、歧义拒绝、无法匹配后的恢复、结束时前段保留、提示恢复、半透明底栏的逐像素输出，以及隐藏时真实触屏点击结束。

`ManualDeviceFrameTest` 的资源均来自离线生成页面。个人应用画面只在被忽略的 `build/manual-recovery-fix` 中用于调试。临时像素诊断代码已移除。

## 实机流程与输出

| 场景 | 独立核对结果 |
| --- | --- |
| 大固定导航 + 持续变化的矩形 + 重复段落 | 两次手动滑动分别接受 1147、1131 px |
| 故意跳到滚动位置 11000 | 不写入、不增加进度，提示“暂未接上，向下滑回一点” |
| 回到滚动位置 2628 | 自动恢复，接受 350 px；恢复滑动指引，结束可用 |
| 上述恢复后的输出 | 1080 × 4968，等于 2340 + 1147 + 1131 + 350；第 1–5 节连续，顶部/底部导航各保留一次 |
| Bilibili 连续手动滑动 | 首轮接受 1137、1120、1132 px，输出 1080 × 5729。成图检查发现半透明底栏重复，随后修复并重新测试 |
| Bilibili 最终版本 | 接受 1123、1135 px，输出 1080 × 4598；折叠导航正常，半透明底栏只出现一次，无悬浮球残影 |
| 手动结束后切换自动，未重启 App | 在首页切换自动、新建截图、授权、同一悬浮球开始；自动接受 17 次 795 px，结束正常 |
| 自动输出 | 1080 × 15855，等于 2340 + 17 × 795；首、中、末段节号连续，无控件残影 |

生成页面的手动与自动成图中，滑动指引色像素均为 0。动态矩形在不同采样时刻可能呈不同颜色；匹配保证内容位置连续，不保证动画处于同一时刻。

查看生成页面证据：

- [无法匹配时的提示](../../../build/manual-recovery-fix/fixture-verified-gap.png)
- [恢复后的持续滑动指引](../../../build/manual-recovery-fix/fixture-verified-recovered.png)
- [手动完整输出](../../../build/manual-recovery-fix/fixture-verified-result.png)
- [自动输出首、中、末段](../../../build/manual-recovery-fix/auto-output-review.png)

## 数据、权限与安装核对

原会话先结束并备份。恢复后对原有 3 个设置文件、草稿清单/备份及 4 个图片分块逐文件计算 SHA-256，共 9 文件与原备份完全一致。正式包 `org.scrollloom` 的最后更新时间仍为 `2026-10-02 17:49:35`。

最终状态：原有悬浮球权限仍为 `allow`，无障碍服务为 `null` / `accessibility_enabled=0`，屏幕捕获为 `null`。临时测试 APK、设备临时备份和像素诊断缓存已删除。手机保留更新后的 debug App 和原用户草稿。

修复 APK：[lianye-manual-fixed-debug.apk](../../../build/manual-recovery-fix/lianye-manual-fixed-debug.apk)

SHA-256（本地文件与手机已安装 base.apk 一致）：

```text
4AA92BC2BCF93E8A4F9EE84F9DE671A674CFF87D7964AC2A14FD149CF2E0196A
```

实机范围为上述华为 Android 10。没有足够重叠、仅有歧义重复内容时仍会提示滑回，避免凭估计补图；其他厂商/Android 版本未在本次获得新的实机证据。
