# P0 修复执行方案（2026-10）

- Task ID: `APPAUSE-20261001-1752`
- 状态：**方案待授权 —— 未修改任何代码**
- 依据：`docs/quality-assessment-2026-10.md`。本方案引用的全部行号已于 2026-10-01 逐行核实。
- 对报告的两处订正（方案已按订正后的事实编写）：
  1. 报告执行顺序表第 3 步括注的「`OverlayManager` 的 `${pkg}Present` Map」在仓库中**不存在**（全仓 "Present" 只匹配 `pausePresentationActive` / `PausePresentationPolicy` / `targetPresent` 布尔与 lambda）。B2 只覆盖已核实的五个 Map。
  2. 报告 §3.4 的「`material-icons-extended` → `material-icons-core` 是纯依赖交换、零重构」不成立：实测用到 25 个不同图标，其中约 13 个不在 core 集内，直接换依赖会编译失败。本方案改走 **R8 先行**路线（B6），依赖交换作为 R8 受阻时的备选。

---

## 全局约束（每个批次都适用）

1. 每改一次代码必须 `./gradlew assembleDebug`（AGENTS.md §1.4）；构建前 `export JAVA_HOME="D:\Dev-Setup\jdk"`。
2. 每批一个独立提交；**提交与 push 随批次授权一并生效**（AGENTS.md §10 权限分离）。
3. 完成后向 `docs/archive/PROGRESS.md` 追加带日期的段落（该文件 append-only）。
4. 单测基线 197 条（TP 批后），每批不得净减。

## 批次总览与推荐顺序

| 批 | 内容 | 对应报告条目 | 改动面 | 风险 |
|----|------|--------------|--------|------|
| B1 | 拆分提交，让 CI 覆盖当前源码 | #2 | 小 | 低（但涉及提交既有工作区改动，见批内说明） |
| B2 | 五个共享 Map 并发保护 | #3 | 极小 | 低 |
| B3 | `saveGroupWithApps` 事务化 + 迁移测试改造 | #4 | 小 | 低-中（测试重写） |
| B4 | Pro 清零竞态修复 | #5 | 极小 | 低 |
| B5 | PauseIntent extra 工厂 + `is_re_remind` 补齐 | #6 | 小 | 低 |
| B6 | R8 试点（替代报告第 9+14 步顺序） | §3.4 | 小配置 | **高，必须真机全链路** |
| B7 | targetSdk 36 升级 | #1 | 中-大 | 中-高，**等待决策 D1** |

---

## B1 — 拆分提交，让 CI 对当前源码产生有效记录

**目标**：`main` 目前领先 `origin/main` 6 个提交，工作区还有约 12 个改动文件 + 多个未跟踪文件从未过 CI。拆成有意义的提交并 push，让 CI（`testDebugUnitTest` + `assembleDebug`）跑在真实源码上。

**建议拆分**（按文件归属，不改内容）：

1. `app功能与测试` — `HomeScreen.kt`、`FeedbackScreen.kt`、`DiagnosticsSnapshot.kt`、`HomeProPresentation.kt`、`XiaomiReliabilitySettings.kt`、`app/src/debug/.../DiagnosticsScreen.kt`、`AndroidManifest.xml`、两份 `strings.xml`、新增测试目录 `app/src/test/.../ui/home/`、`ui/feedback/`
2. `landing与文档` — `README.md`、`docs/landing-v2/*`、`zh.html`、`sitemap.xml`、`docs/archive/PROGRESS.md`、`docs/accessibility-hyperos-review.md`
3. `质量报告与修复方案` — `docs/quality-assessment-2026-10.md`、`docs/fix-plan-p0-2026-10.md`
4. `.playwright-mcp/` 不提交 → 在 `.gitignore` 加一行（见决策 D4）

**⚠️ 协议说明**：这些工作区改动不是本会话产生的。授权本批 = 授权按上述文件归属拆分提交并 push；我不会审查或修改它们的内容（需要内容级审查请单独说明）。

**验证**：push 前 `./gradlew testDebugUnitTest assembleDebug`（PASS 预期：197+）；push 后 GitHub Actions 绿。

---

## B2 — 五个共享 Map 并发保护

**落点**：`AppauseAccessibilityService.kt`

| 行 | 字段 | 现状 | 改为 |
|----|------|------|------|
| 561 | `reRemindJobs` | `mutableMapOf<String, Job>()` | `java.util.concurrent.ConcurrentHashMap<String, Job>()` |
| 569 | `reRemindContinue` | `mutableMapOf<String, CompletableDeferred<Unit>>()` | 同上（值类型不变） |
| 572 | `initialContinueSignal` | 同上 | 同上 |
| 575 | `temporaryPassExpiryJobs` | `mutableMapOf<String, Job>()` | 同上 |
| 588 | `leaveTimers` | `mutableMapOf<String, Job>()` | 同上 |

写法与该文件 `:552` 的 `systemImageCache`（已用 `ConcurrentHashMap`，全限定名风格）保持一致。

**为什么够**：Map 的危害在结构损坏（并发 put 成环 → CPU 100%）。`ConcurrentHashMap` 的单键 put/get/remove 原子即可消除；`Job.cancel()` 与 `CompletableDeferred.complete()` 本身线程安全。已知的一处真实跨线程写（`OverlayManager.kt:464` 在 `Dispatchers.IO` 协程里调 `scheduleTemporaryPassExpiry` → 写 `:1841` 的 `temporaryPassExpiryJobs`）由此直接被覆盖。不引入锁、不改访问时序（避免过度设计）。

**验证**：`./gradlew testDebugUnitTest`；模拟器回归场景 A/B（Bilibili 直播间 stationary 工作流，拦截/取消/继续/re-remind 各一遍）。

---

## B3 — `saveGroupWithApps` 事务化 + 迁移测试改造

### 3a. 事务化（修"中断后分组应用列表为空"）

**落点 1**：`AppGroupDao.kt` 末尾新增 `@Transaction` 默认方法（Room 对 Kotlin DAO 默认方法 + `@Transaction` 是官方支持写法；方法内的多个操作会被包进同一事务）：

```kotlin
import androidx.room.Transaction  // 文件头新增

@Transaction
suspend fun saveGroupWithApps(group: AppGroup, packageNames: List<String>): Long {
    val groupId = if (group.id == 0L) insertGroup(group) else { updateGroup(group); group.id }
    removeAllAppsFromGroup(groupId)
    insertGroupApps(packageNames.map { GroupApp(packageName = it, groupId = groupId) })
    return groupId
}
```

> 注意：`@Transaction` 必须落在 **DAO 层**——Room 不认 Repository 方法上的这个注解。

**落点 2**：`AppGroupRepository.kt:61-79` 的方法体改为委托（保留原 docstring，逻辑语义不变，只是从"多次独立调用"变成"一次事务"）：

```kotlin
suspend fun saveGroupWithApps(group: AppGroup, packageNames: List<String>): Long =
    groupDao.saveGroupWithApps(group, packageNames)
```

不需要给 Repository 注入 `AppDatabase`（无需改构造器，`AppGroupRepositoryTest` 的 fake DAO 不受影响）。

### 3b. 迁移测试改造（修"忘写 migration 也全绿"）

现状：`AppDatabaseMigrationTest.kt` 手写 SQL 建 v1 表、`onUpgrade` 里硬编码顺序跑 1_2…5_6，**从不校验迁移后的 schema**。`app/schemas/` 只有 `1.json` 与 `6.json`（2–5 当年未导出，无法补）。

改法（`room-testing` 已在依赖里，`build.gradle.kts:179`）：

1. `app/build.gradle.kts` `android {}` 内新增，让单测能以 asset 形式读到 schema JSON：

```kotlin
sourceSets {
    // MigrationTestHelper 从 assets 读 Room 导出的 schema JSON；
    // Robolectric 单测需要 test sourceSet 显式包含 app/schemas。
    getByName("test") { assets.srcDir("schemas") }
}
```

2. 测试主体改用 `MigrationTestHelper`：

```kotlin
// createDatabase(DB, 1) 由 1.json 建表并播种（替代手写 CREATE TABLE）
// runMigrationsAndValidate(DB, 6, true, MIGRATION_1_2 .. MIGRATION_5_6)
//   —— 自动对照 6.json 校验列/identityHash，迁移写错即 FAIL
// 现有的"数据保留 + 新列默认值"SQL 断言全部保留
```

3. 新增**迁移覆盖元测试**（防"忘加 migration"）：反射枚举 `AppDatabase.Companion` 的 `MIGRATION_*` 常量，断言 1→6 每个相邻步都有对应迁移、无缺口，且终点 == `AppDatabase` 的 `version = 6`（`AppDatabase.kt:35`）。

**验证**：`./gradlew testDebugUnitTest --tests "*AppDatabase*"`；另手工构造一次"故意删掉 MIGRATION_5_6"验证元测试确实变红（红后再恢复）。

---

## B4 — Pro 清零竞态修复

**落点**：`GroupEditorViewModel.kt:215`。

现状：`isPro` 是 `proState.isPro.stateIn(..., initialValue = false)`（`:37-38`），`save()` 读 `isPro.value`。首帧窗口内（`entitlement` 冷流尚未发射）保存时，正版 Pro 被当成免费，四项 re-remind 配置被清零。

```kotlin
// :215 改为：
- val reRemindOn = if (isPro.value) _reRemindEnabled.value else false
+ // save() 在协程内挂起读取真实 Pro 状态：stateIn 的初值 false 只是首帧占位，
+ // 读 .value 会把正版 Pro 误判为免费（proState.isPro 是冷流，first() 等首个真实发射）。
+ val proUnlocked = proState.isPro.first()
+ val reRemindOn = if (proUnlocked) _reRemindEnabled.value else false
```

新增 `import kotlinx.coroutines.flow.first`。`isPro` StateFlow 本身保留（UI 门控仍用它）；"非 Pro 强制清零"的产品语义不变，只消灭竞态。

**验证**：新增单测（Robolectric，`@Config(application = AppauseApp::class)`）：未等 `isPro` 发射即 `save()`，断言 Pro 用户的 `reRemindMinutes/CooldownSeconds/Repeat/Escalate` 保留原值。手工路径：debug 开 Pro 开关 → 编辑已有 re-remind 分组 → 立即保存 → 重进确认未清零。

---

## B5 — PauseIntent extra 工厂 + `is_re_remind` 补齐

**问题回顾**：`OverlayManager.kt` 三处手工组装 `PauseActivity` Intent（`:695-702` 直启、`:746-755` alarm、`:779-788` Handler 兜底），第三处漏了 `is_re_remind` → 兜底路径上的 re-remind pop 被当作初始冷却，服务的 re-remind 循环等不到 Continue 信号而卡死。

**改法**：

1. 新增 `app/src/main/java/com/appause/android/ui/pause/PauseIntentFactory.kt`：`data class PauseSpec(...8 个字段含 isReRemind...)` + `activityIntent()` / `alarmIntent()`，extra 键名收敛为常量，字段逐一对齐——漏传成为编译期不可能。
2. `OverlayManager` 三处改为调用工厂；Handler 兜底由此**自动**带上 `is_re_remind`。
3. `schedulePauseViaAlarm` 在 `try` 前用 `alarmManager.canScheduleExactAlarms()`（API 31+）记一条 `PersistentLog`，把"为什么走了 Handler 兜底"变成可诊断事实。
4. **不在本批**做 manifest 权限变更（是否声明 `SCHEDULE_EXACT_ALARM` 是决策 D2，涉及隐私文案）。

**验证**：扩展现有 `PauseActivityIntentTest`（已在测试树中）：断言三种 Intent 的 extra 集合完全一致；模拟器跑一次 MIUI-fallback 场景确认 re-remind pop 语义正确。

---

## B6 — R8 试点（新顺序：替代报告第 9 步 + 第 14 步）

**为什么改顺序**：`isMinifyEnabled = true` 后 R8 会裁掉 `material-icons-extended` 里 2500+ 未用图标类，体积收益与"换 core"重叠且免掉约 13 处图标替换（视觉变更）。报告原第 9 步取消，R8 提前为独立批次。

1. `app/build.gradle.kts:99` `isMinifyEnabled = false` → `true`（`proguard-android-optimize.txt` 已配置）。
2. `./gradlew assembleRelease`，按报错逐条补 `-dontwarn` / keep 规则（Room/DataStore/Compose 一般零规则起跑；**Pro 激活 Worker 的 JWT 校验链路必须真机实测**——反射/序列化是 R8 最常见的翻车点）。
3. 记录体积对比（基线 12.27 MB）。
4. 真机全链路清单：拦截→暂停(overlay 2032)→取消→重拦、继续→re-remind 循环、临时通行证发放与到期、Pro 激活与离线校验、开机重启后服务恢复。
5. 独立提交；**不与 B2–B5 混批**。

---

## B7 — targetSdk 36（等待决策 D1，暂不展开）

骨架：AGP `8.7.3` → `8.9.1+`（compileSdk 36 的硬性前置）→ `compileSdk/targetSdk = 36` → `PauseActivity` 补 edge-to-edge insets（`enableEdgeToEdge()` + `WindowInsets` 兜底，overlay 路径 `OverlayManager.kt:310-313` 已有几何处理可参照）→ 真机回归。延期窗口截止 **2026-11-01**。

---

## 待你拍板

| # | 决策 | 影响 |
|---|------|------|
| D1 | 是否仍维护 Google Play 上架？ | 决定 B7 是 P0 还是降级 |
| D2 | 是否声明 `SCHEDULE_EXACT_ALARM`？ | 决定 B5 是否追加 manifest 变更 |
| D3 | 授权哪些批次执行？（可只授权 B2+B3+B4 这类小批先行） | 各批独立授权，含各自的提交+push |
| D4 | `.playwright-mcp/` 加入 `.gitignore`？ | B1 内容 |
