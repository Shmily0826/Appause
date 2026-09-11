# Appause 测试缺口分析（Test Gap Analysis）

> 原始盘点基线为 `main` @ `461ee33`（2026-09-06，134 个用例）；当前源基线为
> `main` @ `ed64329`（2026-09-11）。下表保留该历史覆盖快照，数量应以最新测试
> 报告重算，不作为当前总数声明。
> 本文把 `docs/INTERCEPTION_PROTOCOL.md` 里的每个环节和现有测试对上号，
> 找出"没被任何测试钉住"的行为。优先级排序对接用户已确认的测试优先级
> （Pro 兑换失败模式 → Room 迁移 → ViewModel → re-remind 虚拟时钟）。

## 1. 现状总览

| 层 | 测试文件 | 用例数 | 覆盖质量 |
|---|---|---|---|
| 纯决策 | `InterceptionDeciderTest` | 27 | ★★★ 每个决策分支都有 |
| Home 确认 | `HomeTransitionPolicyTest` | 17 | ★★★ |
| 爆发指纹 | `BurstTrackerTest` | 10 | ★★★ 含时钟注入 |
| 临时通行 | `TemporaryPassPolicyTest` | 5 | ★★ 编解码/到期边界 |
| Pro 授权 | `LicenseVerifierTest` + `ProStateRedeemTest` | 29 | ★★（失败模式仍有缺口，见下） |
| 展示策略 | `OverlayPresentationPolicyTest` | 7 | ★★★ |
| re-remind 门控 | `ReRemindSchedulePolicyTest` | 4 | ★★ 只测了"要不要调度" |
| 会话标记 | `SessionStateTest` | 2 | ★★ |
| bypass | `InterceptionManagerTest` | 7 | ★★ |
| 健康状态 | `AccessibilityHealthPolicyTest` | 6 | ★★★ |
| 数据库迁移 | `AppDatabaseMigrationTest` | 1 | ★ 明显不足 |
| ViewModel | Stats(7) / Onboarding(5) / 临时通行选择(7) | 19 | ★ 只有三处有 |

## 2. 缺口清单（按风险排序）

### G1（高）——Service 编排层零覆盖：决策 → 副作用的映射
`AppauseAccessibilityService.handleForegroundChange` 是"纯决策 + 副作用执行"
的编排层：每个 `PreGroupDecision` 映射到一组副作用（清 bypass、启动离开计时器、
`noteCancelled`、dismiss、写 `lastForegroundPackage`…）。**决策本身测了 27 个，
但"哪个决策触发哪些副作用"没有任何测试。** 举个例子：`SkipSystem` 分支里
`maybeStartLeaveTimerFor` + cancel 抑制清理 + `lastForegroundPackage` 更新这
三件事的顺序和条件，现在只靠人读代码保证。
- **建议**：把编排映射抽成可测的纯函数（decision → effect list），或引入
  带 fake 时钟/fake OverlayManager 的轻量服务级测试。这是重构 R1/R2 前的
  必要安全网。
- **补充（2026-09-06 深度审查，与 ENGINEERING_REVIEW.md P1-4 同源）**：
  编排层还存在**并发面**——`onAccessibilityEvent` 对每个事件独立
  `serviceScope.launch`，1.5 s poller 也并发调用同一函数，对
  `lastEventForeground`/`lastForegroundPackage` 的读-改-写中间还挂起一次
  Room 查询，没有任何 Mutex/串行化。测试缺口因此是双重的：副作用映射
  没测试，且并发正确性只能靠 `pauseShown` 守卫兜底。修复方案见
  `docs/ENGINEERING_REVIEW.md` P1-4。

### G2（高）——已完成（2026-09-06）：看门狗决策核心抽为 `PauseGuardPolicy` + 8 个边界用例
`pauseShown` getter 的自我修复逻辑（协议 §5）是历史上两个真实 bug 的修复物，
但它的触发条件依赖 `SystemClock.elapsedRealtime()`，无法在 JVM 里直接测。
- **建议**：把 watchdog 判定抽成 `pauseGuardWatchdog(elapsedMs, overlayAttached,
  activityVisible) -> GuardAction` 纯函数 + 注入时钟，测四个象限：
  宽限期内/宽限期满无窗口/硬上限超时（含 attached-but-hidden）/正常持有。

### G3（高，部分完成 2026-09-06：G3a LicenseVerifier 失败分支已补 6 例，verify() 同时加固为任何畸形 token 一律返回 null）——Pro 兑换失败模式（用户既定优先级 #1）
`ProStateRedeemTest` 15 例覆盖了主路径；缺口在失败分支矩阵：过期 JWT、
未来生效的 nbf、设备绑定不匹配、KV 无此码、重复兑换的网络时序、Worker 5xx
后 UI 状态、兑换中途进程死亡。`worker/` 侧的签发逻辑完全没有测试。
- **建议**：先给 `LicenseVerifier` 补 JWT 失败分支参数化用例（纯 JVM，成本低）；
  Worker 侧用 vitest/miniflare 建最小测试（独立任务）。

### G4（中）——Room 迁移只有 1 个用例（用户既定优先级 #2）
`AppDatabaseMigrationTest` 目前 1 例。已有 schema 演进多次（GroupApp、
recommended、临时通行在 DataStore 不走 Room，但 AppGroup 列有增补）。
- **建议**：用 Room 的 `MigrationTestHelper` 补全每个历史版本路径
  （需要 androidTest 或导出 schema 的 JVM 等价物）；先盘点
  `AppDatabase.kt` 里实际存在的 migration 列表再排。

### G5（中）——re-remind 循环的时间数学（用户既定优先级 #4）
`scheduleReRemind` 的锚定计算（`targetAppearAt = lastContinueAt + minutes −
rePopSeconds`、escalate 倍数、临时通行暂停时钟、away 时 2s 重查）是用户
明确报过 bug 的区域（"等了不止 1 分钟"），现在只有 4 例门控测试。
- **建议**：把每轮的时间计算抽成纯函数 `nextPopAt(...)`，虚拟时钟参数化测试；
  循环本身保持手工真机验证。

### G6（中）——离开计时器 / reArm 生命周期
`startLeaveTimer` 的幂等性、`maybeStartLeaveTimerFor` 的 prev/current 条件、
`reArm` 清理三件套（bypass + re-remind + sessionStart）——目前全部没有测试。
协议 §7 状态机的 AwayTimer 边完全靠真机验证。
- **建议**：`LeaveTimerPolicy` 式纯函数抽取（何时启动/取消/到点 re-arm），
  与 G1 一并做。

### G7（中）——ViewModel 层只有 3/9 有测试
有：Stats、Onboarding、临时通行选择器。缺：Home（含健康卡片状态合并）、
Settings、Pro、GroupEditor、AppSelect、Recommended。
- **建议**：优先补 HomeViewModel（它是健康状态 + 拦截状态的 UI 汇合点，
  且刚加过 lifecycle race 修复）和 ProViewModel（对接 G3）。

### G8（低）——OverlayManager 降级链 / PauseActivity / ForegroundChecker
这三处强依赖 Android 框架（WindowManager、AlarmManager、UsageStatsManager），
JVM 测不了，instrumented test 成本高。现状是**真机矩阵验证**（PROGRESS.md
里的小米 P0 记录）承担了这部分。可以接受，但要在发版清单（§4）里保持
真机冒烟，不因为"有单测"而跳过。

### G9（低）——BurstTracker 与真实时钟的交互
`record()` 用 `System.currentTimeMillis()`（wall clock），用户改系统时间会
扰动 120ms 窗口。概率极低、后果轻（一次误抑制/漏抑制），记录在案即可，
不值得改（改 elapsedRealtime 反而破坏可注入性）。

## 3. 建议的执行顺序

1. G3a（LicenseVerifier 失败分支，纯 JVM、半天级）
2. G2（看门狗纯函数抽取 + 四象限测试）
3. G1 + G6（编排映射与离开计时器，同一个重构任务）
4. G4（Room 迁移盘点 + 补齐）
5. G7a（HomeViewModel）
6. G5（re-remind 时间数学）

每完成一项：`testDebugUnitTest` 全绿 + `assembleDebug` 通过 +
TEST_REPORT.md 追加记录（append-only）。
