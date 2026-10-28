# 连页交互原型 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已选品牌方向做成可点击、可检查编辑、覆盖关键异常的 Android 交互原型。

**Architecture:** 独立的本地状态模型管理权限、单草稿、编辑历史与导出状态；视图只负责产品界面、示例目标 App 与局部手势。字面 HTML、CSS 和 JavaScript 打包成一个对话内可呈现的片段，使用已有 PNG 标识，所有外部操作为模拟。按已获授权的「继续」在当前会话执行，审核点为设计规格、可点击页面与最终验证；不创建提交或发布。

**Tech Stack:** 原生 HTML/CSS/JavaScript、Node 内置 test/assert、Playwright + Chrome Headless、Lucide（宿主提供）。

---

## 文件责任

- `design/brand/lianye/prototype/model.cjs`：纯状态变化、草稿与编辑历史，兼容浏览器与 Node。
- `design/brand/lianye/prototype/model.test.cjs`：保存失败、权限拒绝、中断与编辑历史的行为检验。
- `design/brand/lianye/prototype/prototype.template.html`：字面 HTML 外壳与评审情境选择。
- `design/brand/lianye/prototype/prototype.css`：独立的产品视觉，320 px 适配与主题。
- `design/brand/lianye/prototype/prototype.js`：页面、底部弹层、捕获计时和编辑手势。
- `design/brand/lianye/prototype/build.mjs`：读取以上文件与批准的 Logo，生成 `lianye-interaction-prototype.html`；不下载资源。
- `design/brand/lianye/prototype/render-ui-mark.mjs`：保留光学源几何，输出同配色的 UI 小尺寸彩色 PNG。
- `design/brand/lianye/prototype/verify-browser.mjs`：通过隔离的 Headless Chrome 检查主流程和截图，报告写入 `review/verification.json`。
- `design/brand/lianye/prototype/verify-host.mjs`：检查实际 iframe、宿主样式、图标与可点击操作，合成查看用流程图。
- `design/brand/lianye/prototype/review/`：关键页面截图与实测记录。

## Task 1：状态与保护

- [x] 写模型行为测试：首次拒绝不能启用服务；中断保留两屏；取消不提交编辑；新编辑丢弃 redo 分支；编辑后失去已保存标记；保存失败不破坏草稿；取消分享不自动保存；停用服务与隐藏按钮分开。
- [x] 执行 `node --test design/brand/lianye/prototype/model.test.cjs`，确认模型缺失时失败。
- [x] 实现 `create`, `grant`, `startCapture`, `stepCapture`, `finishCapture`, `commit`, `undo`, `redo`, `exportResult`；模型用归一化 crop/masks 数据，与图片元素解耦。
- [x] 重跑同一命令，结果 6 tests passed、0 failures。

## Task 2：原型页面

- [x] 使用以下字面外壳，所有动态界面插入 `ly-screen`，模态插入 `ly-overlay`，保留根节点供主题与宿主微调。

```html
<section id="ly-interaction-prototype" aria-label="连页交互原型">
  <label class="form-label">体验情境
    <select class="form-select" id="ly-scenario"></select>
  </label>
  <div class="ly-phone" aria-label="连页 Android 应用">
    <div class="ly-statusbar">9:41</div>
    <div id="ly-screen"></div>
    <div id="ly-overlay"></div>
    <div class="ly-navigation"></div>
  </div>
  <div class="text-small text-muted">交互原型 · 授权、截图与导出为模拟</div>
</section>
```

- [x] 用产品私有 CSS 变量定义纸白、石墨与烧橙；首页、系统准备、桌面、目标页面、检查编辑、结果反馈和设置均保持不透明背景。
- [x] 主按钮贯通流程；底部弹层具有原生按钮、明确关闭、键盘 Escape、焦点回到触发动作；每个图标按钮具有 accessible name。
- [x] 绑定裁剪与遮盖手势、键盘/滑块替代、撤销重做和缩放；所有编辑在应用时写历史，在取消时保持原历史。
- [x] `build.mjs` 使用 `readFile` 与 `replace` 直接组合 literal template、style 和 script；只把批准 Logo 的 PNG 编成 CSS data URL。执行 `node design/brand/lianye/prototype/build.mjs`，片段为 68,564 bytes。

## Task 3：浏览器验收与交付

- [x] 运行 `node design/brand/lianye/prototype/verify-browser.mjs`：首次完整流程、所有情境、裁剪/遮盖/撤销/分享取消/保存失败/草稿退出保护、320/390/736 px 与深色模式；63 checks passed。
- [x] 检查新鲜截图；修复布局或行为后重跑相关验收。页面 JS 异常为 0，横向溢出为 0。另通过宿主 iframe 的 6 项检查。
- [x] 检查文件无真实网络、权限、捕获、分享或存储调用（宿主原型状态记忆除外），所有交互属于本地演示。
- [x] 更新品牌记录的本轮授权与设计状态，准备内嵌原型与 PNG 查看入口。手机实机和应用实施尚未验证，不能写成已完成。
