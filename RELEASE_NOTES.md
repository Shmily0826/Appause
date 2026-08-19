# Appause v0.5.38 — 发布说明 (Release Notes)

> 复制本文件内容粘贴到 GitHub Release 的 Description 即可。
> 下载链接：`https://github.com/Shmily0826/Appause/releases/download/v0.5.38/Appause-v0.5.38.apk`

---

## 🇨🇳 中文

### 本次更新 (v0.5.38) — 先体验再授权 + 发布说明校正
- **首次引导改为价值优先**：语言选择后先展示真实暂停页预览，再说明产品作用和隐私边界，之后才进入权限设置，减少第一次使用时连续授权带来的压力。
- **隐私说明提前**：在请求无障碍服务前明确说明，Appause 只识别当前打开的应用，不读取屏幕内容、聊天记录或账号信息。
- **中文表达优化**：引导页改用「等待时间」「提醒页面」等更自然的说法，英文内容同步更新。
- **界面无障碍与本地化修正**：诊断页标题、返回和刷新说明改用资源文案；设置页图标补充可读说明。
- **安装与隐私文档校正**：显示悬浮窗是可选兼容性备用权限，不再写成必需；更新时应直接覆盖安装，不能先卸载；用户主动发送反馈前会看到诊断内容，其中可能包含分组名和应用包名。
- **发布资料更新**：README、安装指南、隐私政策、酷安材料、中英文截图和国内/海外收款路线说明与当前实现保持一致。

### 本次更新 (v0.5.37) — 无障碍覆盖层稳定拦截 + 反馈自带诊断
- **悬浮窗权限改为「可选」**：暂停屏走无障碍覆盖层（TYPE_ACCESSIBILITY_OVERLAY，小红书无法藏掉），通常不需要「显示悬浮窗」权限。引导页与设置页已说明：若发现暂停页不显示、或拦截没有生效，再回来开启它重试。未开启时不再显示为红色告警。
- **反馈自动附带诊断状态**：在「设置 → 反馈」提交问题（bug / 建议）时，会自动附带本机结构化诊断状态（无障碍服务是否在运行、总开关、电池豁免、悬浮窗权限、最近一次拦截判定、被控应用最近判定、阻断界面方式、分组数等）。通过邮件 / GitHub / Appause 发送都会带上，你收到反馈就能直接看到「为什么没拦住」，无需让用户手动开诊断页复制。说明卡已注明：不含任何应用使用记录或隐私内容。
- **文案优化**：引导页去掉「分心应用」等不准确表述（改为「目标应用」），并整体润色更通顺；权限说明同步更新。
- **细节修复**：新建/编辑分组页滑块内缩、冷却输入更紧凑、分组名为空时的错误提示时机更合理、Pro 锁定区合并为单个升级按钮；统计页「原因分析」进度条去掉误绘的悬浮端点。
- **调试工具（仅 Debug 版）**：诊断页新增「重新开启引导页」按钮（与开关 Pro 并列），方便反复测试引导流程。Debug 包（`com.appause.android.debug`）才有，Release 包不含。

### 本次更新 (v0.5.29) — 新增「电池优化」警告 + 校正权限说明
- **首页新增醒目的「电池优化未关闭」红色警告**：在 HyperOS / 小米上，若 Appause 的电源限制是「智能」（未豁免省电），系统会**在后台杀掉无障碍服务、且不会自动重启**——表现就是最经典的那句抱怨：「第一次打开被限制的应用不拦截，切回 Appause 才拦，再开才正常」。以前只有 debug 诊断页提了一句，普通用户根本看不到。现在只要没设成「无限制」，首页顶部就常驻这条红色警告，点一下直接跳到「电池优化」设置，把坑挡在前面。
- **校正权限说明（重要）**：之前首页把「显示悬浮窗」列为必开权限，这是**错的**。停顿屏用的是 `TYPE_ACCESSIBILITY_OVERLAY`（2032）覆盖层，小红书无法藏掉它，且**不需要**「显示悬浮窗」权限；反而授予它会让部分路径回退到 2038、被小红书藏掉。正确的权限要求见下方「必须开启的权限」。
- 诊断页（debug）也新增「电池优化（省电策略）」状态行，并写入诊断报告。
- 升到 v0.5.29 / 81。

### ✅ 必须开启的权限（首次使用请逐项确认）
1. **无障碍服务（必须）** — 系统「设置 → 无障碍 → Appause」打开开关。这是 Appause 检测前台 app 的唯一方式。
2. **电池优化 = 无限制（必须）** — 系统「设置 → 应用 → Appause → 电池 → 无限制」。**不设这个，拦截会静默失效**（服务被后台杀掉且不重启）。v0.5.29 首页会直接警告你。
3. **使用情况访问（强烈建议）** — 系统「设置 → 隐私 / 安全 → 使用情况访问 → Appause」。让前后台判定最准；不授权也能拦，但可能误判前台 app。

> ⚠️ **不需要**「显示悬浮窗（悬浮窗 / 在其他应用上层显示）」：新版停顿屏用无障碍覆盖层（2032），小红书藏不掉它，且无需该权限。授予它反而可能让停顿屏走 2038 被小红书藏掉。
>
> 额外建议（小米 / HyperOS）：在「最近任务」把 Appause 卡片**锁定**，并在应用设置里允许**自启动**，服务更稳。

### 本次更新 (v0.5.28)（已被 v0.5.29 取代，但 2032 窗口类型修复保留）
- 彻底修好「第一次打开小红书不拦截 / 看不到停顿屏」：根因是悬浮窗窗口类型选错。反编译能用的 release（base.apk / v0.5.1）证明它用 **`TYPE_ACCESSIBILITY_OVERLAY`（2032）**，而小红书的 `setHideOverlayWindows()` 会把 **`TYPE_APPLICATION_OVERLAY`（2038）** 藏掉。v0.5.28 把 2032 设为唯一优先类型。该修复在 v0.5.29 中保留。

### 本次更新 (v0.5.27)（已被 v0.5.28 的根因修复取代）
- 曾把悬浮窗改回 2038 优先——但实测 2038 在 HyperOS/小红书 上被 `setHideOverlayWindows` 藏掉，故 v0.5.28 改用 2032 优先。（0.5.27 仍贡献了守卫宽限 8s→1.5s + 30s 硬上限的修复，已保留。）

### 本次更新 (v0.5.26)（已被 v0.5.27 回退）
- 原方案：停顿屏改用真正前台 Activity（PauseActivity）。实测在 HyperOS 上小红书会重新前台盖住 Activity、且守卫卡死，故 v0.5.27 改回悬浮窗优先方案。

### 本次更新 (v0.5.25)
- **修好「第一次打开小红书不拦截」**：v0.5.24 偶尔出现——首次打开 xhs 停顿屏不弹，切去 Appause 再切回来才拦，第三次起正常。根因是去重守卫（`lastForegroundPackage` 相等就静默跳过）记了**过期**的「当前前台 = xhs」：HyperOS 吞掉「离开 app」的窗口事件，且 `getForegroundPackage()` 对受控 app 不可信（后台的 xhs 也可能被报成前台），于是下次真开 xhs 时被当成「同 app 内切换」直接吞掉，没有日志、没有拦截。修法两条：(1) 去重守卫改为只在「**上一条事件也是同一个 app**」时才跳过——这能区分「真重开（离开过→上一条是别的应用）」和「app 内切页（feed→笔记）」；(2) 前台轮询器不再用 `getForegroundPackage()` 给受控 app 重新「确认前台」，避免它把后台的 xhs 误报成前台、污染去重状态。停顿屏现在每次真开都弹。升到 v0.5.25 / 77。

### 本次更新 (v0.5.24)
- **彻底修好「回到 Appause 才拦截 / xhs 和 bilibili 都不拦」**：v0.5.23 的诊断直接证明——这台 HyperOS 上系统用量日志对这两个 app **根本不可信**：开 xhs 时无障碍事件已经是 `event=com.xingin.xhs`，但 `getForegroundPackage()` 却返回 `com.appause.android.debug`（Appause 自己！），`wasResumedRecently(xhs)` 也是 false，于是确认逻辑把 xhs 判定为误报跳过；bilibili 只能靠 `wasResumedRecently` 在 600ms 复查那一刻拦到，但那时你已经回 Appause 了，停顿屏弹在了 Appause 里。根因就是「不可信的用量日志 + 600ms 延迟」。v0.5.24 **直接信任无障碍事件**：事件一到就立刻拦截（事件包名就是真前台），**唯一的抑制只剩「最近任务重放」风暴守卫**，且阈值从 2 个真实包提高到 3 个（`BURST_MIN_DISTINCT=3`）——真开一个 app 顶多 1 个、从另一个被控 app 直接切过来最多 2 个真实包，都碰不到 3，所以真启动永不误杀；而 HyperOS 的「重放」同一瞬间炸出一堆缓存任务（≥3 个真实 app），仍会被正确挡掉。停顿屏现在**在 app 还显示在屏幕上时就盖上去**，不再等你切回 Appause。升到 v0.5.24 / 76。
- 顺手把你之前要的「步骤 6.5 满诊断日志」保持下来：会打出 `6.5 event=… burstSuppress=… burstReal=…`，配合 `overlay added while fg=… type=…`，一眼能看清拦没拦、为什么。

### 本次更新 (v0.5.23)
- **回退 v0.5.11–v0.5.22 的「数包数风暴守卫」——它是误杀真启动的元凶**：你分组里 xhs 和 bilibili 都在，开 xhs 时无障碍事件簇里天然就有两个真实包，被守卫误判成「最近任务重放」直接 SKIP，所以两个 app 都不拦。对照 git 历史（tag v0.5.1 / commit 0c2504f，即你手机上能用的 release base.apk）确认：v0.5.1 根本没有这个守卫，拦截稳定。v0.5.23 把步骤 6.5 **完整恢复成 v0.5.1 的用量日志确认逻辑**（立即查，不一致则等 600ms 复查一次，仍不一致才算误报→跳过；未授权用量访问则直接放行）。
- **新增 `wasResumedRecently` 兜底（只增不减拦截）**：HyperOS 用量日志的「当前最前」可能滞后于一次极短的打开，但 RESUMED 事件仍证明用户确实打开了 app。现在即便「当前最前」读错，只要最近 5s 内有被控 app 的 RESUMED 就照样拦截——通知/最近任务重放不会产生 RESUMED，不会被误拦。
- **步骤 6.5 加满诊断日志（你要求的）**：打出 `6.5 #1 event=… usageLog=… burstSuppress=…` 与 `6.5 #2 usageLogRetry=… resumedRecently=…`，方便直接看清楚「系统用量日志到底返回了什么、为什么拦/不拦」。风暴守卫现在只记录、不再门控。
- 升到 v0.5.23 / 75。

### 本次更新 (v0.5.22)
- **照你手机上能用的 release（base.apk, v0.5.1）逐字节对齐修复**：反编译对比发现 base.apk 的代码和 debug 完全一样，差别在**运行时权限**——release 版没开「使用情况访问」，所以它的确认逻辑直接跳过、**事件一到立刻拦截**；而 debug 版开了「使用情况访问」，确认逻辑去读 HyperOS 的用量日志（已证实它根本不记录小红书的 RESUMED），结果要么误判、要么拖慢 200–600ms，停顿屏弹出来时已经错过时机。v0.5.22 **彻底不用用量日志确认**：事件簇沉降 60ms（只够识别最近任务重放）就立刻拦截，和 release 一样快。升到 v0.5.22 / 74。
- **停顿屏恢复悬浮窗优先（2038），与 base.apk 一致**：之前的失败是拦截太晚，不是悬浮窗类型不对。现在拦截几乎即时，悬浮窗在小红书窗口还没站稳时就盖上。addView 失败再退 2032 → PauseActivity。
- **修复 AlarmManager SecurityException**：Android 14+ 无 SCHEDULE_EXACT_ALARM 权限会抛异常，PauseActivity 兜底改用手头就有的 Handler 重拉（无需权限）。
- **真正修好「在 rednote/小红书 里看不到停顿屏、切回 Appause 才看到」**：对照**能成功的旧版 v0.4.7**（诊断 2026-08-06 14:36）发现根因——旧版悬浮窗 `addView` 抛 `BadTokenException` → **回退到 PauseActivity（一个 Activity）**，而 Activity 不受 `setHideOverlayWindows` 影响、小红书藏不了，所以停顿屏稳稳盖在小红书上面（实测停了 65s）。后来你授予了「显示悬浮窗」权限，`TYPE_APPLICATION_OVERLAY` 的 `addView` **不再抛异常、直接成功**，于是代码用悬浮窗、**不再回退到 PauseActivity**——但小红书会调 `Window.setHideOverlayWindows()` 把 `TYPE_APPLICATION_OVERLAY` 藏起来，悬浮窗加成功了却看不见。这就是"自从某版本后就不能正常拦截"的真相：不是拦截逻辑坏了，是悬浮窗成功反而绕过了能用的 PauseActivity。**修复**：① 优先用 `TYPE_ACCESSIBILITY_OVERLAY`（无障碍叠层，`setHideOverlayWindows` 对它无效，小红书藏不了）；② 若它抛异常，**直接回退 PauseActivity**（不再试会被藏的 `TYPE_APPLICATION_OVERLAY`），恢复 v0.4.7 那条被验证可用的路径。用 service 原始 context 取 WindowManager 以保住无障碍 token。另加诊断日志 `overlay added while fg=… type=…` 印证。升到 v0.5.20 / 72。
- 附带保留 v0.5.19 的时序改进（确认窗口从 600ms 缩到 ~200ms），让停顿屏在可见时也更跟手。

### 本次更新 (v0.5.19)
- **停顿屏现在"打开 app 的瞬间"就弹，不再要等 600ms、看起来像"切回 Appause 才拦"**：v0.5.18 虽然识别出了真启动，但仍沿用 v0.5.1 的 600ms 用量日志二次确认延迟——而窗口事件比用量日志早几百毫秒到达，于是判定要等 600ms 后才落。若你在这 600ms 内切回了 Appause（实测你确实常常这么做：16:20:14.125 开小红书，16:20:14.288 就回 Appause），停顿屏就弹在 Appause 里，而不是小红书里，看起来像"只在切回 Appause 后才拦"。v0.5.19 改为：**事件簇形状本身就是结论**——真启动的簇只有"被控 app 1 个真实包 + 启动器 + 系统组件"，开 app 的瞬间就已定型。现在只等约 200ms（刚好让"最近任务重放"的第二个真实包露出面）就拦截，比 600ms 快近 3 倍，且仍然能正确拦掉重放、仍然不受你切回 Appause 的影响。升到 v0.5.19 / 71。
- **彻底修好「开了又立刻回 Appause 看诊断 → 不拦截」**：v0.5.17 改成查"被控 app 最近有没有真正 RESUMED（被打开过）"，但实测发现 HyperOS 上**极短时间的打开（<~200ms）根本不会在用量日志里留下 RESUMED 记录**——而你开小红书后往往不到 200ms 就跳回 Appause 抓报告，于是 `wasResumedRecently` 返回 false，照样被跳过（诊断实测：09:05:24.657 开小红书，09:05:24.814 就回 Appause，仅隔 157ms）。v0.5.18 改用**三个互相独立的信号任一成立即拦截**：① 600ms 后用量日志仍显示被控 app 在最前；② 用量日志里查到被控 app 的 RESUMED；③ **无障碍事件簇本身是"真启动"形状**（被控 app + 启动器 + 系统组件 = 只有 1 个真实 app），而不是"最近任务重放"形状（同一瞬间炸出 2+ 个真实 app）。第 ③ 个信号读的是无障碍事件簇、不依赖用量日志，所以"开了就拦，回去早也不影响"这次真正成立。通知和最近任务重放仍会被 ③ 正确识别并忽略。
- **风暴守卫恢复参与判定，但不再单独门控**：旧版"数包数"风暴守卫（isBurstSuppressed）重新接回，但只作为上面 ③ 这一路信号，且是「三选一」的其中一项——它能帮忙拦掉明确的"最近任务重放"，却再也无法单独把一次真启动误杀（这是 v0.5.11–14 反复踩坑的根因）。同时把 Appause 自己的包排除在"真实 app"计数外，避免从 Appause 里点开被控 app 被误判为重放。
- 暂停屏文案保持单行"给自己几秒，再决定是否继续。"不变。升到 v0.5.18 / 70。

### 本次更新 (v0.5.17)
- **拦截现在"开了就拦"，不再被你自己回 Appause 看诊断的操作误杀**：之前步骤 6.5 在 600ms 宽限后复查"当前前台是不是被控 app"，而你的测试习惯是开小红书后立刻跳回 Appause 抓报告——此时系统前台已变成 Appause，于是被判定"不是真前台"而跳过（诊断里显示 `SKIP: not actually foreground (real=com.appause.android.debug)`）。现改为复查"被控 app 在最近窗口里有没有真正 RESUMED（被打开过）"，只要真打开过就拦，回去早也不影响。通知、最近任务重放仍不会产生被控 app 的 RESUMED，照常忽略。
- **暂停屏文案收敛为单行**：只保留"给自己几秒，再决定是否继续。"，去掉了之前多加的第二行提示。纯文案改动本不升版本号，但因本次同时改了拦截逻辑，升到 v0.5.17 / 69。

### 本次更新 (v0.5.16)
- **与能正常使用的旧 release（base.apk = v0.5.1）逐行对齐拦截逻辑**：对照旧版反编译 + git 历史（commit 0c2504f / tag v0.5.1）确认，旧版能稳定拦截靠的是「读系统用量日志（ForegroundChecker.queryEvents）作为权威判定 + 600ms 宽限期二次确认」，根本没有“数包数”的风暴守卫。v0.5.11 把这段用量日志判定整段删掉、只留不可靠的风暴守卫，才是“装了不拦截”的真正根因（HyperOS 真开 app 与最近任务重放发出的无障碍事件字节级相同，数包数分不出来）。v0.5.16 把步骤 6.5 完整恢复成 v0.5.1 的逻辑：立即查用量日志，若不一致则在 600ms 宽限后复查一次，仍不一致才算误报（通知）→ 跳过；一致即拦截。未授权用量访问时直接放行（同 v0.5.1）。
- **风暴守卫已彻底退出拦截判定**：旧版“数包数”的风暴守卫（recordWindowEvent / isBurstSuppressed / isBurstNoisePackage）不再参与门控，避免它把真启动误判为最近任务重放。
- **诊断页「被控应用最近判定」现在显示真正原因**：授权后若仍不拦截会显示 `SKIP: not actually foreground (real=…)`（真·误报，如通知），而不是旧的 `SKIP: burst replay`。

### 本次更新 (v0.5.15)
- **真·修复「装了不拦截」：恢复系统用量日志作为权威判定**。v0.5.11 把原来可靠的「真启动判定」（`ForegroundChecker` 读系统 `queryEvents()` 用量日志）整个删掉，改成只靠包数量的风暴守卫。这才是 v0.5.11–v0.5.14 连续四个版本都"还是不拦截"的根因——在 HyperOS 上，**真开 app 和最近任务重放发出的无障碍事件字节级相同**（目标包 + 启动器 + 一堆缓存任务的窗口事件，里面还夹着别的真实 app 比如邮件）。数包数根本分不出来，所以真启动被当成重放跳过了。现在步骤 6.5 在已授予"使用情况访问"时，直接问系统用量日志（HyperOS 不会伪造它）：真是你打开的 app 才有真正的 `ACTIVITY_RESUMED`，所以能准确识别。未授权时才退回风暴守卫兜底。
- **判定前等 400ms 再查系统**：窗口事件会比用量日志早几百毫秒到达，先等事件风暴平息、用量日志记好真前台，再判定，避免读到"打开之前"的 app。实测 400ms 既能准确判定，又不会像旧版 1s 那样出现"用户回 Appause 看一眼就判定失败"的死循环。
- **诊断页「被控应用最近判定」现在会显示真正原因**：授权后若仍不拦截，会显示 `SKIP: not the real foreground app (real=…)`（说明系统认定前台是别的 app）；只有未授权时才会显示旧的 `SKIP: burst replay`。

### 上版本 (v0.5.14)
- **彻底修好「不拦截」：不再靠手写系统包名单**。v0.5.11–v0.5.13 连续三个版本都栽在同一个坑：风暴守卫要区分"真启动"和"最近任务重放"，靠的是一份手写的 OEM 系统组件名单；而 HyperOS 每次开 app 附带的组件都不一样（搜索框、SystemUI 插件、个人助理……），只要漏了一个，就会被当成"第二个真实 app"→ 真启动被误判为重放 → 不弹停顿屏。现在改为直接问系统：凡是随系统镜像预装的包（`FLAG_SYSTEM`）一律算背景噪音，不再逐个写名字。真启动永远只剩「你点开的那一个」真实包 → 稳定拦截。
- **风暴判定改为相对目标 app**：只有当同一簇事件里出现了目标 app **之外**的真实 app，才算最近任务重放。这样即便被控 app 本身是厂商预装（例如自带浏览器），也不会自己把自己的拦截压掉。
- **修复风暴守卫过度抑制（真启动被误判为 phantom）**：HyperOS 真从桌面开 app 时会在同一毫秒内发出 目标包 + 启动器 + 系统搜索框 + SystemUI 插件 等多个包，旧规则「120ms 内 ≥3 个不同包即判风暴」把这些系统包也算进去，导致真启动被跳过。现改为只统计「非系统、非启动器」的真实 app 包，且需 ≥2 个真实 app 才判为风暴——真启动（仅目标 1 个真实包）正常拦截，最近任务重放（含第二个真实 app 如 personalassistant）仍被挡住。
- **权限说明可折叠 + 每条另起一行**：设置 → 权限页顶部「为什么需要这些权限？」改为可点开/收起的卡片，展开后每条说明各自占一行，不再挤成一大段；首页弹窗同步采用同样的逐条排版。
- **权限已授权就隐藏「打开设置」按钮**：无障碍、显示悬浮窗、使用情况访问三项一旦已授予，对应的系统设置入口按钮直接隐藏，避免「已经开了还让我点」的困惑。
- **消灭权限页红→绿闪烁**：进入权限页前会先读取真实权限状态，不再默认显示红色「未开启」再刷新成绿色。
- **关于页不再显示原始 true/false**：调试信息改为显示「已开启 / 未开启」并带颜色；顺手修了 Android 版本号之前一直显示 "%d (%s)" 的 bug。
- **开源卡排版优化**：底部「开源可审计」卡片的按钮改为描边样式并带上外链图标，不再占满整行。

### 本次更新 (v0.5.1)
- **修复「用着用着突然不拦截了」**：停顿屏有个内部标记，用来避免倒计时期间重复弹窗。原先这个标记只在停顿屏正常关闭时才复位——一旦覆盖层没能显示，或停顿页被某些 App 挤到后台，标记就会永久卡住，之后**所有**拦截都被静默跳过，表现就是 App 突然失灵。现在加了看门狗：只要屏幕上既没有覆盖层、也没有可见的停顿页，标记会自动释放。
- **首页权限提示合并成一条**：原来无障碍和悬浮窗分两处提醒，现在统一为顶部一条「请开启必要权限」横幅，缺哪个列哪个，点「去开启」直达系统授权页。
- **设置里说明每个权限的用途**：新增「为什么需要这些权限？」卡片，逐条讲清用途和边界（例如悬浮窗只用于绘制 Appause 自己的停顿屏）。
- **新增「开源可审计」说明**：注明 MIT 许可证、代码全部公开、无账号、无埋点，并提供仓库直达按钮。
- **修复统计页「原因分布」不跟随语言**：切换到英文后，原因标签仍显示中文。原因是标签在语言切换时没有重新取值，现已改为跟随当前界面语言。
- **进度条视觉修正**：原因分布的进度条右侧不再有一截多余的浅色轨道。
- **文档补齐悬浮窗权限**：README / 隐私政策 / 安装指南此前都没写这个权限，甚至写着"不需要"，容易让人装完以为坏了。现已全部更正为必需权限并说明用途。

### 本次更新 (v0.5.0)
- **修复小米 / HyperOS（Android 16）暂停屏不显示**：原来依赖的悬浮窗类型在国产 ROM 上被系统拒绝。现在改用**系统覆盖层 `TYPE_APPLICATION_OVERLAY`**（需要「显示悬浮窗」权限），窗口画在**所有 App 任务之上**——连小红书这类会主动把自己重新提到前台、盖掉普通弹窗的 app 也挡得住。
- **「显示悬浮窗」改为必开且可引导**：之前正式版因没声明该权限，系统设置里根本找不到入口，导致在小米上永远显示不出停顿页。现在首页会出现**红色警告横幅**、设置页新增「显示悬浮窗」权限卡片、首次引导也新增一步，点了直接跳到系统授权页，不再悄悄失败。
- **修复"5 秒弹出第二个窗口"**：兜底启动时原本会重建 PauseActivity、把倒计时归零，表现为"第二个窗口"。现已改为只把现有页面提前台、不重建。
- **后台启动更稳**：保留 `AlarmManager` 兜底拉起 PauseActivity，确保 MIUI/HyperOS 上能稳定把它提到前台。

### 本次更新 (v0.4.7)
- **提示语更直白**：引导页欢迎语去掉「给你片刻喘息」这类绕弯表述；暂停屏的「试着做几次深呼吸」改为「停顿几秒，再决定要不要继续」。
- **引导页分组步骤改为「暂停屏预览」**：原来的装饰性倒计时换成和真实拦截一样的动画（应用图标、名称、提示语、倒计时环 + 跳动数字），让用户直观看到打开分心应用时看到的界面；它只是概念演示，不催促建分组（底部「稍后创建」依旧可点）。
- **设置页把 Pro 移到顶部提示栏**：Pro 不再和其他设置项混在同一栏，而是放在设置页最上方的提示栏——已激活时显示「Appause Pro 已开启」，未激活时显示「升级到 Appause Pro」并可点击进入。
- **权限已授权显示绿色「已开启」**：设置 → 权限页里，无障碍、电池优化、使用情况访问三项，只要已授予就显示绿色的「已开启」（连同下方说明文字一并变绿），未授予才标红。

### 本次更新 (v0.4.6)
- **统计「原因分布」显示真正的标签**：之前会直接显示 `work`/`bored` 等英文键（切换系统语言后尤其明显）。现在会显示本地化的打开原因（如「工作需要」「无聊」），Pro 用户自定义过的文字也会正确显示。
- **反馈文案更通顺**：反馈页引导语与发送失败提示语在中文、英文下都做了润色。
- **权限页不再误用红色**：设置 → 权限页里「必需」徽标原本始终是红色，哪怕权限已授予也会红，容易让人以为还有问题。现在权限已授予时改为中性灰，未授予才标红。

### 本次更新 (v0.4.5)
- **引导页视觉优化**：每步新增 72dp 圆形图标作为视觉焦点；顶部增加步骤进度圆点（共 5 步，当前步高亮）；标题与说明改为居中，整体更像引导而非纯文字。
- **修复分组页两个「跳过」**：分组引导页底部次级按钮原先也叫「跳过」，与右上角全局跳过重复。已改为「稍后创建」，仅表示跳过建组、仍进入「一切就绪」页；右上角「跳过」仍是整段引导退出。

### 本次更新 (v0.4.4)
- **免费版分组数量限制调整为 1 个**：免费用户最多创建 1 个分组，达到上限后首页「新建分组」按钮显示锁形图标并跳转 Pro 介绍页；分组列表上方也会出现提示卡，说明「免费版最多 1 个分组，升级 Pro 解锁无限分组」。Pro 用户分组数量无限制。

### 本次更新 (v0.4.3)
- **修复分组名输入框光标跳动**：修改分组时，在名称框快速输入光标会跳到最左侧（输入框直接绑定了异步状态流导致）。已改为本地状态 + 外部同步的写法，输入顺畅。顺带复查了所有文本输入框：选择应用搜索框、反馈、激活码、设置提示语/原因等本就正确，无同类问题。

### 本次更新 (v0.4.2)
- **暂停设置页普通用户可见但上锁**：进入「设置 → 暂停设置」后，默认提示语和自定义打开原因两个区域都会显示；未解锁 Pro 时，输入框呈灰色禁用状态并带锁形图标，点击卡片可跳转 Pro 介绍页。
- **默认提示语占位文案更清晰**：输入框提示从「暂停画面信息」改为「暂停一下」，避免歧义。

### 本次更新 (v0.4.1)
- **设置页改为二级菜单**：原本一整页塞满的设置项，现在拆成「外观 / 权限与运行 / 暂停设置 / Pro / 反馈 / 关于」分类，点进去各看各的，不再一长条往下滚。
- **引导流程更顺**：
  - 修复"下一步"按钮和手机底部导航栏重叠的问题（已让出系统导航栏安全区）。
  - 创建分组不再强制：分组引导页提供「跳过」，返回也不会再从头来一遍（步骤状态已记住）。
  - 分组引导页旁边加了一个循环倒计时提示，纯装饰，不用等它也能直接下一步。

### 本次更新 (v0.4.0)
- **Pro 介绍页对比更突出**：免费版 / Pro 两列加了列头，Pro 列用实心高亮 pill 强调；每行 Pro 单元格改为高亮圆角卡片，一眼能看到 Pro 多了什么。
- **新增「更多功能推出中」占位**：对比表底部加了"推出中"标签与一行说明，为后续高级功能留位置。
- **Pro 激活按钮更清晰**：主按钮由"在线兑换"改为「用激活码激活」，次按钮明确标注「导入许可证（换设备 / 恢复）」，输入框提示收敛为"输入你购买的激活码"，主次操作不再混淆。

### 本次更新 (v0.3.20)
- **使用统计新增「原因分布」**：统计页现在按"为什么打开"聚合，展示每个原因的次数与占比进度条（仅统计点了「继续」且选了原因的记录）。
- **再次提醒计时更合理**：下一轮提醒从你点「继续」之后才开始计时，不再在倒计时出现时就已经在计下一轮。
- **暂停屏新增深呼吸提示**：提示语下方常驻一行"试着做几次深呼吸。"
- **术语统一**：应用内"冷静时长"统一为"冷却时长"。

### 本次更新 (v0.3.19)
- **自定义打开原因（Pro）**：Pro 用户可在设置里修改暂停屏上 4 个"打开原因"的文字（工作 / 无聊 / 查消息 / 其他），改成自己习惯的说法。

### 本次更新 (v0.3.17)
- **新增"显示监测通知"开关**：不需要常驻通知栏时可在设置里关闭。
- **再次提醒更灵活**：
  - **重复提醒**：可选择"只提醒一次"，或"持续重复提醒"。
  - **逐次延长冷却**：开启后，每次提醒的冷静时长依次变长（1×、2×、3×……，例如 10s、20s、30s）。

### 本次更新 (v0.3.16)
- **修复首次打开不拦截**：从桌面图标第一次打开目标 App 现在会立即拦截（之前要切屏才拦）。
- **修复评论区误拦**：在目标 App 内点评论区、输入框等不再误触发暂停屏。

### 本次更新 (v0.3.15)
- **权限提示更明确**：设置页把"电池优化"和"无障碍服务"标为必需，使用情况访问改为可选。
- **修复取消后立刻重开**：点取消后马上再打开目标 App 现在也会正常拦截（之前要等几秒）。
- **再次提醒冷静时长可单独设置**：不再跟随首次拦截的冷却时间。

### 本次更新 (v0.3.14)
- **修复"打开不限制、切屏才限制"**：新增前台轮询（每 1.5s 用系统"使用情况访问"确认真正在前台的 App），兜住从桌面图标打开 App 时漏掉拦截的情况。配合上一版的误报修复，现在无论是打开还是切屏都会正常弹暂停，且仍不会因通知误报。
- **修复"点取消后随意切换"**：取消后原先会把该 App 永久豁免、再也拦不住。现在取消只豁免取消那一刻的残留事件（约 1.5s），之后再次打开该 App 会正常重新拦截；取消仍是"这次先不进，送你回桌面"。

### 本次更新 (v0.3.13)
- **修复误报**：弹暂停前会用系统「使用情况访问」确认真正在前台的 App。以前下拉通知栏、而 b 站等媒体 App 有"正在播放"通知时，会误判为打开而弹暂停；现在不再误报。需要在设置 → 使用情况访问里给 Appause 授权（与开无障碍类似，一次性）。
- **暂停屏适配横屏**：横屏时原先整列内容过高会被裁掉、按钮点不到；现在改为可滚动并限制最大宽度，竖屏横屏都能完整显示。

### 本次更新 (v0.3.12)
- **反馈可匿名提交**：设置 → 反馈新增「通过 Appause 发送」，直接把留言发到服务器，无需邮箱或 GitHub 账号；联系方式可留空保持匿名。邮件与 GitHub 方式仍保留。

### 本次更新 (v0.3.11)
- **激活结果弹框**：Pro 激活改为弹框明确提示「激活成功」或失败原因（激活码无效 / 已达设备上限 / 网络错误等）；网络错误会提示可能是当前网络无法访问激活服务器域名。

### 本次更新 (v0.3.10)
- **重定义 Pro 范围**：现在 Pro = **无限分组 + 再次提醒 + 自定义暂停提示语**。
- **免费放开更多能力**：更长冷却（最长 60 秒）、完整使用统计历史（365 天）现在**所有人免费可用**，不再限制。
- 服务层已按 Pro 状态真正管控「再次提醒」，免费用户即便之前开过也不会再弹出；分组编辑器也会强制免费用户关闭该选项。

### 本次更新 (v0.3.9)
- **新增应用内反馈**：设置 → 反馈，可一键提交「问题反馈」或「功能建议」。应用版本、Android 版本、机型、语言会自动附带并显示给你确认，支持邮件或 GitHub Issue 两种方式，全程不收集任何遥测。
- **文档同步**：README / PRIVACY / 架构等早期文档已更新到当前状态（含 Pro 一次性激活联网说明、反馈入口等）。

> v0.3.8 重要修复回顾：重做前台切换逻辑，App 内打开图片/评论/分享面板不再反复弹冷却；会话计时 + 离开 3 分钟重新冷却。

### Appause 是什么
Appause 是一个基于无障碍服务的**本地专注工具**。当你打开抖音、微博、游戏等分心应用时，它会盖上一层「暂停屏」，让你先冷静几秒，再决定要不要继续。

- **纯本地**：数据只存本机，不上传、不联网、无账号
- 仅通过无障碍服务读取前台**应用包名**（不读屏幕内容、不监听输入）

### 这个版本包含
- **应用分组**：把易分心的 App 归组，统一设置冷静时长
- **暂停屏（Pause Screen）**：打开目标 App 即弹出冷静倒计时
- **再次提醒（Re-remind）**：冷却结束后再次轻推你一下
- **使用统计**：看清时间都花在了哪些 App 上
- **深色模式**：随系统或手动切换
- **推荐应用清单**：快速把常见分心 App 加进分组
- **Pro**：无限分组、**再次提醒**、自定义暂停提示语（一次性联网激活，设备绑定授权）
- **应用内反馈**：设置里一键提交问题或建议，自动附带设备信息，不收集遥测

### 如何安装
见 [INSTALL.md](INSTALL.md) —— 含「开启未知来源」「放行 Play Protect / 小米 / 华为 等机型的安装警告弹窗」「开启无障碍服务」「加后台白名单」完整步骤。

### 隐私
见 [PRIVACY.md](PRIVACY.md) —— 我们**不收集、不上传任何数据**。

### 已知限制
- 需手动在系统设置开启「无障碍服务」，且授权后需保持开启；
- 部分国产 ROM（小米 HyperOS / 华为 / OPPO / vivo）会杀后台或重启后失效，需按 INSTALL.md 加**自启动 / 电池无限制 / 锁定多任务**白名单；
- 当前免费版分组上限 1 个（Pro 解锁无限分组）；冷却最长 60 秒、统计历史 365 天均已全免费开放；**再次提醒**为 Pro 功能。

### 反馈与问题
请在 [GitHub Issues](https://github.com/Shmily0826/Appause/issues) 提交，或邮件联系（见 PRIVACY.md）。

---

## 🇺🇸 English

### What's new in v0.5.38 — value-first onboarding and accurate release docs
- **Value-first onboarding**: users now see the real pause-screen preview before permission setup, followed by a clear explanation of what Appause does and why permissions are needed.
- **Privacy before Accessibility**: the guide explicitly says Appause identifies the currently open app only; it does not read screen content, messages, or account information.
- **Clearer copy**: Chinese wording now uses familiar terms such as “waiting time” and “reminder screen”; English copy has been updated to match.
- **Accessibility and localization polish**: Diagnostics and Settings controls now use localized, readable content descriptions.
- **Correct install/privacy guidance**: “Display over other apps” is documented as an optional compatibility fallback; upgrades should install over the existing app; user-initiated feedback accurately discloses the diagnostic fields shown before sending.
- **Release materials refreshed**: README, install guide, privacy policy, store notes, bilingual screenshots, and domestic/overseas payment-route notes now match the app.

### What's new in v0.5.37 — stable accessibility-overlay interception + self-diagnosing feedback
- **"Display over other apps" is now optional**: the pause screen draws on the accessibility overlay (TYPE_ACCESSIBILITY_OVERLAY, which apps like Xiaohongshu cannot hide), so the permission is normally not needed. Onboarding and Settings now say: if the pause screen doesn't show, or interception stops working, come back and enable it and try again. When ungranted, it no longer shows as a red error.
- **Feedback now attaches diagnostic status**: when you submit a bug/suggestion from Settings → Feedback, it automatically includes the device's structured diagnostic state (whether the accessibility service is alive, master switch, battery exemption, overlay permission, last interception decision, last target-app decision, which overlay type was used, group count, etc.). It is attached to the email / GitHub / in-app send, so you can see "why didn't it block" without asking the user to copy a diagnostics report. A note states it contains no usage history or private data.
- **Copy polish**: onboarding no longer says "distracting apps" (now "target apps") and reads more naturally throughout; permission explanations updated to match.
- **Fixes**: group editor slider inset and tighter cooldown input; group-name error appears at the right time; Pro lock merged into a single upgrade button; stats "reason breakdown" progress bar no longer draws a stray floating dot.
- **Debug-only tooling**: the Diagnostics screen gained a "Restart onboarding" button (next to the Pro toggle) for quick testing. Only in the debug build (`com.appause.android.debug`); not in release.

### What's new in v0.5.2
- **Collapsible, line-per-item permission explainer**: the "Why does Appause need these permissions?" card on Settings → Permissions is now expandable/collapsible, and each reason sits on its own line instead of one dense paragraph. The home-screen dialog uses the same line-per-item layout.
- **Hide "open settings" once granted**: the system-settings buttons for Accessibility, Display over other apps, and Usage access disappear as soon as the permission is granted, so there is no confusing "open settings" on an already-granted item.
- **No more red-to-green flash**: permission status is read before the screen shows, so it no longer enters red ("Not enabled") and then refreshes to green.
- **About no longer shows raw true/false**: debug info now reads "Enabled / Not enabled" with colour; also fixed the Android-version line that used to render as "%d (%s)".
- **Open-source card polish**: the bottom "open source & auditable" card now uses an outlined button with an external-link icon instead of a full-width button.

### What's new in v0.5.1
- **Fixed: interception silently stopping after a while.** An internal guard flag keeps the pause screen from re-triggering during a countdown, but it was only cleared when the pause screen closed normally. If the overlay failed to attach, or another app buried the pause screen, the flag stuck raised forever and every later interception was skipped — the app looked broken. A watchdog now releases the guard whenever neither the overlay nor a visible pause screen exists.
- **One permission banner on the home screen** instead of two separate warnings. It lists exactly which permissions are missing and links straight to the system settings page.
- **Settings now explains each permission** — a "Why are these permissions needed?" card covers what each one does and where the limits are (e.g. the overlay permission only draws Appause's own pause screen).
- **Open-source notice added**: MIT licence, full source, no account, no tracking, with a button to the repository.
- **Fixed: reason breakdown ignored the language setting.** Switching the app to English still showed Chinese reason labels, because the labels were not re-resolved on a language change.
- **Visual fix**: the reason-breakdown progress bars no longer show a leftover tinted track on the right.
- **Docs corrected**: the README, privacy policy and install guide never mentioned the overlay permission — the README even claimed it was not required. All now list it as mandatory and explain what it is used for.

### What's new in v0.5.0
- **Fixed pause screen not showing on Xiaomi / HyperOS (Android 16)**: the previous overlay type was rejected by OEM ROMs. Appause now uses a **system overlay (`TYPE_APPLICATION_OVERLAY`)**, which requires the "Display over other apps" permission and draws **above all app tasks** — including anti-tamper apps like RedNote/Xiaohongshu that try to re-front themselves over a normal dialog.
- **"Display over other apps" is now required and guided**: the release build previously never declared the permission, so the entry was missing from system settings and the pause screen silently never appeared on Xiaomi. Now a **red warning banner** shows on the Home screen, a **Settings permission card** is added, and **onboarding** has a new step — each opens the system grant page directly, so it can never fail silently again.
- **Fixed the "second window ~5s later"**: the backup launch used to rebuild PauseActivity and reset the countdown, looking like a duplicate window. It now re-fronts the existing screen without rebuilding.
- **More reliable foreground launch**: kept the `AlarmManager` fallback that brings PauseActivity to the foreground on MIUI/HyperOS.

### What's new in v0.4.7
- **Plainer copy**: the onboarding welcome line no longer uses roundabout phrasing like "take a breath"; the pause screen's "try a few deep breaths" is now "pause a few seconds before you decide."
- **Onboarding group step now shows a live pause-screen preview**: the old decorative countdown is replaced by the same animation the real interception uses (app icon, name, prompt, countdown ring + ticking number), so users see exactly what they'll get when a distracting app opens. It's a concept demo only — it does not push them to create a group (the "Later" button stays tappable).
- **Pro moved to a top banner in Settings**: Pro is no longer mixed in with the other settings rows. It now sits in a banner at the top of Settings — "Appause Pro is active" when unlocked, or "Upgrade to Appause Pro" (tappable) otherwise.
- **Granted permissions show green "Enabled"**: in Settings → Permissions, accessibility, battery optimization, and usage access now show a green "Enabled" (with the detail line also green) once granted; only the not-granted state is red.

### What's new in v0.4.6
- **Statistics "reason breakdown" shows real labels**: previously it rendered raw keys like `work`/`bored` (most visible after switching the system language). It now shows the localized open-reason labels (e.g. "Work", "Bored"), and respects Pro custom text.
- **Smoother feedback copy**: the feedback intro and the "couldn't send" message read more naturally in both Chinese and English.
- **No more misleading red on granted permissions**: in Settings → Permissions, the "Required" badge used to be red even after the permission was granted, which looked like something was still wrong. It is now neutral gray when granted and red only when not granted.

### What's new in v0.4.5
- **Onboarding visual polish**: each step now leads with a 72dp circular icon as a visual anchor; a step-dot indicator (5 dots, current highlighted) sits at the top; titles and body text are centered for a more guide-like feel.
- **Fixed duplicate "Skip" on the group step**: the group step's secondary button used to also say "Skip", duplicating the top-right global skip. It now reads "Later" — it skips only group creation and still lands on the "All set" page, while the top-right "Skip" exits the whole guide.

### What's new in v0.4.4
- **Free-tier group limit set to 1**: free users can create at most one group. Once reached, the Home "New group" button shows a lock icon and jumps to the Pro screen; a banner above the group list explains "Free version is limited to 1 group — upgrade to Pro for unlimited groups". Pro users have no group limit.

### What's new in v0.4.3
- **Fixed group-name TextField cursor jump**: when editing a group, typing fast in the name field snapped the cursor to the start (the field was bound directly to an async state flow). Switched to local-state-with-sync, matching the search box. Audited every other text field — all already correct.

### What's new in v0.4.0
- **Pro page comparison stands out**: the Free/Pro table now has column headers, with the Pro header and every Pro cell rendered as a highlighted pill so it's obvious what Pro adds.
- **"More coming soon" placeholder**: a "推出中 / coming soon" badge and line at the bottom of the comparison table reserves space for future Pro features.
- **Clearer Pro activation buttons**: the primary button is now "用激活码激活 / Activate with code", and the secondary is clearly labelled "导入许可证（换设备 / 恢复） / Import license (device switch / restore)". The input hint now reads "输入你购买的激活码 / Enter the activation code you purchased", so the two actions no longer look like duplicates.

### What's new in v0.3.20
- **Stats: reason breakdown**: the stats page now groups by "why did you open it", showing each reason's count and a proportion bar (counts only records where you tapped Continue and picked a reason).
- **Re-remind timing fixed**: the next nudge now starts counting only after you tap Continue, instead of counting the next round while the countdown is already on screen.
- **Pause screen deep-breath hint**: a steady "Try taking a few deep breaths." line now sits under the prompt.
- **Terminology unified**: in-app "冷静时长" is now consistently "冷却时长" (cooldown).

### What's new in v0.3.19
- **Custom open reasons (Pro)**: Pro users can rename the four "why are you opening this?" options on the pause screen (Work / Bored / Check messages / Other) to whatever wording they prefer.

### What's new in v0.3.17
- **New "show monitoring notification" toggle**: turn off the persistent status-bar notification in Settings when you don't need it.
- **More flexible re-remind**:
  - **Repeat reminders**: choose a single nudge, or repeated nudges.
  - **Escalate cooldown**: each nudge lasts longer than the last (1×, 2×, 3× …, e.g. 10s, 20s, 30s).

### What's new in v0.3.16
- **Fix first-open not blocked**: opening a target app from its icon now intercepts immediately (previously only switching did).
- **Fix comment false-intercept**: tapping comments / text fields inside a target app no longer wrongly triggers the pause screen.

### What's new in v0.3.15
- **Clearer permission hints**: Settings now marks "Battery optimization" and "Accessibility" as required, and "Usage access" as optional.
- **Fix reopen right after cancel**: reopening the target app immediately after cancelling is now blocked again (previously it took a few seconds).
- **Independent re-remind duration**: the re-remind calm screen length is now set separately from the first-intercept cooldown.

### What's new in v0.3.14
- **Fix "opens aren't blocked, only switches are"**: added a foreground poller (every 1.5s, using the system "Usage access" API) that reliably catches apps opened from their icon — a case the window-event stream used to miss. Combined with the v0.3.13 false-positive fix, both opening and switching now trigger the pause, and notifications still don't.
- **Fix "cancel lets you switch freely"**: cancel used to permanently exempt the app from interception. Now it only suppresses the stale event in the ~1.5s right after cancel; the next genuine open is intercepted normally. Cancel still means "not this time — back to home".

### What's new in v0.3.13
- **False-positive fix**: before showing the pause screen, Appause now confirms the genuinely foreground app via the system "Usage access" API. Previously, pulling down the notification shade while an app like Bilibili had a "now playing" notification would wrongly trigger the pause. Grant Appause "Usage access" in Settings (one-time, like enabling Accessibility).
- **Pause screen fits landscape**: the screen is now scrollable with a capped width, so in landscape it no longer overflows and clips the buttons.

### What's new in v0.3.12
- **Anonymous feedback**: Settings → Feedback adds "Send via Appause" which posts your message to the server directly — no email or GitHub account needed; leave the optional contact blank to stay anonymous. Email and GitHub options remain.

### What's new in v0.3.11
- **Activation result dialog**: Pro activation now shows a dialog reporting success or the failure reason (invalid code / device limit reached / network error, etc.). The network-error case notes the activation server domain may be unreachable.

### What's new in v0.3.10
- **Redefined Pro**: Pro now = **unlimited groups + re-remind + custom pause text**.
- **More free for everyone**: longer cooldown (up to 60s) and full stats history (365 days) are now free — no longer gated.
- Re-remind is now enforced at the service layer by Pro status: free users who previously enabled it won't get re-reminds, and the group editor forces it off for free users.

### What's new in v0.3.9
- **In-app feedback**: Settings → Feedback lets you file a bug report or suggestion in one tap. App version, Android version, device model and language are attached automatically and shown for confirmation; send by email or a pre-filled GitHub issue. No telemetry is collected.
- **Docs refreshed**: README / PRIVACY / Architecture and other early docs now match the current app (including the one-time Pro activation network note and the feedback entry point).

> v0.3.8 highlights: reworked foreground-switch logic so opening images/comments/share sheets inside a blocked app no longer re-pops the cooldown; session timer + 3-minute leave re-arm.

### What is Appause
Appause is a **local-first focus tool** built on Android's AccessibilityService. When you open a distracting app (Douyin, Weibo, games…), it overlays a **Pause Screen** so you take a few seconds to breathe before deciding whether to continue.

- **Fully local**: all data stays on your device. No network, no account, no upload.
- Only reads the foreground **package name** via AccessibilityService (no screen content, no keylogging).

### What's in this release
- **App groups**: organize distracting apps and apply a single cooldown.
- **Pause Screen**: a calm countdown overlay when a target app launches.
- **Re-remind**: a gentle nudge when the cooldown ends.
- **Usage stats**: see where your time goes.
- **Dark mode**: follows system or manual toggle.
- **Recommended apps**: quick-add common distractors.
- **Pro**: unlimited groups, **re-remind**, and custom pause text (one-time online activation, device-bound license).
- **In-app feedback**: one-tap bug report / suggestion from Settings, with auto-attached device info and no telemetry.

### Install
See [INSTALL.md](INSTALL.md) for "install unknown apps", dismissing Play Protect / Xiaomi / Huawei and other OEM install warnings, enabling AccessibilityService, and keeping the service alive.

### Privacy
See [PRIVACY.md](PRIVACY.md) — we collect and upload **nothing**.

### Known limitations
- AccessibilityService must be enabled manually and kept on.
- Some OEM ROMs (Xiaomi HyperOS / Huawei / OPPO / vivo) kill background services or drop them after reboot — whitelist auto-start / battery / task-lock per INSTALL.md.
- Free tier: max 1 group (Pro unlocks unlimited); cooldown up to 60s and 365-day stats history are now free for everyone; **re-remind** is a Pro feature.

### Feedback
File issues at [GitHub Issues](https://github.com/Shmily0826/Appause/issues).

---

## 下载 / Download
- GitHub Releases（主）: https://github.com/Shmily0826/Appause/releases/latest
- 蓝奏云镜像（国内，备用）: https://shmily0826.lanzoup.com/b01eunt29a （密码：1234）
