# 连页展示与 SEO 验收

2026-10-09 已更新公开 README、产品介绍页和 GitHub 项目信息。

- [产品介绍与实机视频](https://gxwane.github.io/lianye/)
- [GitHub README](https://github.com/gxwane/lianye#readme)
- [成功的 Pages / F-Droid 部署](https://github.com/gxwane/lianye/actions/runs/37939960796)
- 网站和素材源码提交：`488ef0b95fcf4dff5d362a3bf5004f4e70d616cb`。

## 实机素材

从已发布 `org.lianye` v0.1.0 采集，Huawei STK-AL00 / Android 10。原创示例文章“一页周末”及 SVG 插画展示手动模式的开始、滑动、结束、裁剪和保存。公开素材包括三张界面图、39.47 秒 MP4、约 3.12 MiB GIF、实际导出的 1080 × 9275 PNG，以及使用现行 Logo 和品牌色的 1200 × 630 分享封面。

设备没有 `/system/bin/screenrecord`。确认命令缺失后，从 Genymobile 官方发布下载便携 scrcpy v5.0.1，下载包 SHA-256 与发布清单一致；使用无窗口、无控制、无音频模式录制。不安装额外手机应用，不用原型画面代替实际界面。演示省略等待和部分重复滑动，并加速播放；相关说明在 README、网站和素材目录内可见。

检查实际成图，段落、标题和插画连续。成图中的悬浮控件色 `#252B2A` 和滑动路线色 `#DB653D` 像素计数均为 0。只复制并删除本次明确创建的相册演示图，原有 50 项相册记录保留；无障碍服务与普通悬浮窗权限恢复，MediaProjection 停止，临时端口映射移除。正式包原先无未保存草稿，采集后首页回到自动模式。

## 本地页面与发布流程

27 项浏览器检查通过：320、390、768、1440 px 无横向溢出；下载链接、图片加载和 FAQ 键盘操作可用；MP4 能实际解码；复制 F-Droid 地址成功和被拒绝时的选择文本回退均通过。无 JavaScript 时正文、下载、FAQ 和订阅地址可用；减少动画偏好关闭平滑滚动，视频不自动播放。浏览器无 JS 异常或失败资源请求。

61 项静态和素材检查通过：标题、摘要、语言、viewport、canonical、Open Graph、Twitter 分享封面、图片 alt、真实 SoftwareApplication JSON-LD、sitemap、相对资源路径及 README 图片链接有效。不存在虚构评分、评论或关键词标签。项目位于 `/lianye/` 子路径，没有创建不能控制主机爬虫行为的子目录 robots.txt。

修改后的 `.github/workflows/fdroid-repo.yml` 通过 actionlint，`git diff --check` 通过。工作流从 `website/` 复制介绍页，从 `docs/assets/showcase/` 复制公共素材，保留 `fdroid/repo/`；签名、APK 下载、索引生成及既有发布入口没有变更。

## 公开访问

本次 GitHub Pages 工作流成功。实际读取 16 个公开 URL，网页、CSS、JS、favicon、sitemap、全部 PNG、GIF、MP4、F-Droid 索引和 APK 返回 HTTP 200；文本内容与提交一致，二进制素材 SHA-256 与本地文件一致。线上浏览器另检查 390 与 1440 px 布局、图片加载和视频解码，均通过，没有资源错误。

F-Droid 两个签名索引仍可访问；v2 索引包含 `org.lianye` 0.1.0，实际下载 APK 的 SHA-256 仍为 `e22930d4f3f060981a2829146b34fbd7245bcb2be06b3cc5148c41c562f45dc8`。没有重新构建或发布 Android APK。

GitHub 公开 README blob 为 `d68853ad96172c71cb72395acf2a9baf381cbc0d`，与本地提交一致。仓库描述已补充中英文定位，官网设为上述地址；Topics 为 android、fdroid、jetpack-compose、kotlin、long-screenshot、offline、privacy、screenshot、scrolling-screenshot。

## 验收边界

本次是 README、网站、素材和仓库信息修改，没有重新运行 Android 应用测试。实机素材只来自上述 Android 10 设备，不代表其他厂商或系统完成兼容性测试。SoftwareApplication 数据供机器理解真实产品属性；没有评分或评论，不声称满足软件应用富媒体搜索结果要求。已发布 SEO 基础配置，不声称已收录或获得搜索排名；站点拥有者可以通过 Search Console 检查收录并提交 sitemap。

原始录屏、下载工具、本地和线上浏览器截图、设备前后状态以及 JSON 验证报告保存在忽略的 `build/showcase-seo/`；公开素材和维护说明已提交。
