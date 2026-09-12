# Appause 工程审查 Backlog（Engineering Review）

> 来源：2026-09-06 的独立深度审查（外部 agent）+ 本地拦截链路审计
> （`docs/INTERCEPTION_PROTOCOL.md` §9），经用户与本地 agent 逐条核实后合并。
> 基线：`main` @ `0256cf5`（2026-09-12）。状态标记：`[ ] 待做` / `[~] 进行中` / `[x] 完成` /
> `[-] 不做`。每完成一项在本文件勾选并在 `PROGRESS.md` 记录。

## 结论摘要

- 成熟度判定：**usable（个人可用）**；v0.5.40 已有公开 Release。本文件继续
  记录工程风险，不把 backlog 评语当成发布状态：
  (a) 无自动化测试门禁；(b) 暂停屏主路径/兜底路径行为已分叉；
  (c) Service 核心状态是伴生对象静态字段 + 有副作用的 getter。
- 项目最好的资产：`BurstTracker` / `InterceptionDecider` / `HomeTransitionPolicy`
  / `OverlayPresentationPolicy` / `TemporaryPassPolicy` 的纯函数化 + 注入时钟，
  以及 `LicenseVerifier`（自实现 RS256 验签、防 alg 混淆、失败一律 null）——
  **不要**换通用 JWT 库。

## P0 — 必须立即修

- [x] **P0-1 · 暂停屏两条路径数据源分叉**（2026-09-06 完成）
      `PauseActivity` 用已废弃的 `getLearningGroupPackageNames()`，overlay 主路径用
      `repository.recommendedApps`。修法：PauseActivity 改用 recommendedApps，
      删废弃方法 + DAO 查询；`TYPE_LEARNING` 在 `findGroupForPackage` 的
      "学习组不拦截"用途**保留**。
- [x] **P0-2 · `_sortedGroups` 手工快照可能写空**（2026-09-06 完成）
      `HomeViewModel.loadAppCounts()` 读 `groups.value` 快照排序，上游
      `WhileSubscribed(5000)` 停止后 ON_RESUME 时快照可能为空且永不重算；
      空态判断（`sortedGroups`）与 FAB 限流（`groups.size`）是两个真相源。
      修法：已改为 `groups` 与拦截频次的 `combine` 派生流。
      附带的 `HomeViewModelTest` 未随本项完成：ViewModel 构造依赖 `AppauseApp`
      强转，JVM 无法直接实例化，需要 Robolectric + 自定义 test Application
      桩，已并入 TEST_GAP_ANALYSIS G7a（HomeViewModel 测试任务）一起做。
- [x] **P0-3 · "今日统计"跨午夜失效**（2026-09-06 完成）
      `HomeViewModel.startOfToday` 是构造期常量，进程长期存活时"今日"永远指向
      创建那天。修法：已改为 `_startOfToday` 状态 + `flatMapLatest` 重算，
      `refreshServiceStatus()`（每次 ON_RESUME 触发）刷新窗口。
- [x] **P0-4 · CI 无任何测试门禁**（2026-09-06 本地完成；生效需推送授权）
      `.github/workflows/` 只有 `metrics.yml`。已新增 `.github/workflows/ci.yml`：
      push(main)/PR 跑 `testDebugUnitTest` + `assembleDebug`（temurin JDK 17），
      失败时上传 test-results 产物。**推送到 GitHub 后才实际生效。**

## P1 — 高价值，近期修

- [x] **P1-1 · `OverlayPresentationPolicy.initialPath` 死参数与不可达分支（纯减法）**（2026-09-06 完成）
      `initialPath` 收敛为常量 `initialPath`；`Settings.canDrawOverlays` 白查删除；
      `alternatePathAfterFailure` 收敛为 `shouldRetryWith2038AfterFailure(isXiaomiApi36OrLater)`；
      `flags()` 去掉恒 false 参数。测试同步精简（7→6 用例）。模拟器回归：
      重装后 2032 暂停屏正常弹出。
- [ ] **P1-2 · 三个 ViewModel 复制同一份权限/健康状态（约 -120 行）**
      Home / Onboarding / Settings 各持 `accessibilityHealth`/`canDrawOverlays`/
      `isUsageAccessGranted`/`isIgnoringBattery`。抽单一持有者。
- [ ] **P1-3 · `pausePresentationActive` 表达式手写 6 处**
      当前 service 中仍有多处 `pausePresentationActive` 组合表达式，且 `pauseShown` getter
      有副作用（看门狗在读取时释放守卫）。**分两步**：先表达式收敛（行为等价），
      副作用外移（`releaseStaleGuard()`）单独立项。需真机回归，**必须在 P0-4 落地后做**。
- [ ] **P1-4 · 事件处理无串行化 + 编排层零测试**
      每个事件 `serviceScope.launch`，poller 并发调同一 `handleForegroundChange`，
      对 `lastEventForeground`/`lastForegroundPackage` 读-改-写中间挂起 Room 查询。
      修法：(a) `Mutex` 或 Channel 单消费者（poller 走同一队列）；
      (b) 决策→副作用映射抽成可注入的 `InterceptionEffects` + 单测。
      改变事件时序，需真机诊断日志比对。**3–4 天，最后做。**
- [ ] **P1-5 · PauseActivity 兜底链**（已修正范围）
      属实部分：直接 `startActivity` 后**无条件**排 AlarmManager（+250ms），
      Alarm 失败还有 Handler 重试——最多三次启动；`onNewIntent` 未实现且
      `targetPackage` 是 `get() = intent.getStringExtra(...)` 惰性读，
      **新拦截目标会被旧 intent 吞掉**（singleInstance re-front 不更新 intent）。
      ~~倒计时重置~~ **不成立**：manifest `launchMode="singleInstance"`，
      重复启动仅 re-front 不重建。修法：统一启动策略 + override `onNewIntent`。
- [x] **P1-6 · `app_launch_records` 无上限增长**（2026-09-06 完成）
      `AppGroupRepository.deleteOldLaunchRecords()`（365 天保留窗口，与统计窗口
      一致）在 `AppauseApp.onCreate` 以 GlobalScope(IO) 异步调用，异常只记日志。
- [x] **P1-7 · bypass 集合非线程安全**（2026-09-06 完成）
      `InterceptionManager.bypassedPackages` 换 `ConcurrentHashMap.newKeySet()`；
      `SessionState` 两个集合一并处理。

## P2 — 有价值但不急

| # | 问题 | 状态 |
|---|---|---|
| P2-1 | 死代码：`ForegroundChecker.wasResumedRecently`、`sessionStart` map（只写不读）、`HomeTransitionPolicy.shouldConfirm`/`shouldConfirmSystemUiHome` 的 `pauseTargetPackage` 死参数、`startLeaveTimer` 不可达的 cancel | [x] |
| P2-2 | `AppSelectScreen.cachedSelectedPackages` companion var 跨屏传结果 → 改 SavedStateHandle | [ ] |
| P2-3 | 分组空名保存静默失败 → 暴露 `nameError` | [ ] |
| P2-4 | `getForegroundPackage`（Binder）在 service :1076/:1479 跑在 Main，其余调用点在 IO → 统一 IO | [ ] |
| P2-5 | `allowBackup="true"`（manifest:61），Room DB + DataStore（含 license_token）在备份范围 → false 或显式规则 | [ ] |
| P2-6 | 注释漂移：GroupEditorViewModel "free 1–30"（实际 free/pro 都是 60）；AppLaunchDao "v1/未来版本"头注释 | [x] |
| P2-7 | 每个前台事件两次 DataStore 挂起读（enabled / temporaryPass）→ stateIn 缓存 | [ ] |
| P2-8 | 测试盲区 → 见 `docs/TEST_GAP_ANALYSIS.md`（G1 已并入 P1-4b，G3a/G4/G5/G7 独立推进） | [ ] |

## WONTFIX（明确不做，防止反复讨论）

- 引入 Hilt/Koin（`by lazy` 够用）。
- 给 2032/2038/Activity 降级链再加抽象（方向相反：应删）。
- 为 re-remind 循环引入虚拟时钟状态机（除非为补 G5 测试）。
- 现在拆分 `AppauseAccessibilityService`（先 P1-3/P1-4 收敛，再拆）。
- 启用 R8/minify（MIT 开源、无第三方 SDK，收益小排查成本大）。
- `PauseScreenContent` 截图/snapshot 测试。
- 多模块拆分（15k 行未到规模）。

## 已核实为"足够好，不要动"

- 纯函数策略层 + 已有 JVM 用例；具体数量以最新测试报告为准。
- `LicenseVerifier`：alg==RS256 显式校验、`SHA256withRSA`、`requireDeviceBinding`
  生产开关、失败一律 null。
- 日志隐私门禁：`AppLogger`/`PersistentLog`/`CrashLog` 全部 `BuildConfig.DEBUG`
  守卫，release 零输出。
- Room 迁移 1→6 完整 + `exportSchema = true`；密钥零入库。
- Home/Recents finalization（2026-09-11）：当前实现使用 standalone
  `TYPE_ACCESSIBILITY_OVERLAY`（2032），通过 system-dialog 的 `homekey` 信号
  立即 dismiss，并将 `recentapps` 排除出 Home 确认；旧 target 事件有 bounded
  late-event check，真正 immediate reopen 仍可重新拦截。当前最终验收证据为
  JVM focused tests、`assembleDebug`、emulator smoke，以及 Xiaomi
  2410DPN6CC / Android 16 的 ADB/logcat/WindowManager objective evidence。
  未声称主观顺滑度或正式延迟测量；当前 main 也晚于 v0.5.40 release tag。

## 执行顺序（已获授权的批次）

1. ✅ 本文件 + 对齐 `TEST_GAP_ANALYSIS.md`（G1 并入 P1-4）、`INTERCEPTION_PROTOCOL.md`（P1-5 修正）
2. P0-4（CI 门禁）
3. P0-1（数据源合并）
4. P0-3 + P0-2（HomeViewModel 两项连做）
5. P1-7（并发集合）
6. P1-1、P1-6、P2 项 —— 待下一批授权
7. P1-3 第一步 → P1-2 → P1-4 —— 高风险，CI 落地并真机回归后另行授权
