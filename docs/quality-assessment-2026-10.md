# Appause 质量工程评估与优化建议（只读评估）

> **对象**：`D:/CODE/project/Appause`，`HEAD = 23e9ebe`，工作区**未干净**（11 已修改 + 10 未跟踪）
> **日期**：2026-10-01
> **方法**：只读静态发现。**未运行 Gradle、未下载依赖、未启动设备、未修改任何源文件。**
> **证据分级**：标记 `[✅]` 的已逐行核实；未标记的来自子代理静态分析（附行号，**改动前请再核一行**）。
> 本文不重复 `docs/TEST_GAP_ANALYSIS.md` 的测试清点，只补**发行合规 / 构建 / 门禁 / 依赖 / 代码风险 / 架构**。

```
质量契约状态：BLOCKED（评估型任务，非门禁任务）
阻塞项：3
  1. Google Play 上架状态未知 → 2.1 无法定级
  2. 无有效 lint / 覆盖率报告（本次未授权执行构建）
  3. 工作区改动无任何 CI 证据 → 2.2 的风险敞口无法量化
冻结范围：只读发现 + 优化建议；不写代码、不改 Gradle/CI、不装依赖
完成标准：给出带行号、可复核的风险清单 + 分组执行顺序 + 待用户决策项
```

---

## 1. 项目事实画像

| 事实 | 值 | 证据 |
|---|---|---|
| 结构 | 单模块 `:app`，无 build-logic 复合构建 | `settings.gradle.kts:25` `[✅]` |
| 依赖管理 | Version Catalog | `gradle/libs.versions.toml` `[✅]` |
| 工具链 | AGP 8.7.3 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29 / Gradle 8.11.1 / JDK 17 | `libs.versions.toml:3-5` `[✅]` |
| SDK | minSdk 26 / **targetSdk 35** / compileSdk 35 | `app/build.gradle.kts:39-44` `[✅]` |
| 版本 | versionCode 97 / versionName 0.5.45 | `app/build.gradle.kts:46-47` `[✅]` |
| 变体 | `debug`（`.debug` 后缀，可与 release 并存）/ `release`，各有独立 source set 放同名桩类 | `app/build.gradle.kts:78-109` `[✅]` |
| UI | Compose + Material3 + Navigation，单 Activity | `MainActivity.kt:36` `[✅]` |
| 持久化 | Room v6（3 张表）+ DataStore + SharedPreferences 镜像 | `AppDatabase.kt:29-39` `[✅]` |
| DI | 无 Hilt/Dagger，Application 手工 `lazy` 服务定位 | `AppauseApp.kt:29-69` `[✅]` |
| 测试 | JUnit4 + Robolectric 4.12.2 + coroutines-test + Room-testing；JVM 约 **258** 个 `@Test`；仪器测试仅 2 文件 6 例 | `app/src/test/**` `[✅]` |
| CI 门禁 | `./gradlew testDebugUnitTest` + `./gradlew assembleDebug` | `ci.yml:25-29` `[✅]` |
| 日志 | 全部经 `AppLogger`，release 构建零 logcat 输出 | `util/AppLogger.kt:19-61` `[✅]` |
| 规模 | main source set Kotlin 约 **16,979 行 / 66 文件**；Top4 文件占 39% | 统计 `[✅]` |

---

## 2. P0 —— 发布阻断级

### 2.1 targetSdk 35 已不满足 Google Play 的目标 API 要求 `[✅]`

- **事实**：`app/build.gradle.kts:44` `targetSdk = 35`。
- **外部政策**：Google Play 自 **2026-08-31** 起要求新应用与更新以 **Android 16（API 36）** 或更高为目标；仅有一次延期窗口到 **2026-11-01**，需主动在 Play Console 申请。今天是 2026-10-01 ⇒ **按公开政策当前版本已无法上架**。
- **这不是改一行版本号的事**，target 36 会打开三组默认行为变更，而本项目每一条都踩在核心链路上：
  1. **Edge-to-edge 强制**（无开关可退）。证据链：
     - `enableEdgeToEdge()` 全仓**只出现 1 次**：`MainActivity.kt:81`；
     - `navigationBarsPadding()` 全仓**只出现 3 次**：`GroupEditorScreen.kt:202`、`OnboardingScreen.kt:108`、`AppSelectScreen.kt:133`；
     - `ui/` 下有 **14 处 `Scaffold(`**；
     - **`ui/pause/` 一处 insets 处理都没有**，而同一个冷却界面走 `OverlayManager` 路径时反而有正确的人工 insets 处理（`OverlayManager.kt:310-313` 用 `WindowInsets.Type.statusBars()/navigationBars()`）⇒ **两条路径行为不一致**，在 target 35 + Android 15+ 上就会显现为倒计时/按钮被系统栏压住。
  2. **后台启动收紧**：核心容错路径 `PauseAlarmReceiver`（`PauseAlarmReceiver.kt:34-57`）正是从后台 `startActivity` 拉起暂停界面 —— 必须在 Android 16 真机重测。
  3. `resources.updateConfiguration`（`MainActivity.kt:78`）是废弃 API，在 36 上更易漂移（见 3.7）。

**建议方案（三步分离，禁止合并成一次改动）**
1. 只把 `compileSdk` 提到 36，修编译错误；
2. 再动 `targetSdk = 36`，并在 Android 16 设备上跑「边到边 / 预测式返回 / 闹钟拉起暂停界面」三条检查清单，记录进 `docs/REPEAT_INTERCEPTION_STRESS_TEST.md`；
3. 视情况申请 11-01 延期窗口做兜底。

> **待你决策**：本项目是否仍在维护 Google Play 上架？若纯走 GitHub Release 分发，此项降级为 P2（但仍建议跟进，因为 target 36 涉及真机行为变更）。

### 2.2 工作区脏且领先远端，生产代码改动从未过 CI `[✅]`

- `HEAD = 23e9ebe`，本地 `origin/main` 引用停在 `a1e7908`（本地引用可能陈旧，判断远端请以 `git fetch origin main` 为准）。
- **已修改（含生产代码）**：`HomeScreen.kt`、`FeedbackScreen.kt`、`DiagnosticsScreen.kt`、`DiagnosticsSnapshot.kt`、两份 `strings.xml`、`AndroidManifest.xml`。
- **未跟踪**：`HomeProPresentation.kt`、`XiaomiReliabilitySettings.kt`（新增生产类）、`app/src/test/.../ui/feedback/`、`.../ui/home/`（新增测试）。
- **影响**：含新增生产类与清单改动的这批文件，**从未经过任何一次 `testDebugUnitTest`**。CI 在守护一个还没 push 的版本。

**建议**：拆成「代码提交」与「落地页文档提交」两组分别 push，让 CI 至少对当前源码产生一次有效记录。

### 2.3 五个共享可变 Map 跨线程无任何保护 `[✅]`

- **事实**：`AppauseAccessibilityService.kt:561/569/572/575/588` 的 `reRemindJobs`、`reRemindContinue`、`initialContinueSignal`、`temporaryPassExpiryJobs`、`leaveTimers` 全部是**普通 `mutableMapOf`**：无 `@Volatile`、非 `ConcurrentHashMap`、未加锁。
- **跨线程证据**：写入点分布在不同线程 —— `:1841` 走 `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)`（`:489`）；`:1947/1958` 由 `OverlayManager.kt:579/586` 从 `Dispatchers.IO` 触发；`PauseActivity.kt:361/369` 也跨组件写。它们**都是实例字段**（companion 对象在 `:194-486` 已结束），不共享给多实例，但同实例内多线程访问成立。
- **决定性佐证**：同一个作者在 `SessionState.kt:10-19` 明确写了注释 ——「these are touched from the service main thread, overlay callbacks on Dispatchers.IO, and PauseActivity, so plain HashSets would be unsafe」，并**用了 `ConcurrentHashMap.newKeySet()`**。说明这个跨线程模型作者清楚，这五个 Map 属于漏做，而非架构取舍。
- **后果**：`HashMap` 并发 put 触发 resize 时可形成环形桶，**`get()` 变死循环 → CPU 100% 卡死**；同时 `remove(pkg)?.cancel()` 丢更新 ⇒ 旧的 re-remind Job 与 leave timer 永不取消，冷却会重复弹出。

**建议**：统一改 `ConcurrentHashMap`。同类还有 `OverlayManager.kt` 里 16 处 `${pkg}Present: Boolean` 形式的分布式共享状态（例：`:753-756` 连续三行的 `getBoolean / write / remove` 组合存在读写竞态），建议合并成 `AtomicReference`。改之前先在单测里显式注入跨线程调用复现问题，再动实现。

### 2.4 Room 写入非原子 + 迁移测试存在"看不见"的盲区 `[✅]`

- **非原子**：`AppGroupRepository.kt:61-79` 的 `saveGroupWithApps` 做三步独立写：`updateGroup`（`:67`）→ `removeAllAppsFromGroup`（`:72`）→ `insertGroupApps`（`:76`），**无 `@Transaction`**。Room 只给单个 DAO 方法各包一层事务，这三步之间不原子。进程被杀发生在 `:72` 之后、`:76` 之前 ⇒ **分组还在但应用列表为空**，该分组从此不再拦截任何应用，用户完全不察觉。`:59` 的注释「This is safe because we're inside a single user action」是对 Room 事务边界的误解。
- **迁移测试盲区**：`AppDatabaseMigrationTest.kt:44-66` **手工构造 v1 建表语句**，`:96-104` 又**无条件顺序执行全部 5 个 migration、忽略 `oldV`**。它绕过 schema-JSON 校验，因此若有人改了 Entity 却忘写 migration 或忘 bump version，**这个测试仍然会绿**，而真实用户升级时 Room 会抛 `IllegalStateException` 崩在启动。`app/schemas/.../AppDatabase/` 下只有 `1.json` 与 `6.json`，缺 2–5，官方 `MigrationTestHelper` 的资产本就不完整。
- **做对的地方**：`AppDatabase.kt:148-159` **没有** `fallbackToDestructiveMigration`，老用户数据不会丢。

**建议**：① 给 `saveGroupWithApps` 加 `@Transaction`，并优先改成 `@Upsert` + 单方法 `replaceAppList(groupId, list)`；② 补一个「元测试」，断言 `1..6` 每一级 Migration 首尾相接，杜绝"加了 version 7 忘写 migration"这类最贵的事故。

### 2.5 Pro 到期后保存一次即永久丢失 re-remind 配置 `[✅]`

`GroupEditorViewModel.kt:215-230`：非 Pro 时把 `reRemindMinutes` / `reRemindCooldownSeconds` / `reRemindRepeat` / `reRemindEscalate` **静默写 0 / 0 / true / false**。而 `:130-133` 加载时是无条件从 DB 恢复的，所以用户只要在 Pro 过期后进分组编辑器改个名字并保存，四项配置就**永久丢失且无任何提示**。

这是长期项目纪要里已记录过的缺陷，**至今未修**。另有一处放大器 `[子代理]`：`isPro` 用 `SharingStarted.WhileSubscribed(5000)` 且 **初值为 `false`**，在 entitlement 首帧（DataStore 读文件 + `parsePublicKey` + Keystore 取指纹，典型几十~数百 ms）窗口内点保存，**即使是正在试用/正版 Pro 的用户也会命中清零分支**。

**建议**：加载进 `_reRemind*` 后，保存时**无条件原样写回**，把"Pro 才能生效"收敛到展示层（只读 + 升级引导）。配合 `isPro` 改 `SharingStarted.Eagerly`，并补两条单测：`Pro→Free 后保存` 与 `冷启动竞态内保存`。

### 2.6 AlarmManager 兜底路径漏传 `is_re_remind`，且精确闹钟权限缺失 `[✅ Manifest 部分]`

- **权限缺失（已核实）**：`AndroidManifest.xml:5-21` 只声明了 `POST_NOTIFICATIONS`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、`SYSTEM_ALERT_WINDOW`、`INTERNET`、`PACKAGE_USAGE_STATS` —— **没有 `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`**。因此 `OverlayManager.kt:763-767` 的 `setExactAndAllowWhileIdle` 在 API 31+ 恒抛 `SecurityException`，`:770` 的 catch 会静默降级。
- **降级路径本身有 bug** `[子代理]`：`OverlayManager.kt:779-788` 的 Handler 重试 Intent 缺 `putExtra("is_re_remind", isReRemind)`，而同文件另两处（`:701`、`:754`）都有。`PauseActivity` 的该 extra 缺省为 `false`，于是一次 re-remind 弹窗被当成首次冷却 → 走 `onSessionStart()` 而非 `completeReRemindContinue()`，原 re-remind 协程阻塞在 `signal.await()`，**该包的后续 re-remind 彻底停止**。也就是说：真机上 overlay `addView` 一旦失败，必然走到这条有 bug 的路径。

**建议**：把三处 PauseIntent 的 extra 组装抽成一个工厂函数（可 JVM 单测），并显式检查 `alarmManager.canScheduleExactAlarms()`，把"是否可用"显式化而不是靠异常兜底。

---

## 3. P1 —— 工程能力缺口

### 3.1 零静态分析 `[✅]`

仓库根目录**不存在** `lint.xml`、`detekt.yml`、`.editorconfig`、`baseline.xml`；`app/build.gradle.kts` 未配置 `lint {}`；`ci.yml` 未调用任何 lint 任务。

这直接导致一批本可自动发现的问题沉淀成人工负担，现存的抑制点就是证据：`settings.gradle.kts:15` 的 `@Suppress("UnstableApiUsage")`、`OverlayManager.kt:81` 的 `@SuppressLint("ClickableViewAccessibility")`、`MainActivity.kt:77` 的 `@Suppress("DEPRECATION")`、`OverlayManager.kt:266-267` 用字面量 `Build.VERSION.SDK_INT >= 36` 绕过 `compileSdk = 35` 的编译期保护。

另有一处**规范漂移**：`XiaomiReliabilitySettings.kt:11-12` 硬编码了 `com.miui.securitycenter` 与其 Activity 类名。它的异常处理写得很好（`:26-43` 三重 catch + 二次降级到系统 App Details），但违反了 `AGENTS.md`「不要硬编码具体应用包名」。建议显式标注为 AGENTS.md 的例外并写明理由，而不是让它看起来像违规。

**建议方案**：① 第一步只加 **Android Lint** —— CI 增加 `./gradlew lintDebug`；首次一定报错，不要临时用 `abortOnError false` 糊过去，而是生成一次 `lint-baseline.xml` 作为技术债台账入库，后续只允许下降。② 第二步再考虑 Detekt/ktlint（`gradle.properties` 里的 `kotlin.code.style=official` 只是声明，不构成执行）。③ 顺手打开 Dependabot/Renovate —— 仓库有 `worker/` 一整套 npm 生态却没有任何依赖更新配置。

### 3.2 零覆盖率度量 `[✅]`

无 Kover/JaCoCo 插件，无任何覆盖报告入口。因此"258 个用例"无法回答真正重要的问题：**这次改动碰到的那几条分支，到底有没有被测过。**

矛盾点在于：新增的 `HomeProPresentation.kt`、`XiaomiReliabilitySettings.kt` **都已经配了测试**（`HomeProPresentationTest` 4 例、`XiaomiReliabilitySettingsTest` 2 例），说明作者完全明白"决策逻辑要抽成纯函数才好测"，缺的只是把它制度化。

**建议方案**：① 引入 Kover，**只做报告不做门禁**，CI 上传 artifact；② scope 先限 `interception/` + `service/`（即 `AppauseAccessibilityService` 决策链的纯函数部分）；③ 门槛必须"先跑一周确认无假阴性再写进 CI"；④ 不要为了百分比给 2156 行的 Service 补 trivial 测试。

### 3.3 CI 门禁覆盖面 `[✅]`

| 项 | 现状 | 建议 | 理由 |
|---|---|---|---|
| 从不 `assembleRelease` | `ci.yml:25-29` | 至少加编译验证（签名可 stub） | `app/src/release/java/...` 的桩类与 release signing 从未被自动化验证过 |
| 报告只在 failure 上传 | `ci.yml:31-37` | 成功也上传（`retention-days: 7`） | 否则无法回溯某次构建跑了几条用例 |
| 无 `timeout-minutes` | 缺失 | 加 `timeout-minutes: 30` | 防 Gradle 卡死占满 runner |
| 无 concurrency | 缺失 | `concurrency` + `cancel-in-progress` | PR 高频推送时排队无意义 |
| 注释过期 | `ci.yml:5` 写 "the 134 JVM tests" | 改为不写具体数字或更新 | 实际约 258，别把过期注释当验收基准 |

### 3.4 APK 体积 12.2 MB，主因是 `material-icons-extended` `[✅]`

- 最近 release APK `output/Appause-v0.5.43.apk` = **12,257,142 字节**；而 `app/src/main/res` 总共只有约 100 KB（`drawable/` 只有 1 个文件），几乎无图片资源。
- **主因**：`app/build.gradle.kts:151` 引入 `material-icons-extended` —— 该 artifact 含约 **2500+ 图标**；而全项目实际只用到 **25 个**（全部 `Icons.Default.*`，仅一个 `Icons.Filled.CheckCircle`）。
- **放大器**：`:99` `isMinifyEnabled = false` ⇒ **无 R8 裁剪、无资源 shrink**，这些图标全部打进 APK。
- **零收益项**：`:150` `androidx-ui-tooling-preview` 是 `implementation`，而全仓库 **`@Preview` 注解数 = 0**。

**建议方案（顺序很重要）**
1. **先换依赖**：`material-icons-extended` → `material-icons-core`，并删除 `ui-tooling-preview`。纯依赖交换，零重构，预期收益最大。
2. **再单独开 R8**（独立 PR）。`app/proguard-rules.pro` 目前是**空模板**，开 R8 前必须先补 Room Converter、无障碍 Intent extra 序列化相关的 keep 规则，并**在真机跑完整拦截路径**。
3. 不要合并 1 和 2 —— 一旦混淆裁掉了某个反射路径，你会分不清是图标切换、编译配置还是混淆导致的失败。

### 3.5 构建可重现性与 Gradle 配置 `[✅]`

- `app/build.gradle.kts:50-54`：`buildConfigField("String","BUILD_TIME", "\"${Date()}\"")`，值在**配置阶段**取 `Date()` ⇒ 每次 Gradle 调用都产生不同值 ⇒ BuildConfig 及其所有依赖方**永远不能 up-to-date**，增量构建白做。同时构建不可重现。
  （该值有真实用途 —— 诊断页靠它证明"装的是哪个包"，别删。建议改为由 `-PbuildTime=` 注入、缺省读 git commit 时间。）
- `gradle.properties`：`org.gradle.jvmargs=-Xmx2048m` 对 AGP + KSP + Compose 偏低，建议提到 **3072–4096m**。
- 配置缓存未开启。AGP 8.7 + KSP 支持度不一，建议先本地验证一次 clean build 再考虑推 CI，不要直接上。
- **`.gitattributes` 缺失**：`core.autocrlf=true` 且仓库内混 LF，Git 正在持续输出 `LF will be replaced by CRLF` 警告，污染每次 diff 输出。

### 3.6 结构化并发缺失 `[✅ 位置已确认]`

`OverlayManager.kt:432/455/571`、`PauseActivity.kt:356/391/432` 共 6 处 `CoroutineScope(Dispatchers.IO).launch {}`：**临时创建、从不 cancel、与任何组件生命周期无关**。`finish()` 之后这些协程仍在跑。

`AppauseApp.kt:79` 的 `GlobalScope` 我判断是**合理接受**（一次性启动清理，注释已写明理由），不需要改。

**建议**：`OverlayManager` / `PauseActivity` 各持有自己的 `SupervisorJob() + Dispatchers.Main.immediate` 成员 scope，在 `onDestroy` / `removeView` 时 cancel；悬浮层的「隐藏 → 重建」路径加 `Job` 句柄，新任务起来前 `job?.cancel()`，避免重复堆叠。

### 3.7 取消记录可能永不落库 + Locale 覆盖三处双轨 `[✅]`

- **丢记录**：`PauseActivity.kt:356-358` 用 fire-and-forget 协程写 `logLaunch("cancelled")`，随后主线程立刻 `noteCancelled`（`:369`）、`startActivity(homeIntent)`（`:376`）、`finish()`（`:377`）。**不需要任何并发竞争，单线程顺序就会发生**：用户回桌面顺手清后台（极常见）⇒ 这条记录永不入库 ⇒ 首页"今日取消"与 Stats 环形图**永久漏记**。同构位置：`:391`（"proceeded"）与 `:432` 附近的 Temporary Pass 授权。
  建议把 `logLaunch` 与 bypass 清理绑进同一个 `lifecycleScope` 任务，或改用能保证落盘的机制（不要用 `runBlocking` 阻塞主线程）。
- **Locale 双轨**：三处各自独立实现，机制还不一致 —— `AppauseApp.kt:97-121`（只用 `createConfigurationContext`）；`MainActivity.kt:50-62` + `:70-78`（`createConfigurationContext` **再叠一次** `@Suppress("DEPRECATION") resources.updateConfiguration`）；`PauseActivity.kt:167-179` + `:191-192`（与 MainActivity 完全同构）。`"appause_locale_prefs"` 的读取和 `== "zh" ? "zh" : "en"` 判定被抄了三遍，三处都各调一次 `Locale.setDefault`（进程级副作用）。这是语言行为漂移的结构性根因。
  建议抽单点 `LocaleOverride.apply(base: Context): Context`；并可借 target-36 迁移顺手换成 AndroidX `AppCompatDelegate.setApplicationLocales` / Android 13+ `LocaleManager`。

---

## 4. 代码层风险清单（合并两份深度分析）

### 4.1 P1

| 位置 | 问题 | 后果 | 建议 |
|---|---|---|---|
| `ui/pause/CountdownState.kt:53-80` + `AppauseAccessibilityService.kt` 多处 | 每 16ms 写 `smoothProgress`（提权的 State），拖动整棵暂停页（含 `BoxWithConstraints`/`AnimatedContent`/`LazyRow`）按 60fps 重组，最长持续整个冷却时长 | **从性能问题升级为功能问题**：服务未声明 `android:process`（`AndroidManifest.xml:109-120` `[✅]`），该 Compose 树的重组与 `onAccessibilityEvent`、`serviceScope(Main)` 共用一根主线程，会挤掉 750ms / 800ms / 1000ms 那几个毫秒级正确性窗口（`:475`、`:508`、`:530`、`:533`），使**"取消后重新打开反被放行"这个历史反复复发的 bug 复现** | 倒计时数字保留 1s 粒度；`smoothProgress` 不提权为 State，改在 Canvas 内读 `withFrameMillis` 或用 `Animatable`；overlay 路径至少降到 30fps |
| `AppauseAccessibilityService.kt:669 / :1334 / :1771-1775`、`OverlayManager.kt:208,213` | 在 Main 线程做 PackageManager / UsageStats `queryEvents`（binder IPC）。同文件 `:1002-1007 / :1105-1110 / :1653-1658 / :1854` 都正确包了 IO，唯独这几处漏了 | 阻塞无障碍回调线程，ANR 风险 + 上述时序窗口失准 | 统一 `withContext(Dispatchers.IO)` |
| `SettingsDataStore.kt` 全文件 + `NavGraph.kt:97-113` | `app/src/main` 全仓**没有任何 `catch (IOException)`**；`hasCompletedOnboarding.first()` 无 try/catch | `preferences_pb` 损坏或磁盘满 ⇒ flow 以 IOException 终止 ⇒ `startDestination` 永远为 null ⇒ **启动后永久白屏，必须卸载重装** | DataStore 统一 `catch (IOException) { emit(emptyPreferences()) }`；`NavGraph` 用 `.catch { emit(false) }` 并落日志 |
| `data/pro/ProState.kt:302-326` + `:277-294` | entitlement 轮询循环在 TRIAL/EXPIRING/DEBUG 期间 `delay` ≤60s 永不结束，每次迭代都重做 `KeyStore.load`（IPC）+ 2048 位 RSA `KeyFactory.generatePublic`；该 flow 无 `flowOn`，运算落在收集方（ViewModelScope = Main） | 进程被无障碍服务长期保活（可达数天）⇒ 每天上万次冗余密码学运算 + 主线程卡顿 | 公钥提为 companion `lazy`；设备指纹进程内 memo；flow 加 `.flowOn(Dispatchers.Default)` 与 `.distinctUntilChanged()` |
| `ui/appselect/AppSelectViewModel.kt:73-91` | 手写 `MutableStateFlow + launch{collect}` 替代 `stateIn`，且 `:78-82` 的 `lowercase()+contains()` 过滤**没有 `flowOn`** | 每次按键都在主线程对 100–300 个 App 做两轮字符串分配，搜索框明显掉帧；写法也与项目其它 ViewModel 不一致 | 改 `.flowOn(Dispatchers.Default).stateIn(..., WhileSubscribed(5000), emptyList())`，必要时加 `debounce(200)` |
| `AppSelectScreen.kt:197-206`、`GroupEditorScreen.kt:159-168 / 852-870`、`RecommendedAppsScreen.kt:177-195`、`TopAppsList.kt:67-80` | 在 Composable 组合期做 `getApplicationIcon().toBitmap()` 与 `getApplicationInfo`，只在 `remember` 里缓存 | LazyColumn 滑出再滑回整批重新解码；`getApplicationIcon` 是 binder IPC + 位图解码，长列表滚动必然 jank | 抽进程级 `AppIconCache`（`LruCache` + `Mutex`），在 `produceState(Dispatchers.IO)` 里异步取；或直接用 Coil |
| `ui/stats/BarChart.kt:79 / :121 / :149-154` | `Paint()`、`maxOf{}`、`substringAfterLast` 全在 `DrawScope` 内，每柱每帧各一次 | 800ms 入场动画 ≈ 336 次 Paint + 336 次 String 分配，GC 抖动掉帧 | `remember(density, color) { Paint() }`；`maxValue` / `dayLabels` 用 `remember(data)` 预计算 |
| `ui/home/HomeScreen.kt:419`、`ui/pro/ProScreen.kt:84-104`、`ProScreen.kt:486-487` | `homeProPresentation(entitlement, System.currentTimeMillis())` 在 Composable 体内裸调用；`countdownNowMillis` 写在顶层；`SimpleDateFormat` 未 remember | 每次重组重算且结果不可引用相等，阻断 Compose 智能跳过；Pro 页每秒重组整页含 5 张 Card；`SimpleDateFormat` 构造很贵 | `remember(entitlement.status, entitlement.expiresAt) { ... }`；倒计时下沉到只渲染该 Text 的子 Composable；`remember { SimpleDateFormat(...) }` |
| `InterceptorDecider.kt:268,276,293,327` → `AppauseAccessibilityService.kt:269-306` | `pauseShown` 是 lambda 而非快照，而它的 getter **带副作用**（会 `releasePauseGuard` 并覆写 `lastDecision`） | 单次决策里被求值 3–4 次，可能把 `lastDecision` 覆写成看门狗文案，**挤掉真实拒绝原因**，让诊断页/反馈报告指向错误方向 | 拆成 `computeGuardState()`（纯）+ 显式副作用；decider 接收快照 Boolean |
| `AppauseAccessibilityService.kt:1305-1306` vs `:978 / :1194 / :1230 / :1897` | `lastEventForeground` 的读-改-写成对出现，只有 `handleForegroundChangeImpl` 受 Mutex 保护，其余四处直接写同一个 `@Volatile` | 丢失更新使 step 2.6 去重的 `previousEventPackage` 采样失真，回到历史 bug 形态 | 所有写收敛到受锁保护的 accessor |
| `AppauseAccessibilityService.kt:686-731` vs `InterceptionManager.kt:41` | 服务重绑时只恢复了 Temporary Pass，**没恢复 bypass 会话与 re-remind 循环**；而 `InterceptionManager.bypassedPackages` 是进程级 `ConcurrentHashMap`，不会被清 | HyperOS 上服务被杀重绑是常态。若重绑发生在用户正停留在已放行 app 内 ⇒ bypass 残留 + 定时器全丢 ⇒ **无限使用且再无提醒**，直到切走才 reArm | `onServiceConnected` 里对 bypass 快照逐个 reArm，与 Temporary Pass 走同一套恢复 |
| `AndroidManifest.xml:6` vs 全仓 grep | 声明了 `POST_NOTIFICATIONS`，但**没有任何 `requestPermission` / `RequestPermission` 调用**（已全仓 grep 确认） | API 33+ 未授权 ⇒ `notificationManager.notify` 被框架静默丢弃 ⇒ 常驻状态通知恒不出现，反而污染诊断结论（"没通知"被用户当作"服务没在跑"） | 在引导/设置页补运行时申请，或明确改为用无障碍绑定状态自证 |

### 4.2 P2

| 位置 | 问题 | 建议 |
|---|---|---|
| `AppauseAccessibilityService.kt:777` | 用 `Context.RECEIVER_EXPORTED` 注册 `ACTION_CLOSE_SYSTEM_DIALOGS` 接收器 | 无需外部发送者却对外导出，第三方可伪造强制撤走冷却窗 → 改 `RECEIVER_NOT_EXPORTED` |
| `AppauseAccessibilityService.kt:1718 / :1814 / :1817 / :1825` | `reRemindJobs[pkg] = job` 在 `launch` **之后**赋值，协程体在 `:1814` 才 `remove` | `scope.cancel()` 后留下已死 Job，`cancelReRemind` 拿到过期引用 → 用 `compute` 原子化 + `invokeOnCompletion` 清理 |
| `OverlayManager.kt:826-897` | `dismiss()` 立刻把 `overlayView/overlayAttached` 置空，真正 `removeView` 延后 100–300ms | 「已 detached」与「窗口仍在」不一致期长达 300ms → 把 `overlayAttached` 置空延到 `removeNow` 执行后 |
| `OverlayManager.kt:682-721` | addView 全失败走 Activity 兜底时**没有**清 `overlayGeometryReapply`（`:362`） | 泄漏 WindowManager + LayoutParams 引用 → 失败分支补置空 |
| `OverlayManager.kt:143` / `:891-892` | `overlayScope` 字段只有读、从未赋值 | 死代码，误导读者 → 删除 |
| `OverlayManager.kt:756-761` | PendingIntent requestCode 固定 `0` + `FLAG_UPDATE_CURRENT` | 两个不同 targetPackage 同时需 AlarmManager 兜底时互相覆盖 → 用 `targetPackage.hashCode()` |
| `OverlayManager.kt:924` + `MainActivity.kt:74` 等 3 处 | 弹窗路径每次 `Locale.setDefault`（进程级副作用） | 改用 `LocaleManager` / `createConfigurationContext` |
| `util/PersistentLog.kt:58-72` + `AppauseAccessibilityService.kt:2146` | 每个生命周期回调都调 `AccessibilityServiceChecker.isEnabled()`（两次 Settings.Secure IPC），且 `PersistentLog` **release 也执行**、无 DEBUG 守卫 | 主线程磁盘 IO + IPC → 移到 IO 线程；release 改为异步或不写 |
| `util/LogBuffer.kt:47-55` | 每条日志 `_lines.value = lines.toList()`（600 行拷贝）且**在 `synchronized(lock)` 内 emit** | 密集日志时复制开销 + 持锁 emit 重入风险 → 改 `SharedFlow`/批量发布 |
| `ui/onboarding/OnboardingViewModel.kt:131` | `if (modelClass.isAssignableFrom(OnboardingViewModel::class.java))` —— **方向写反了** | 请求父类（`ViewModel`/`AndroidViewModel`）时会 classCast 崩；`ViewModelFactoryContractTest` 只校验构造子存在，覆盖不到 → 修正方向 + 补反向测试 |
| `ui/navigation/NavGraph.kt:106-111` 及所有导航 lambda | 未 `remember`；另 `:9 / :11` 重复 import `setValue` | 每次重组生成新实例，所有 `composable` 目标无法跳过重组 → `remember(navController) { ... }` |
| `MainActivity.kt:89`、`ui/pause/PauseActivity.kt:226` | 全仓仅有的 2 处 `.collectAsState(...)`，其余 50+ 处都用 `collectAsStateWithLifecycle` | Activity 退后台仍收集 DataStore Flow → 统一 |
| `ui/appselect/AppSelectScreen.kt:289-292` | `object AppSelectScreen { var cachedSelectedPackages / cachedInitialPackages }` 全局可变静态中转 | 两个来源共用同一槽位，靠"谁先重组谁消费"决定归属；进程被杀后选择直接丢失 → 迁到 `savedStateHandle` |
| `ui/recommended/RecommendedAppsViewModel.kt:26-30`、`PauseActivity.kt:234-238 / 251-257 / 265-275` | init / `LaunchedEffect(Unit)` 里做无限 `collect` 替代 `stateIn` | 无生命周期感知 → 统一 `stateIn(..., WhileSubscribed(5000), ...)` |
| `PauseActivity.kt:203-213` | onCreate/`setContent` 前在主线程做两次 PackageManager IPC | 直接延长暂停页首帧 —— 而暂停页对"立刻可见"有强需求 → `produceState(Dispatchers.IO)` 异步加载，先用包名占位 |
| `res/values/strings.xml` | **364 条 `<string>` 中 64 条零引用**（已排除 XML/Manifest 引用，确认无 `getIdentifier` 动态引用，测试也未引用），另有 1 条死 plurals `delete_message_recommended` | 真死资源，可清理。**注意**：`pro_free_cooldown` / `pro_pro_cooldown` / `pro_feature_history` / `stats_free_limit_*` 不仅是死串，数字还是旧商业模型的残留，写文案时**绝不能参考** |
| `FeedbackScreen.kt:362-386` | 8 处诊断文案硬编码中文（`"--- 诊断状态 / Diagnostics ---"` 等） | 双语硬写在 Kotlin 中，翻译无法走资源体系 → 提取或明确为"仅开发者可读" |
| 默认冷却值 | `AppGroup.kt:39` `cooldownSeconds` **无默认值**；`GroupEditorViewModel.kt:54` 初值 `MutableStateFlow(10)`；`strings.xml:115-116` 只写范围 `1s`–`60s` | 三层没有单一真源 → 补一个常量并让三处引用同一处 |
| 仓库卫生 | 根目录 `NUL/`（4 个 wrangler 日志，2026-09-22 误建）；`.playwright-mcp/` 未跟踪 | 只加 `.gitignore`，**不删文件**（沙箱删除易失败） |

---

## 5. 做得好的地方（请在"优化"时不要误伤）

1. **隐私边界清晰且自洽**：`canRetrieveWindowContent="false"`（`accessibility_service_config.xml:24`）+ `AppLogger` release 零 logcat（`AppLogger.kt:19-61`）+ `allowBackup="false"`（`AndroidManifest.xml:60`）+ `INTERNET` 权限注释明写"仅用于一次性 Pro 兑换"。这四件事是 MIT 开源本地工具的核心卖点。
2. **测试策略本身是对的**：把 OEM/时序相关决策抽成 `*Policy` 纯函数再用 JVM 测试钉住 —— `interception/` + `service/` 下 18 个测试文件、约 258 个用例，其中 `InterceptionDeciderTest`(28)、`HomeTransitionPolicyTest`(27)、`ProStateRedeemTest`(27)、`TemporaryPassPolicyTest`(24)、`LicenseVerifierTest`(20) 是硬资产。`InterceptionInvariantTest` 还做了随机不变量测试。
3. **`SessionState.kt` 与 `InterceptionManager.kt` 用 `ConcurrentHashMap` 且注释解释了理由** —— 这正好反证 2.3 是漏做而非架构取舍。
4. **网络调用有边界**：15s connect/read timeout 都设了、`disconnect()` 也调了（`ProState.kt:156-169`、`FeedbackScreen.kt:332-341`）。唯一小瑕疵：`outputStream.use{}` 抛异常时 `disconnect()` 走不到，建议改 `try/finally`。
5. **`FLAG_IMMUTABLE` 已正确使用**（`OverlayManager.kt:760`），符合 target-31+ 要求。
6. **`XiaomiReliabilitySettings.kt:26-43` 的异常处理是范本**：三重 catch + 二次降级到系统 App Details。
7. **Room 没有 `fallbackToDestructiveMigration`**（`AppDatabase.kt:148-159`），加上迁移链 1→6 完整声明，老用户数据不会静默丢。
8. **零 `TODO/FIXME`**（main + debug 全扫结果为 0），注释密度高且在解释 "why"。

---

## 6. 建议执行顺序（已按"互相掩盖失败原因"的风险排过序）

| # | 动作 | 章节 | 是否阻断发版 | 改动面 |
|---|---|---|---|---|
| 1 | 确认 Play 上架需求 → 决定是否上 target 36 | 2.1 | **是** | 小改动 + 大范围真机验证 |
| 2 | 拆分工区提交，让 CI 对当前源码产生有效记录 | 2.2 | 否 | 小 |
| 3 | 五个 Map 改 `ConcurrentHashMap`（含 `OverlayManager` 的 `${pkg}Present`） | 2.3 | 否 | 小 / 收益极高 |
| 4 | `saveGroupWithApps` 加 `@Transaction` + 迁移元测试 | 2.4 | 否 | 小 |
| 5 | 修 `GroupEditorViewModel` 静默清零 + `isPro` 竞态 | 2.5 | 否 | 小 |
| 6 | PauseIntent extra 抽工厂 + `canScheduleExactAlarms()` 显式化 | 2.6 | 否 | 小 |
| 7 | CI 加 `lintDebug` + baseline 台账 | 3.1 | 否 | 小（配置） |
| 8 | Kover **只报告不门禁**，scope 限 `interception/`+`service/` | 3.2 | 否 | 小（配置） |
| 9 | `material-icons-extended` → `core`，删 `ui-tooling-preview` | 3.4 | 否 | 极小（纯依赖） |
| 10 | BUILD_TIME 可重现化 + Gradle 内存/超时/concurrency + `.gitattributes` | 3.5 | 否 | 小（配置） |
| 11 | `CountdownState` 重组降到 draw 阶段 + 主线程 IPC 移出 | 4.1 | 否 | 中 / 需真机验证 |
| 12 | DataStore IOException 兜底（白屏风险） | 4.1 | 否 | 小 / 收益高 |
| 13 | 其余 P1/P2 批量 + 清理 64 条死字符串 | 4.x | 否 | 中 |
| 14 | 开启 R8（**独立 PR，必须真机全链路验证**） | 3.4 | 否 | 中大 / 高风险 |

**禁止把 1 / 9 / 14 合并**：R8 一旦裁剪了某个反射路径，你会分不清是图标依赖切换、编译配置还是混淆导致的失败。

---

## 7. 本次评估未做的事（避免误读）

- **未运行** `./gradlew` 任何任务 ⇒ 没有有效测试报告、lint 报告、覆盖率报告。所有"建议"在执行前仍是**待验证**状态。本文只声明"已配置"，不声明"已执行""已通过"。
- **未核对真机行为**（无障碍绑定、HyperOS 杀后台、悬浮层可见性）。真机结论请以 `docs/REPEAT_INTERCEPTION_STRESS_TEST.md` 与 `docs/accessibility-hyperos-review.md` 为准。
- 本次评估**未涉及依赖漏洞 / 许可证 / SBOM**（属于软件成分分析，需联网扫描，未授权）。这与"静态代码分析"是两项不同的工作。
- 行号基于 2026-10-01 的工作区快照；源文件移动后需重新定位。
- 未标记 `[✅]` 的条目来自子代理静态分析，行号可靠但不等于已复现。

---

## 8. 待你决策

1. **是否仍维护 Google Play 上架？** 决定 2.1 是 P0 还是降为 P2。
2. **是否接受「先换 icons 依赖、后单独开 R8」分两步？** 还是一次性做完（风险更高）？
3. **是否授权我执行只读的 Gradle 任务发现**（如 `./gradlew --dry-run` 列任务树）来验证 CI 步骤？还是继续保持"完全不执行构建"？
4. **是否要我把第 4 章写成可执行的改动方案**（精确落点 + diff 文本，仍不改代码）？如果要，建议从 #2–#5 这 4 条开始，它们都是小改动高收益。
