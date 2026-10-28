# 首页视觉精修验收

## 完成的调整

首页标题、说明和插画组成居中的紧凑整体。中文标题使用 24sp / 32sp，取消固定换行；短文案收紧为“自动滚动，拼成长图。”及“自己滑动，拼成长图。”。标题和说明采用均衡断行，内容不足以容纳时仍可滚动，底部操作保持可达。

原来的错位纸片和横向裁剪状标记，替换为上下对齐的两段页面及短橘色连接。图形使用原生 Canvas 和主题颜色，深色模式下的小图片块单独提高对比度。顶部保持原 Logo 几何形状，改为主题墨色和橘色绘制，去掉浅色底板，并与页面网格做光学对齐。

模式选择去掉厚底色及白色选中块，使用文字与短下划线表示选择，保留至少 48dp 触控范围、RadioButton 角色和 selected 状态。截图、权限、草稿保护及悬浮球状态路由沿用现有实现。

## 构建和自动化验收

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline
```

最终产品代码构建成功，耗时 16 秒。随后调整测试截图同步及基准画面等待条件，重新运行 `:app:assembleDebugAndroidTest :app:lintDebug --offline`，构建成功。Lint 为零错误、90 项既有警告；依赖检查为零禁止的网络依赖。`git diff --check` 退出码为 0。

```powershell
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

最终结果为 **OK (24 tests)，55.769 秒**，记录在 `build/home-visual-refinement/native-tests-complete.txt`。包含自动/手动入口、保存后回到新截图首页、草稿恢复及裁剪、帮助和准备流程、悬浮控件及其隐藏像素检查。

现有视觉验收增加中文普通字号深色、中文 1.8 倍字体浅色 320dp、英文 1.8 倍字体深色 320dp 组合；两种模式均显示，选中状态正确，主操作可以点击。没有增加针对样式数值的单元测试。

## 实机视觉检查与测试同步

设备为 Huawei STK-AL00，Android 10 / API 29，1080 × 2340，density 3。检查了浅色自动/手动首页、中文深色、中文/英文大字体窄屏，以及带真实解码缩略图的未保存草稿首页。

第一轮大字体中文标题留下了不均衡的尾行，使用均衡断行后修正；说明再缩短，使大字体下的句意更完整。深色插画的图片块原先与页面底色接近，修整后可见。截图采集额外等待 Android Surface 及水波纹结束，避免将解码前的空帧或点击中的状态作为最终视觉结果。

一轮完整回归的浮层隐藏像素检查失败。失败的 `native-copy/recovery-baseline.png` 中有华为系统的图片删除拦截通知，`recovery-hidden.png` 中通知已消失；变化来自系统横幅，隐藏图中没有自有浮层。原基准等待仅检查中心像素，改为等待整个被比较区域呈现纯品红测试画面后再取基准。没有修改产品捕获逻辑或放宽像素断言。重新运行完整套件通过。

本地最终证据位于 `build/home-visual-refinement/`：

- `before-home.png`、`after-home.png`：实际应用首页，后者在用户数据恢复并重新启动后截取。
- `native-accepted/`：最终原生视觉变体、帮助、准备及浮层截图。
- `draft-accepted/home-with-draft.png`：真实解码的草稿缩略图。
- `native-tests-complete.txt`：最终完整实机回归结果。

深色及窄屏字体变体由原生 Compose 测试注入主题、字号及 320dp 内容宽度；系统状态栏仍是设备当前设置。本次只有连接的华为设备接受实机验收。

## 数据恢复

开始前新备份包含 **12 个文件，27,463,990 字节**，涵盖当前 `shared_prefs` 和 `files/drafts`。恢复后所有文件内容与本次新备份的 SHA-256 完全一致，重新启动最终版本后再次核对仍一致。没有使用旧任务备份。

相册已有 **4 个文件名**保持一致。无障碍服务为 `null`，开启状态为 `0`，屏幕捕获为 `null`。本次未开启这些权限。调试版保留更新，正式版未修改；测试辅助包、手机 QA 截图目录及本次临时备份和 UI dump 已删除。

没有提交、发布或向外部发送消息。
