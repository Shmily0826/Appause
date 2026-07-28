# Appause v0.3.8 — 发布说明 (Release Notes)

> 复制本文件内容粘贴到 GitHub Release 的 Description 即可。
> 发布前请把下方下载链接里的 `USER` 替换为你的 GitHub 用户名。

---

## 🇨🇳 中文

### Appause 是什么
Appause 是一个基于无障碍服务的**本地专注工具**。当你打开抖音、微博、游戏等分心应用时，它会盖上一层「暂停屏」，让你先冷静几秒，再决定要不要继续——它是**减速带，不是拦截墙**。

- **纯本地**：数据只存本机，不上传、不联网、无账号
- 仅通过无障碍服务读取前台**应用包名**（不读屏幕内容、不监听输入）

### 本次更新 (v0.3.8)
- **修复拦截误触发**：重做了前台切换逻辑。现在进入被限制 App 后，App 内打开图片、评论、分享面板、外部播放器等瞬时切屏**不再反复弹冷却**；只有真正离开（切到桌面或停留 3 分钟以上）才会重新冷却。
- **会话计时模型**：可选「再次提醒」按墙钟计时，离开 App 期间暂停但不清零，回来继续；离开满 3 分钟整体重置。

### 这个版本包含
- **应用分组**：把易分心的 App 归组，统一设置冷静时长
- **暂停屏（Pause Screen）**：打开目标 App 即弹出冷静倒计时
- **再次提醒（Re-remind）**：冷却结束后再次轻推你一下
- **使用统计**：看清时间都花在了哪些 App 上
- **深色模式**：随系统或手动切换
- **推荐应用清单**：快速把常见分心 App 加进分组
- **Pro 脚手架（内测）**：无限分组、更长冷却、自定义提示语等入口已就位（激活逻辑后续版本接入）

### 如何安装
见 [INSTALL.md](INSTALL.md) —— 含「开启未知来源」「放行 Play Protect / 小米 / 华为 恐吓弹窗」「开启无障碍服务」「加后台白名单」完整步骤。

### 隐私
见 [PRIVACY.md](PRIVACY.md) —— 我们**不收集、不上传任何数据**。

### 已知限制（请先看，避免差评）
- 需手动在系统设置开启「无障碍服务」，且授权后需保持开启；
- 部分国产 ROM（小米 HyperOS / 华为 / OPPO / vivo）会杀后台或重启后失效，需按 INSTALL.md 加**自启动 / 电池无限制 / 锁定多任务**白名单；
- 当前为免费版，分组上限 2 个、冷却上限 30 秒（Pro 解锁更高上限，激活尚未开放）。

### 反馈与问题
请在 [GitHub Issues](https://github.com/USER/Appause/issues) 提交，或邮件联系（见 PRIVACY.md）。

---

## 🇺🇸 English

### What is Appause
Appause is a **local-first focus tool** built on Android's AccessibilityService. When you open a distracting app (Douyin, Weibo, games…), it overlays a **Pause Screen** so you take a few seconds to breathe before deciding whether to continue — a **speed bump, not a blocker**.

- **Fully local**: all data stays on your device. No network, no account, no upload.
- Only reads the foreground **package name** via AccessibilityService (no screen content, no keylogging).

### What's new in v0.3.8
- **Fixed false re-triggers**: reworked the foreground-switch logic. Opening images, comments, share sheets, or external players inside a blocked app no longer re-pops the cooldown. A re-cooldown only happens on a real leave (Home screen, or staying away 3+ minutes).
- **Session timer model**: optional re-remind counts wall-clock time; leaving the app pauses but doesn't reset it, and returning continues; a 3-minute away window fully re-arms.

### What's in this release
- **App groups**: organize distracting apps and apply a single cooldown.
- **Pause Screen**: a calm countdown overlay when a target app launches.
- **Re-remind**: a gentle nudge when the cooldown ends.
- **Usage stats**: see where your time goes.
- **Dark mode**: follows system or manual toggle.
- **Recommended apps**: quick-add common distractors.
- **Pro scaffolding (preview)**: entry points for unlimited groups / longer cooldown / custom text are in place (activation lands in a later build).

### Install
See [INSTALL.md](INSTALL.md) for "install unknown apps", dismissing Play Protect / OEM warnings, enabling AccessibilityService, and keeping the service alive.

### Privacy
See [PRIVACY.md](PRIVACY.md) — we collect and upload **nothing**.

### Known limitations
- AccessibilityService must be enabled manually and kept on.
- Some OEM ROMs (Xiaomi HyperOS / Huawei / OPPO / vivo) kill background services or drop them after reboot — whitelist auto-start / battery / task-lock per INSTALL.md.
- Free tier: max 2 groups, 30s cooldown cap (Pro raises limits; activation not yet open).

### Feedback
File issues at [GitHub Issues](https://github.com/USER/Appause/issues).

---

## 下载 / Download
- GitHub Releases（主）: https://github.com/USER/Appause/releases/latest
- 蓝奏云镜像（国内，备用）: _（替换为你的蓝奏云分享链接）_
