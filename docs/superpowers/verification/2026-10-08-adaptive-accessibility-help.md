# 自适应无障碍帮助验收

## 实现

帮助不提供品牌或系统选择器。自动识别小米/Redmi/POCO/黑鲨、OPPO、一加、realme、vivo/iQOO、华为、荣耀、三星，其他设备使用通用 Android 指引。荣耀、一加、realme 独立识别；品牌字段优先处理共享制造商的子品牌，支持大小写、空字段和仅品牌字段。

权限卡展示实际设备类别和 Android 版本。未开启显示一个前往设置按钮；确实受限时优先打开本包应用信息；开关已开启但服务未连接时给重新开启指引；服务连接后只保留状态与管理按钮。未知探测结果不宣称已放行或已受限。Android 10–12 不展示 Android 13 的受限菜单，手动模式明确说明无需无障碍。

从帮助打开设置时保留帮助页，返回后重读权限；连接变化时刷新系统开启状态。拒绝或无法开启时可直接回到现有“自己滑动”模式，不启动捕获、不出现额外选择器。首次自动捕获准备页同样识别受限和未连接状态，屏幕捕获授权仍有原来的优先级。

设置导航逐级尝试服务详情、带定位参数的无障碍列表、普通列表、系统设置；缺失 Activity 或权限拒绝均自动回退，全部失败有明确短提示。服务详情 action 是系统接口，部分 ROM 要求系统权限，因此只作为可失败的优化；没有增加特权权限、反射或厂商私有组件路径。回退到设置首页时提示搜索无障碍。

指引从无障碍页面开始，以三步短行呈现，旧版菜单别名及搜索兜底放在展开内容中。不根据 Android 版本猜测 MIUI/HyperOS/ColorOS/MagicOS 的具体版本。官方核对资料列于设计文档；这不构成所有 ROM 版本均实机验证的声明。

## 验证

最终产品变更后的完整命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

完整构建 **BUILD SUCCESSFUL，14 秒**。单元报告汇总 **175 tests，0 failures，0 errors，0 skipped**。Lint **0 errors、0 fatal、90 warnings**，与本任务前的 90 项警告一致；最初新增的一项未使用资源警告通过复用已有应用信息按钮资源消除。禁止的网络依赖 **0**。

最后添加准备页优先级测试后，辅助测试包再次构建成功（2 秒）；最终完整原生回归 **OK (32 tests)，78.906 秒**。包含设置 Intent 自身组件参数与回退、品牌指引、权限状态替换、旧系统不显示受限菜单、手动兜底、准备页优先级、中英文大字体窄屏、现有截图入口、模式切换、浮窗、草稿、预览及导出回归。原生 Intent 单测不启动设置、不授予权限。

`git diff --check` 按仓库现有换行配置退出码 0；6 个新增 Kotlin 文件与 3 个本任务文档另行检查，没有尾部空格或未完成的计划步骤。一次临时关闭 autocrlf 的检查把既有 CRLF 文件视为尾部空白；未改动无关文件，按原配置复核通过。

首次视觉检查发现 1.8 倍字体下两位步骤编号在固定 26dp 宽度内换行，改为最小宽度随字体扩展、禁止编号换行；重新构建并通过上述最终回归。媒体导出测试产生的 Huawei 系统通知遮住部分早期截图，因此在通知消失后仅重跑帮助视觉测试，**OK (5 tests)，25.45 秒**，获得 17 张干净截图。没有为了截图修改手机通知设置。

Huawei STK-AL00，Android 10，1080 × 2340，density 3。真实点击更新后的帮助页“前往设置”，打开 `com.android.settings` 的无障碍页；滚动至“已安装的服务”，进入列表找到“连页”，状态为“已关闭”。返回两次后仍显示原帮助页、华为 Android 10 和“未开启”，未把未授权返回误判为成功。展开路径中的“已安装的服务”与实机吻合。没有开启任何无障碍或屏幕捕获权限。

首次 UI dump 在 Activity 刚启动时返回空根节点；确认进程存活、屏幕开启后重读成功，属于 Surface 尚未就绪，没有更改产品代码。最终截图在数据恢复后、实际应用的帮助页中生成。

## 证据

位于 `build/adaptive-accessibility-help/`：

- `native-tests.txt`：最终 32 项原生回归输出。
- `visual-check.txt`：清洁截图的 5 项针对性视觉回归。
- `verification-metrics.json`：175 项单元测试与最终 Lint 汇总。
- `help-device.png`：实际 Huawei 帮助页，默认折叠状态。
- `help-device-expanded.png`：实际 Huawei 帮助页，设备对应的三步指引。
- `settings-device.png`、`installed-services.png` 及对应 XML：真实系统入口与连页服务列表。
- `help-after-return.xml`：返回保留帮助页与真实未开启状态。
- `renders-clean/`：9 类设备指引、受限/未知/未连接/已开启/手动、1.8 倍字体中文浅色及英文深色 320dp、准备页。
- `full-final/`：完整回归中的首页、帮助、准备、草稿替换及浮窗渲染。

其他品牌的画面和 Android 13+ 权限状态是注入模型后在 Huawei 上的原生渲染与交互检查，不是其他品牌或 Android 13+ 系统的实机测试。大字体和深色仅注入 Compose 配置，系统状态栏保持设备原设置。实际品牌 ROM 的菜单名称仍可能随版本变化，已提供别名与自动导航回退。

## 数据恢复与清理

本任务新备份包含 **3 个文件、586 字节**，当前偏好为 **AUTO**、曾尝试开启、无受限记录；草稿目录及 current/tiles 为空。未使用任何前次任务备份。恢复后以及最终实际打开帮助、进入设置并返回后，全部 3 个文件的 SHA-256 都与本次备份完全一致，证据为 `restoration-check.json`。

相册原有 **4 个 PNG 文件名完全一致**。测试开始前调试包有一个屏幕捕获会话，随为安装和验收执行的 force-stop 关闭；没有重新申请捕获。结束时屏幕捕获为 `null`，无障碍服务仍为 `null`、开启值 `0`。不把运行中捕获会话称为已恢复。

删除测试辅助包 `org.scrollloom.debug.test`。外部 QA 路径分别解析确认为调试包 `files/adaptive-help`、`files/ui-polish`、`files/ui-qa` 后删除；确认外部 files 目录为空。删除本次命名的手机临时备份、截图和 UI dump。手机留在更新后的调试版帮助页，原有 AUTO 模式保留。

正式版未安装或修改。没有提交、发布、外部消息或新增网络依赖。
