# Appause

[English](README.md) | [简体中文](README.zh-CN.md)

**App + Pause** — 在打开应用的冲动和行动之间，留出片刻。

Appause 是一款以本地存储为主的安卓专注工具。你可以选择目标应用、设置分组和冷却时间。
当目标应用进入前台时，Appause 会显示一段简短的暂停倒计时，让你决定是否继续。

**官网：** [中文](https://shmily0826.github.io/Appause/zh.html) · [English](https://shmily0826.github.io/Appause/)

## 最新下载：v0.5.50 公开测试版

从 [GitHub 下载签名安装包 Appause-v0.5.50.apk](https://github.com/Shmily0826/Appause/releases/download/v0.5.50/Appause-v0.5.50.apk)。
[蓝奏云归档](https://shmily0826.lanzoup.com/b01eunt29a)提供 v0.5.49，提取密码 **1234**。

- 包名：`com.appause.android`
- versionCode：`102`
- SHA-256：`b05b5fadcac3b7ed6013ab96b7bbcf01b220c180bc7f2e41ae084002c11f3daa`

本版修复了临时系统窗口导致倒计时提前关闭或重新开始的问题。
本版变更见 [v0.5.50 发布说明](https://github.com/Shmily0826/Appause/releases/tag/v0.5.50)。

后续公开发布的 APK 使用 `Appause-v<版本号>.apk` 命名。
`versionCode` 是内部版本编号，不属于公开文件名。
首次安装前可查看[安装指南](docs/INSTALL.md)。

## 功能

- **应用分组**：将目标应用放入分组，共用冷却设置。
- **暂停页面**：打开目标应用前显示倒计时，结束后可以继续；点击取消会返回桌面。
- **会话与再次提醒**：可选择在目标应用内停留一段时间后再次提醒。返回桌面或离开一段时间后，下次打开会重新触发冷却。
- **使用统计**：回顾使用情况，以及自己选择继续打开应用的原因。
- **推荐应用与深色模式**：设置替代应用入口，并跟随系统切换主题。
- **厂商设置指引**：说明小米 / HyperOS 等系统上的电池、自启动和后台运行设置。
- **反馈**：通过「设置 → 反馈」查看整理好的诊断信息，再自行决定是否发送问题或建议。
- **Appause Pro**：包含无限分组、再次提醒、自定义暂停提示和打开原因。可在 Pro 页面开启一次七天试用；结束后，通过「设置 → 反馈」申请免费永久授权码。没有订阅或付费版本，核心功能保持免费。

## 截图

| 首页 | 暂停页面 | 分组 |
|---|---|---|
| ![首页](images/screenshots/zh/home.png) | ![暂停页面](images/screenshots/zh/pause.png) | ![分组](images/screenshots/zh/group.png) |

| 使用统计 | 设置 | 反馈 |
|---|---|---|
| ![使用统计](images/screenshots/zh/statistics.png) | ![设置](images/screenshots/zh/settings.png) | ![反馈](images/screenshots/zh/feedback.png) |

## 隐私与权限

Appause 通过 Room 和 DataStore 在本机保存分组、冷却设置和统计。
没有账号、广告、分析 SDK 或云同步。无障碍服务通过前台应用的包名判断是否需要暂停；
`canRetrieveWindowContent` 设为 `false`，不读取屏幕内容、聊天消息、截图或键盘输入。
完整说明见[隐私政策](PRIVACY.md#中文简体)。

| 设置项 | 用途 |
|---|---|
| **无障碍服务 — 必需** | 检测当前处于前台的应用，以便暂停你设置的目标应用。 |
| **电池不受限制 — 小米 / HyperOS 等系统上的后台运行设置** | 减少系统对检测服务的后台限制。同时允许自启动，并在系统支持时锁定最近任务。 |
| **使用情况访问 — 可选，建议开启** | 在本机确认前台应用，减少通知等情况引起的误触发。 |
| **显示在其他应用上层 — 可选的兼容性备用方案** | 暂停页通常使用无障碍覆盖层。只有设备无法显示暂停页时，才需要尝试开启此权限。 |
| **通知 — 可选** | 显示持续运行的状态通知。安卓 13 及以上可能询问此权限；它不用于检测前台应用。 |
| **网络 — 仅在你主动操作时使用** | 开始 Pro 试用或兑换永久授权码时联网，返回的授权令牌在本机验证并保存，之后可离线检查授权。反馈也只会在你主动发送时传出。不进行自动联网授权检查或日常状态检查。 |

Appause 是辅助用户调整使用习惯的无障碍工具，不是监控、安全或医疗工具。
你随时可以关闭服务或调整分组。

## 系统要求

- 安卓 8.0 及以上（API 26+）。
- 正式包名：`com.appause.android`。
- 通过 GitHub Releases 提供 APK 下载，目前未上架 Google Play。

## 从源码构建

项目使用 Kotlin、Jetpack Compose、Room、DataStore 和 Gradle Kotlin DSL。
构建需要 JDK 17，以及包含 API 35 的 Android SDK。

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

正式版签名使用不纳入 Git 的本地配置。不要提交密钥库、密码、私钥、激活码或 Worker 密钥。

## 测试情况

v0.5.50 签名安装包已在 HyperOS 真机上核对服务状态和打开 B 站时的倒计时。

v0.5.49 是公开测试版。发布前，已在一台运行安卓 16 的小米 HyperOS 手机上验证本次主页键 / 最近任务键修复及主动重开行为。
同一份 v0.5.49 签名 APK 还在以下云端真实设备上通过了人工核心流程检查：

| 设备 | 安卓版本 | 测试日期 | 结果 |
|---|---|---|---|
| 三星 Galaxy S24 | 14 | 2026-11-03 | 核心流程通过；另确认七天试用激活成功。 |
| Google Pixel 9 | 16 | 2026-11-04 | 核心流程通过；关闭无障碍后正确显示恢复权限提示。 |

核心流程包括：开启无障碍、显示暂停页、取消返回桌面、Continue 进入目标应用、重新打开时拦截，以及跳转推荐应用。
已保存截图、设备状态快照和本地日志；这两轮日志审阅未发现应用崩溃或 ANR（应用无响应）。

两轮云端测试均通过 ADB 安装，尚未验证浏览器下载安装、长时间后台、熄屏、重启恢复或所有设备与系统。
三星这轮未开启电池优化豁免和「显示在其他应用上方」也能完成核心操作，但短时测试不能证明长期后台稳定。
CI 会在每次推送时运行单元测试。
如果 Appause 在你的手机上出现问题，欢迎[提交 Issue](https://github.com/Shmily0826/Appause/issues)。

## 安装与反馈

- [安装指南](docs/INSTALL.md)
- [隐私政策](PRIVACY.md#中文简体)
- [GitHub Issues](https://github.com/Shmily0826/Appause/issues)
- [v0.5.50 发布说明](https://github.com/Shmily0826/Appause/releases/tag/v0.5.50)
- [v0.5.49 发布说明](https://github.com/Shmily0826/Appause/releases/tag/v0.5.49)

## 开源协议

[MIT](LICENSE) © 2026 Appause authors.
