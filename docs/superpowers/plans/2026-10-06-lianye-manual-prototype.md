# 连页手动截图原型 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在精简原型中完成无障碍拒绝后的手动兜底，并保留原有自动流程。

**Architecture:** 在现有状态模型中增加 auto/manual 意图及独立屏幕捕获能力。手动模拟由稳定滚动触发，视图保留原生滚动容器；完成后复用已有草稿和编辑导出。无新增 Android 修改、无导入、无悬浮窗授权。

**Tech Stack:** HTML/CSS/JavaScript、Node test/assert、现有 Playwright/Chrome、宿主 Lucide。

## 文件与执行

- [x] 保存 `review/prototype-v2.html` 和 `review/overview-v2.png` 快照。
- [x] 在 `model.test.cjs` 增加手动授权隔离、取消、等待/重复/回滚、失去重叠、结束与重新授权测试；修改前原 8 个模型测试通过、新 4 个测试因 API 不存在失败；修改后模型与 OEM 合计 19 个通过。
- [x] `model.cjs` 增加 `requestManual`、`grantProjection`、`observeManualScroll`：

```js
function requestManual(s) {
  s.mode='manual';s.projection=false;s.pendingStart=true;s.sheet='projection';
}
function grantProjection(s, allowed) {
  s.projection=allowed;s.sheet=null;s.pendingStart=false;
  s.screen=allowed?'desktop':'home';
}
```

每次手动开始重置位置与缺口；`startCapture` 未授权时返回 false。稳定滚动累计可见内容，反向和重复不重复计数；大幅跨越模拟缺口并冻结可靠部分。结束释放手动授权，自动 Android 10 路径不受该策略影响。

- [x] `prototype.js` / `prototype.template.html`：增加评审情境“手动截图”；首次、拒绝、帮助提供入口；投屏授权不改变无障碍状态；手动无自动推进定时器、无悬浮控件；滚动容器延迟 180ms 触发 `observeManualScroll`，不重建正在滚动的 DOM。返回动作结束并进入结果，快速滚动行内反馈。手动帮助的自动设置默认折叠，仍复用六类路径。
- [x] 更新状态保存版本到 3，兼容缺少 mode 的 V2，重新挂载时停止手动会话并保留已捕获内容；保存竞态守卫不变。
- [x] `verify-manual.mjs`：实际点击和滚动，验证取消、拒绝后入口、无自动推进/悬浮控件、回滚去重、返回结果、下一次授权、速度缺口、状态恢复、三种宽度与深色；45 checks，0 JS errors；生成 manual 系列截图及 `review/manual-verification.json`。
- [x] 构建：`node design/brand/lianye/prototype/build.mjs`，片段 64,353 bytes。模型与 OEM 19 pass、原 98 项流程通过、保存竞态通过、新手动 45 项通过；真实 iframe 10 项通过。
- [x] 生成 `review/manual-overview.png`，实际检查入口、捕获授权、桌面提示、目标内容、共用结果、缺口结果、帮助和新总览；更新 README 与设计记录。最终展示同一可点击原型，明确系统操作为模拟。
