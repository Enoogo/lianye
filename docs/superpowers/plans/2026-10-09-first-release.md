# Lianye v0.1.0 Release Implementation Plan

> **For agentic workers:** Execute inline. The user now explicitly authorizes committing, pushing and publishing after verification; this supersedes the earlier no-publish boundary. Keep the physical directory rename deferred and preserve existing phone installations/data.

**Goal:** 将已确认的连页实现推送到默认分支，发布首个正式版 `v0.1.0`，验证签名 APK 与 F-Droid 分发。

**Architecture:** 先检查工作树与远端，补齐发布说明和 CI 验收；提交经检查的产品源码、测试和文档，正常快进推送默认分支。标签触发构建、签名、校验和发布，再运行索引生成及 Pages 部署。下载远端实际产物验证身份与签名。

**Tech Stack:** Git/gh、Gradle、Python、actionlint、Android SDK、ADB、GitHub Actions/Pages。

- [x] 检查默认分支、版本/标签和 secrets 名称。远端默认分支为 master，仅旧 `v0.1.0-beta.1` 为预发布；Pages 尚未启用。官方 actionlint 校验当前两份 YAML 通过。
- [x] 补充 `CHANGELOG.md` 的 `v0.1.0` 用户说明，README 增加正式下载入口，纠正「尚未正式发布」的时态；保留旧测试版包名变化说明。
- [x] 增加 `tool/verify_release.py`：拒绝版本/标签不符、非正式包、调试 APK、网络权限、Lint 错误及签名不符。发布 CI 构建时运行 JVM/Lint/Release，实际 APK 校验后再创建发布；说明取自 CHANGELOG。
- [x] 将现有产品元数据交给自托管 F-Droid 索引，启用此仓库的 Actions Pages 发布。保留原有 keys/secrets，不公开凭据。
- [x] 新鲜运行 JVM、Debug/AndroidTest/Release、Lint 与依赖检查；检查计数和报告。进行实机原生回归与正式包的自动/手动捕获冒烟，临时权限测试后恢复。
- [x] 审计即将提交的文件，排除密钥、local.properties、缓存和测试输出；核对差异及远端祖先，无强制推送。
- [x] 提交本次已批准实现；正常快进推送 master 和未占用的 `v0.1.0` 标签。跟踪 Actions，按实际错误修复，不将已公开的版本标签改写为其他源码。
- [x] 下载远端 APK 检查包名、版本、签名、校验和；确认正式 release 非草稿/非预发布，F-Droid 索引与 Pages 实际可用。报告真实链接及未覆盖的设备边界。

验收已完成，实际发布、签名、测试、分发与兼容边界见 [发布验收记录](../verification/2026-10-09-first-release.md)。
