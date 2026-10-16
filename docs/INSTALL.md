# 如何安装 Appause（侧载 / 直链安装指南）

Appause 目前未上架应用商店。最新版 **v0.5.46 / versionCode 98** 通过
[Appause v0.5.46] 分享，蓝奏云提取密码 **1234**，文件名为 `Appause-v0.5.46.apk`。
也可以使用 [GitHub v0.5.46] 备用下载；两个渠道的安装包与校验值相同。

## 第一步：从官方页面下载并核对文件

从 [Appause v0.5.46] 下载 `Appause-v0.5.46.apk`，密码 **1234**。预期 SHA-256：
`ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927`。
在本机计算下载文件的值；两者不一致时不要安装，应从官方页面重新下载。

- Windows：`Get-FileHash .\Appause-v0.5.46.apk -Algorithm SHA256`
- macOS / Linux：`shasum -a 256 Appause-v0.5.46.apk`

Android 会在安装时验证 APK 签名。如果系统报告签名无效、安装包损坏，或更新包与已安装应用
签名不匹配，请停止安装并重新从官方下载页面获取文件；不要尝试绕过签名检查。

## 第二步：仅为下载来源允许安装

Android 8（API 26）及以上，需要给**用来打开 APK 的应用**（浏览器或文件管理器）单独授予
“安装未知应用”权限：点击 APK；如果系统提示禁止安装，进入设置并仅为该来源打开“允许来自此来源”，
返回安装器继续。不同机型的设置名称可能略有不同。安装后可关闭该来源的安装权限。

## 第三步：认真处理安全提示

“允许来自此来源”只允许用户发起侧载，不代表 APK 已通过安全扫描。若 Play Protect 或设备厂商的
安全功能显示具体威胁、拦截安装或要求关闭扫描，请保持保护功能开启并停止安装。先从官方下载页面
重新下载并核对 SHA-256；如提示仍存在，等待官方澄清或联系设备安全功能提供方。不要因 APK 来自
应用商店之外就把所有安全警告视为通用提示，也不要关闭 Play Protect、厂商扫描或其他安全保护。

## 第四步：完成安装并配置运行权限

打开 Appause 后，首页会列出尚未开启的权限。**无障碍服务必须开启**，它用于检测当前打开的应用，
以便在你选择的应用前显示暂停提示。使用情况访问是可选权限，可帮助减少前台应用判断误报。
“显示悬浮窗 / 在其他应用上层显示”是兼容性备用权限；暂停页通常使用无障碍覆盖层，不需要该权限，
只有暂停页未显示时再按应用提示尝试开启。部分设备需要将 Appause 加入电池优化或自启动例外，
具体设置因厂商而异。

## 常见问题

- **安装被拦截或解析失败**：重新从官方下载页面下载并核对 SHA-256。若安全功能报告具体威胁，停止安装并保持扫描开启。
- **打开目标应用没有反应**：确认无障碍服务仍在运行；如暂停页没有显示，再检查系统的后台限制和可选悬浮窗权限。
- **如何更新**：从官方下载页面下载新版本并覆盖安装，不要先卸载；卸载会清除本机数据。若签名或完整性检查失败，请停止并联系维护者。
- **换手机 / 恢复出厂**：App 不提供配置或授权的导出 / 导入。分组、推荐应用等本机设置需要在新设备上重新配置；Pro 授权与设备绑定，换机后需要在新设备上重新激活。

## How to install (English)

Appause is not currently in an app store. The latest **v0.5.46 / versionCode 98**
is available directly from [GitHub v0.5.46]. If GitHub is hard to reach, use
[Appause v0.5.46] on Lanzou, password **1234**. Both provide the same APK.

### 1. Download from the official page and verify the file

Download `Appause-v0.5.46.apk` from [GitHub v0.5.46], or use Lanzou with password **1234**. Expected SHA-256:
`ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927`.
Do not install the file if the values differ; download it again from the official page.

- Windows: `Get-FileHash .\Appause-v0.5.46.apk -Algorithm SHA256`
- macOS / Linux: `shasum -a 256 Appause-v0.5.46.apk`

Android verifies the APK signature during installation. If Android reports an invalid signature, a damaged
package, or a signature mismatch with the installed app, stop and obtain the APK from the official release.
Do not try to bypass signature checks.

### 2. Allow installs only from the app opening the APK

On Android 8 (API 26) and later, grant **Install unknown apps** to the specific browser or file manager you
use to open the APK. Tap the APK; if Android blocks it, open Settings and enable **Allow from this source**
for that app only, then return to the installer. Wording varies by device. You can turn this per-source
permission off after installation.

### 3. Respect security warnings

The per-source install permission only permits a user-initiated sideload; it does not mean a security scan
approved the APK. If Play Protect or your device maker reports a specific threat, blocks installation, or asks
you to turn off scanning, leave protection enabled and stop. Download again from the official release and
verify its SHA-256. If the warning remains, wait for official clarification or contact the security feature's
provider. Do not treat every warning as generic just because the APK is distributed outside an app store, and
do not disable Play Protect, vendor scanning, or other protections.

### 4. Finish setup

Open Appause and enable **AccessibilityService**. It detects which app is in the foreground so Appause can
show the pause prompt before a target app. Usage access is optional and can reduce foreground-detection
false positives. **Display over other apps** is an optional compatibility fallback; the pause screen normally
uses an accessibility overlay. Try that permission only if the pause screen does not appear. Some devices
may require a battery-optimization or auto-start exception; the setting varies by manufacturer.

To update, install the newer APK from the official download page over the existing app. Do not uninstall first because
that removes local data. Stop if Android reports a signature or integrity problem.

[Appause v0.5.46]: https://shmily0826.lanzoup.com/b01eunt29a
[GitHub v0.5.46]: https://github.com/Shmily0826/Appause/releases/download/v0.5.46/Appause-v0.5.46.apk
