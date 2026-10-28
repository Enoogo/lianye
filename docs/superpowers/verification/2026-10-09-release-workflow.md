# 连页发布工作流本地验收

日期：2026-10-09。用户明确延后物理目录改名，本次停止目录移动，继续检查现有发布配置。仅修改本地工作树，没有提交、推送、发布 APK、触发远端 Actions 或部署 Pages。

## 修正

Release 成功后通过 `needs` 直接调用本地可复用 F-Droid 工作流，并传入刚发布的标签、既有 secrets 和所需权限。原有 release 事件与手动入口保留；手动运行省略标签，默认下载最新发布的 `Lianye-*.apk`。没有新增用户选项或访问令牌。

F-Droid 签名库和别名从 `config.yml` 读取，密码通过 `{env: FDROID_KEYSTORE_PASS}` 引用既有环境变量。移除官方 `fdroid update` 不支持的四个签名命令行参数；保留 `fdroid` 密钥别名和现有 secret 名称，不重新生成签名材料。

直接调用解决的是 GitHub 对 `GITHUB_TOKEN` 产生的 release 事件不创建后续工作流的问题，依据[官方触发规则](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow)和[可复用工作流文档](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows)。参数与环境配置依据[F-Droid update 官方源码](https://github.com/f-droid/fdroidserver/blob/master/fdroidserver/update.py)和[配置读取源码](https://github.com/f-droid/fdroidserver/blob/master/fdroidserver/common.py)。

## 实际验证

修改前运行 `build/project-rename/audit_release_workflows.py`，退出码 1；确认缺少直接工作流调用、官方参数声明拒绝原有签名参数。下载步骤的输入映射与密码配置也未满足修正后的检查条件。证据：`build/project-rename/release-workflow-before.json`。

修改后同一脚本退出码 0，5 组检查通过、失败 0：

| 检查 | 结果 |
| --- | --- |
| YAML、调用链与权限 | Release 依赖正确，指定标签传给复用工作流；release 事件与手动入口保留 |
| 官方 CLI 参数 | 从官方全局/update/metadata 源码 AST 读取参数声明，当前命令全部被接受 |
| 工作流 shell 语法 | 10 个实际 shell 脚本通过 Git Bash 语法解析；GitHub 表达式使用无凭据占位值 |
| 下载场景 | 实际执行下载 shell，CLI 替身记录 argv；指定标签、预发布标签与无标签的 3 个场景正确 |
| 配置与 Pages | 实际执行生成脚本，URL 为 `https://gxwane.github.io/lianye/fdroid/repo`；密码保持环境引用；执行官方环境解析函数，含引号、美元符号、反斜线及换行的测试值保持一致 |

结果：`build/project-rename/release-workflow-audit.json`，包含官方参考源码的 SHA-256，未保存真实密码或访问令牌。此脚本读取官方参数声明和配置函数用于本地诊断，没有安装或运行 F-Droid 服务端。

当前身份审计通过：114 份源码包声明、37 份资源 XML、两份工作流与元数据，活动旧名为 0。品牌生成器检查 14 份资产一致，`git diff --check` 退出码 0。Git 跟踪的 `gradlew` 模式为 `100755`；origin fetch/push 均使用 `https://github.com/gxwane/lianye.git`。

## 边界

没有实跑 GitHub Actions、没有签出真实 F-Droid 索引、没有部署 Pages。服务器 secrets 的实际值、保留的密钥别名以及 Pages 启用状态仍需首次发布前验证，不能把本地解析与替身测试视为远端部署成功。本次未修改 Android 应用代码，未重复运行此前已通过的 Android 回归。
