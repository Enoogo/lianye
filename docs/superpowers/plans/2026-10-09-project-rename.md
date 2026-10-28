# Lianye Project Rename Implementation Plan

> **For agentic workers:** Execute inline using executing-plans. User approved full rename including package ID and explicitly declined backups. Preserve inherited worktree changes; do not commit, push, or publish.

**Goal:** 将当前项目身份统一为连页 / Lianye，包括 `org.lianye` 和仓库；物理目录改名按用户最新要求延后。

**Architecture:** 对当前 UTF-8 源文件做有边界的机械改名并移动包树，保持行为与既有 UI。CI 从 GitHub 上下文生成 Pages 地址，保留现有签名材料。远端变更在代码验收后执行；停止尝试移动本地根目录。

**Tech Stack:** Kotlin/Compose、Gradle、Android Manifest/VectorDrawable、Python 标准库、PowerShell、Git、现成 GitHub CLI、ADB。

## Task 1: 身份迁移

- [x] 用工作区内临时脚本扫描 `app/src`、`app/build.gradle.kts`、`app/proguard-rules.pro`、`settings.gradle.kts`、`.github/workflows`、`metadata`、`tool`、`README.md`、`LICENSE`、三份现行产品文档。替换 `org.scrollloom → org.lianye`、`ScrollLoom → Lianye`、`scroll-loom → lianye`、`scrollloom → lianye`、`Loom → Lianye`、`loom → lianye`；不扫描构建输出及历史验收/探索。
- [x] 核对目的路径未占用、绝对路径位于工作区；使用 PowerShell `Move-Item -LiteralPath` 将 main/test/androidTest 的 `org/scrollloom` 包树迁移至 `org/lianye`，并同步改名其中品牌前缀文件；将 `metadata/org.scrollloom.yml` 与两个本地签名文件改名。
- [x] 设置 `rootProject.name = "Lianye"`；更新 `tool/sync_brand_assets.py` 的包与生成目录，执行生成及 `--check`。
- [x] 更新应用更新链接和 About 原生测试的 Intent 匹配路径；修正 F-Droid 来源为实际 owner/new repo，更新名称与 Android 10+、自动/手动能力说明；发布 APK 使用 `Lianye-${VERSION}.apk`，Pages URL 使用 GitHub owner/repository 输入生成。
- [x] 私下更新 `local.properties` 的 storeFile 文件引用，不输出任何密码；现行品牌、架构、路线图和 README 统一当前名称与已批准 UI，历史记录不改写。

## Task 2: 验收

- [x] 执行活动范围旧名扫描及包声明/目录检查；解析全部资源 XML、工作流 YAML、元数据；`python tool/sync_brand_assets.py --check`、`git diff --check` 与本次新增文件行尾检查通过。
- [x] 执行 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug verifyZeroNetworkDependencies --offline`；检查测试计数、Lint severity 与签名 Release 产物，不能仅凭 Gradle 退出码宣称零错误。
- [x] 检查 Debug/Release 编译 Manifest 包名、服务及 FileProvider；记录证书指纹时仅输出指纹，不输出密钥或凭据。
- [x] 安装 `org.lianye.debug` 及辅助包，执行已有原生测试；检查新包首页、帮助、更新入口、草稿和导出，并确认无旧名 activity/action 解析错误。不做数据备份。
- [x] 清理本次新测试辅助包、QA 目录与手机临时文件，停用本次新增权限，恢复原有服务列表。保留新应用在首页。

## Task 3: 仓库与目录

- [x] 使用现成 `gh` 检查仓库管理权限和目标名称；代码验收后执行 `gh repo rename lianye --repo gxwane/scroll-loom --yes`，并通过 `gh repo view gxwane/lianye` 核对实际地址。更新仓库介绍、local origin，并再次核验。沙箱无法使用系统凭据时按实际权限要求在沙箱外调用，不修改全局登录配置或打印凭据。
- [x] 停止本项目 Gradle daemon，写入 `docs/superpowers/verification/2026-10-09-project-rename.md`；记录已做/未做的边界。若权限或认证确实阻塞，说明具体原因，不伪造完成。
- 用户延后：物理目录移动不纳入本次完成条件。当前目录为 `E:\Documents\scroll-loom`；保留已写好的 `build/project-rename/relocate_workspace.ps1`，供用户以后从父目录执行。未来移动仍须验证源、目标和父目录，拒绝覆盖已有目标，同步本地签名路径并核对文件。
