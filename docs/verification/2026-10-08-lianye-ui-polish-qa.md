# 连页实机 UI 精修验收

日期：2026-10-08。设备：华为 STK-AL00，Android 10 / API 29，1080 × 2340，480dpi，系统字号 1.0。沿用已确认的连页名称、Logo 和 V5 流程。

## 本轮变化

| 页面 | 修正 |
| --- | --- |
| 共用视觉 | 14–15sp 正文、Medium 标题，暖纸及暖灰表面；52dp 主按钮、12dp 圆角，页面辅助动作保留至少 48dp 触控区；新增一致的细线矢量工具图标。 |
| 首页 | 缩小品牌区，调整插图比例；草稿显示限定首段的宽幅真实预览，状态和尺寸归到同一行，整张卡片可继续编辑。 |
| 预览与裁剪 | 统一工具栏、查看菜单、细滚动条及保存操作；裁剪和分享改为轻量图标动作，保留原有四角、全图、撤销和取消逻辑。 |
| 帮助与准备 | 紧凑的模式切换和三步说明；厂商路径和排障按需展开；保留 Xiaomi、OPPO、vivo、Huawei、Samsung、AOSP 帮助资源。路径中的产品名称避免逐字断行。 |
| 覆盖确认 | 暖纸弹窗及纵向完整的保存、放弃、取消动作；独立窗口沿用页面的语言、字号和密度。 |
| 悬浮控件 | 统一纸色、石墨主动作、圆角、线条和轻量阴影；保留持续路线提示、窗口 alpha、触摸透传和截图前隐藏规则。 |

## 构建与测试证据

最终产品源码（包括路径断行修正）运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

结果：`BUILD SUCCESSFUL in 40s`，78 tasks（15 executed）。单元测试 XML：22 suites，143 tests，0 failures，0 errors。Lint：0 errors，88 warnings，其中 79 条为 UnusedResources；本轮未宣称警告清零。

仅以 `adb install -r` 更新调试包与测试包，随后运行完整实机测试：

```powershell
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

结果：`Time: 25.13`，`OK (10 tests)`。覆盖平台导出、首页可访问性、裁剪应用及重建恢复、预览恢复、帮助/准备/确认操作、大字号英文弹窗，以及普通原生悬浮窗口的挂载和结束清理。悬浮测试运行时临时允许 SYSTEM_ALERT_WINDOW，结束后恢复 default；该测试没有申请屏幕捕获，也没有启用无障碍。

平台导出测试清理自身图片时，华为系统曾显示通知遮住截图。为取得干净的视觉证据，在完整测试通过后，仅重跑 `UiVisualReviewTest`：`Time: 11.181`，`OK (3 tests)`，重新拉取对应截图。

`git -c core.safecrlf=false diff --check` 通过。没有提交或重置现有脏工作区。

## 实机检查发现的实现问题

1. 首段缩略图的异步解码曾读取变化中的 viewport，得到 0 缩放并卡在采样循环。改为不可变的尺寸快照，并在 PreviewTileCache 拒绝非有限或非正缩放。新增回归测试已验证先失败、修正后通过；实机首页已显示原有草稿内容。
2. Android Dialog 独立窗口曾覆盖测试页面的语言及字号。显式传递宿主 LocalConfiguration、LocalContext 和 LocalDensity 后，1.8 倍英文弹窗中的三个操作均可见且可点；取消不会保存或放弃。

## 截图

[离线截图总览（内嵌 16 张 PNG，可分类与放大）](../../build/lianye-ui-polish/ui-review.html)。已验证所有 PNG 签名、1080 × 2340 尺寸、内嵌图片完整性和脚本语法；当前 computer-use 返回 `apps=[]`、`browsers=[]`，所以未完成总览本身的浏览器点击验收。

以下画面直接截自更新后的手机，使用恢复的原有草稿：

- [草稿首页](../../build/lianye-ui-polish/home-final.png)
- [预览](../../build/lianye-ui-polish/preview-final.png)、[查看菜单](../../build/lianye-ui-polish/view-menu-final.png)
- [裁剪局部](../../build/lianye-ui-polish/crop-final.png)、[裁剪全图](../../build/lianye-ui-polish/crop-full-final.png)
- [新建确认](../../build/lianye-ui-polish/replace-final.png)
- [帮助](../../build/lianye-ui-polish/help-final.png)、[华为完整路径](../../build/lianye-ui-polish/help-path-final.png)

测试页面在同一台手机上渲染首次使用、准备、手动帮助、1.8 倍英文弹窗。普通悬浮截图来自真实 WindowManager 窗口，使用模拟的 READY / FULL / ROUTE 会话状态；不把这些截图当作新一轮实际屏幕捕获结果。深色和大字号首页也通过本机配置测试检查；部分原回归截图含系统通知或过渡效果，不作为主要交付图。

## 数据与权限恢复

自动化前备份调试包 `files/drafts` 与 `shared_prefs`，测试结束并 force-stop 后恢复。原草稿尺寸仍为 **1080 × 3415、未保存**；草稿文件 SHA-256 与备份一致：

```text
91CBCD92CCAEDA98A6A283E6F6C24545B5BBFD0331A98376E20E3DC5DC92B35B
```

4 个原始 tile 文件及全部 3 个偏好文件分别进行 SHA-256 校验，均与备份一致。移除本轮生成的 `activity-qa.raw` 及手机上的临时备份。实机查看裁剪后取消，覆盖确认也只取消；没有保存、放弃或覆盖用户草稿。最终手机留在原草稿首页。

最终核实：SYSTEM_ALERT_WINDOW 为 default，enabled_accessibility_services 为 null，accessibility_enabled 为 0，Media Projection 为 null，font_scale 为 1.0。正式包 `org.scrollloom` 未更新、未清理；正式、调试及测试包均仍安装。

## APK 与验证范围

[本轮调试 APK](../../build/lianye-ui-polish/lianye-ui-polished-debug.apk)，`org.scrollloom.debug`，`0.1.0-debug`。SHA-256：

```text
56F7907C950B7BDE914A93B1525D3A94680161C2BAFD509CD28D560DE7045065
```

真实设备验证限于上述华为 Android 10；其他品牌帮助资源仍在，但未逐台验收其他品牌或其他 Android 版本的渲染与触摸规则。截图拼接、OEM 权限路径和捕获行为未在本轮重新设计。
