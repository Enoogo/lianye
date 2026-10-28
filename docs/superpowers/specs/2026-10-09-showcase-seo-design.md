# 连页产品展示与搜索入口

2026-10-09 用户确认：README 真实截图与短动图、现有 GitHub Pages 产品介绍页和基础 SEO、GitHub 项目信息一起实施。沿用已确认的范围，在当前会话完成。

## 展示内容

README 首屏明确 Android 开源长截图工具定位、Android 10+、自动滚动与手动滑动、本机处理和下载入口。加入首页、手动截图、裁剪三张正式版实机截图，以及滚动、结束、裁剪、保存的真实操作 GIF。增加简短英文介绍。保留现有隐私、限制、构建和许可证说明。

用专门编写的示例文章录制，避免展示个人页面。原始录屏、设备状态和验收证据置于忽略的 build/showcase-seo/；压缩的公开素材置于 docs/assets/showcase/，网站和 README 共用。注明版本、设备及演示模式；截图尺寸调整和录屏剪辑不改变应用行为。GIF 提供静态图和完整 MP4 替代，网页尊重减少动画偏好。

## 产品介绍页

沿用 GitHub Pages 地址 https://gxwane.github.io/lianye/，使用原生 HTML/CSS 和少量渐进增强脚本，不引入前端构建框架。暖白、墨黑、陶橙配色来自现行品牌。页面包含产品定位、下载和源码、三张截图、操作演示、两种捕获模式、隐私、三步使用、常见问题和 F-Droid 订阅地址。无 JavaScript 时正文、下载和订阅仍可用。

网站源文件置于 website/。发布工作流复制网站和公共素材，同时保留 pages/fdroid/repo 的既有分发目录；后续发布不会覆盖介绍页。部署前以现有公开 APK 生成仓库索引，不重新发布 Android 应用。

## SEO 与 GitHub

网页提供中文标题和摘要、正确语言和 viewport、canonical、Open Graph/Twitter 分享元数据与 1200×630 封面、图片 alt、软件应用 JSON-LD 和 sitemap。结构化数据仅声明真实属性，不伪造评分或评论。不增加无效的关键词标签。项目位于主机子路径，robots.txt 必须由主机根路径控制，不能把 /lianye/robots.txt 当成有效爬虫规则；本次不改用户其他站点。

GitHub 设置自然的中英文项目描述、项目官网及 android、long-screenshot、scrolling-screenshot、screenshot、offline、privacy、kotlin、jetpack-compose、fdroid 等 Topics。README、官网与 Release 互相链接。搜索平台的账号验证和收录效果不属于本次代码验收。

## 验收

逐帧/抽帧检查真实录屏、公开图片和成图；保留用户已有草稿和相册文件，恢复本次变动的权限。使用本地 HTTP 预览，在 320、390、768 和 1440 px 检查布局、链接、键盘操作、无 JS、减少动画以及视频和图片资源加载。检查 SEO 属性、JSON-LD、sitemap 和发布目录中 F-Droid 索引是否保留。运行 actionlint 验证修改后的工作流。部署后读取公开网页、素材和原有仓库索引，核对可用性；搜索收录不以发布成功代替。

参考：[Google SEO 入门](https://developers.google.com/search/docs/fundamentals/seo-starter-guide)、[robots.txt 根路径规则](https://developers.google.com/crawling/docs/robots-txt/create-robots-txt)、[GitHub Topics](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics)。
