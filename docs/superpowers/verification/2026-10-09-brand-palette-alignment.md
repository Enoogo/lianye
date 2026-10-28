# 连页品牌色统一验收

日期：2026-10-09。用户确认暖白底、墨黑取景框、陶橙长页后执行。保持现有几何与交互，更新 `org.scrollloom.debug` 测试包；正式包未改动，未提交或发布。

## 实现

- `design/tokens/tokens.json` 的 `brand` 为颜色输入：Paper `#F7F4EE`、Ink `#252B2A`、Accent `#DB653D`、DarkBackground `#1D2422`、DarkAccent `#F4A584`。
- `tool/sync_brand_assets.py` 从该输入及既有 `refined-01/geometry.json` 生成 14 份 Kotlin、Android 颜色/矢量与现行 SVG。主题常量使用生成定义；桌面普通图标保持暖白，应用与启动标记按明暗主题反色，单色图标保留同形轮廓。
- 更新首页标题文字颜色，保证深色背景上的应用名称清楚；首页和关于共用已有 BrandMark。Android 12+ 启动主题指定透明品牌标记与对应背景。
- 更新现行品牌母版与设计说明，历史探索资产保留。`tool/render_brand_preview.ps1` 生成圆形、圆角及 24/32/48/64 px 预览。

## 本次验证

| 检查 | 结果 |
| --- | --- |
| `python tool/sync_brand_assets.py --check` | 14 份文件一致 |
| SVG/XML 解析、桌面安全范围 | 通过；含笔画的保守半径 31.955，小于安全半径 33 |
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline` | BUILD SUCCESSFUL；Lint 0 errors、90 warnings，与上一轮数量一致；禁止网络依赖为 0 |
| 既有首页布局测试、AboutUiReviewTest | `OK (2 tests)`；首页 3 个布局、关于 4 个布局；中英文、明暗主题及 1.8 倍字体覆盖 |
| 华为 STK-AL00 / Android 10 / API 29 | 调试包安装成功；系统应用信息图标、实际首页与关于已截图并目视检查 |
| 工作区空白 | `git diff --check` exit 0；本次文件的行尾空白检查通过 |

实机截图：

- [系统应用信息中的实际图标](../../../build/brand-palette-alignment/icon-device.png)
- [实际首页](../../../build/brand-palette-alignment/home-device.png)
- [实际关于](../../../build/brand-palette-alignment/about-device.png)
- [深色首页原生渲染](../../../build/brand-palette-alignment/home-renders/home-dark-chinese.png)
- [深色关于原生渲染](../../../build/brand-palette-alignment/about-renders/english-dark.png)
- [圆角桌面遮罩模拟](../../../build/brand-palette-alignment/previews/launcher-rounded-512.png)

证据位于 `build/brand-palette-alignment/`：`build.txt`、`instrumentation.txt`、`asset-audit.json`、`restoration-check.json`、`device-state-final.txt`。图标遮罩预览为模拟渲染；系统应用信息截图为 Android 实际加载的图标。当前手机为 Android 10，因此 Android 12+ 启动画面和 Android 13+ 系统主题图标仅完成资源生成及编译检查，没有实机显示验收。

## 数据与清理

安装前创建本次新备份。测试后、最终操作后分别比较全部 9 个草稿/设置文件，SHA-256 与本次备份完全一致，共 15,255,281 字节；相册原有 5 张长图文件清单一致，没有新增截图到相册。

测试前无障碍服务已开启；结束后恢复该状态。`dumpsys accessibility` 确认 Bound 为连页、Enabled 为自身服务、Binding 为空；MediaProjection 为 null。已移除 `org.scrollloom.debug.test`，清理本次外部 QA 目录与三个手机临时 PNG；保留工作区验收图片，手机停留首页。
