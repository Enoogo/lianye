# Brand Palette Alignment Implementation Plan

> **For agentic workers:** Execute inline using executing-plans; user has approved the design. Preserve the inherited worktree and do not commit or publish.

**Goal:** 使桌面图标与连页界面的暖白、墨黑、陶橙体系一致。

**Architecture:** 颜色输入由 `design/tokens/tokens.json` 的 brand 字段提供。`tool/sync_brand_assets.py` 生成 `BrandPalette.kt`、Android 品牌颜色与矢量、现行 SVG；主题常量引用品牌定义。Android 12+ 启动主题继承对应明暗基础主题。

**Tech Stack:** Kotlin、Compose、Android VectorDrawable、SVG、Python 标准库、ADB。

## Task 1: 颜色与资产

- [x] 在 `design/tokens/tokens.json` 增加固定及深色品牌色，将现行颜色令牌与当前 Theme.kt 对齐；新建 `tool/sync_brand_assets.py`，使用现有 `refined-01/geometry.json`，生成文件支持 `--check`。
- [x] 生成 `app/src/main/java/org/scrollloom/ui/common/theme/BrandPalette.kt` 与 `app/src/main/res/values/brand_colors.xml`、`values-night/brand_colors.xml`。`Color.kt` 品牌常量引用 `BrandPalette`，`Theme.kt` 深色强调色同样引用定义，既有 UI 色值不变。
- [x] 生成 `ic_launcher_background.xml` 的暖白底、`ic_launcher_foreground.xml` 的墨黑角标与陶橙长页、同形单色层，以及对应 `design/brand/logo_*.svg` 和透明启动标记。
- [x] 更新普通和深色 `themes.xml` 使用品牌背景；增加 `values-v31/themes.xml` 指定 `windowSplashScreenBackground` 与 `windowSplashScreenAnimatedIcon`，继承共用基础主题。更新 `design/README.md` 与 `DESIGN_TOKENS.md`，说明现行与历史资产。

## Task 2: 验证与实机

- [x] 执行 `python tool/sync_brand_assets.py --check`，预期生成文件完全一致；生成圆形、圆角和小尺寸图标预览并目视检查，验证安全范围与现有几何不变。
- [x] 执行 `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline`，预期成功、零 Lint errors、禁止网络依赖为零。重新备份当前调试数据后安装调试包。
- [x] 执行已有 `UiVisualReviewTest#homeModesRemainReachableAcrossDarkAndLargeFontLayouts` 和 `AboutUiReviewTest`，检查首页、关于的明暗标记；不为单纯可逆配色调整增加镜像测试。查看系统应用信息图标和实际首页并截图。

## Task 3: 收尾

- [x] 比较本次备份的所有文件哈希与相册文件清单，恢复原有已启用无障碍服务并确认 Bound/Enabled 正确且无 Binding 残留；清理辅助包、QA 图片和本次手机临时文件。
- [x] 执行 `git diff --check` 及新增文件空白检查，完成 `docs/superpowers/verification/2026-10-09-brand-palette-alignment.md` 并勾选本计划。
