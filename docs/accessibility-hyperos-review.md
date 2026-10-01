# Appause Accessibility / HyperOS 后台失效：独立技术评审

日期：2026-09-29
设备：Xiaomi 2410DPN6CC · Android 16 · HyperOS OS3.0.308.0.WOBCNXM
评审范围：Q1–Q6、现有结论复判、"为什么最近更频繁"、可行方案排序

本文不假设现有结论成立。每条结论标注证据级别：
`[证明]` = 有官方文档 / AOSP 源码 / 可复现实测 ` [强证据]` = 多源一致但无一手源码
`[推断]` = 推理，需实测确认 ` [否]` = 已排除

---

## 0. TL;DR

1. **`SwipeUpClean` 是小米官方定义的用户主动清理行为，且在 HyperOS 上以 force-stop 形式实现。** 小米开发者文档把它与 `OneKeyClean / ForceClean / GarbageClean / LockScreenClean / GameClean / OptimizationClean` 并列 —— 也就是说，**"最近任务上划"只是 6 个清理入口之一，另外 5 个（尤其"锁屏清理"）完全绕开 Recents。** `excludeFromRecents` 最多只能堵住其中一个入口。 `[证明]`
2. **force-stop 之后，AOSP 会主动把你的无障碍服务从 `enabled_accessibility_services` 里删除并持久化**（`AccessibilityManagerService.onPackagesForceStoppedLocked`，android-16.0.0_r4）。所以"无障碍开关看起来还在 / 还在但没绑上"其实是**两类不同的故障**，必须分开诊断、分开给恢复指引。 `[证明]`
3. **force-stop 后不存在任何无特权自恢复路径**，这是 Android 安全模型的硬边界，不是实现问题。但"只是进程被杀 / 自启动被拦"这一类是**可以自愈的**（系统会自动重绑）。 **你现在最该做的一件事，是把这两类区分开。** `[证明]`
4. **`FGS 没用` 这条判断是对的，而且现在有机制解释**：系统绑定无障碍服务时已经带了 `BIND_FOREGROUND_SERVICE_WHILE_AWAKE`，你早就"白嫖"了 FGS 级的进程优先级；自建 FGS 唯一的新增效果，是给用户在 Android 13+ 任务管理器里多了一个"停止"按钮。 `[证明]`
5. **`excludeFromRecents` 不是"更恶意"的方案，但也不是"解决方案"**。它从产品上看基本正常（有图标、有无障碍开关、有 `disableSelf()`），我倾向保留；但它对 6 个清理入口只覆盖 1 个。**真正缺的是"让失败变响亮"，而不是"让进程不死"。** `[推断]`
6. **"最近更频繁"最值得先查的不是 Security Center，而是：锁屏清理 / 神隐模式 / 一键清理 / 是否刚升到 HyperOS 3。** 这几个都能在用户完全没碰 Recents 的情况下 force-stop Appause。现有证据链里，"近期更常打开 Appause"和"Security Center 更新"都只是相关性。 `[推断·可证伪]`

---

## 1. Q1：SwipeUpClean 到底是不是 force-stop？

### 1.1 小米官方口径（一手）

小米开发者平台《MIUI 进程管理适配说明》(dev.mi.com/distribute/doc/details?pId=1607) 明确列出：

| 名称 | 触发入口 | Reason |
|---|---|---|
| 一键清理 | 最近任务 / 悬浮球 | `OneKeyClean` |
| 强力清理 | 负一屏 | `ForceClean` |
| 垃圾清理 | 安全中心 | `GarbageClean` |
| 锁屏清理 | 安全中心 | `LockScreenClean` |
| 游戏清理 | 安全中心 | `GameClean` |
| 优化清理 | 安全中心 | `OptimizationClean` |
| **上滑清理** | **最近任务** | **`SwipeUpClean`** |
| Power 异常查杀 | 被动（过度耗电） | `AutoPowerKill` |
| Thermal 异常查杀 | 被动（发热） | `AutoThermalKill` |

官方文档给的自查命令就是你已经在用的：
```
adb logcat -b events | grep am_kill
# [0,5253,com.xxx,500,LockScreenClean]   最后一列即 Reason
```

官方对被用户主动杀死后如何自启的回答只有一句：**引导用户在安全中心打开自启动开关。** 官方完全没有承诺"能保住无障碍授权"。 `[证明]`

### 1.2 HyperOS 在部分事件上是硬编码 force-stop（一手 logcat）

开源项目 `hyperos-accessibility-fix`（Magisk/KernelSU 模块，作者为解决这个问题逆向了 HyperOS）记录了：

```
<ultra battery saver was enabled>
I ActivityManager: Force stopping com.urbandroid.lux appid=10415 user=0: LockScreenClean
D ActivityManager: Force removing proc 8855:com.urbandroid.lux:background/u0a415
```

即 **HyperOS 会在某些系统事件上调用 `ActivityManager` 的 force-stop（不是 kill）**，Reason 用的是上面那张官方表里的名字。这与你抓到的 `USER REQUESTED → FORCE STOP → SwipeUpClean` 完全同构。 `[证明]`

### 1.3 结论

**Q1 答案：是的，HyperOS 的 SwipeUpClean 走的是 package force-stop 语义（不是 AOSP 的 `removeTask`），这是小米自有实现，不是 AOSP 行为。** `[证明]`

推论（重要）：小米的清理是 **package 维度**的，不是 task 维度的。所以任何"我让自己的 task 更干净"的思路（单独进程、`stopWithTask`、finish activity）在 force-stop 面前都无效。

---

## 2. 决定一切的分叉：force-stop 还是"只杀进程"

这是我认为你目前**最该先做但还没做**的实验。两条路径的后果完全相反，而你现在把它们的表现混在一起描述了。

### 2.1 AOSP 源码层面的差异

**路径 A —— 进程被杀（crash / LMK / `am kill` / task 被移除）**
`AccessibilityServiceConnection.bindLocked()` 用的是：
```java
int flags = Context.BIND_AUTO_CREATE
          | Context.BIND_FOREGROUND_SERVICE_WHILE_AWAKE
          | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS
          | Context.BIND_INCLUDE_CAPABILITIES;
mContext.bindServiceAsUser(mIntent, this, flags, new UserHandle(userState.mUserId));
```
`BIND_AUTO_CREATE` 意味着 **服务所在进程异常死亡后系统会重新拉起并重绑**（走 `ActiveServices.scheduleServiceRestartLocked`）。代价：重启有退避，**首次约 1s，之后成倍增长**（1s / 4s / 8s …）；次数也有限制。这正好解释你观察到的">40 秒不恢复"。 `[证明]`

注意 `BIND_FOREGROUND_SERVICE_WHILE_AWAKE`：**系统已经替你把无障碍进程顶到了 FGS 级优先级。** 这就是"FGS 没用"的根本原因 —— 你加的那个 FGS 是重复的。

**路径 B —— package 被 force-stop**
`AccessibilityManagerService.onPackagesForceStoppedLocked()`（android-16.0.0_r4，约 L1050）：
```java
boolean onPackagesForceStoppedLocked(String[] packages, AccessibilityUserState userState) {
    final Set packageSet = new HashSet(List.of(packages));
    final ArrayList continuousServices = new ArrayList(
        userState.mInstalledServices.stream()
            .filter(service -> (service.flags & FLAG_REQUEST_ACCESSIBILITY_BUTTON) == FLAG_REQUEST_ACCESSIBILITY_BUTTON)
            .map(AccessibilityServiceInfo::getComponentName).toList());
    continuousServices.removeIf(name -> !packageSet.contains(name.getPackageName()));

    boolean enabledServicesChanged = false;
    final Iterator it = userState.mEnabledServices.iterator();
    while (it.hasNext()) {
        final ComponentName comp = it.next();
        if (packageSet.contains(comp.getPackageName())) {
            it.remove();
            userState.getBindingServicesLocked().remove(comp);
            userState.getCrashedServicesLocked().remove(comp);
            enabledServicesChanged = true;
        }
    }
    if (enabledServicesChanged) {
        persistComponentNamesToSettingLocked(
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            userState.mEnabledServices, userState.mUserId);   // ← 写回 Settings.Secure，持久化
    }
    // continuousServices 只被用来清理"无障碍按钮"快捷方式目标，不会被重新加回 enabled 列表
    ...
}
```
**要点：**
- 被 force-stop 的包，其无障碍服务**会被从启用列表里摘掉并写回 `Settings.Secure`**。这就是你看到的"Accessibility 被移出 enabled list"。 `[证明]`
- 顺带排掉一个诱人的想法：**`flagRequestAccessibilityButton` 并不能保命**。代码里 `continuousServices` 只用于清理快捷方式目标，enabled 列表照样被 remove。 `[证明]`
- 因为写进了 `Settings.Secure`，**重启手机也不会恢复**。`stopped` 状态同样持久化。**你现在的恢复文案里"若仍无法恢复，再重启手机"对这一类无效。** `[证明]`

### 2.2 验证矩阵（建议 30 分钟内跑完）

```bash
PKG=com.appause.android
snap() {
  echo "--- enabled list:"; adb shell settings get secure enabled_accessibility_services
  echo "--- package state:"; adb shell dumpsys package $PKG | grep -iE "stopped=|User 0:"
  echo "--- a11y bound:";    adb shell dumpsys accessibility | grep -iE "bound services|services:"
  echo "--- events:";        adb shell logcat -b events -d | grep -iE "am_kill|am_force_stop|Force stopping" | tail -5
  echo "--- proc:";          adb shell ps -A | grep -i appause
}

# 基线
snap

# 对照 1：纯杀进程（不 force-stop，不进 stopped 态）→ 预期：几秒内自动重绑
adb shell am kill $PKG; sleep 8; snap

# 对照 2：Android 13+ "用户停止"（stop-app）→ 预期：进程没了，但 job 继续、不进 stopped 态
adb shell cmd activity stop-app $PKG; sleep 8; snap

# 对照 3：真正的 force-stop → 预期：enabled 列表里 Appause 消失
adb shell am force-stop $PKG; sleep 8; snap

# 实验：最近任务上划 Appause（无 excludeFromRecents 的构建）
sleep 8; snap
```

**判定规则：**

| 观察 | 判定 | 含义 |
|---|---|---|
| `am kill` 后 8s 内自动重绑，上划后不重绑 | 上划 = force-stop | **硬边界**，只能在"减少触发 + 失败可见"上做文章 |
| 上划后 Appause 仍在 enabled list，但 `Bound services:{}` 且长期不恢复 | 上划 = 杀进程，但**自启动被 MIUI 拦** | 这是**可改善**的：Autostart / 省电策略 / 神隐 / 进程结构是有效杠杆 |
| 两者都导致 enabled list 消失 | force-stop | 同上（硬边界） |

补一组入口矩阵（每个入口单独跑 `snap`）：
`最近任务上划` `最近任务一键清理` `安全中心→垃圾清理` `安全中心→优化清理` `锁屏 5 分钟后` `进入省电/超级省电` `负一屏强力清理`。

**`锁屏 5 分钟后` 这一项是我最希望你先跑的**（见第 7 节）。

---

## 3. Q2：为什么"设置里 enabled，但 Bound services 为空"？

按可能性排序，每一个都有对应的可验证信号：

1. **服务在 `crashedServices` 黑名单里。** AOSP 对"短时间内反复崩溃"的无障碍服务会加黑名单，用户重新开关才会移除（AOSP commit "Allow restarting of crashed a11y services"）。表现为开关开着但不绑。→ 查 `logcat` 里有没有 `Scheduling restart of crashed service`。 `[强证据]`
2. **MIUI 把"系统帮你重绑无障碍"当成了自启动并拦截。** 这条与你"已开自启动"矛盾，但要注意：MIUI 的自启动白名单是**分场景**的（自启动 / 关联启动 / 后台运行），且 Security Center 更新可能会重置其中某一项。 `[推断]`
3. **包处于 stopped 态。** 系统 bind 失败或 bind 后立即被杀。→ `dumpsys package | grep stopped` 直接可判。 `[证明（机制）]`
4. **进程存在但服务还没 bind 完**（`binding` 中间态），几秒后自愈。→ 这是正常的，别当成故障报警。 `[证明]`
5. **`AccessibilityServiceInfo.crashed` 字段**：确认是 `@hide`，第三方 App 读不到，别在这条路上花时间。 `[证明]`
6. **AOSP 9–11 的老 bug**（force-stop 后重开，开关是 on 但显示 "Not working. Tap for info."）：Google 已修，Android 12+ 设备不应复现；你的设备是 16，基本可排除。 `[强证据]`
7. **UiAutomation 抢占**：AOSP 里 UiAutomation 注册后会压过其它无障碍服务，所以 Mobile MCP 一定会污染测试。你们的结论正确，继续用 adb-only。 `[强证据]`

**对你现有代码的评价**：`AccessibilityServiceChecker` 读 `Settings.Secure` 是对的选择（它就是 AOSP 的唯一真相源）；`AccessibilityHealth` 把 "enabled" 和 "connected" 分开也是对的。缺的是：**没有区分"还在列表里但没绑"和"已经不在列表里"** —— 这两类的恢复路径完全不同（前者：开关 off→on；后者：必须重新启用，重启无效）。

---

## 4. Q3：force-stop 后有没有合法的检测 / 恢复路径？

### 4.1 硬边界（不用 root / Shizuku / WRITE_SECURE_SETTINGS / Device Owner 的前提下）

**没有。而且不是"难"，是设计上就不允许：**

- **stopped 态**：force-stop 后包进入 stopped 态，隐式广播、alarm、job 全部不投递/被取消；`FLAG_INCLUDE_STOPPED_PACKAGES` 只能由**别的 App** 显式发给你才有用，你自己发不了。 `[证明]`
- **授权被删**：就算别的 App 用显式 intent 把你"唤醒"、清掉 stopped 态，`enabled_accessibility_services` 里也已经没有你了 —— 系统不会再绑。而 `Settings.Secure` 的写权限是 `signature|privileged|development`，第三方拿不到。 `[证明]`
- **stopped 态跨重启保留**，所以重启也不是出路。 `[强证据]`
- **进程死了就什么都做不了**：通知、widget、TileService 全都依赖你的进程。**"force-stop 期间 Appause 无法发出任何信号"是构造性的，不是 bug。** 这一点决定了产品策略只能是"预防 + 下一次打开时大声说"。

第三方旁证（一个开源 blocker 项目的官方 troubleshooting，措辞几乎一致）：
> "The one thing no app can survive: a real Force stop … Android cancels every alarm and blocks every broadcast to a force-stopped app until you open it again by hand — this holds even with Always-on VPN, which we verified."

### 4.2 但"检测"是可以做到的，而且很便宜

**`ActivityManager.getHistoricalProcessExitReasons(null, 0, N)`**：查自己包的历史退出原因，**不需要任何权限**（查别人的包才需要 `DUMP`）。返回 `ApplicationExitInfo`，含 `reason` / `subReason` / `description` / `importance` / `timestamp`。

你可以在 Appause 每次启动时做一次分类：

| 命中 | 判定 | 该显示的指引 |
|---|---|---|
| `REASON_USER_REQUESTED` + description 含 `SwipeUpClean` | 最近任务上划 → force-stop | "你在最近任务里划掉了 Appause，系统会把它彻底停掉（包括无障碍授权）。请把 Appause 加进后台锁定，或干脆不要清它。" |
| description 含 `LockScreenClean` | 锁屏清理 | "系统在你锁屏后清理了 Appause。请到 设置→省电与电池→锁屏后清理内存 设为从不。" |
| `REASON_LOW_MEMORY` | 内存回收 | 正常，说明 autostart 生效/不生效，可提示省电策略 |
| `REASON_USER_REQUESTED`（无 MIUI 关键字） | 用户手动 force-stop | 直接给无障碍设置深链 |

这不是可选项，是**当前状态下唯一能把"静默失败"变成"可解释失败"的手段**，而且零权限、零依赖。注意 `description` 官方标注"仅供调试"，所以只在本地做关键字匹配、不外发、不展示原文（符合你们"不加埋点"的既有约定）。

### 4.3 恢复路径（用户侧，按成本排序）

1. Android 13+：`Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS` + 组件名 extra，**直接跳到 Appause 这一条服务的详情页**（自带开关和"未正常运行"提示），比跳无障碍总列表少 3 次点击。上线前在真机验证 extra 名。 `[推断·需真机确认]`
2. 兜底：`Settings.ACTION_ACCESSIBILITY_SETTINGS`。
3. 小米自启动：`miui.intent.action.OP_AUTO_START`（或 `com.miui.securitycenter` → `com.miui.permcenter.autostart.AutoStartManagementActivity`）—— 你现在用的是 `AutoStartManagementActivity`，建议**两条都试，失败再退到 `ACTION_APPLICATION_DETAILS_SETTINGS`**（你已有这个 fallback，good）。
4. **多任务加锁**：没有可编程入口，只能图文引导。

---

## 5. Q4：成熟 App 怎么做？

| App / 类别 | 策略 | 对我们的启示 |
|---|---|---|
| **MacroDroid**（自动化，强依赖 a11y） | 官方 Troubleshooting 页明确列出：排除电池优化 → 厂商电池管理（指向 dontkillmyapp）→ **小米必须开自启动** → 保持常驻通知 → 内置"Issues Identified"自动扫描（含"a11y 服务被禁用或异常"）并给一键跳转 | **"自动扫描 + 一键跳到对应设置页"是成熟 App 的通用做法**，Appause 的 health 检测已经走在这条路上，还差"跳转"和"分类" |
| **Tasker** | 同样依赖 dontkillmyapp + 自启动 + 电池无限制；不试图对抗 force-stop | 一致 |
| **Bitwarden / 密码管理器** | 什么都不做。因为它们的失败是**即时可见的**（自动填充失败），不需要保活 | **可见性可以替代保活**：Appause 的失败天然静默，所以必须额外造可见性 |
| **本地 VPN 型 blocker**（Opal / BlockSite / Freedom 一类） | 用 VPNService + FGS + **看门狗 alarm（约 5 分钟一轮）** + 建议开 Always-on VPN | 它们的自愈能力来自"VPN 能被 App 自己重启"，而**无障碍授权不行**。看门狗对 Appause 无效 |
| **Always-on VPN + Block connections without VPN** | 一旦 App 死了，**手机断网** —— 把静默失败变成响亮失败 | **这是最值得抄的一招**（见第 9 节） |
| **Lock Me Out / AppBlock 类** | 用 Device Admin / Device Owner 防卸载与防绕过，**不防杀进程** | 与保活无关，别混淆 |
| 中文 a11y 生态（GKD / 李跳跳一类） | 通用结论：**MIUI/HyperOS 必须"省电策略=无限制" + "多任务界面加锁"**；且普遍反馈"通知使用权和 TileService 的拉起比无障碍更不受限" | 佐证"加锁"是小米生态的标准答案；TileService 值得作为状态入口 |

**一句话总结业界做法：没有人解决 force-stop，所有人都在做"减少触发 + 让失败立刻可见 + 一键跳到正确的设置页"。**

---

## 6. Q5：excludeFromRecents 评估

### 6.1 现状

`app/src/main/AndroidManifest.xml`（工作区未提交）已经给 `MainActivity` 和 `PauseActivity` 都加了 `android:excludeFromRecents="true"`，服务上也已经有 `android:stopWithTask="false"`；`strings.xml` 的 `service_help_body` 也已经写了 "Appause is kept out of Recents"。

### 6.2 有效性

| 场景 | 是否防住 | 证据 |
|---|---|---|
| 用户在 Recents 上划 Appause 卡片 | **有效**（没有卡片可划） | 机制确定 |
| 用户"一键清理"（OneKeyClean） | **不确定，必须实测** | 小米清理是 package 维度；很可能枚举的是"可清理进程/最近使用列表"而非 Recents UI。`[推断]` |
| 锁屏清理 / 垃圾清理 / 优化清理 / 负一屏强力清理 | **无效** | 入口不在 Recents `[推断]` |
| 内存压力 / LMK / 发热 | **无效** | 与 Recents 无关 |
| 用户去"设置→应用→强制停止" | **无效**（也不该防） | — |

**所以：`excludeFromRecents` 是一个有价值的窄口径修复，不是根因修复。** 如果你们做完它发现"还是偶尔掉线"，那不是它没生效，而是其它 5 个入口在起作用。

### 6.3 副作用清单（逐条检查过）

- **从 launcher 图标正常打开**：不受影响。 ✅
- **Deep link / 通知 / `startActivity`**：不受影响（只影响 Recents 卡片展示）。 ✅
- **任务恢复**：用户不能从 Recents 回到上次页面，只能从图标重进 —— 对 Appause 这种单页 App 影响极小。 ✅
- **不能彻底移除**：`excludeFromRecents` 只对**任务栈的根 Activity** 生效，栈内其它 Activity 会跟着隐藏；你们两个 Activity 都标了，OK。 ✅
- **`AppTask.setExcludeFromRecents(false)`（API 21+）可以在运行时反过来把它加回 Recents** —— 这给你们要的"显式、可逆"提供了一条真实可行的路：默认排除，用户在设置里可以"在最近任务中显示 Appause"，App 在 Activity 活着的时候调用一次即可（注意每次新建任务都要重新调用）。 `[推断·需真机验证]`
- **Play / 信任**：`excludeFromRecents` 本身不违反 Play 政策（不是隐藏图标、不是 Device Owner、不是诱导卸载）。触发政策的红线是"**让用户无法停止**"。所以配套必须有：① 无障碍设置里的开关（系统自带）；② App 内显眼的"停止保护"→`disableSelf()`；③ 诚实文案。 ✅（前提：这三件事都做）

### 6.4 产品判断

我**不同意**"它很像恶意软件"这个判断的前提。恶意软件的判定标准是**隐藏 + 无法停止 + 用户不知情**。Appause 三条都不沾（有图标、MIT 开源、无障碍设置里有开关、App 内有停止入口）。Recents 卡片只是 Android 的一种"最近用过"的便捷入口，不是"进程管理器"，把常驻服务型 App 从 Recents 里摘掉是正常的 Android 做法（密码管理器、自动化工具、无障碍工具普遍如此）。

**真正让人不安的是"我划掉了它但它还在跑"——而这恰恰是 `excludeFromRecents` 消除掉的那个误解。**

---

## 7. "为什么最近更频繁"：候选解释排序

按我认为的先验概率排序，每条都给了可证伪的检查方法：

### H1（最高）：锁屏清理 / 神隐模式被打开或被更新改回默认 `[推断·高价值]`
MIUI 的 `LockScreenClean` 是官方列出的清理理由之一，且 HyperOS 有"进入超级省电时对所有包 force-stop"的硬编码行为（第 1.2 节 logcat 实证）。**这类清理不需要用户碰 Recents**，表现为"莫名其妙就失效了"。
- 检查：`设置 → 省电与电池 → 右上角设置 → 锁屏后清理内存`（设为"从不"）；`设置 → 省电与电池 → 神隐模式`（关掉，或把 Appause 设为"无限制"）；开着 `adb logcat -b events | grep am_kill` 锁屏 10 分钟看有没有 `LockScreenClean`。
- **注意：Security Center 更新（13:39）完全可能重置/改变这类开关的默认值** —— 这比"更新改了清理策略"更常见，也更容易验证。

### H2：刚升到 HyperOS 3 / Android 16 `[推断·可证伪]`
设备是 `OS3.0.308.0`（HyperOS 3 = Android 16）。如果这次大版本升级发生在最近一两周，"一个月稳定 / 最近不稳"就完全说得通 —— 大版本升级会重置后台策略、无障碍状态、电量策略，且 Android 16 的 `AccessibilityManagerService` 本身也有重构（我在源码里看到 A16 新增了 `Flags.packageMonitorDedicatedThread()` 之类的改动）。
- 检查：`adb shell getprop ro.build.date.utc`、`ro.build.version.incremental`、`ro.miui.ui.version.name`；再看设置里的"系统更新"记录时间。

### H3：行为暴露 `[推断]`
最近开发测试多，Appause 频繁出现在 Recents，于是更容易被顺手划掉。**这条在加上 `excludeFromRecents` 之后自动消失**，可以用"上线 excludeFromRecents 后两周内是否还有同类故障"来反向验证。

### H4：Security Center 12.8.7 更新改变了清理策略 `[相关性，无证据]`
时间上接近但无法证明。可以先做 H1/H2 的检查（成本低得多），H4 作为兜底假设保留。

### H5：不要再考虑
`v0.5.40` vs `v0.5.45` 的 regression —— 你们的 A/B 已经排掉了"上划可被杀"这一点。但补一句：**你们的 A/B 设计有个漏洞** —— 你们测的是"v0.5.40（无 excludeFromRecents）"和"当前 main（有 excludeFromRecents）"，两者对同一个操作的表现本来就应该不同。**真正该对比的是"当前 main 有 excludeFromRecents vs 当前 main 无 excludeFromRecents"**，否则无法量化这个 mitigation 的实际收益。 `[推断]`

---

## 8. Q6：替代方案逐条判决

| 方案 | 判决 | 理由 |
|---|---|---|
| `android:persistent="true"` | ❌ 硬边界 | 仅系统签名/特权 App 可用 |
| **自建 Foreground Service** | ❌ 无增益（甚至负收益） | 系统绑定已带 `BIND_FOREGROUND_SERVICE_WHILE_AWAKE`；自建 FGS 只在 Android 13+ 任务管理器里给用户多一个"停止"按钮 |
| `android:stopWithTask="false"` | ⭕ 保持，但**不是修复**（默认就是 false） | 只对 AOSP 的"移除任务"有效，对 force-stop 无效。你们已经写了，留着无害 |
| 无障碍服务放独立进程 `:a11y` | ⭕ 边际 | 同上：只在 AOSP 的 task 移除路径有用；MIUI 是 package 维度全杀。代价是多一个进程的内存和跨进程状态复杂度 |
| 双 App / 双进程看门狗 | ❌ | 技术上确实能用"另一个 App 显式 intent"清掉 stopped 态，但**清掉之后无障碍授权依然没了**，目的达不成；还踩 Play 的互拉保活红线 |
| WorkManager / AlarmManager 看门狗 | ❌ 对 force-stop 无效；⭕ 对"仅杀进程"类有效 | force-stop 会取消 alarm/job。而"仅杀进程"类系统本来就会自动重绑，看门狗是多余的 |
| `BOOT_COMPLETED` | ❌ | stopped 态和 enabled 列表的删除都跨重启保留 |
| Task locking（小米"后台加锁"） | ✅ **推荐（用户侧）** | 小米生态的标准答案，官方社区/dontkillmyapp/MacroDroid 一致推荐；**用户可见、可逆、OEM 认可**；缺点是 App 无法代劳，且需确认它对单个上划是否也生效 |
| `NotificationListenerService` | ⭕ 仅作辅助 | 业界反馈它比无障碍更抗杀，但它不能替代前台 App 检测 |
| **TileService（快捷设置磁贴）** | ✅ **推荐（产品侧）** | 用户可见的状态入口 + 一键修复/停止；是"我还在跑，点这里停我"的正统 Android 表达 |
| **常驻状态通知（带"停止保护"动作）** | ✅ **推荐（产品侧）** | 把 excludeFromRecents 带来的"看不见"补偿回来；也是 Android 对后台服务的标准表达方式 |
| `VpnService` 改架构 | ❌ 不建议 | 拿不到前台 App（VPN 看不到是哪个 App 在联网的可靠映射），且 force-stop 一样杀；还带来耗电和隐私争议 |
| **"Always-on"式响亮失败** | ✅ **推荐（架构思路）** | 抄 VPN blocker 的思路：让"保护失效"这件事在用户下一次能感知的地方**足够响**，而不是追求"永不失效" |
| Device Admin / Device Owner | ❌ | 只防卸载/防绕过，不防杀进程 |
| Shizuku / root / WRITE_SECURE_SETTINGS | ❌ | 已排除，且会毁掉"零权限"的产品定位 |
| 无障碍按钮 `flagRequestAccessibilityButton` | ❌ | AOSP 源码证实：enabled 列表照样被 remove（第 2.1 节） |

---

## 9. 比 excludeFromRecents 更符合 Android 常规的方案（推荐组合）

核心思路转换：**不要试图"让进程永远不死"（在 HyperOS 上做不到），而是"让进程更难被杀 + 让失效一定被看见 + 让恢复只要两下点击"。**

### A. 保留 `excludeFromRecents`，但把它变成"常规"而不是"偷偷"

对 Appause 这种"长期运行的系统绑定服务 + 短暂 UI"的形态，Recents 里没有卡片本身是正常的。把它变常规只需要三件配套：

1. **常驻状态通知**：`Appause 正在保护 3 个应用` + 动作按钮 `暂停保护` / `打开 Appause`。这是 Android 对"我在后台跑"的标准表达（Tasker、MacroDroid、所有 VPN 都这么做），正好抵消"看不见"的不安。建议做成可关闭（设置项），但默认开。
2. **App 内显眼的"停止保护"** → `AccessibilityService.disableSelf()`。你们已经在构思里有，务必落地。
3. **诚实文案**：明确写"Appause 不显示在最近任务里，这是为了避免被系统清理；你可以在 设置 → 无障碍 里随时关掉它，也可以在 Appause 里点『停止保护』"。**现在 `service_help_body` 里那句 "Appause is kept out of Recents to reduce accidental cleanup" 方向对，但没给出"怎么停"，建议补。**

### B. 把"用户侧防护"做成首次引导的一部分（仅 Xiaomi）

按顺序引导，每步都能一键跳转：
1. 无障碍（必须）
2. **电池 → 无限制**（必须）
3. **自启动**（必须）
4. **多任务加锁 / 安全中心→加速→设置→锁定应用**（强烈建议）—— 图文引导，无 intent 可用
5. **锁屏后清理内存 = 从不**（强烈建议）
6. **神隐模式 = Appause 无限制**（建议）

其中 **4/5/6 是目前完全没做的三件事**，而它们对应的恰恰是"用户没碰 Recents 也会掉线"的那几个入口。**我认为这三件事的收益大概率高于 `excludeFromRecents`。**

### C. 让失效变响亮（最高性价比）

1. 启动时 `getHistoricalProcessExitReasons(null, 0, 1)` 分类上次死因 → 显示**具体的**原因和**对应的那一个**修复动作（第 4.2 节的表）。
2. 健康状态三态化：`HEALTHY` / `ENABLED_BUT_UNBOUND`（引导开关 off→on）/ `NOT_ENABLED`（引导重新启用，**并明确说重启手机没用**）。
3. 修复 `service_not_connected_desc` 里"再重启手机"这句 —— 对"已被移出 enabled 列表"这一类无效，会浪费用户一次操作并损害信任。
4. Android 13+ 用 `ACTION_ACCESSIBILITY_DETAILS_SETTINGS` 直接跳到 Appause 那一条（真机验证后上线）。
5. 可选：Quick Settings 磁贴显示状态（进阶项）。

### D. 明确不做的事

- 不做任何"偷偷自启"：双 App 互拉、alarm 自拉、native 保活。这与产品定位（MIT、零埋点、不联网）冲突，且在 force-stop 面前全部无效。
- 不引入 root / Shizuku / Device Owner。

---

## 10. 硬边界 vs 还能改善（回答你的最后一个问题）

### 硬边界（Android 安全模型层面，无特权 App 无法突破）

1. **package 被 force-stop 后，无障碍授权会被 AOSP 主动删除并持久化** —— 有源码。
2. **force-stop 后 stopped 态下：广播、alarm、job 全部失效**，跨重启保留 —— 因此**不存在可靠自恢复**。
3. **进程死亡期间无法发出任何用户可见信号**（通知、widget、磁贴全都依赖进程）。"静默失败"是构造性的。
4. `android:persistent`、写 `Settings.Secure`、自启无障碍 —— 全部需要系统级权限。

### 还能通过正确实现改善的

1. **降低触发概率**：锁屏清理 / 神隐 / 一键清理 / 省电策略 / 多任务加锁 —— 这些是可控的用户侧设置，目前只覆盖了其中一部分。
2. **消除"上划"入口**：`excludeFromRecents`（已做，收益待量化）。
3. **把静默失败变成可解释失败**：`ApplicationExitInfo` 分类 + 三态健康 + 深链跳转 —— **当前最大的空白，成本最低**。
4. **缩短恢复时间**：从"用户自己找设置"缩短到"两下点击"。
5. **正确的期望管理**：在文案里直接告诉小米用户"清后台会让它停，这是系统行为"，比事后道歉有效得多。
6. **区分两类故障**（enabled 但未绑 vs 已从列表移除）—— 这是所有后续正确行为的前提。

---

## 11. 建议的落地顺序

| 优先级 | 事项 | 成本 |
|---|---|---|
| P0 | 跑第 2.2 节的验证矩阵，确定设备上 SwipeUpClean 到底是 force-stop 还是杀进程 | 30 分钟 adb |
| P0 | 跑第 7 节的 H1 检查（锁屏清理 / 神隐 / 超级省电） | 15 分钟 |
| P0 | 修正 `service_not_connected_desc` 的"重启手机"文案；健康状态拆成三态 | 小 |
| P1 | 接入 `getHistoricalProcessExitReasons` 分类 + 对应指引 | 中 |
| P1 | Xiaomi 引导补齐"多任务加锁 / 锁屏清理 / 神隐模式"三项 | 中 |
| P1 | 常驻状态通知 + "停止保护"动作 | 中 |
| P2 | 保留并验证 `excludeFromRecents`（补：A/B 对比同一构建的开关版本） | 小 |
| P2 | `ACTION_ACCESSIBILITY_DETAILS_SETTINGS` 深链（先真机验证） | 小 |
| P3 | Quick Settings 磁贴 | 中 |
| — | **不做**：FGS、双 App 看门狗、VPN 改架构、Device Owner、Shizuku | — |

---

## 12. 参考来源

- 小米开发者平台《MIUI 进程管理适配说明》— 官方清理 Reason 表与自查命令：https://dev.mi.com/distribute/doc/details?pId=1607
- AOSP `AccessibilityManagerService.onPackagesForceStoppedLocked()`（android-16.0.0_r4，L1050 附近）：https://cs.android.com/android/platform/superproject/+/android-16.0.0_r4:frameworks/base/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java
- AOSP 同文件 `master` 分支全文（含 `PackageMonitor`、ContentObserver 触发链）：https://android.googlesource.com/platform/frameworks/base/+/master/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java
- `hyperos-accessibility-fix`（HyperOS 硬编码 force-stop 的 logcat 实证 + 上述源码片段）：https://github.com/chkndrp/hyperos-accessibility-fix
- `AccessibilityServiceConnection.bindLocked()` 的 bind flags（`BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE_WHILE_AWAKE | BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`）
- 无障碍服务保活机制分析（自动重绑 vs force-stop 移除、重启退避、TileService 更抗杀）：https://github.com/5ec1cff/my-notes/blob/master/keep-accessibility-service-alive.md
- `AccessibilityServiceInfo.crashed` 为 `@hide`（第三方不可用）；AOSP "crashed a11y services 黑名单" commit
- Android 官方文档：`adb shell cmd activity stop-app`、Task Manager 用户停止语义、REASON_USER_REQUESTED
- `ActivityManager.getHistoricalProcessExitReasons` 查询自身无需权限：CommonsWare / StackOverflow 74896445
- MacroDroid 官方 Troubleshooting（厂商电池管理 / MIUI 自启动 / 内置问题扫描）：https://wiki.macrodroid.com/wiki/index.php/Troubleshooting
- 本地 VPN 型 blocker 的 troubleshooting（force-stop 无法幸免、看门狗 alarm、Always-on VPN）：https://github.com/pratikkuikel/distraction-free/blob/main/docs/troubleshooting.md
- `<service android:stopWithTask>` 默认值为 `false`（官方文档）
- 小米自启动设置页 intent：`miui.intent.action.OP_AUTO_START` / `com.miui.permcenter.autostart.AutoStartManagementActivity`
- 未来风险（Android 16 Advanced Protection 可能限制非 `isAccessibilityTool` 的无障碍授权）：Google 官方博客 + Canary 2602 相关报道 —— 目前仅 Canary，暂不影响，但值得跟踪

## 13. 已完成的 Recents 上滑 A/B（2026-09-30）

在 Xiaomi 2410DPN6CC（HyperOS OS3.0.308.0.WOBCNXM，Android 16，序列号 `6036d5b`）上，对 Debug 包做了同一条件下的 Security Center「锁定应用」对照。A/B 临时设置 Debug `MainActivity` 的 `excludeFromRecents=false`，Release 包未改动：

| 锁定应用 | Recents 单卡上滑结果 |
|---|---|
| 关闭 | Debug 进程消失，`stopped=true`；`ApplicationExitInfo` 为 `USER_REQUESTED` / `FORCE_STOP`，描述为 `due to SwipeUpClean` |
| 开启 | 结果相同：进程消失，`stopped=true`；相同退出原因、子原因和描述 |

**结论仅限于这台设备、此系统版本、Debug 临时构建和 Recents 单卡上滑路径：锁定应用未阻止本次 `SwipeUpClean` force-stop。** 这不能证明该设置对其他清理入口或其他 Xiaomi 机型/版本都无效；本次没有测试安全中心其他清理、锁屏清理、省电模式等路径。不要向用户承诺 Autostart、电池策略或「锁定应用」能阻止 force-stop。

A/B 后已移除 Debug 临时 manifest 覆盖并恢复安装正常 `v0.5.45-debug`（`MainActivity excludeFromRecents=true`）；Release 始终未修改。Accessibility 的「已启用」只表示系统授权仍在；健康状态还会单独检查本进程是否连接。即使两者均正常，也不等同于端到端拦截已实测。