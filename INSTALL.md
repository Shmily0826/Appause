# 如何安装 Appause（侧载 / 直链安装指南）

Appause 通过本页面或网盘链接**直接分发 APK**（未上架传统应用商店）。
安卓默认禁止"未知来源"安装，首次安装需手动放行。按下面三步即可。

> 提示：安装过程中任何"此应用可能有害"的提示，都是系统对**所有非商店 APK** 的通用警告，并非 Appause 有问题。按第三步放行即可。

---

## 第一步：下载 APK

从 [GitHub Releases] 或国内镜像（如蓝奏云）下载最新版本的
`appause-vX.X.X-release.apk`。

## 第二步：允许"安装未知应用"

安卓 8（API 26）及以上，需要给**用来打开这个 APK 的 App**
（浏览器 / 文件管理器 / 网盘）单独授予安装权限：

1. 下载完成后点击 APK；
2. 若提示"禁止安装未知应用"，点**设置**；
3. 打开"**允许来自此来源**"开关；
4. 返回，继续安装。

> 不同机型入口名称略有差异，可能是"未知来源应用""安装未知应用"
> 或"允许安装应用"，含义相同。

## 第三步：放行安全扫描警告（重要）

- **Google Play Protect**：提示时选"**仍要安装**"（或"了解更多 → 仍然安装"）。
  也可临时在 Play 商店 → 右上头像 → Play Protect → 关闭"扫描应用"后再装。
- **小米（MIUI / HyperOS）**：弹"有风险"时点"**继续安装**"；若被"手机管家"拦截，
  进入管家 → 防护中心 → 临时关闭"安装监控"。
- **华为（EMUI / HarmonyOS）**：弹"纯净模式"拦截时点"**退出纯净模式**"或"继续安装（风险自负）"。
- **OPPO / vivo**：类似，选"**允许安装**"即可。

## 第四步：完成安装并开启无障碍服务

1. 打开 Appause；
2. 按应用内引导开启**无障碍服务（AccessibilityService）**——
   这是 Appause 检测前台应用、弹出暂停屏所必需的，无法绕过；
3. 按提示将 Appause 加入系统**自启动白名单 / 电池无限制 / 多任务锁定**，
   避免被系统杀后台（否则拦截会失效）；
4. 为减少误报，建议在系统设置 → 应用 → 特殊应用权限 → **使用情况访问**
   里给 Appause 授权（设置页也有入口）。授权后，通知栏里的媒体通知
   （例如 b 站"正在播放"）就不会误触发暂停。

---

## 常见问题

- **安装被拦截 / 解析失败**：确认 APK 下载完整（重新下载），并检查"未知来源"权限已开。
- **装完不拦截**：多半是无障碍服务被关或后台被清理，回设置重开并加白名单。
- **如何更新**：卸载旧版再装新版即可。配置存在本机数据库，正常不会丢；
  保险起见可在设置里先**导出配置**。
- **换手机 / 恢复出厂**：用设置里的"导出许可证 / 配置"备份，到新机导入即可（Pro 用户同理）。

---

## How to install (English)

1. Download the latest `appause-*-release.apk` from [GitHub Releases].
2. Tap the APK, then allow **"Install unknown apps"** for the app you opened it with.
3. If Play Protect or your device maker shows a *"harmful app"* warning,
   choose **"Install anyway"** — this is a generic warning for all non-store APKs,
   not specific to Appause.
4. Open Appause and enable the **AccessibilityService** when prompted, then
   exempt it from battery optimization so it is not killed in the background.

[GitHub Releases]: https://github.com/Shmily0826/Appause/releases
