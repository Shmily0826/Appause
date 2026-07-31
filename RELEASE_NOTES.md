# Appause v0.3.18 — 发布说明 (Release Notes)

> 复制本文件内容粘贴到 GitHub Release 的 Description 即可。
> 下载链接：`https://github.com/Shmily0826/Appause/releases/download/v0.3.18/Appause-v0.3.18.apk`

---

## 🇨🇳 中文

### 本次更新 (v0.3.18)
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
- 当前免费版分组上限 2 个（Pro 解锁无限分组）；冷却最长 60 秒、统计历史 365 天均已全免费开放；**再次提醒**为 Pro 功能。

### 反馈与问题
请在 [GitHub Issues](https://github.com/Shmily0826/Appause/issues) 提交，或邮件联系（见 PRIVACY.md）。

---

## 🇺🇸 English

### What's new in v0.3.18
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
- Free tier: max 2 groups (Pro unlocks unlimited); cooldown up to 60s and 365-day stats history are now free for everyone; **re-remind** is a Pro feature.

### Feedback
File issues at [GitHub Issues](https://github.com/Shmily0826/Appause/issues).

---

## 下载 / Download
- GitHub Releases（主）: https://github.com/Shmily0826/Appause/releases/latest
- 蓝奏云镜像（国内，备用）: https://shmily0826.lanzoup.com/b01eunt29a （密码：1234）
