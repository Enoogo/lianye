# 连页产品展示与 SEO Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 让访客通过真实演示了解连页、直接下载，并补齐官网和 GitHub 的搜索入口。

**Architecture:** website/ 保存静态介绍页，docs/assets/showcase/ 保存 README 与官网共享的正式版演示素材。现有 F-Droid 工作流组装网站与分发仓库后部署到同一 GitHub Pages 地址。

**Tech Stack:** HTML/CSS、浏览器原生视频、Python 标准库与 Pillow、FFmpeg、ADB、Playwright、GitHub Actions。

---

### Task 1: 正式版实机素材

**Files:** Create `docs/assets/showcase/{home,capture,crop}.png`, `demo.gif`, `demo.mp4`, `social-card.png`, `README.md`；过程文件在 `build/showcase-seo/`。

- [x] 保存设备当前无障碍、悬浮窗权限与相册清单；核对正式包 `org.lianye`、版本 0.1.0，检查现有草稿。
- [x] 编写原创示例文章，以本机 HTML 在手机浏览器打开。录制手动捕获、结束、裁剪和保存；录屏只在示例内容和连页中进行。
- [x] 使用官方便携 scrcpy v5.0.1，以 `--no-window --no-playback --no-control --no-audio` 获取录屏（此设备缺少 screenrecord 命令），再用 FFmpeg 导出 432 px 宽的 GIF 与 MP4；从同一录屏抽取首页、截图和裁剪画面，以 Pillow 按原比例缩小截图。GIF 目标不超过 8 MiB。
- [x] 使用现行 Logo 与品牌色生成 1200×630 分享封面。检查成图没有悬浮控件、引导路线或接缝错误；恢复本次设备设置。
- [x] 写素材说明，记录版本、设备、手动模式、原创文章和录屏剪辑范围。

### Task 2: README

**Files:** Modify `README.md`。

- [x] 首段明确“Android 开源长截图工具 / Open-source Android scrolling screenshot app”；加入官网、正式下载链接。
- [x] 使用相对路径引用三张图片和 GIF，提供 MP4 链接与描述文字。保留隐私和技术细节，演示排在使用说明之前。
- [x] 检查所有新增本地链接存在，图片总宽度适合 GitHub 手机阅读。

### Task 3: 静态官网

**Files:** Create `website/index.html`, `website/styles.css`, `website/site.js`, `website/sitemap.xml`, `website/favicon.svg`。

- [x] 构建带导航、产品定位、下载、截图、视频、模式说明、使用步骤、FAQ 与 F-Droid 订阅的静态 HTML。资源相对路径为 `assets/showcase/`，部署地址固定为现有官网。
- [x] 以 `#F7F4EE`、`#252B2A`、`#DB653D` 实现响应式布局、可见焦点、触控目标和 reduced-motion。视频默认展示海报和原生控件，不自动播放。
- [x] 复制订阅地址只作渐进增强；Clipboard 不可用时显示可选中的地址，不改变下载或订阅基础能力。
- [x] 提供 title、description、canonical、OG/Twitter、SoftwareApplication JSON-LD 与 sitemap；不提供子路径 robots.txt。

### Task 4: 发布与项目信息

**Files:** Modify `.github/workflows/fdroid-repo.yml`；GitHub repository settings。

- [x] 用以下组装替代内嵌占位页，原有签名、APK、索引和发布入口不变：

```bash
mkdir -p pages/fdroid pages/assets/showcase
cp -r fdroid/repo pages/fdroid/
cp -r website/. pages/
cp docs/assets/showcase/*.png docs/assets/showcase/*.gif docs/assets/showcase/*.mp4 pages/assets/showcase/
touch pages/.nojekyll
```

- [x] GitHub 描述设为“连页 / Lianye · Android 开源长截图工具 / Open-source scrolling screenshots. 自动滚动、手动滑动，本机拼接，无网络权限。”；官网设为 `https://gxwane.github.io/lianye/`；Topics 按设计文档设置。
- [x] 先完成本地检查并提交，再推送并手动触发现有 Pages 工作流；读取运行结果和线上网页、视频、图片及 F-Droid 索引。

### Task 5: 验证与交付

**Files:** Create `docs/superpowers/verification/2026-10-09-showcase-seo.md`；检查脚本与报告保存在 `build/showcase-seo/`。

- [x] Playwright 通过本地 HTTP 检查 320/390/768/1440 px 无横向溢出、图片和视频加载、下载 href、FAQ 键盘操作、复制成功与失败、无 JS、减少动画；保存桌面和手机截图。
- [x] Python HTMLParser 检查唯一 title/description/canonical、JSON-LD 可解析、软件属性真实、sitemap URL 和所有相对路径有效。检查组装目录保留 F-Droid 索引。
- [x] 运行 `actionlint .github/workflows/fdroid-repo.yml` 与 `git diff --check`，预期 exit 0。
- [x] 发布后检查公开 URL 状态码、元数据、资源字节和索引未丢失，记录结果；最终提供官网、README 与实际验证范围。
