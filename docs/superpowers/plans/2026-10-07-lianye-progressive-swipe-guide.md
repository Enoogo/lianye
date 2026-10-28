# 连页渐进式滑动指导 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 同步局部示意与完整原型，首次成功后收起文字但保留参考路线。

**Architecture:** 沿用原型的 `manualHintSeen` 学习记录，增加仅本次有效的关闭状态。指导单独绘制，刷新指导时保留目标页滚动节点；浮球、错误提示与通知分支继续使用既有流程。

**Tech Stack:** HTML/CSS/JavaScript、Node、Playwright、现有 visualize 预览包装。

---

### Task 1: 更新指导状态与视觉

**Files:** `design/brand/lianye/prototype/manual-swipe-guide.html`、`model.cjs`、`prototype.js`、`prototype.css`。

- [x] 给模型创建及新会话准备增加 `manualGuideDismissed=false`，保留跨会话的 `manualHintSeen`。
- [x] 局部示意使用独立关闭状态及完整/简化样式：

```javascript
guide.hidden=stage!=='recording'||hintDismissed;
guide.classList.toggle('compact',frames>1);
close.hidden=guide.hidden;
```

- [x] 完整原型在录制正常、有悬浮能力、未关闭时绘制指导；`manualHintSeen` 为真则隐藏标签、保留淡色路径。错误及收尾状态优先退出指导。
- [x] 主体 `pointer-events:none`，关闭按钮接收操作；关闭按钮设置可见文字和访问名称，至少 44×44 px。
- [x] 刷新指导只替换指导节点；换侧拖动同步方向。关闭事件设置会话字段并把键盘焦点交还结束按钮。

### Task 2: 构建与验收

**Files:** `verify-manual.mjs`、`README.md`、生成片段及 review 图片。局部验收脚本沿用 `build/lianye-prototype/inspect-swipe-guide.mjs`。

- [x] 更新已有手动检查，验证「完整 → 简化 → 关闭」以及再次滑动仍保留/关闭不重现、结束清除。保留通知、错误、窄屏、换侧和结果的回归。
- [x] 构建及运行状态、手动与宿主检查：

```powershell
node design/brand/lianye/prototype/build.mjs
node --test design/brand/lianye/prototype/model.test.cjs design/brand/lianye/prototype/device-help.test.cjs
node design/brand/lianye/prototype/verify-manual.mjs
node design/brand/lianye/prototype/verify-host.mjs
node build/lianye-prototype/inspect-swipe-guide.mjs
```

运行宿主及局部检查前，用现有 `render.py` 包装各片段到 `build/lianye-prototype/host-preview.html` 和 `manual-swipe-guide-preview.html`。期望检查通过且无 JS 异常。

- [x] 查看完整首屏、简化状态、320 px 浅深色与换侧截图，确认路径仍在而文字退出、操作不被覆盖。
- [x] README 标记 V5，记录本轮实际运行的检查及原型边界；交付可打开的预览。

## 本轮证据

- 生成片段 84,318 bytes；状态与厂商 25 项、自动浏览器 98 项、手动浏览器 86 项、宿主 10 项、局部示意 8 项检查通过，浏览器无 JS 异常。
- 320 px 内容区域实际宽度 286 px，终点标签原先左侧越界 6.1875 px；加入路径横向最小边距后，标签完整留在区域内，原手动检查通过。
- 局部示例文章最多可滚动 662 px；检查曾错误期待其第三次上滑产生第 4 屏。改用两轮实际可追加内容验证保留与关闭，没有增补虚构文章或计数。
- 当前预览：`build/lianye-prototype/host-preview.html` 与 `build/lianye-prototype/manual-swipe-guide-preview.html`。生成截图及 QA 报告位于 `design/brand/lianye/prototype/review/`。
