# 连页 v0.1.0 发布验收

2026-10-09 已发布首个正式版。公开安装包不是草稿或预发布，手机已安装从 Release 实际下载的 APK。

- [正式发布与下载](https://github.com/gxwane/lianye/releases/tag/v0.1.0)
- [项目 F-Droid 仓库页面](https://gxwane.github.io/lianye/)
- F-Droid 订阅地址：`https://gxwane.github.io/lianye/fdroid/repo`
- 产品源码提交与 `v0.1.0` 标签：`31280f85846aed78c0768ef5025873015dde0a87`。后续提交只修正发布工具、分发配置和文档，没有改动已发布的应用源码或标签。

## 安装包身份

| 项目 | 实际结果 |
| --- | --- |
| 文件 | `Lianye-v0.1.0.apk`，2,170,109 字节 |
| 包名 | `org.lianye` |
| 版本 | `0.1.0` / versionCode `1` |
| Android | minSdk 29，targetSdk 35 |
| 调试与联网权限 | 不可调试，未声明 INTERNET |
| SHA-256 | `e22930d4f3f060981a2829146b34fbd7245bcb2be06b3cc5148c41c562f45dc8` |
| 签名证书 SHA-256 | `668646d9ddc4cabf26c1e8553f86a92bfcb3e3d533ff58479b10230d047c7b3a` |

下载文件、发布的 `SHA256SUMS.txt`、云端验证报告和 F-Droid 下载包的哈希一致。签名与原有密钥及旧测试版公开证书一致。旧测试版包名不同，保持分别安装，不覆盖其数据。

## 检查与实机验收

- JVM：本地和云端均为 175 项，0 失败、0 错误、0 跳过。云端测试报告成功率 100%。
- 原生实机回归：Huawei STK-AL00 / Android 10，`OK (33 tests)`。
- 签名解析回归：6 项通过，覆盖旧编号格式、新版按签名方案输出的格式、跨方案同证书，以及错误证书、多签名、无法识别的输出拒绝。
- Release、Debug、AndroidTest 构建及运行时依赖检查通过；Lint 无 Error/Fatal。本地 90 个 Warning，云端 87 个 Warning，未声称无警告。
- 14 项品牌资产同步检查和两份工作流 actionlint 检查通过。
- 版本错误、Debug APK 和模拟 Lint Error 均被发布校验拒绝。
- 正式包手动捕获：持续滑动指引、4 次滑动、结束、保存；输出 1080 × 6949。保存后首页没有继续编辑提示。
- 随后切换自动模式，使用统一悬浮球完成捕获和保存；输出 1080 × 21076，包含生成测试页面的最后一个 `Section 28 / 28` 标记。
- 下载公开 APK 后再次安装成功，版本与首页正确。

测试只使用生成的页面。自动捕获期间不运行 `uiautomator dump`：该工具会临时替换无障碍连接并打断捕获。控制悬浮球时先确认展开状态，避免连续两次点击导致开始后立即结束。按正常操作完成的自动截图没有此测试工具干扰。

本轮生成的 4 张长图及临时截屏已清理，原有 5 张长图保留；辅助测试包和测试草稿已清理。无障碍列表恢复测试前的原组件，连页测试权限已关闭，MediaProjection 停止。原有安装和数据保留，公开正式包留在设备上。

## 分发证据

[正式包构建与发布任务](https://github.com/gxwane/lianye/actions/runs/37880201346/job/113657907291) 成功。该次运行的旧 F-Droid 配置失败，随后独立修复并部署；最终 [F-Droid / Pages 运行](https://github.com/gxwane/lianye/actions/runs/37889931768) 全部成功。

公开验证直接下载页面、索引和 APK：页面返回 HTTP 200；`index-v1.jar` 与 `entry.jar` 签名通过，后者指向的 `index-v2.json` 哈希正确。索引中的包名、版本、APK 哈希及应用签名与 Release 一致。

F-Droid 仓库证书 SHA-256：`58859c7d2a9b4d8a7fea3330f6df879238bb1df7402a214810db60d26a2a75d5`。

现代 v2 索引使用 JDK 默认签名策略验证。v1 按 F-Droid 的旧索引兼容规则验证 SHA1，并固定同一仓库证书；兼容策略仅用于本次验证进程，没有修改系统安全配置。APK 和现代索引的哈希均核对 SHA-256。

仓库与应用图标由现行 `design/brand/logo_master.svg` 生成。实际下载的仓库图标及 en-US、zh-CN 应用图标像素一致，512 × 512，暖白、墨黑、陶橙色值正确。

发布流程保留原有密钥与 secrets。修复内容包括新版 apksigner 输出解析、从固定产品标签构建时使用运行版本的验证工具、F-Droid 2.4.5 / Androguard 4.1.4 独立环境和图标源路径。

## 验收边界

本次实机为 Android 10 华为设备；没有将厂商帮助适配或资源编译检查当成其他 Android 系统的实机结果。受保护页面无法截取，动态画面、重复排版及固定控件仍可能影响拼接，导出前需要检查。F-Droid 地址为项目自托管仓库，不代表已收录到 f-droid.org 主仓库。

原始构建、设备测试、截图、签名及公开产物验证报告位于忽略的 `build/release-v0.1.0/`，未提交密钥、密码、生成安装包或缓存。
