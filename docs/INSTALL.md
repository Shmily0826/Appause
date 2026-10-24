# 如何安装 Appause（侧载 / 直链安装指南）

Appause 目前未上架应用商店。最新版 **v0.5.46 / versionCode 98** 通过
[Appause v0.5.46] 分享，蓝奏云提取密码 **1234**，文件名为 `Appause-v0.5.46.apk`。
也可以使用 [GitHub v0.5.46] 备用下载；两个渠道的安装包与校验值相同。

## 第一步：下载安装包

从 [Appause v0.5.46] 下载 `Appause-v0.5.46.apk`，密码 **1234**，或使用 [GitHub v0.5.46]。
下载后打开文件，按系统提示安装。

## 第二步：仅为下载来源允许安装

Android 8（API 26）及以上，需要给**用来打开 APK 的应用**（浏览器或文件管理器）单独授予
“安装未知应用”权限：点击 APK；如果系统提示禁止安装，进入设置并仅为该来源打开“允许来自此来源”，
返回安装器继续。不同机型的设置名称可能略有不同。安装后可关闭该来源的安装权限。

## 第三步：配置运行权限

打开 Appause 后，首页会列出尚未开启的权限。**无障碍服务必须开启**，它用于检测当前打开的应用，
以便在你选择的应用前显示暂停提示。使用情况访问是可选权限，可帮助减少前台应用判断误报。
“显示悬浮窗 / 在其他应用上层显示”是兼容性备用权限；暂停页通常使用无障碍覆盖层，不需要该权限，
只有暂停页未显示时再按应用提示尝试开启。部分设备需要将 Appause 加入电池优化或自启动例外，
具体设置因厂商而异。

### 无障碍里的 Appause 是灰色的（安卓 13 及以上）

安卓 13 起，从浏览器或文件管理器安装的应用默认不能直接开启无障碍服务：Appause 在无障碍列表里
显示为灰色，点击后提示「受限设置 / Restricted setting」。这是系统对所有侧载应用的保护，不是
安装包有问题。解除方法：

1. 在无障碍设置里先点一次 Appause，关掉「受限设置」提示。
2. 打开 设置 → 应用 → Appause（应用信息页）。
3. 点右上角 ⋮，选择「允许受限设置」，按提示用锁屏密码或指纹确认。
4. 回到无障碍设置，打开 Appause。

如果 ⋮ 菜单里没有这一项，先回到第 1 步再点一次 Appause。不同品牌的菜单名称可能略有不同。

## 常见问题

- **提示安装包损坏、解析失败或签名不匹配**：先停止安装，从官方下载页面重新下载；如果仍然失败，把提示截图发给开发者。
- **提示有害应用或具体安全风险**：先停止安装，保留系统保护，并把提示截图发给开发者确认。
- **打开目标应用没有反应**：确认无障碍服务仍在运行；如暂停页没有显示，再检查系统的后台限制和可选悬浮窗权限。
- **如何更新**：从官方下载页面下载新版本并覆盖安装，不要先卸载；卸载会清除本机数据。若签名或完整性检查失败，请停止并联系维护者。
- **换手机 / 恢复出厂**：App 不提供配置或授权的导出 / 导入。分组、推荐应用等本机设置需要在新设备上重新配置；Pro 授权与设备绑定，换机后需要在新设备上重新激活。

<details>
<summary>可选：核对安装包的 SHA-256</summary>

如果你想确认下载的文件与发布版一致，可以在电脑上计算 SHA-256。此步骤不需要在手机上操作。

预期值：`ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927`。

- Windows：`Get-FileHash .\Appause-v0.5.46.apk -Algorithm SHA256`
- macOS / Linux：`shasum -a 256 Appause-v0.5.46.apk`

结果一致，说明文件与发布版相同；不一致时请重新下载。

</details>

## How to install (English)

Appause is not currently in an app store. The latest **v0.5.46 / versionCode 98**
is available directly from [GitHub v0.5.46]. If GitHub is hard to reach, use
[Appause v0.5.46] on Lanzou, password **1234**. Both provide the same APK.

### 1. Download the APK

Download `Appause-v0.5.46.apk` from [GitHub v0.5.46], or use Lanzou with password **1234**.
Open the downloaded file and follow Android's installation prompts.

### 2. Allow installs only from the app opening the APK

On Android 8 (API 26) and later, grant **Install unknown apps** to the specific browser or file manager you
use to open the APK. Tap the APK; if Android blocks it, open Settings and enable **Allow from this source**
for that app only, then return to the installer. Wording varies by device. You can turn this per-source
permission off after installation.

### 3. Finish setup

Open Appause and enable **AccessibilityService**. It detects which app is in the foreground so Appause can
show the pause prompt before a target app. Usage access is optional and can reduce foreground-detection
false positives. **Display over other apps** is an optional compatibility fallback; the pause screen normally
uses an accessibility overlay. Try that permission only if the pause screen does not appear. Some devices
may require a battery-optimization or auto-start exception; the setting varies by manufacturer.

#### Appause is greyed out in Accessibility (Android 13 and later)

From Android 13, apps installed from a browser or file manager cannot turn on an accessibility service
straight away: Appause appears greyed out in the Accessibility list, and tapping it shows **Restricted
setting**. Android applies this to every sideloaded app; it does not mean the APK is faulty. To unlock it:

1. In Accessibility settings, tap Appause once and dismiss the **Restricted setting** message.
2. Open Settings → Apps → Appause (the App info page).
3. Tap ⋮ in the top-right corner, choose **Allow restricted settings**, and confirm with your screen lock
   if asked.
4. Go back to Accessibility settings and turn Appause on.

If **Allow restricted settings** is missing from the ⋮ menu, repeat step 1 first. Menu names vary by brand.

### Troubleshooting

- **Damaged APK, parsing error, or signature mismatch**: stop and download the file again from the official page. If it still fails, send the developer a screenshot of the message.
- **Harmful-app warning or a specific security risk**: stop the installation, leave system protection enabled, and send the developer a screenshot for review.
- **No pause appears**: check that the accessibility service is running. If needed, check background restrictions and the optional overlay permission.
- **Updating**: install the newer APK over the existing app. Do not uninstall first, because that removes local data. If Android reports a signature or integrity problem, stop and contact the developer.
- **Changing phones or resetting the device**: there is no configuration or license export/import. Set up groups and recommended apps again on the new device, then activate Pro there if needed.

<details>
<summary>Optional: verify the APK's SHA-256</summary>

To check that your download matches the release, calculate its SHA-256 on a computer. You do not need to do this on your phone.

Expected value: `ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927`.

- Windows: `Get-FileHash .\Appause-v0.5.46.apk -Algorithm SHA256`
- macOS / Linux: `shasum -a 256 Appause-v0.5.46.apk`

A matching value confirms the file matches the release. If it differs, download it again.

</details>

[Appause v0.5.46]: https://shmily0826.lanzoup.com/b01eunt29a
[GitHub v0.5.46]: https://github.com/Shmily0826/Appause/releases/download/v0.5.46/Appause-v0.5.46.apk
