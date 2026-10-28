# 首页取景框视觉验收

本轮重画主图：墨色四角限定一屏范围，完整连续页面向下延伸到框外，橘色轮廓标记延伸部分，与既定 Logo 的取景角及长条关系呼应。删除“连成”“长图”字组，不再依赖 slogan 或标点组织首页。

页面是完整轮廓，移除旧双卡片圆角缝。纸张使用轻微层次、细边线和有序图文内容；第一次实机检查发现山形缩略图仍像通用占位图，改成曲线图像和轮廓线。全部图形为主题感知的原生 Canvas，按 300 × 328 网格等比居中，不增加位图、字体、网络依赖或动画。

仅修改 HomeEmptyHero 和 LongCaptureIllustration 的呈现。主屏滚动、底部模式及主操作、草稿、帮助、准备及捕获反馈沿用现有实现。

## 验证

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

最终构建成功，10 秒；Lint 零错误、90 项既有警告，禁止的网络依赖为零。`git diff --check` 退出码 0。初次绘制代码的 RoundRect 构造参数与本项目版本不符，已按编译器所列 Rect / radius 重载修正，并完成以上最终构建。

Android 完整回归 **OK (24 tests)，55.424 秒**，输出保存于 `build/home-viewfinder/native-tests.txt`。本轮没有修改测试源码，也没有新增样式数值测试。

Huawei STK-AL00 / Android 10 / 1080 × 2340 / density 3。检查了浅色自动/手动首页、中文深色、中文 1.8 倍字体浅色 320dp、英文 1.8 倍字体深色 320dp。主图缩放正常，取景角、页面和接续边界可辨；模式和主按钮仍显示并可点击。检查了原生草稿页面及缩略图。注入主题和字体的测试截图保留设备当前系统状态栏设置。

最终证据位于 `build/home-viewfinder/`：

- `before-home.png`、`after-home.png`：实际应用的修改前后截图；最终截图在恢复用户数据并重新启动后生成。
- `first-render.png`：首次实机比例检查，缩略图随后重绘。
- `native-final/`：最终浅色、深色及大字体窄屏截图。
- `draft-final/`：草稿、预览及裁剪渲染。
- `native-tests.txt`：完整实机测试结果。

## 恢复

本次新备份含 **3 个文件、588 字节**，覆盖当前偏好和草稿目录，当时没有保留的草稿文件。恢复后及最终启动后，全部文件内容的 SHA-256 与本次新备份完全一致。用户选择的手动模式保持不变，没有使用旧任务备份。

相册的 4 个已有文件名保持一致。无障碍服务为 `null`、开启状态 `0`，屏幕捕获为 `null`；本次没有开启这些权限。测试辅助包、QA 截图目录及本次临时备份与 UI dump 已删除。手机停留在更新后的调试版首页，正式包未修改。

没有提交、发布或外部消息。
