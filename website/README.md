# 连页产品介绍页

现有官网：<https://gxwane.github.io/lianye/>。HTML、CSS 和渐进增强脚本直接部署，无 npm 构建步骤。此目录不参与 Android 应用打包。

## 资源与发布

README 和官网共用 `docs/assets/showcase/` 的实机素材，来源见该目录说明。`favicon.svg` 是现行 `design/brand/logo_symbol.svg` 的副本；品牌母版变化后同步。`social-card.svg` 是链接分享封面的可编辑源文件，渲染为 1200 × 630 PNG 后保存到 `docs/assets/showcase/social-card.png`。

`.github/workflows/fdroid-repo.yml` 构建已发布 APK 的 F-Droid 索引，复制此目录到 Pages 根目录，将演示素材复制到 `assets/showcase/`，并保留 `fdroid/repo/`。页面修改提交到默认分支后，可手动运行该工作流，无需重新发布 APK。静态预览可以先按相同目录结构复制文件，再通过本地 HTTP 服务打开；不要只打开 HTML 文件检查 Clipboard 或媒体请求。

## 内容维护

实际界面变化后重新采集截图与录屏，更新素材说明及页面中的演示版本和成图尺寸。下载链接使用 `releases/latest`，不手写可能过期的 APK 版本地址。首页、FAQ 和 JSON-LD 中的权限、隐私及导出描述应与根 README 和实际应用一致。

canonical、sitemap、分享图片和 F-Droid 订阅 URL 当前固定为上述官网。仓库迁移或 fork 建站时，需要一起更新这些地址及 GitHub 官网设置。此项目运行在 `/lianye/` 子路径；在这里放 `robots.txt` 无法控制主机爬虫行为，故没有创建该文件。

SoftwareApplication 数据仅包含真实属性，没有评分或评论；不据此宣称符合 Google 软件应用富媒体结果要求。收录检查和 sitemap 提交可在站点拥有者自己的 Search Console 中完成。
