# 连页发布配置复核

用户要求延后物理目录改名，先继续其他工作。本次复核已改名的发布、下载与 F-Droid 配置；只修改本地工作树，不推送、触发发布或修改远端凭据。

## 已查明的问题

1. Release 工作流用 `GITHUB_TOKEN` 创建发布，独立 F-Droid 工作流只监听 `release.published`。GitHub 对该令牌产生的这类事件不创建后续工作流，因此自动发布后的索引部署链路断开。
2. 当前 `fdroid update` 命令把签名配置作为命令行参数，但官方参数解析器不接受这些参数。签名库、别名及密码应由 `config.yml` 提供；密码使用 F-Droid 支持的环境变量引用。

手动运行时空标签并不是已证实的故障：GitHub CLI 官方实现将空标签解释为最新 release。保留这个行为，不把推测写成修复成果。

## 设计

推荐在 Release 成功后直接调用可复用的 F-Droid 工作流，传递刚发布的标签和既有 secrets。独立的 `release.published` 与手动运行入口仍保留；手动运行默认下载最新 release。使用相同仓库的可复用工作流无需新增访问令牌；与改用 PAT 或依赖额外外部事件相比，这条依赖关系更直接。

下载标签通过环境变量进入 shell；有标签时明确下载该版本，无标签时省略参数。仅匹配连页的 `Lianye-*.apk` 发布产物。Pages URL 继续从实际仓库身份生成。

F-Droid 配置保留现有 `fdroid` 密钥别名和既有 secret 名称，不重新生成签名材料。添加 `keystorepass`、`keypass` 的环境引用，索引命令只使用官方支持的 `--create-metadata --rename-apks`。调用者和被调用者声明所需的 Pages/OIDC 权限。

## 验证边界

先复现现有工作流缺少直接调用，以及官方解析器拒绝当前签名参数。修改后解析 YAML、检查调用依赖和权限、执行实际下载 shell 的有标签/无标签场景（CLI 替身，不访问发布），执行配置生成 shell 并核对环境引用，使用官方参数声明核验索引命令。

不重新运行与本次配置修改无关的 Android 回归；不宣称已实跑 GitHub Actions、已签出 F-Droid 索引或已部署 Pages。服务器 secrets 的实际值、签名别名和 Pages 启用状态仍须首次发布前验证。

参考：[GitHub 工作流触发规则](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow)、[可复用工作流](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows)、[F-Droid update 源码](https://github.com/f-droid/fdroidserver/blob/master/fdroidserver/update.py)、[F-Droid 配置读取源码](https://github.com/f-droid/fdroidserver/blob/master/fdroidserver/common.py)。
