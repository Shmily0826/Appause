# Appause — 重复拦截 / 倒计时重置 压力测试诊断报告

> ⚠️ **2026-09-17 修正**：本文的 root cause 是**真实存在的缺陷**（已在 9-16 复现、9-17 修复并验证），
> 但**真机取证显示它不是用户在真机上遇到的那一个**。真机 cooldown 只有 10 秒，30 秒看门狗无从触发，
> 而真机日志里存在大量「倒计时未走完就重开」的记录。请**先读第 11 节**，再看本文其余部分。

> Goal: `APPAUSE_HUMAN_LIKE_MULTI_APP_STRESS_TEST_V1`
> 日期: 2026-09-16 · 结论: **已稳定复现**（模拟器）
> 被测构建: `com.appause.android.debug` 0.5.40-debug / versionCode 92
> 代码基准: `main @ 9759c20`

---

## 1. 结论摘要

**同一个有效 Pause 仍然存在（窗口已 attach、倒计时仍在运行）时，Appause 会再次执行 Intercept，
attach 第二个 pause 窗口，并把第一个窗口变成无法回收的孤儿。用户看到的就是"倒计时走到一半突然
从 60 重新开始"。**

- 复现稳定性：**3/3 次**（同一场景重复执行，"切回目标 App"的每一次都必然再拦一次）
- 触发条件：`group.cooldownSeconds > 30` 且 pause 屏连续显示超过 30 秒
- 对照实验：在 guard 有效窗口内做同样的切换 → **无重复拦截**，证明触发点是看门狗释放
- 默认 20 秒冷却下 **不会触发**（第一轮 27 次拦截全部成对、零泄漏）

---

## 2. 复现环境

| 项 | 值 |
|---|---|
| 设备 | AOSP `Medium_Phone` 模拟器（`emulator-5554`，`sdk_gphone16k_x86_64`，SDK 37） |
| 受控分组 | `Stress` — 2 apps：Clock(`com.google.android.deskclock`) + Calendar(`com.google.android.calendar`) |
| 冷却时长 | 20s（第一轮）/ **60s（复现轮）** |
| 非受控 App | Messages `com.google.android.apps.messaging` |
| 高频事件 App | Chrome `com.android.chrome` |
| 编排 | `scripts/stress/ui_stress.py`（ADB + uiautomator，操作间 200–1500ms 类人停顿） |
| 分析 | `scripts/stress/analyze.py` |
| 证据 | `scripts/stress/evidence/run-20260916-*` |

---

## 3. 最短稳定复现步骤

前置：分组冷却 > 30 秒（本例 60 秒），目标 App 在受控分组内。

1. 打开受控 App（本例 Calendar）→ pause 屏出现，倒计时 60s 开始
2. **什么都不点，等 30 秒以上**（倒计时走到约 27–30）
3. 切到任意**不受控** App（本例 Messages）
4. 立刻切回目标 App

**预期（当前实现）**：pause 屏再次弹出，倒计时从 60 重新开始。
**问题**：第 3 步切走时旧 pause 屏并没有被撤掉，第 4 步是**在旧屏还挂着的情况下又建了一个新的**。

---

## 4. 证据（时间戳取自 logcat epoch，跨 tag 精确排序）

单次场景 `S11` 的完整序列：

```
12:09:32.384  INTERCEPT: com.google.android.calendar → group=Stress, cooldown=60s
12:09:32.671  Overlay shown for com.google.android.calendar (type=2032)   ← 窗口 #1

12:10:02.786  W Pause guard watchdog: exceeded max hold — releasing guard  ← 释放 guard（t+30s）
                ▲ 窗口 #1 仍然 attach；倒计时仍在跑（60s 才到 12:10:32）

12:10:09.562  Event: com.google.android.apps.messaging
12:10:09.604  SKIP: not in any group (com.google.android.apps.messaging)
                ▲ 没有 "Overlay dismissed" —— 窗口 #1 留在屏幕上
                  （走不到 AbandonCooldown：该分支要求 pauseShown()==true）

12:10:11.069  Event: com.google.android.calendar
12:10:11.148  INTERCEPT: com.google.android.calendar → group=Stress, cooldown=60s  ★ 第二次
12:10:11.202  Overlay shown for com.google.android.calendar (type=2032)          ★ 窗口 #2
                ▲ 两次 Overlay shown 之间没有任何 Overlay dismissed = 窗口叠加

12:10:15.210  W Cooldown abandoned (user left to ...messaging before continuing)
12:10:15.213  Overlay dismissed        ← 只拆掉了 #2；#1 成为永久孤儿
12:10:16.945  INTERCEPT: com.google.android.calendar ...                          ★ 第三次
12:10:16.967  Overlay shown for com.google.android.calendar (type=2032)          ★ 窗口 #3
```

视觉证据（同一次场景）：

| 文件 | 画面 |
|---|---|
| `S11-before-leaving.png` | 切走前：倒计时 **27**，环形进度已走过约 45% |
| `S11-after-return.png` | 切回后：倒计时 **58**，环形进度归零 —— 倒计时重新开始 |

`analyze.py` 自动判定输出：

```
FINDINGS (5):
  [WATCHDOG_RELEASE]      12:10:02.786
  [DUPLICATE_INTERCEPT]   12:10:11.148  — INTERCEPT while overlay is still up (no dismissal since)
  [OVERLAY_LEAK]          12:10:11.202  — second overlay attached, first not removed
  [WATCHDOG_RELEASE]      12:10:47.281
  [WATCHDOG_RELEASE]      12:11:58.798
```

---

## 5. Root Cause

### 5.1 根本矛盾

`pauseShown` 是 **guard（逻辑态）与窗口（物理态）之间唯一的耦合点**，
而所有防重入都只信 guard，没有任何地方校验物理窗口是否还在。

看门狗只清 guard，**不拆窗口**。于是出现"guard 说没有 pause，屏幕上却挂着 pause"的窗口，
这个窗口状态在两个关键位置都被无视：

1. `InterceptionDecider.decidePreGroup` 的步骤 4.5（放弃判定 / 已显示跳过）整体被 `pauseShown()` 门控；
2. `OverlayManager.show()` 的唯一防重入是 `if (AppauseAccessibilityService.pauseShown) return`。

### 5.2 三处相关代码

**① `PauseGuardPolicy.evaluate`（`service/PauseGuardPolicy.kt:66-71`）**
`RELEASE_MAX` 的判断排在 `overlayAttached || pauseActivityVisible` **之前**，
所以"窗口还 attach 着但 guard 已满 30 秒"必然释放 guard：

```kotlin
): GuardAction = when {
    elapsedMs > maxMs -> GuardAction.RELEASE_MAX          // ← 优先命中，不看 attach
    overlayAttached || pauseActivityVisible -> GuardAction.KEEP
    elapsedMs < graceMs -> GuardAction.KEEP_WITHIN_GRACE
    else -> GuardAction.RELEASE_STALE
}
```

**② `AppauseAccessibilityService.pauseShown` 的 RELEASE_MAX 分支（`AppauseAccessibilityService.kt:283-288`）**
只释放逻辑态，不触碰窗口：

```kotlin
PauseGuardPolicy.GuardAction.RELEASE_MAX -> {
    releasePauseGuard(watchdogExpired = true)   // 清 guard / pauseTargetPackage
    lastDecision = "WATCHDOG: released stale guard (exceeded max hold)"
    AppLogger.w(TAG, "Pause guard watchdog: exceeded max hold — releasing guard")
    false                                       // ← 窗口仍在 WindowManager 中
}
```

**③ `OverlayManager.show()`（`service/OverlayManager.kt:168-171` 与 `:613`）**
防重入只看 guard；一旦通过，`overlayView` 被直接覆盖，旧窗口从此无人引用：

```kotlin
if (AppauseAccessibilityService.pauseShown) {          // ← guard 已被释放 → 检查失效
    AppLogger.d(TAG, "Pause screen already showing, skipping")
    return
}
...
overlayView = overlayHost                              // ← 旧窗口引用被覆盖，无法再 dismiss
```

`dismiss()` 只移除 `overlayView` 指向的那一个窗口，因此叠加的旧窗口永久残留。

### 5.3 为什么倒计时重置

`OverlayManager.kt:523` 的 `rememberCountdownState(cooldownSeconds)` 以 `cooldownSeconds` 为 remember key、
随新 Compose host 从零开始。窗口 #2 是全新的 host，所以倒计时从 60 重跑——
**不需要任何 countdown 层的 bug，窗口重建本身就是重置。**

### 5.4 触发条件（为什么"恰好是一半"）

| 条件 | 说明 |
|---|---|
| `cooldownSeconds > 30` | 必须大于 `PAUSE_GUARD_MAX_MS = 30_000` |
| pause 屏连续显示 > 30s | 用户发呆、走开、或被别的事打断 |
| 切走（非 Home）再切回 | 切到 Home 会走 confirmed-Home 拆除路径，不会触发 |

当冷却为 60 秒时，30 秒**正好是倒计时的一半** —— 与用户描述的
"倒计时进行到一半时被再次触发"精确吻合。

如果冷却 ≤ 30 秒，倒计时会先结束并 `startBypass(target)`，bypass 会兜住后续事件，因此不触发。

---

## 6. 对照实验（证明触发点确实是看门狗）

| 场景 | 切换行为 | guard 状态 | 结果 |
|---|---|---|---|
| `S12`（对照） | 与 S11 相同的"切走→切回"×4 | 始终有效（未满 30s） | 日志为 `SKIP: cooldown overlay is showing`，**零重复拦截** |
| `S11`（复现） | 同样的"切走→切回" | 已被看门狗释放 | `INTERCEPT` + `Overlay shown`，**每次都重复** |

差异只有"是否超过 30 秒"，这是单变量对照，可以排除"切换动作本身导致重拦"的解释。

---

## 7. 已覆盖但未复现的场景

第一轮（冷却 20s，每场景前强制重置到干净态）共 11 个场景、27 次拦截：

| 场景 | 内容 | INTERCEPT 次数 | 结果 |
|---|---|---|---|
| S1 | A → 倒数 3s → Recents → B → 回 A | 2 | 无重复 |
| S2 | A → Home → 立刻重开 A → Home → A | 3 | 每次都有 `AbandonCooldown` + `Overlay dismissed`，属既有设计路径 |
| S3 | A/B 交替 + 快速重开 | 4 | 无重复 |
| S4 | Back / Home / Recents 交替 | 2 | 无重复 |
| S5 | 通知栏展开/收起 | 1 | 无重复 |
| S6 | 横竖屏切换 | 1 | 无重复 |
| S7 | A → 受控 B → 回 A | 2 | 无重复 |
| S8 | 快速切出切回 ×4 | 4 | 无重复 |
| S9 | 高频 window 事件（Chrome / Settings 轮换） | 3 | 无重复 |
| S10 | 3 轮 A/普通/B/Settings 连续轮换 | 4 | 无重复 |
| S12 | 同一 App 内 Activity 反复触发 | 1 | 无重复 |

统计上：27 次 `INTERCEPT` 对应 27 次 `OVERLAY_SHOWN` 与 27 次 `OVERLAY_DISMISSED`，完全配对。

**注意 S2 的行为**：A → Home → 立刻重开 A 会产生**新的**拦截（2 秒内），用户同样会看到倒计时重置。
但它是 `AbandonCooldown` 的既定设计（每一步都有明确的 `Overlay dismissed`），
与本次报告的缺陷**根因不同**，不应混为一谈。它是否也该改，是一个独立的产品判断。

---

## 8. 证据边界（模拟器 vs 真机）

- 本报告全部结论来自 **AOSP 模拟器**。真机 Xiaomi 2410DPN6CC / HyperOS / Android 16 **未参与本轮测试**。
- 已知差异：HyperOS 会杀后台、并且存在"窗口 attached 但被 `setHideOverlayWindows` 隐藏"的情况
  （这正是 30 秒硬上限当初被引入的原因）。真机上该缺陷的表现可能更严重（叠加的孤儿窗口可能
  与 `overlayAttached` 状态交叉影响），也可能因为系统更早回收进程而表现不同。
- 模拟器上的 `PASS` **不能**当作 Xiaomi 上的 `PASS`。真机验证应作为独立后续阶段。
- 本轮**未做任何代码修改**，未 commit / push / tag / release。

---

## 9. 修复方向（仅分析，未实施）

核心目标：**让 guard 的释放与窗口的拆除保持一致**，使 `pauseShown` 重新成为可信的单一闸门。

| 方案 | 做法 | 评价 |
|---|---|---|
| A（推荐方向） | 看门狗释放 guard 时**同步拆掉对应窗口**（RELEASE_MAX / RELEASE_STALE 分支） | 让逻辑态与物理态重新一致，`show()` 的检查自动恢复有效。需处理 companion object 访问 service 实例的管道；对 HyperOS "attached-but-invisible" 场景语义也正确（那种窗口本来就该拆） |
| B（最省事但有回归风险） | `show()` 里加上物理窗口检查：`if (pauseShown \|\| overlayAttached \|\| overlayView != null) return` | 单点改动最小，能直接杜绝叠加。**风险**：HyperOS 上窗口 attached 但被藏住时会永久拒绝新的 pause，回归 v0.5.x 修过的"彻底不弹"缺陷 |
| C | 把 `maxMs` 判断挪到 `overlayAttached` 检查之后（即取消"attached 也能释放"） | **不建议** —— 等价于取消 30 秒上限，同样回归历史缺陷 |
| D | `show()` 发现 `overlayView != null` 而 `pauseShown == false` 时，先 `dismiss()` 再重建 | 自愈而非拒绝，不产生"拒绝新 pause"的回归；可作为 A 的补充防线 |

建议组合：**A + D**（一致化 + 孤儿自愈）。按本次任务约定，**根因明确但修复面不算小**
（涉及 guard/窗口一致性、历史 HyperOS 语义、需要回归测试），因此**停在诊断，不做猜测式修改**。

---

## 10. 复现工具

```
scripts/stress/ui_stress.py    # ADB 编排：12 个场景 + 每场景前干净态重置 + logcat 抓取
scripts/stress/analyze.py      # 从 logcat 重建 pause 生命周期，判定重复拦截 / 窗口泄漏
scripts/stress/uinav.py        # Compose 界面的 dump-and-tap 助手（无稳定 resource-id 时的兜底）
scripts/stress/evidence/       # 每次运行的 logcat.txt + actions.log + 截图（gitignored）
```

用法：

```bash
ADB=/path/to/adb.exe python scripts/stress/ui_stress.py --scenarios S1,S2 --cooldown 20
python scripts/stress/analyze.py            # 默认分析最新一次运行
```

---

## 11. 修复状态（2026-09-17）

第 5 节的 root cause 已被修复，方案即第 9 节的组合 A + 部分 D：

- `OverlayManager` 新增静态 `overlayCanBeHiddenByTarget`（**仅 2038 fallback 为 true**，2032 恒 false；
  addView 失败、回退 PauseActivity、dismiss 三处均复位）。
- `PauseGuardPolicy.evaluate` 判断顺序改为：
  `pauseActivityVisible -> KEEP` / `overlayAttached && !hideable -> KEEP` / `elapsedMs > maxMs -> RELEASE_MAX` / …
- 效果：2032 只要还 attach 就永不释放 guard，因而杜绝「guard 说没有 pause、屏幕上却挂着 pause」；
  30 秒硬上限**只对可被 `setHideOverlayWindows` 隐藏的 2038 fallback 保留**，不回归 HyperOS 历史缺陷。

验证：`run-20260916-232249`、`run-20260916-232559`、`run-20260917-000356`（S17 近倒计时结束，60s 冷却）
均为 1 INTERCEPT / 1 overlay / 0 次 WATCHDOG_RELEASE，analyzer 报 clean。
对照修复前的 `run-20260916-120927`：3 次 WATCHDOG_RELEASE + DUPLICATE_INTERCEPT + OVERLAY_LEAK。

**真机 Xiaomi/HyperOS 当时未验证。**

---

## 12. 真机取证：用户实际遇到的是**另一条**路径（2026-09-17）

真机（2410DPN6CC / HyperOS / Android 16）`files/appause-service.log` 是**跨进程持久化日志**，
保留了 08-29 → 09-17 共 1976 行真实使用记录。从中得到：

### 12.1 真机配置与本文假设不符

真机唯一分组：`test`，**cooldown = 10 秒**，目标为 `com.xingin.xhs`（小红书）与 `tv.danmaku.bili`（B站）。

`PAUSE_GUARD_MAX_MS = 30_000`，而 cooldown 只有 10 秒 —— **倒计时会先结束，30 秒看门狗在真机配置下
根本没有机会触发**。本文第 5 节那条路径**不适用于真机**。

### 12.2 真机日志里重复拦截是常态

| 指标 | 值 |
|---|---|
| `Overlay shown` 总数 | 210（**全部 type=2032**） |
| 同一 App 在 10 秒冷却内被重复显示的簇 | **28 个** |
| 「倒计时未走完就重开」的次数 | **40 次** |
| 重开间隔 | min 1.0s / **median 3.0s** / max 9.0s |
| 非首次显示落在 10 秒窗口内的比例 | **100%** |

最严重的一次：

```
09-11 14:31:53  Overlay shown  com.xingin.xhs
09-11 14:31:57  Overlay shown  tv.danmaku.bili
09-11 14:32:06  Overlay shown  tv.danmaku.bili   +8.4s
09-11 14:32:08  Overlay shown  tv.danmaku.bili   +2.3s
09-11 14:32:10  Overlay shown  tv.danmaku.bili   +2.1s
09-11 14:32:39  Overlay shown  tv.danmaku.bili   +29s
09-11 14:32:41  Overlay shown  tv.danmaku.bili   +1.9s
09-11 14:32:43  Overlay shown  tv.danmaku.bili   +1.5s
09-11 14:32:45  Overlay shown  tv.danmaku.bili   +1.8s
09-11 14:32:46  Overlay shown  tv.danmaku.bili   +1.2s
```

**B站 在 7 秒内被弹了 5 次**，而冷却设定是 10 秒。这就是用户描述的「倒计时走到一半又重新开始」，
且**与 30 秒看门狗无关**。

### 12.3 为什么 PersistentLog 定不了因

它只记录 `Svc:` / `Overlay:` / `App:` 三类（1687 / 210 / 41 条），**没有 decider 的决策细节**——
看不到 `Cooldown abandoned (user left to X)`、`SKIP: …`、`Overlay dismissed` 这些关键行。
**定位这条路径必须抓真机 logcat。**

### 12.4 已发现的可疑干扰源

- 真机上 **Checky（`com.checky.app`）的 AccessibilityService 与 Appause 同时 enabled 且都在运行**
  （pid 11416 / 395）。Checky 是自动签到类应用，会主动切换前台 App —— 与历史上「两个 Appause 并存
  互相干扰」属同一类问题，**需优先排除**。
- 服务被反复重建：20 天内 `onCreate` **284 次**、`onDestroy` **251 次**（HyperOS 杀后台剧烈），
  进程内状态（bypass / guard）会随之丢失。
- 已排除：`OverlayManager` 里的 "DEBUG-ONLY auto-continue" 只有定义、无调用点（注释过时，死代码）。

### 12.5 待办

真机第二条路径**尚未定位**。需要真机 logcat 复现，并优先排除 Checky 的无障碍服务干扰。
真机此前装的是 9-13 旧构建，现已 `install -r` 更新为含上述修复的 debug 包（数据与授权保留）。

---

## 13. 第 12 节已定位：「切走再切回倒计时归零」——**这是有意设计，不是缺陷**

> **产品决定（用户，2026-09-17）：不修改。** 必须强制等待满 10 秒，不能让用户靠"切出去躲一躲"
> 绕过等待。所以"离开即重置"是**期望行为**。
>
> **后续任何人（包括 AI）都不要把这条当成 bug 去修。**

### 13.1 真实机制（已真机复现，无需 Home）

```
INTERCEPT: bili → cooldown=10s
Overlay shown for bili (type=2032)                  ← 倒计时开始
W Cooldown abandoned (user left to com.checky.app before continuing) — dismissing overlay
Overlay dismissed / Bypass cleared: tv.danmaku.bili
INTERCEPT: bili → cooldown=10s                      ← 切回来，重新拦截
Overlay shown for bili (type=2032)                  ← 倒计时从满值重开
```

触发条件：**暂停屏显示期间切到任意其他 App，再切回目标 App**。
`PreGroupDecision.AbandonCooldown` → dismiss overlay + clearBypass → 切回时既无 guard 也无 bypass → 新 INTERCEPT。

**冷却设成多少都会这样，与 30 秒看门狗无关。** 这正是用户报告的现象。

### 13.2 为什么它和「已点 Continue 后有 3 分钟宽限」不矛盾

两者语义不同，不是不一致：

| 场景 | 语义 | 行为 |
|---|---|---|
| 倒计时**尚未结束**就切走 | 用户**还没完成**这次强制等待 | 重置，回来重新等满 → **符合设计意图** |
| 已点 Continue（等待已履行）后切走 | 用户**已经守规矩**，短暂切出不应再罚 | 3 分钟 `RESUME` 宽限 |

### 13.3 30 秒看门狗缺陷的适用边界（供参考）

第 5 节那条缺陷**真实且已修**，但只在特定配置下才会暴露：

| `cooldownSeconds` | 是否触发 | 原因 |
|---|---|---|
| ≤ 30 | **不会** | 倒计时先走完 → `startBypass` 建立 bypass，兜住后续事件 |
| ≥ 31 | **会** | 倒计时仍在跑（无 bypass）、guard 已被看门狗释放 → 切回重复拦截 + 窗口叠加 |

判定是 `elapsedMs > 30000ms`，所以分界在 **31 秒**。用户真机冷却为 10 秒，**不会遇到**。
但冷却上限为 60 秒，任何把冷却调长的用户都会遇到——修复仍应保留并发布。

### 13.4 实测补充（2026-09-17，模拟器，修复前构建）

**触发不需要用户做任何操作**——目标 App 自己切换一个页面就够了。

实验：受控 App 为 Clock，cooldown 60 s；打开后**全程不碰设备、不切走、不按 Home**，只让 Clock
自己打开它自己的配置页（同包名的不同 Activity）。设备：`Medium_Phone` API 37，构建为 9-14 的修复前 APK。

```
247.025  INTERCEPT: com.google.android.deskclock → group=Stress, cooldown=60s
247.076  Overlay shown for com.google.android.deskclock (type=2032)      ← 窗口 #1
263.985  Event: deskclock class=...AnalogAppWidgetConfigActivity
264.000  SKIP: cooldown overlay is showing (com.google.android.deskclock) ← guard 有效，正常挡下
278.170  W Pause guard watchdog: exceeded max hold — releasing guard      ← 窗口 #1 仍 attach
300.192  Event: deskclock class=...DigitalAppWidgetConfigActivity         ← 用户从未离开 Clock
300.480  INTERCEPT: com.google.android.deskclock → group=Stress, cooldown=60s  ← 第二次拦截
300.513  Overlay shown for com.google.android.deskclock (type=2032)       ← 窗口 #2 叠加
```

两次 `Overlay shown` 之间**没有 `Overlay dismissed`** → 窗口泄漏同时确认。

**结论修正**：第 13.3 节的第 3 个条件（"期间发生一次前台事件"）**不需要用户参与**。
只要目标 App 自己产生任何 window 变化（切页、加载、广告刷新、内容滚动导致的 Activity 变更），
就会触发。对于内容持续变化的 App（B站/小红书/抖音等），这意味着**冷却设为 31 秒以上时基本必然发生**。

证据：`scripts/stress/evidence/emulator/self-event3.log`。



