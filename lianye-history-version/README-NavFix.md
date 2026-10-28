# Lianye NavFix — 修改说明（README）

基于 [gxwane/lianye](https://github.com/gxwane/lianye) `master` 分支源码的定制构建。
包名改为 `org.lianye.navfix`，可与官方版共存；使用独立自签名证书。

- 当前版本：`0.1.24-navfix-long`（versionCode 25）
- minSdk 29 / targetSdk 35
- 构建环境：JDK 21.0.12、Gradle 8.11.1、AGP 8.8.0、SDK Platform 35、Build-Tools 35.0.0

---

## 0.1.24

### Finish 按钮：截图时不入画（回滚 FLAG_SECURE，恢复逐帧隐藏）

**问题**（0.1.23 用户实测）：Finish 按钮显示出来了，但**被截进了长图**。更糟的是按钮成为画面中的"固定锚点"——两帧里按钮位置不动，OverlapMatcher 把它当成零位移证据，deltaY 塌缩到 58/60（期望 735），拼接随之损坏（日志 `frame=2 deltaY=58 expected=735`）。

**根因**：0.1.23 用 `FLAG_SECURE` 让窗口"对截图不可见、对人可见"。但 `AccessibilityService.takeScreenshot` 在 Pixel 上**并不跳过** FLAG_SECURE 窗口（该 flag 主要约束 MediaProjection/DRM），按钮照常入画。

**修复**：回滚到逐帧隐藏机制（即 QQ 已验证成功的原始流程）：
- **每帧截图前** `hideBeforeCapture()`：控件 `alpha=0` + `dispatchDraw` 停绘 + 等待帧栅栏，确保截图瞬间屏幕上没有控件像素。
- **每帧截图后** `showAfterCapture()`：立即恢复显示。
- 控件窗口全程 attach、可点击——两帧之间的间隙足够点按 Finish。
- 点 Finish / 会话结束沿用原有行为：`dismissOverlay()` 隐藏控件 + 打开预览。
- 0.1.23 中"磁贴启动显示控件"的改动**保留**（否则没有 Finish 可点）。

**效果**：Finish 全程可见可点，但永远不进长图，也不干扰帧匹配（deltaY 恢复正常）。

### 版本号
- versionCode 24 → 25
- versionName `0.1.23-navfix-long` → `0.1.24-navfix-long`

---

## 0.1.23（已被 0.1.24 部分回滚）

### 截图过程中可见的 Finish 按钮（可手动结束）

**问题**：从控制中心磁贴启动截图后，屏幕上没有任何按钮，只能等引擎自己判断到底部才结束。

**根因**（两处叠加）：
1. `FloatingOverlayManager.hideBeforeCapture()` 每帧截图前把控件 `alpha = 0f` 并让 `dispatchDraw` 直接返回——控件不可见。
2. `startCaptureFromTile()` 里调用了 `overlayManager?.hide()`，把悬浮控件整个从窗口移除。

**修复**：
- **磁贴入口不再移除控件**：`startCaptureFromTile()` 改为 `overlayManager?.show()`，整个会话都有 Finish 按钮（0.1.24 保留）。
- ~~**`FLAG_SECURE` 替代 alpha 隐藏**~~（**0.1.24 回滚**：Pixel 的 takeScreenshot 不跳过 FLAG_SECURE 窗口，按钮被截入画并破坏匹配）。

### 版本号
- versionCode 23 → 24
- versionName `0.1.22-navfix-long` → `0.1.23-navfix-long`

---

## 0.1.22

### 快捷设置磁贴：控制中心收起后再截图

**问题**：点磁贴后立刻截图，截到的是控制中心面板本身（`ALIGNMENT_FAILED sad=263.50`），不是背后的页面内容。原因：`ACTION_CLOSE_SYSTEM_DIALOGS` 广播在 Android 12+ 被限制，非系统应用无法用它收起控制中心。

**修复**（`LianyeAccessibilityService.kt`）——三层收起策略 + 等待动画完成：

1. **`GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`**（API 33+）：Android 13 引入的 AccessibilityService 专用全局动作，专为收起通知面板/快速设置面板设计，Android 13+ 上即时生效。
2. **`ACTION_CLOSE_SYSTEM_DIALOGS` 广播**：保留作为旧设备的后备。
3. **上滑手势模拟**：`dispatchGesture` 发送 200ms 快速上滑（屏幕 80% → 25%），模拟人手操作收起面板，兜底 OEM ROM 无视系统 API 的情况。
4. **等待 700ms** 再开始截长图：面板收起动画约 300–500ms，等待确保面板完全消失后才采集第一帧。

### 版本号
- versionCode 22 → 23
- versionName `0.1.21-navfix-long` → `0.1.22-navfix-long`

---

## 0.1.21

### 拼接丢内容根因修复：匹配器搜索范围 + 纹理打分 + 期望行程裁决

**用户实测**（5 次手势滚动，微信 1–40 数字消息）：长图仍缺号。真机日志（17:13 会话）关键线索：
- 手势行程恒定 653px（`startY=1260.2 → endY=606.8`）
- frame1 报 `deltaY=116`（实际 653）→ 丢失 537px
- frame2 报 `deltaY=44`（实际 653）→ 丢失 609px
- frame3 报 `deltaY=632`（接近真值，此时画面进入高纹理区）

**三层根因**（`OverlapMatcher.kt`）：

1. **搜索范围被模板位置封顶**：`maxShift = min(maxSearchRange, tStartPrev)`。模板放在 y≈116 处时搜索上界只有 116px，653px 真实偏移根本不在搜索范围内——返回的是边界垃圾值。修复：`chooseTemplateStart` 新增 `minStartY` 参数，强制模板位置 ≥ 期望位移（`minTemplateStart = minOf(expectedDeltaY, maxSearchRange, sigPrev.height - tH)`）。

2. **纹理打分不适用于右对齐聊天气泡**：原版只看中间列方差，微信气泡全在右 1/3 列、中间纯白 → 方差≈0 → 模板落在空白高位。修复：改为**三列平均** `((left+center+right)/3)` 选纹理最丰富区域。

3. **周期性混叠**：消息间距 164px 的重复布局使亮度签名在 `trueShift ± k×164` 处出现混叠峰，与真峰同分（SAD=0.0 平局）。修复：新增 **expected-stroke tie-break**——从手势行程推算 `expectedDeltaY = startY - endY`，当匹配结果不可信（`ambiguityRatio ≥ 0.7` 或 `score > 6.0`）且全局 0 偏移差 ≥ 4.0（真到底保护，两帧完全一致时不重锚）时，在近平局集合（`score ≤ minScore + tieTol`）中选**最接近期望行程**的偏移。

**测试**（`OverlapMatcherTest.kt` 新增 2 个回归用例）：
- 右对齐气泡布局 + blank 中间列 → 锁定 240px 大位移（而非边界垃圾值）
- 真到底（identical frames + expectedDeltaY=350）→ 正确报 deltaY=0 + isBottomReached

全部 **191/191** 测试通过。

**引擎侧**（`LianyeEngine.kt`）：
- `processFrameStep` 计算 `expectedDeltaY = round(command.startY - command.endY)` 并传入匹配器
- 决策日志新增 `expected=` 字段，便于诊断

### 版本号
- versionCode 21 → 22
- versionName `0.1.20-navfix-long` → `0.1.21-navfix-long`

---

## 0.1.20

### 快捷设置磁贴：点击直接开始自动截长图
旧版点磁贴只是**显示/隐藏悬浮球**，还得再点一下悬浮球才开始截图。

**新版**：点磁贴 → 立即收起控制中心 → 直接开始自动截长图（不再显示悬浮球）。

实现（`LianyeTileService.kt` + `LianyeAccessibilityService.kt`）：
- 新增 `LianyeAccessibilityService.startCaptureFromTile()`：复用悬浮球的 `beginAutoCapture` 逻辑（原 `onStartCapture` 重构为独立方法），跳过悬浮球中转。
- 新增 `collapseQuickSettings()`：发送 `Intent.ACTION_CLOSE_SYSTEM_DIALOGS` 广播，**在第一帧截图前**收起控制中心/通知面板，避免面板被拼进长图。AccessibilityService 有权限发此广播；即使失败（极旧 API）截图仍会继续，只是面板会入镜。
- 磁贴状态简化为：服务未连接 → `UNAVAILABLE`；否则 `INACTIVE`（点击即启动）。

### 拼接丢内容 / 重叠（Pixel 9a 实测，1–40 数字消息）
用户实测：发 1–40 顺序数字消息，截长图后**只剩 19 条**。像素分析结论：

| 测量项 | 数值 |
|---|---|
| 屏幕物理分辨率 | 1080 × 2424 |
| 消息气泡间距（pitch） | **164 px** |
| 长图实际高度 | **3991 px**（= 1.65 屏） |
| 1–40 应有高度 | **7060 px**（= 2.91 屏） |
| 缺口 | **3069 px ≈ 18.7 条消息** |

**实际拍到的数字**：`1 2 3 4 5 6 7 8 9`（全）→ **[气泡被 seam 切断]** → `17 18 19 20 21 22 23 24` → **`28`（双气泡重叠 h=132）** → `33`

**缺失**：
- **seam 1**（y 1765→2067，gap 302px）：**10–16** 共 7 条被丢弃，且 10 号气泡被切掉一半（残余 h=36）
- **seam 2**（y 3215→3565，gap 350px）：**25–27** 共 3 条丢弃，28 号双气泡**重叠**（h=132 vs 正常 110），**29–32** 共 4 条丢弃
- **未到达底部**：**34–40** 共 7 条从未被采集到

**根因**（`LianyeEngine.appendIncrementStrip`）：

```kotlin
val safeCropStartY = maxOf(0, (nextSlice.height - bottomBarHeight) - deltaY)
val safeCropHeight = minOf(deltaY, nextSlice.height - safeCropStartY)
```

每帧只从 `nextSlice` 的 `(height - bottomBarH - deltaY)` 起取 `deltaY` 行追加。当 `deltaY` 估算**偏小**（实际滚动更多）→ `nextSlice` 顶部应补内容被跳过 = **丢行**（seam 1/2 的 7+3+4 条）；当 `deltaY` 估算**偏大** → 同一行被写两次 = **重叠**（28 号 h=132）。这是一套"按估算 delta 裁条"的脆弱逻辑，对匹配误差零容错。

**待修复方向**（下版）：把"裁条"改为"重叠区累计"——不再相信单帧 deltaY，而是维护 `accumulatedY`，每帧用 `OverlapMatcher` 的实测 delta 累加，从 `nextSlice` 取 `[accumulatedY - prevAccumulated, accumulatedY)` 区间，误差可自愈。

### 版本号
- versionCode 20 → 21
- versionName `0.1.19-navfix-long` → `0.1.20-navfix-long`

---

## 0.1.19

### 停止截图判定过早（截一两张就自动停 / 第二次会话更早）— 根因与修复

真机日志（Pixel 9a，0.1.18 版）显示：

```
Frame -1 match deltaY=0 bottom=true stationary=true dynamic=false sad=13.40 amb=0.44
frame=1 decision isBottom=true (bottom=true, stat=1/0+1) deltaY=0 sad=13.400922 initialWritten=false hasStitched=false
Completion reason=COMPLETED frames=2 height=1867
```

**根因**：`OverlapMatcher` 的"到底"判定阈值过松。

旧逻辑：`isBottomReached = isStationary && (finalScore / 4.0f <= 3.5f)`，即 **`sadScore ≤ 14.0` 就判"到底"**。
而真机采集到的帧对在 0 偏移下残差为 `sad=13.40`（**捕获时机抖动 / BAND 模式下相邻帧裁剪 y 范围不一致**导致的假匹配），落入这个过松的阈值内，于是**单帧就短路停止**（`stat=1/0+1`），引擎走 `isBottom=true` 路径、直接完成，只拼出 1 帧（`height=1867` = 初始帧高度）。

#### 修复
1. **`isBottomReached` 阈值收紧**：`finalScore <= 4.0f`（不再除以 4）。
   经实验量化，**真正"到底"的帧对**（页脚固定、内容静止、仅有采集噪声）残差在 **0–1.5**，远低于 4.0；而"假到底"（捕获时机抖动造成的畸变重叠）残差高达 **13+**。阈值 4.0 是二者之间清晰的安全边界。
2. **新增 `isDeformedMatch` 判定**：`isStationary && !isBottomReached && finalScore > 8.0f` → 标记为**畸变匹配**（不是"页面停了"，而是"这一帧的对齐本身坏了"）。引擎侧把它从 `consecutiveStationaryCount` 中剔除，**不让畸变帧累积触发 `newStatCount >= 2` 的停止路径**，而是重新尝试手势滚动。
3. **`newStatCount >= 2` 成为主要停止路径**：真正到底时，连续两帧 clean-stationary 即可安全停止；单帧噪声不再致命。
4. **初始帧就静止时**（首帧到底）：残差阈值从 `<= 15.0` 收紧到 `<= 8.0`，高残差不再被当成"已完成 1 帧的长截图"提交，而是报 `ALIGNMENT_FAILED`，避免拼出错位图。

#### 测试
- **`ConsecutiveSessionStopTest`**（2 用例）：原"bug 复现"测试因 dispatcher 不模拟页面滚动、每帧都喂相同帧而恒失败（`frames=2`）；**已修复测试本身**，让 dispatcher 真正推动 `PageFrameCapturer`，断言"第二次会话在页面还能滚时至少织 5 帧"。现在通过。
- **`OverlapMatcherTest.deformed overlap with low displacement but high residual must NOT trigger bottom reached`**（新增）：构造 0 偏移但残差 > 8 的帧对，断言 `isDeformedMatch=true` 且 `isBottomReached=false`。通过。
- **`OverlapMatcherTest.clean stationary frames must still declare bottom reached after threshold tightening`**（新增）：纯 0 偏移 clean 帧对，断言仍正常触发 `isBottomReached`（不误伤真到底）。通过。
- 全量 **189/189** 测试通过（0.1.18 为 187 测试、2 失败）。

#### 诊断日志增强
- `Completion` 行新增 `lastSad=` 字段，便于真机定位"到底"时的最终残差。
- 匹配日志新增 `deformed=` 字段，可直接看出哪几帧是畸变匹配。

### 版本号
- versionCode 19 → 20
- versionName `0.1.18-navfix-long` → `0.1.19-navfix-long`

---

## 0.1.18

### 应用内诊断日志（可复制）
新增「帮助 → 诊断日志」面板：
- 实时显示引擎/手势/匹配器日志（每帧匹配位移、停止判定原因、手势注入结果）
- **复制全部**：一键复制到剪贴板，方便粘贴到反馈中
- **刷新 / 清空**：手动控制
- 日志同时写入应用私有文件 `files/lianye-diagnostics.log`（超 2MB 自动截断保留最近内容），应用重启不丢失

`LianyeLogStore`（`platform/LianyeLogStore.kt`）接管了 `LianyeEngine`、`OverlapMatcher`、`AccessibilityGestureDispatcher` 的关键日志点，取代单纯 logcat 输出。

用途：真机跑一次截图后回到「帮助 → 诊断日志」，点「复制全部」把内容发回，即可定位「第二次截一两张就停」等停止判定问题。

### 版本号
- versionCode 18 → 19
- versionName `0.1.17-navfix-long` → `0.1.18-navfix-long`

---

## 0.1.17

### JVM 仿真验证（无 KVM，无法启动 Android 模拟器）
新增 `FullHeightStitchTest`：用合成长页面驱动**真实的 LianyeEngine**（真实 OverlapMatcher、TileStore、StreamingPngAssembler 瓦片格式），逐像素校验拼接结果。

覆盖 7 个场景：
1. 普通模式（仅中带）连续拼接
2. 全高度模式：首帧含上带、尾帧含下带
3. 全高度模式 + 固定底栏（底栏只出现一次）
4. 全高度模式 + 短页面（一屏内到底）
5. 全高度模式 + 下带窄条（断言最后一块高度 = 屏高 − 下界）
6. 连续两次会话，验证状态不串场
7. `detectBottomBarHeight` 定点验证

全部通过；整个测试套件 26 个测试类、0 失败。

### 诊断日志
`LianyeEngine` 现在会在结束时打印：
- `Bottom extension height=...`
- `Full-height=... tiles=[...]`

如果真机仍异常，`adb logcat -s LianyeWeaving` 可直接看到每块瓦片高度，判断是尾条过大、缺失还是拼接顺序错误。

## 0.1.16

### 底栏检测用错帧（拼接错位/尾段异常）
- `detectBottomBarHeight()` 原先把「首帧」当作比较对象。全高度模式下首帧是 `0..bottom`（比后续 `top..bottom` 高出一段），函数内部按 `min(height)` 逐行比较，导致比较的是屏幕顶部而不是底部，算出的固定底栏高度完全错误。
- 结果：首帧被裁掉过多/过少，接缝错位，尾部看起来像贴了整屏内容。
- 改为使用**与后续帧同原点的 `prevSlice`**（band 切片）做检测，裁剪量重新正确。

### 跨会话残留（第二次只能截 1–2 张）
- `captureSpan` 是截图器上的可变状态，上一轮若在收尾阶段被中断，可能停留在 `BOTTOM_ONLY`。下一轮采集到的就只是屏幕最底部的一条窄带（几乎静止），匹配器连续两帧判定「已到底」，于是 1–2 张就停。
- 现在：
  - 每轮 `startWeaving` / `startManualWeaving` 开始时强制 `setCaptureSpan(BAND)`；
  - `releaseSession()` / `release()` 也重置为 `BAND`。
- 另一常见原因：上一轮已经滚到页面底部，重新截图前需先回到页面顶部，否则确实会很快触底。

## 0.1.15

### 底部延伸条错误拼接修复
- 0.1.14 里 `CaptureSpan.BOTTOM_ONLY` 的裁剪区间写错：起点用了 `bandTop`，导致返回的是「中 + 下」几乎整屏，而不是只有 `bandBottom..screenHeight` 的下带，长图末尾因此多出一大块类似全屏的内容。
- 修正为：
  - `BAND` = `bandTop..bandBottom`
  - `FIRST` = `0..bandBottom`
  - `TOP_ONLY` = `0..bandTop`
  - `BOTTOM_ONLY` = `bandBottom..screenHeight`
- 手动模式的上带补拍同时得到修正（之前 `TOP_ONLY` 也会返回错误区间）。

## 0.1.14

### 全高度模式崩溃的真正原因与修复
- 0.1.13 在开始采集后立刻做了两次连续截图（先补上带、再截主体），触发了 Android 无障碍截图 API 的频率限制，`takeScreenshot` 返回空，会话以 `capture interrupted` 结束。
- 自动模式改为 **首帧直接截 `0..bottom`**（`CaptureSpan.FIRST`）：
  - 首帧原样写入瓦片，天然包含「上 + 中」；
  - 匹配用的视图用 `crop(top, ...)` 裁回与后续帧一致的坐标原点，不影响重叠匹配；
  - 手势中心按带宽重新计算，滑动仍落在框选区内。
- 不再有开头的第二次截图，从根源上避开限流。
- 手动模式仍保留上带补拍，但补拍后 `delay(400ms)` 再进入 250ms 采样循环。
- 底部延伸条仍在会话存活时抓取一次（滚动若干秒后，不会触发限流）；失败则跳过，不影响主图。

## 0.1.13

### 全高度模式崩溃修复
- 首尾延伸条的补拍全部包进 `runCatching`，失败只记录日志、不中断会话。
- 底部延伸条改为在 `finalizeContent()` **之前**抓取（会话仍存活），再追加到已完成的瓦片之后，避免在收尾阶段调用截图 API。
- 采集协程增加 `catch (Throwable)` 兜底：任何异常都会走 `failWeaving` 并打开预览，而不是杀掉进程。
- 全高语义确认：第一张 = 上 + 中（`0..bottom`），中间 = 中（`top..bottom`），最后 = 中 + 下（`top..H`）。

### 吸附设置即时生效
- 修改某条边的阈值时自动打开该边的吸附开关，下一次拖动立即按新阈值吸附。
- `ACTION_UP` 时按落点再执行一次吸附，保证松手位置也落在边缘。
- 仍然只在拖动时吸附；打开开关本身不会移动框线。
- `Done` 才写入范围/吸附/全高；`Cancel` 丢弃本次全部修改。

### 悬浮球边距
- 可拖动范围改为距屏幕四边至少 **50 物理像素**（此前是 50dp）。

### 振动开关
- Help → Capture controls 新增 `Vibrate when long-press hides the button`，默认开启。
- 开启时长按隐藏悬浮球会触发系统默认的长按触觉反馈（`HapticFeedbackConstants.LONG_PRESS`，无需振动权限）。

## 0.1.12

### 截图范围下边界可到屏幕底部
- `CaptureRegionStore.fullFrame()` 改为返回**完整物理屏幕**（含导航栏），拖动上限随之放开。
- 新增 `defaultFrame()`：默认范围仍是「全屏减去导航栏」，「Reset」按钮恢复到该值。
- 两个截图器不再把 `region.bottom` 截断到导航栏上沿，用户明确拖到导航栏区域时会照常截取（会带入导航栏像素，属预期行为）。

### 全高度模式（Full height）
- 位置：框选面板 → 齿轮 → 设置表下方 `Full height` 开关。
- 含义：
  - 所有帧的**宽度**始终等于框选宽度；
  - **中间**的帧只截框选高度带 `[top, bottom]`；
  - **第一张**额外包含 `[0, top]`（框选下界以上的全部内容）；
  - **最后一张**额外包含 `[bottom, screenHeight]`（框选上界以下的内容）。
- 实现方式：不改变匹配帧的坐标原点，而是在会话开始/结束时各补拍一条**窄带**（`CaptureSpan.TOP_ONLY` / `BOTTOM_ONLY`），分别前后拼接。这样不会破坏重叠匹配，也不会让中间帧重复导航栏。
- 适用于自动与手动两种模式。
- 局限：首尾补拍发生在开始/结束的那一刻，若期间页面顶部/底部内容发生变化（视频、时钟），补拍内容以结束时为准。

### 坐标输入与保存语义
- 面板里的 X/Y 输入框：输入即作用于红框（实时），无需确认。
- 吸附阈值、吸附开关：改动即时作用于后续拖动；但**不再即时落盘**。
- `✓ Done`：保存范围 + 吸附设置 + 全高开关到应用，关闭面板，恢复悬浮球（点球开始截图）。
- `Cancel`：不写入任何本次修改（范围、吸附、全高全部丢弃），下次打开恢复上次保存值。

---

## 0.1.11

- **坐标原点修正**：框选窗口此前被系统 inset，`y=0` 实际在状态栏下方。现设置 `setFitInsetsTypes(0)` / `setFitInsetsSides(0)` 与 `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`，并使用 `Display.getRealMetrics()` 取真实分辨率，`(0,0)` 为物理屏幕左上角。
- **直接弹出框选窗口**：点击 `Set capture area` 不再先显示悬浮球，直接显示红框 + 面板（默认屏幕居中）；Done/Cancel 后再恢复悬浮球。
- 设置表：Link 行增加 `Link` 标签；勾选框用 `FrameLayout` 包裹居中，使其中心与 Switch 轨道中心同轴。

## 0.1.10

- **设置区二次打开空白修复**：不再用 `GONE/VISIBLE` 切换 `TableLayout`（WRAP_CONTENT 窗口下二次测量会把行压成 0 高度），改为关闭时移除、打开时重新构建整套设置视图。
- 角标：`Top-left` → `┌`，`Bottom-right` → `┘`（框选面板与 App 主界面同步）。

## 0.1.9

- 设置表输入框固定 100×40dp、字号 14sp、`includeFontPadding=false`，解决数值被裁切。
- 新增 **Quick Settings 磁贴** `Lianye capture`（`LianyeTileService`）：
  - 无障碍未连接 → `STATE_UNAVAILABLE`
  - 点击切换悬浮球显示/隐藏，状态同步 `STATE_ACTIVE / INACTIVE`
  - 需在控制中心编辑页手动添加一次。

## 0.1.8

- 框选面板标题栏可拖动，`translationX/Y` 直接跟手，无动画。
- 每个边独立 **吸附开关 + 阈值**，支持列级 `Link` 联动；只在拖动该边时生效，打开开关不会自动移动线。
- 红线 1px 描边向内偏移 0.5px，`x=0`、`y=0`、右边/下边贴边时都不再消失；矩形语义为「红线外边缘围起来的范围」。
- 主界面坐标改为 4 个可编辑数字输入框。
- 全局英文：`uiText()`/`localized()` 恒返回英文，删除 `values-zh`，清理硬编码中文。

## 0.1.7

- 新增**截图范围**功能：
  - 主界面图标/按钮进入框选，红框 + 坐标面板，四边可拖。
  - `CaptureRegionStore` 持久化（SharedPreferences）。
  - 截图器按范围裁剪（`PixelSlice` 使用 stride + offset，零拷贝）。
  - 自动模式滑动坐标补偿选区顶部偏移（`contentTopInScreen`）。
  - 默认范围 = 全屏减去导航栏。

## 0.1.6

- 清理 0.1.4/0.1.5 遗留的吸边、折叠、半隐藏、删除目标窗口等死代码。
- 空闲悬浮球默认 **屏幕居中**，首次显示居中，之后记忆位置。
- 拖动范围强制留边 50dp，避免与返回手势冲突。

## 0.1.5

- 悬浮球：短按开始截图，长按关闭（移除边缘判定）。
- 去掉 `Surface` elevation 造成的方形阴影，改为手绘圆形光晕。

## 0.1.4

- 空闲态只显示 52dp 圆形图标，不再显示长条按钮/半贴边形状。
- 曾实现「拖到边缘关闭」，因判定不稳定在 0.1.5 改为长按。

## 0.1.3

- 点击 Stop 立即收起悬浮球；截图自然完成后同样收起。
- 引入过底部拖拽删除目标窗（0.1.4 移除）。

## 0.1.2

- 导出上限放开到 PNG 规范上限（`Int.MAX_VALUE`，即 2³¹−1）：移除 30,000px 自动分图、`MAX_DIMENSION=100_000` 校验。
- 导出本身是流式写 PNG，不创建整图 Bitmap；实际瓶颈为存储与查看器解码能力。

## 0.1.1

- **导航栏修复**：`AccessibilityService.takeScreenshot` / MediaProjection 返回整屏，手势导航栏半透明且固定，会被逐帧拼进长图。
- 新增 `SystemBarCrop`，按 `WindowInsets` 计算导航栏高度并在截图时裁掉；沉浸式（inset=0）不裁。
- 包名改为 `org.lianye.navfix`，版本号重置为独立序列。

---

## 关键文件

| 文件 | 作用 |
|---|---|
| `platform/CaptureRegionStore.kt` | 选区持久化、默认/全屏边界、全高开关 |
| `platform/ScreenGeometry.kt` | 真实物理分辨率（原点=屏幕左上角） |
| `platform/RegionSnapSettings.kt` | 四边吸附开关/阈值持久化 |
| `ui/floating/RegionSelectionOverlay.kt` | 全屏框选器：红框、拖动、坐标输入、设置表 |
| `ui/floating/FloatingTouchLayout.kt` | 悬浮球拖动容器（无吸边，50dp 安全边距） |
| `ui/floating/LianyeFloatingBubble.kt` | 悬浮球 UI（圆形图标/结束按钮） |
| `service/capture/SystemBarCrop.kt` | 导航栏高度与整屏判定 |
| `service/capture/AccessibilityFrameCapturer.kt` | 无障碍截图 + 选区/延伸条裁剪 |
| `service/capture/MediaProjectionFrameCapturer.kt` | 录屏截图 + 选区/延伸条裁剪 |
| `engine/FrameCapturer.kt` | `CaptureSpan` 定义（BAND / TOP_ONLY / BOTTOM_ONLY） |
| `engine/LianyeEngine.kt` | 自动/手动采集、首尾延伸条拼接、停止判定（含 deformed 匹配重试） |
| `engine/OverlapMatcher.kt` | 帧重叠匹配（SAD + 模板 + 二峰消歧）、停止阈值（clean ≤ 4.0 / deformed > 8.0） |
| `engine/model/MatchResult.kt` | `isDeformedMatch` 标志（畸变匹配非"到底"） |
| `service/LianyeTileService.kt` | 快捷设置磁贴 |
| `platform/LianyeLogStore.kt` | 诊断日志环形缓冲 + 文件落盘 + 剪贴板复制 |
| `ui/main/components/DiagnosticsSection.kt` | 帮助 → 诊断日志面板（刷新/复制全部/清空） |

## 签名与校验

- 签名证书：`/root/lianye-fix.jks`（CN=Lianye NavFix，自签名）
- `Lianye-NavFix-0.1.20.apk` SHA-256：
  `7c82a2c67b5c39be51fa4d25c313a957f0beab9581dd49813fdb523e6a348394`
- 备用下载：<https://gofile.io/d/kN7Zvt37>
- 安装方式：`adb install -r Lianye-NavFix-0.1.20.apk`（若签名冲突需先卸载旧版）

## 构建

```bash
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

`local.properties` 需包含：

```properties
sdk.dir=/path/to/android-sdk
storeFile=/path/to/keystore.jks
storePassword=******
keyAlias=******
keyPassword=******
```

## 已知限制

- 全高模式的顶部/底部延伸内容取自会话开始/结束瞬间，动态内容可能不一致。
- 拖动下边界进入导航栏后，长图会包含导航栏像素（这是手动选择的结果）。
- 超长 PNG 的导出能力受存储与查看器解码限制，尽管编码器支持到 2³¹−1。
- 框选窗口覆盖状态栏区域时，红线画在状态栏下方会被系统 UI 遮挡，但选区坐标仍然有效。

---

## 源码包说明

- `lianye-navfix-0.1.24-source.zip`：完整项目源码（含 `app/`、`docs/`、`website/`、`tool/`、Gradle wrapper）。
  已剔除 `build/`、`.gradle/`、`.idea/`、`.kotlin/` 等生成物，以及 `design/brand/` 下的 32MB logo 设计稿 PNG。
- 编译前需自行创建 `local.properties`：
  ```
  sdk.dir=/path/to/Android/Sdk
  ```
- 构建命令：
  ```
  export JAVA_HOME=/path/to/jdk-21
  ./gradlew :app:assembleRelease
  ```
- 签名用你自己的 keystore（本包不含签名私钥）。

### 已交付 APK 校验值

| 版本 | SHA-256 |
|---|---|
| `Lianye-NavFix-0.1.24.apk` | `0342c27311a26f3738545b05421c259ed89132e3e0f1496b464e444ad55017d8` |
| `Lianye-NavFix-0.1.23.apk` | `3263021717734e8db4dad9000678ae35321c8579fbce338b6af20a2f4f058437` |
| `Lianye-NavFix-0.1.22.apk` | `43d8ca1fb63e19812892f842eb71569e2f412e4247804d579bef3be999716adc` |
| `Lianye-NavFix-0.1.21.apk` | `d51894d22f94e48c41a63b68c6ae59496470f473fc6f1290ff90b62e26913794` |
| `Lianye-NavFix-0.1.20.apk` | `7c82a2c67b5c39be51fa4d25c313a957f0beab9581dd49813fdb523e6a348394` |
