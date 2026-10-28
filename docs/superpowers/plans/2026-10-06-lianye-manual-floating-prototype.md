# 连页手动悬浮控制 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 将用户确认的手动悬浮控制及必要状态提示落实为可点击、可验收的 V4 原型。

**Architecture:** 继续使用独立状态模型、界面运行时、产品样式和离线浏览器验收。悬浮与通知能力独立于无障碍；新增首屏确认阶段，并在结束前收尾待处理的稳定滚动，不重建正在浏览的页面。

**Tech Stack:** 原生 HTML/CSS/JavaScript、Node test、已安装 Playwright/Chrome/Lucide、visualize 包装。

## 1. 状态边界

Files: `design/brand/lianye/prototype/model.cjs`, `model.test.cjs`。

- [x] 添加行为测试：授权/打开页面不产生首屏；`startCapture` 后帧数为零；`confirmManualFrame(s,true)` 后一屏；失败不产生草稿；`manualControl(s)` 独立于无障碍；双入口不可用阻止开始；首次成功追加才记录教学完成；开始位置为接续基准。
- [x] 运行 `node --test design/brand/lianye/prototype/model.test.cjs` 确認新增边界失败。
- [x] 实现 `manualControl`、`confirmManualFrame`、`cancelManual`，加入 manualPhase、manualHintSeen、manualOrigin 与能力标志。原自动路径仍直接产生首屏。滚动仅在 recording 阶段处理，重复与回滑忽略，无重叠模拟冻结计数。

## 2. 可操作界面

Files: `prototype.js`, `prototype.css`, `prototype.template.html`。

- [x] 归档现有片段与手动总览至 `review/prototype-v3.html` 和 `review/manual-overview-v3.png`。
- [x] 目标页面改为明确点击开始；手动控制按 idle/taking/recording/failed/finishing 显示动作与状态。增加随控件移动的准备、首次教学与错误提示；正常进度仅局部更新控件，不重建目标页面。
- [x] 手动结束同步处理等待中的滚动位置后转入 finishing，再进入共同结果。准备取消释放授权；首屏失败重试；系统中断保留已有画面。保存竞态守卫保持原状。
- [x] 实现通知栏模拟和双入口不可用的设置分支，更新帮助、状态恢复版本及外部情境选项。

## 3. 验证与交付

Files: `verify-manual.mjs`, `verify-host.mjs`, `README.md`, `review/*`。

- [x] 更新浏览器验收：明确开始/首屏回调/教学/缺口/准备取消/首屏失败/最后一屏/通知/阻塞/中断与恢复/触控目标/主题/窄屏；截图准备、教学、稳定进度、缺口、结果、通知与阻塞状态。
- [x] 构建并运行状态/OEM、原自动、保存竞态、手动和宿主检查：`node design/brand/lianye/prototype/build.mjs`，`node --test design/brand/lianye/prototype/model.test.cjs design/brand/lianye/prototype/device-help.test.cjs`，`node design/brand/lianye/prototype/verify-browser.mjs`，`node design/brand/lianye/prototype/verify-save-race.mjs`，`node design/brand/lianye/prototype/verify-manual.mjs`，包装后 `node design/brand/lianye/prototype/verify-host.mjs`。
- [x] 实际查看总览、窄屏、深色、浮球移动后的提示和通知分支截图；更新文档中已观察到的结果与模拟边界。
- [x] 在本轮最终响应展示当前交互片段。
