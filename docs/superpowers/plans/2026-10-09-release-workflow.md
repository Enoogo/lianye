# Lianye Release Workflow Implementation Plan

> **For agentic workers:** Execute inline using executing-plans. User deferred local folder rename and requested continuing other work. Preserve inherited changes; do not commit, push, publish or change credentials.

**Goal:** 让现有发布和 F-Droid 索引流程正确衔接，保持手动补跑能力。

**Architecture:** Release 通过 `needs` 调用本地可复用工作流，显式传递标签；同一 F-Droid 作业处理 release 事件、手动运行与复用调用。签名配置通过环境引用供 F-Droid 读取。

**Tech Stack:** GitHub Actions YAML、Bash、Python/PyYAML、官方 F-Droid 参数声明。

## Task 1: 复现和输入检查

- [x] 在 `build/project-rename/` 保存一份本地审计脚本，从官方源码 AST 收集全局/update/metadata 参数，检查现有索引命令；检查 Release 是否直接依赖并调用 F-Droid 工作流。运行脚本确认两项失败，并保存结果。
- [x] 确认手动空标签被现成 `gh` 与官方实现视为最新发布；不要增加不必要的用户输入。

## Task 2: 实现

- [x] 修改 `.github/workflows/fdroid-repo.yml`，增加 `workflow_call` 的 `release_tag` 字符串输入和既有两个 F-Droid secrets；下载步骤通过环境变量和 Bash 参数数组传标签，默认无标签，并明确仓库与 `Lianye-*.apk`。
- [x] 在生成的 `config.yml` 增加 `keystorepass: {env: FDROID_KEYSTORE_PASS}` 与相同的 `keypass` 环境引用；将索引命令改为 `fdroid update --create-metadata --rename-apks`，保留现有密钥别名。
- [x] 修改 `.github/workflows/release.yml`，新增 `deploy-fdroid-repo` 作业，`needs: build-and-release`、`uses: ./.github/workflows/fdroid-repo.yml`、传 `release_tag: ${{ github.ref_name }}`，继承 secrets 并赋予 Pages/OIDC 及只读 contents 权限。

## Task 3: 验证和记录

- [x] 运行本地审计：两份 YAML 解析、自动调用关系及权限、官方 CLI 参数检查、下载 shell 的指定标签与最新版本场景、配置生成及 Pages 链接。保存无凭据的结果。
- [x] 运行 `build/project-rename/audit_identity.py`、`tool/sync_brand_assets.py --check` 和 `git diff --check`；检查本次新增文档无多余行尾空白。
- [x] 将结果与未实跑 Actions/签名索引/Pages 的边界写入 `docs/superpowers/verification/2026-10-09-release-workflow.md`。
