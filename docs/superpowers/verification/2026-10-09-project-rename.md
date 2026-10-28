# 连页项目改名验收

日期：2026-10-09。用户要求统一所有当前项目名称，包括尚未发布的包名，并明确不需要备份。本次未新增手机草稿或设置备份，未提交、推送代码或创建发布。

## 当前身份

| 项目 | 名称 |
| --- | --- |
| 中文 / 英文品牌 | 连页 / Lianye |
| Gradle 根工程 | Lianye |
| GitHub 仓库 | https://github.com/gxwane/lianye |
| 正式包 / namespace | org.lianye |
| Debug 包 / AndroidTest 包 | org.lianye.debug / org.lianye.debug.test |
| 发布 APK | Lianye-v0.1.0.apk |
| 本地签名文件 | lianye-release.jks / lianye-fdroid.jks |

## 实现与远端

114 份 Kotlin/Java 的包声明与目录一致，main/test/androidTest 包树已移动至 `org/lianye`。服务、引擎、状态、仓库、主题、颜色、控件、通知频道、Intent actions、日志和引用均使用 Lianye/lianye；同步 Manifest、R8 规则及资源生成器。

更新发布工作流的 APK 与签名文件名。F-Droid 文件名为 `metadata/org.lianye.yml`，来源及问题链接使用实际账号 gxwane；简介与 Android 10+、自动/手动、本机处理能力一致，移除尚未核验的新包发布构建条目。Pages URL 从 GitHub owner/repository 上下文生成。现行 README、品牌、架构、路线图统一当前方案；历史探索、验收原文及 Git 历史不改写。

用户指出已有 gh 后定位现有工具。沙箱内 gh 返回 401，沙箱外同一工具能够使用现有登录并确认 ADMIN 权限。代码验收后执行 `gh repo rename lianye --repo gxwane/scroll-loom --yes`，再次 `gh repo view gxwane/lianye` 确认新名、新 URL 与 ADMIN。仓库介绍改为「连页 / Lianye — Android 长截图工具。支持自动滚动与自己滑动，图片在本机处理。」；本地 origin 的 fetch/push 地址均为 `https://github.com/gxwane/lianye.git`。没有更改全局登录配置，没有打印或持久化访问令牌。

## 本次验证

| 检查 | 结果 |
| --- | --- |
| 当前源码/配置旧名扫描 | 0；历史记录与构建缓存不纳入当前输入 |
| 目录与声明、XML/YAML | 114 份包声明、37 份资源 XML、两份工作流与元数据通过 |
| 品牌生成器 `--check` | 14 份生成资产一致 |
| JVM 回归 | 175 tests，0 failures/errors/skips |
| Debug、AndroidTest、签名 Release、Lint、网络依赖守卫 | BUILD SUCCESSFUL；Lint 0 errors、90 warnings，与改名前一致；禁止网络依赖为 0 |
| 完整原生回归 | `OK (33 tests)`；华为 STK-AL00 / Android 10 |
| 关于页复验 | `OK (1 test)`；中英文、明暗、1.8 倍字体；确认品牌名及新更新 URL 的 Intent 命中 |
| 编译 Manifest / 实际 APK | Debug `org.lianye.debug`，Release `org.lianye`；服务为 org.lianye 下的新类，FileProvider 为 `${applicationId}.files` |
| 新服务实机连接 | `dumpsys activity services org.lianye.debug` 显示 LianyeAccessibilityService，`received=true hasBound=true`；无 Binding 残留 |
| 工作区空白 | `git diff --check` exit 0；本次当前文本检查通过 |

Release 签名 SHA-256：`668646d9ddc4cabf26c1e8553f86a92bfcb3e3d533ff58479b10230d047c7b3a`。签名材料只改文件名及路径引用，没有重新生成证书。

本次证据位于 `build/project-rename/`：`build-final.txt`、`instrumentation.txt`、`about-repeat.txt`、`identity-audit.json`、`service-registration.txt`、`device-state-final.txt`。

- [新包实际首页](../../../build/project-rename/home-device.png)
- [新包系统图标](../../../build/project-rename/icon-device.png)
- [英文深色关于（复验）](../../../build/project-rename/about-renders/about-review/english-dark.png)
- [签名 Release APK](../../../build/project-rename/Lianye-v0.1.0.apk)

## 实机收尾与边界

新包安装前手机上不存在 `org.lianye` 包，原生测试在新调试包中执行。清理了该新包中测试生成的示例草稿及四个 QA 图片目录、两个手机临时 PNG，卸载 `org.lianye.debug.test`。没有删除原有其他安装或读取其草稿/设置。

临时允许新包悬浮窗和无障碍用于验收；完成后新包悬浮权限恢复为 ignore，新服务停用。原有服务列表恢复为 `org.scrollloom.debug/org.scrollloom.service.LoomAccessibilityService`，Bound/Enabled 正确，Binding 为空；MediaProjection 为 null。原有相册 5 张长图清单保持一致。手机停留新包首页。

本次验证覆盖已有 UI、草稿、导出、服务身份与路由回归，没有声称在新包内额外完成新的完整真实页面长截图，也没有新的 Android 12+ 启动画面实机验收。首次正式发布前仍需在发布目标设备上进行捕获验收。

## 本地根目录

目录改名状态：按用户最新要求延后，不是本次工作的阻塞项。当前目录为 `E:\Documents\scroll-loom`，目标 `E:\Documents\lianye` 不存在。物理目录名不影响新包名、Gradle 工程身份、构建或安装；未来移动时需同步本地签名路径。

此前两次移动被 Windows 文件占用拒绝，没有强制结束用户编辑器或当前工作进程。保留 `build/project-rename/relocate_workspace.ps1` 供用户以后从 `E:\Documents` 执行移动、签名路径同步和文件核验；现在停止目录移动工作。
