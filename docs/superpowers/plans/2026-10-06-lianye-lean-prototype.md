# 连页精简原型 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按用户明确删减和三份独立评审，交付保留厂商帮助的简洁原型。

**Architecture:** 继续使用独立的本地状态模型与字面 HTML 片段；增加已有厂商路径的数据模块，删除遮盖模型与页面。保存状态与实际裁剪版本绑定，过程和结果反馈留在当前页。原有应用和 V1 设计快照保留，所有更改集中在原型与设计记录。

**Tech Stack:** HTML/CSS/JavaScript、Node test/assert、已有 Playwright/Chrome、宿主 Lucide。

## 文件与步骤

- [x] `device-help.cjs`：六组 `label/short/full` 与原有资源一致；`get(vendor)` 提供回退，完整路径与短路径分层展示。`device-help.test.cjs` 读取 Android 中文资源核对六个字段。
- [x] `model.cjs` / `model.test.cjs`：编辑只保留 crop；保存时记当前裁剪签名，提交/撤销/重做重新判断是否已保存；只在未保存草稿被替换时保护。测试取消分享、中断保留、失败重试、旧保存版本恢复与隐藏/停用独立。

```js
const signature = edit => JSON.stringify(cleanEdit(edit));
function refreshSaved(s) {
  if (s.draft) s.draft.saved = s.draft.savedEdit === signature(current(s));
}
```

- [x] `prototype.template.html` / `prototype.js` / `prototype.css`：画面外模拟机型，产品内默认本机路径。重写精简首页、结果和裁剪，取消重复成功弹层；首次系统返回延续已选择的任务；分享模拟系统面板；高级排障折叠。
- [x] `build.mjs`：把厂商数据与模型、视图一起内联，维持无网络片段和已批准 Logo；运行 `node design/brand/lianye/prototype/build.mjs`。
- [x] 运行 `node --test design/brand/lianye/prototype/model.test.cjs design/brand/lianye/prototype/device-help.test.cjs`，实际 15 pass，0 failures。
- [x] 更新 `verify-browser.mjs`：六类 OEM，第一条流程、权限拒绝、裁剪可读/取消/局部历史、保存反馈、分享取消、返回/替换保护、失败与中断、320/390/736 px、主题和状态恢复；实际 98 checks，0 JS errors。
- [x] 更新 `verify-host.mjs`：实际 iframe 与宿主样式的闭环、操作目标、对比度，重制新流程 PNG；实际 7 checks；已查看首页、授权、结果、裁剪、草稿、帮助、窄屏、深色及总览。
- [x] 更新设计与评审记录，最终展示精简稿、独立评估结论与六屏流程总览；不把浏览器原型验证写成 Android 实机验证。三位独立 agent 已分别复核 V2。
- [x] 处理 V2 复核收尾：帮助补回手动结束及检查后保存；恢复高级 ADB 复制的模拟交互；保存期守卫与回调归属修复。`verify-save-race.mjs` 修改前失败、修改后通过，UX agent 独立运行同一验证也通过。
