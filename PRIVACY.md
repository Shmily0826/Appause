# Privacy Policy

**Effective date:** 2026-07-24
**App:** Appause (Android)

Appause is built around a single promise: **your data never leaves your device.**

This document explains what we collect, what we don't, and the permissions the
app uses. It is written for non-experts. If anything is unclear, open an issue
on the project repository.

---

## English

### What we collect

**Nothing.** Appause does not collect, transmit, or store any personal
information outside of your own device.

- There is no account, no login, and no registration.
- The app makes **no network requests** of any kind — no servers, no analytics,
  no crash reporters, no advertisements, and no third-party SDKs.
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
| Foreground Service | To keep foreground-app detection running while the device is in use. |
| POST_NOTIFICATIONS (Android 13+) | To show the persistent "detection active" notification. |

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

**什么都没有。** Appause 不会以任何形式收集、上传或在设备之外存储任何个人信息。

- 没有账号、没有登录、没有注册。
- 应用**不进行任何网络请求**——没有服务器、没有分析统计、没有崩溃上报、
  没有广告，也没有任何第三方 SDK。
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
| 前台服务 (Foreground Service) | 在设备使用期间保持前台应用检测持续运行。 |
| POST_NOTIFICATIONS（Android 13+） | 显示常驻的"检测中"通知。 |

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
