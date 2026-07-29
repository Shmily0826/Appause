# Appause v0.3.10 — 发布说明 (Release Notes)

> 复制本文件内容粘贴到 GitHub Release 的 Description 即可。
> 下载链接：`https://github.com/Shmily0826/Appause/releases/download/v0.3.10/Appause-v0.3.10.apk`

---

## 🇨🇳 中文

### 本次更新 (v0.3.10)
- **重定义 Pro 范围**：现在 Pro = **无限分组 + 再次提醒 + 自定义暂停提示语**。
- **免费放开更多能力**：更长冷却（最长 60 秒）、完整使用统计历史（365 天）现在**所有人免费可用**，不再限制。
- 服务层已按 Pro 状态真正管控「再次提醒」，免费用户即便之前开过也不会再弹出；分组编辑器也会强制免费用户关闭该选项。

### Appause 是什么
Appause 是一个基于无障碍服务的**本地专注工具**。当你打开抖音、微博、游戏等分心应用时，它会盖上一层「暂停屏」，让你先冷静几秒，再决定要不要继续。

- **纯本地**：数据只存本机，不上传、不联网、无账号
- 仅通过无障碍服务读取前台**应用包名**（不读屏幕内容、不监听输入）

### 本次更新 (v0.3.9)
- **新增应用内反馈**：设置 → 反馈，可一键提交「问题反馈」或「功能建议」。应用版本、Android 版本、机型、语言会自动附带并显示给你确认，支持邮件或 GitHub Issue 两种方式，全程不收集任何遥测。
- **文档同步**：README / PRIVACY / 架构等早期文档已更新到当前状态（含 Pro 一次性激活联网说明、反馈入口等）。

> v0.3.8 重要修复回顾：重做前台切换逻辑，App 内打开图片/评论/分享面板不再反复弹冷却；会话计时 + 离开 3 分钟重新冷却。

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
见 [INSTALL.md](INSTALL.md) —— 含「开启未知来源」「放行 Play Protect / 小米 / 华为 恐吓弹窗」「开启无障碍服务」「加后台白名单」完整步骤。

### 隐私
见 [PRIVACY.md](PRIVACY.md) —— 我们**不收集、不上传任何数据**。

### 已知限制（请先看，避免差评）
- 需手动在系统设置开启「无障碍服务」，且授权后需保持开启；
- 部分国产 ROM（小米 HyperOS / 华为 / OPPO / vivo）会杀后台或重启后失效，需按 INSTALL.md 加**自启动 / 电池无限制 / 锁定多任务**白名单；
- 当前免费版分组上限 2 个（Pro 解锁无限分组）；冷却最长 60 秒、统计历史 365 天均已全免费开放；**再次提醒**为 Pro 功能。

### 反馈与问题
请在 [GitHub Issues](https://github.com/Shmily0826/Appause/issues) 提交，或邮件联系（见 PRIVACY.md）。

---

## 🇺🇸 English

### What is Appause
Appause is a **local-first focus tool** built on Android's AccessibilityService. When you open a distracting app (Douyin, Weibo, games…), it overlays a **Pause Screen** so you take a few seconds to breathe before deciding whether to continue.

- **Fully local**: all data stays on your device. No network, no account, no upload.
- Only reads the foreground **package name** via AccessibilityService (no screen content, no keylogging).

### What's new in v0.3.10
- **Redefined Pro**: Pro now = **unlimited groups + re-remind + custom pause text**.
- **More free for everyone**: longer cooldown (up to 60s) and full stats history (365 days) are now free — no longer gated.
- Re-remind is now enforced at the service layer by Pro status: free users who previously enabled it won't get re-reminds, and the group editor forces it off for free users.

### What's new in v0.3.9
- **In-app feedback**: Settings → Feedback lets you file a bug report or suggestion in one tap. App version, Android version, device model and language are attached automatically and shown for confirmation; send by email or a pre-filled GitHub issue. No telemetry is collected.
- **Docs refreshed**: README / PRIVACY / Architecture and other early docs now match the current app (including the one-time Pro activation network note and the feedback entry point).

> v0.3.8 highlights: reworked foreground-switch logic so opening images/comments/share sheets inside a blocked app no longer re-pops the cooldown; session timer + 3-minute leave re-arm.

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
See [INSTALL.md](INSTALL.md) for "install unknown apps", dismissing Play Protect / OEM warnings, enabling AccessibilityService, and keeping the service alive.

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
- 蓝奏云镜像（国内，备用）: _（替换为你的蓝奏云分享链接）_
