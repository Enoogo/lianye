# 发布工作流维护验收

2026-10-09，提交 `68db7a5122e3e4308915a48a218edecf7573325b` 更新两份发布工作流。针对 Node.js 20、setup-java v4 弃用，以及 ubuntu-latest 即将迁移的提醒进行维护。

## 配置

两份工作流均固定 `ubuntu-24.04`，Java 仍为 Temurin 17。

| Action | 更新后的版本 |
| --- | --- |
| actions/checkout | v7.0.1 |
| actions/setup-java | v6.0.1 |
| actions/upload-artifact | v7.0.2 |
| actions/upload-pages-artifact | v5.0.0 |
| actions/deploy-pages | v5.0.1 |
| softprops/action-gh-release | v3.0.3 |

JavaScript Actions 均使用 Node 24。Pages 上传的复合 Action 内部使用 Node 24 版 upload-artifact，一并消除间接依赖的提醒。

Release 的手动入口增加布尔输入 `publish`，默认 `true`，保持原发布行为。`publish=false` 完成构建、测试、签名及报告上传，跳过 GitHub Release 写入和后续 F-Droid 调用。推送版本标签仍执行完整发布。

## 实际验证

- actionlint v1.7.12 校验两份工作流通过。
- 本地审计通过：11 段 Bash 语法、F-Droid 命令参数、指定版本/预发布/最新版本下载、索引配置及 Pages 地址。
- [Release 只验收运行](https://github.com/gxwane/lianye/actions/runs/37901167146) 成功，`release_tag=v0.1.0`、`publish=false`；发布步骤和 F-Droid 调用按预期跳过。
- [F-Droid / Pages 部署](https://github.com/gxwane/lianye/actions/runs/37901171821) 成功，使用已有正式安装包。
- 两次实际运行的检查注释均为 0，三类维护提醒均未再出现。应用 Lint 仍有 87 个 Warning、0 Error，与 CI 弃用提醒分别记录。
- 云端 JVM 测试 175 项，0 失败、0 跳过，成功率 100%。签名及包名/版本/非调试/无 INTERNET 校验通过。
- 重新下载公开仓库验证：页面 HTTP 200，现代和旧索引签名通过，v2 索引哈希通过，仓库及两种语言应用图标一致，APK 与 GitHub Release 一致。

## 发布产物保持一致

既有 `v0.1.0` 标签仍指向 `31280f85846aed78c0768ef5025873015dde0a87`，应用源码和 Gradle 配置没有变更。

正式 Release 的 ID、更新时间、两个文件的 ID、大小、更新时间及摘要与维护前相同，未覆盖文件。APK SHA-256：`e22930d4f3f060981a2829146b34fbd7245bcb2be06b3cc5148c41c562f45dc8`。本次云端重建报告和公开 F-Droid 下载也得到相同哈希。

原始云端测试、Lint 与签名报告保存在忽略的 `build/ci-node24-verification/`。公开仓库验证报告位于 `build/release-v0.1.0/fdroid-public-verification.json`。
