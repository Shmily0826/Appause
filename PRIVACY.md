# Privacy Policy

**Effective date:** 2026-07-29
**App:** Appause (Android)

> 🔗 A linkable, hostable version of this policy is available at
> [`privacy-policy.html`](privacy-policy.html) and is published via GitHub Pages at
> `https://shmily0826.github.io/Appause/privacy-policy.html` — use this URL as the
> privacy-policy link when submitting to app stores (Coolapk / Google Play / F-Droid).
> (Enabled 2026-07-29: Settings → Pages → deploy from branch `main`, `/root`.)

Appause is built around a single promise: **your data never leaves your device.**

This document explains what we collect, what we don't, and the permissions the
app uses. It is written for non-experts. If anything is unclear, open an issue
on the project repository.

---

## English

### What we collect

**Almost nothing.** Appause does not collect or transmit your personal
information. Your app groups, cooldowns, and usage stats are stored **locally**
and never leave your device.

- There is no account, no login, and no registration.
- The app makes **no network requests during normal use** — no analytics, no
  crash reporters, no advertisements, and no third-party SDKs.
- The only network use is a **one-time** license redeem when you activate
  Appause Pro. It sends a redemption code and a non-identifying device
  fingerprint (a SHA-256 hash of an on-device key) to the activation server,
  which returns a signed license token that is **verified on your device** and
  works offline afterwards. No browsing history, messages, or app usage is sent.
- Feedback is sent **only when you choose to** (Settings → Feedback). The app
  never sends anything automatically. The "Send via Appause" option transmits
  your message plus the auto-attached device/app metadata (version, Android
  version, model, language) to our server (a Cloudflare Worker) for the
  developer to read — no email or account is required, and you can leave the
  optional contact field blank to stay fully anonymous. The email and GitHub
  options remain available.
- We keep a single **aggregate download counter** on our server (the same
  Cloudflare Worker) to track how many times Appause has been installed across
  all download channels (GitHub Releases and mirrors). It records **only a
  number** — no IP address, device identifier, or personal data is ever stored.
  This is aggregate analytics, not user tracking. Because the counter runs on
  our own infrastructure, it is self-reported and approximate; treat it as a
  rough floor rather than an audited figure. The authoritative install numbers
  come from the platforms themselves (e.g. GitHub Release downloads, Coolapk).
- All configuration (your app groups, cooldowns, and usage stats) is stored
  **locally** in an on-device database (Room) and preferences store (DataStore).

### How the Accessibility Service is used

Appause uses Android's AccessibilityService **only** to detect which app is
currently in the foreground, by reading its **package name**.

- `canRetrieveWindowContent` is set to `false`. We do **not** read your screen
  content, do **not** take screenshots, and do **not** record anything you type
  or see.
- We only use the package name to decide whether to show the pause screen for a
  group you configured.
- Appause excludes itself, the system launcher, and system UI from interception.

This is an **accessibility / habit-forming feature**, not a monitoring or
security tool.

### Permissions

| Permission | Why it is needed |
|------------|------------------|
| AccessibilityService | To detect the foreground app by package name (as described above). |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | To draw the pause screen on top of the app you just opened. Without it the pause screen cannot appear on many devices, so Appause has nothing to show you. It draws only Appause's own pause screen; it never reads or records what is underneath. |
| Foreground Service | To keep foreground-app detection running while the device is in use. |
| POST_NOTIFICATIONS (Android 13+) | To show the persistent "detection active" notification. |
| INTERNET | Only for the **one-time** Appause Pro license redeem and for feedback you choose to send via "Send via Appause". Not used during normal use, and the app works fully offline otherwise. |
| Usage Access (`PACKAGE_USAGE_STATS`) | To confirm which app is genuinely on screen before showing the pause screen — this is what stops a media app's notification (e.g. a video playing in the shade) from triggering the pause by mistake. The query is local; no usage data ever leaves your device. |

### Your control over data

- All data lives on your device. Clearing the app's data or uninstalling the
  app removes everything permanently.
- You can review or delete any group at any time from within the app.

### Children

Appause is not directed at children and does not knowingly collect data from
anyone. (It collects no data from anyone.)

### Changes

If this policy changes, the updated version will be posted in this file in the
project repository.

### Contact

Questions or concerns: open an issue on [GitHub](https://github.com/Shmily0826/Appause),
or email [rng2018520@gmail.com](mailto:rng2018520@gmail.com).

---

## 中文（简体）

### 我们收集什么

**几乎没有。** Appause 不会收集或上传你的个人信息。你的应用分组、冷却时间、
使用统计都**仅保存在本机**，绝不会离开你的设备。

- 没有账号、没有登录、没有注册。
- 应用**在日常使用中不进行任何网络请求**——没有分析统计、没有崩溃上报、
  没有广告，也没有任何第三方 SDK。
- 唯一的联网场景是激活 **Appause Pro 时的一次性兑换**：会发送兑换码和一个
  不带个人身份的「设备指纹」（本机密钥的 SHA-256 哈希）到激活服务器，服务器
  返回一张经过签名的许可证令牌，令牌在你的设备上**本地校验**，之后完全离线
  可用。不会上传任何浏览记录、消息或使用行为。
- 反馈**只有你主动选择时才会发送**（设置 → 反馈），应用不会自动上传任何内容。「通过 Appause 发送」会把你的留言以及自动附带的设备/版本信息（版本号、Android 版本、机型、语言）发到我们的服务器（Cloudflare Worker）供开发者查看——无需邮箱或账号，联系方式留空即可完全匿名。邮件与 GitHub 方式依然可用。
- 我们在服务器（同一个 Cloudflare Worker）上维护一个**纯聚合的下载计数器**，用来统计 Appause 通过各个渠道（GitHub Release 及镜像）被安装的总次数。它**只记录一个数字**——不会保存任何 IP 地址、设备标识或个人数据。这是聚合统计，不是用户追踪。由于计数器运行在我们自己的服务器上，它是一个自报、近似的数字，请将其视为粗略下限而非经审计的精确值；权威的安装数据来自各分发平台本身（如 GitHub Release 下载量、酷安下载量）。
- 你的所有配置（应用分组、冷却时间、使用统计）都**仅保存在本机**的数据库
  （Room）和偏好存储（DataStore）中。

### 无障碍服务（AccessibilityService）的用途

Appause 使用系统的 AccessibilityService，**仅用于**检测当前前台运行的是哪个
应用——通过读取其**包名**实现。

- `canRetrieveWindowContent` 已设为 `false`。我们**不读取**你的屏幕内容，
  **不截图**，也**不记录**你输入或看到的信息。
- 我们仅用包名来判断是否要为你配置的分组显示暂停界面。
- Appause 会自动排除自身、系统桌面和系统界面，不会拦截它们。

这是一个**无障碍 / 习惯养成功能**，不是监控或安全工具。

### 权限说明

| 权限 | 用途 |
|------|------|
| AccessibilityService | 如上所述，通过包名检测前台应用。 |
| 显示悬浮窗 / 在其他应用上层显示（`SYSTEM_ALERT_WINDOW`） | 把停顿界面画在你刚打开的那个应用之上。不开这个权限，很多设备上停顿界面根本弹不出来，Appause 也就没东西可展示。它只负责绘制 Appause 自己的停顿界面，不会读取或记录下层内容。 |
| 前台服务 (Foreground Service) | 在设备使用期间保持前台应用检测持续运行。 |
| POST_NOTIFICATIONS（Android 13+） | 显示常驻的"检测中"通知。 |
| INTERNET（联网） | 仅用于激活 **Appause Pro 时的一次性许可证兑换**，以及你主动选择的「通过 Appause 发送」反馈。日常使用不会联网，其余功能完全离线。 |
| 使用情况访问（`PACKAGE_USAGE_STATS`） | 用于在弹暂停前确认真正在前台的 App，避免媒体通知（例如通知栏里正在播放的视频）误触发暂停。该查询完全在本地进行，使用记录不会上传。 |

### 你对数据的控制权

- 所有数据都在你的设备上。清除应用数据或卸载应用会永久删除全部内容。
- 你可以随时在应用内查看或删除任意分组。

### 儿童

Appause 不面向儿童，也不会有意收集任何人的数据（事实上它不收集任何人的数据）。

### 变更

本政策如有变更，更新版本将发布在仓库的此文件中。

### 联系方式

如有疑问，请在 [GitHub](https://github.com/Shmily0826/Appause) 提交 Issue，
或发送邮件至 [rng2018520@gmail.com](mailto:rng2018520@gmail.com)。
