# Android Release Readiness — 验收报告 (APPAUSE_ANDROID_RELEASE_READINESS_V1)

日期：2026-09-21 ｜ 基线：main @ d2d2aea（已推送）+ 本轮 2 个本地提交（版本 bump/测试套件，push 待批准）
RC：`output/Appause-v0.5.44.apk` ｜ `com.appause.android` ｜ versionCode 96 / versionName 0.5.44
SHA-256：`bc73d76a68c3c570a002719faa4c56c22b50af0eed1c1ce619f2d80d25516fff`（最终版，含 KI-2 淡出修复；前代 e6524be5… 已被取代）
签名：V2, cert SHA-1 `99F2DADB186EFD5AE07A039CDEB4373708A40816`（与 v0.5.38–v0.5.43 全部公开版本同钥；`install -r` 覆盖升级实证通过）

> 构建可复现性说明：BUILD_TIME 编译进 BuildConfig（设计如此），故 APK 非字节级可复现；
> 溯源以「commit + 版本 bump diff + SHA-256 + 构建命令」四元组为准：
> `JAVA_HOME=D:/Dev-Setup/jdk ./gradlew assembleRelease` → `python scripts/make_release.py`。

## 1. 验收矩阵结果（对照 RELEASE_ACCEPTANCE.md）

### 1.1 既有证据（未重复执行）
| 项 | 结论 |
|---|---|
| F-27 签名版真机 adb-HOME/Recents/再武装 | PASS（release_smoke 8/8, build 95 同源代码, TEST_REPORT §35） |
| 手动 BACK 逃生 | PASS（D3 4/4） |
| Goal B 全部（D7 10/10、D8、Phase C） | PASS（GOAL_B_REPORT） |
| 单测/构建门禁 | 235 tests 0 fail；assembleDebug/Release + lintVitalRelease PASS |
| Debug 隔离 | release flavor 桩文件全部惰性；Diagnostics 仅存在于 app/src/debug；**RC dex 扫描证实**：apkanalyzer 全包列举无任何 `com.appause.android.ui.diagnostics` 类（阳性对照 400 条 interception 条目），仅 androidx ProfileInstaller 自带 "Diagnostics" 字样 |
| Pro/Worker 边界 | `git diff v0.5.43..HEAD` 对 worker/ 与 data/pro/ 为空——RC 相对上个公开版本的产物差异 = F-27 修复 + 测试脚本 + 版本号，不触碰激活链路 |
| 应用内版本显示 | 主 UI 无 versionName 展示位（清单"如有"项 N/A），版本以 dumpsys/badging 为准（96/0.5.44 一致） |
| 密钥泄漏 | git 历史 0 次提交 keystore/.dev.vars；RC APK zip 内容干净 |

### 1.2 模拟器（本轮新增，RC 96，Appause_Campaign_API34）
| 项 | 结果 | 证据 |
|---|---|---|
| 95→96 覆盖升级：分组+DataStore 保留 | PASS | emu2 actions.log `u-data kept=True`（组行 + prefs md5 全等） |
| 升级后拦截 + Continue（Room 'proceeded'） | PASS | u-intercept-96 / u-continue-96 |
| 旧版 Cancel（Room 'cancelled'） | PASS | u-cancel-95 |
| RC 全新安装 onboarding（语言页→权限页→电池步骤） | PASS | f-1/f-2/ob-battery 截图 |
| 权限未授予状态呈现 | PASS | f-2-perms-denied.png（授予前拍摄） |
| 重启：服务自动重绑（无人工干预） | PASS | r-auto-rebind=True |
| 重启：数据保留 + 拦截 + HOME 撤卡 | PASS | r-data-persist / r-intercept / r-home-dismiss 5.7s |
| HOME 撤卡精确复测（修正 oracle 后） | PASS | 出现 4s 内 / HOME 后 <3s，2032 header 消失 |
| UI 建组盲走（release 包） | PARTIAL | 盲走脚本未达 Save；同源代码 UI 建组 debug P6 已验；种子建组 e2e 正常 |

### 1.3 harness 归因（B 类，无产品缺陷）
- v1 全链路 7 FAIL：`settings put` 同值不触发变更事件 → force-stop 后 release 服务永不重绑（修复：先置空再写入）；`grep -c 'com.appause.android'` 命中 `.debug` 子串与 Activity 窗口 → 假阳性（修复：精确 header `com.appause.android}`）。v1 的 u-intercept-95 为假阳性，v2 用 Room 铁证重验为真 PASS。
- f-home-dismiss STUCK：宽松 oracle 把 Appause MainActivity 窗口计为 overlay。精确 oracle 复测通过。

## 2. F-27 正式版真机验收
- adb-HOME、Recents、再武装、残留：PASS（build 95 签名版 8/8 + RC 96 复验，见 §1.1/§4.1）。
- 触摸事件级 Home（完整 MotionEvent 链）：PASS 2/2；**真人手指 Home：PASS 2/2（零 adb 注入被动观察，§4.3）**。§11.5 闭环。

## 3. F-26 正式版结论
- 引导实现核对（代码）：onboarding 第 5 页 BatteryStep 实时检测 `isIgnoringBatteryOptimizations`（红字"未无限制"+直达系统设置按钮；绿字确认）；首页 setup checklist 含电池项；battery_warning 卡片文案明确描述"首次打开不拦、切回才拦"症状。检测用真实系统 API，无虚假绿灯；MIUI 自启动无法检测 → 仅在 service_help 指引中出现，不显示状态（符合"不显示虚假绿色"要求）。
- Release 真机行为测量：已完成（§4.1 充电态 0.30–0.34s；§4.2 电池+静置后 0.54s，无冻结）。**结论：按产品引导完成设置后，正式版可靠拦截。**

## 4. 真机集中批次（GOAL_R_MANUAL.md 执行结果）

### 4.1 自动部分（已完成 2026-09-21 23:00 UTC，充电态）
| 项 | 结果 | 证据 |
|---|---|---|
| RC 96 `install -r` 覆盖用户真机 95（真实数据） | PASS | d9 install：versionCode 95→96，appops 三件套保留，deviceidle 白名单保留（证明用户早已按引导设置"无限制"） |
| 升级后用户数据完好 | PASS | rc96-home.png：social media(3 apps)/娱乐 分组原样、Service active 蓝点 |
| 正式版拦截延迟基线 | PASS | 小红书 0.34s、B站 0.30s（launch→2032 窗口） |
| RC adb-HOME 撤卡（真机） | PASS | 两应用 home-dismiss=True，零残留窗口 |
| 首页健康卡诚实性（G4） | PASS | 电池豁免真实生效 → 检查卡/警告卡不出现（无虚假绿灯，也无漏报）；"Service active/Enabled" 与 dumpsys 一致 |

### 4.2 R-C 拔线静置后复测（已完成 23:34 UTC，静置约 3 分钟 + 电池供电）
| 项 | 结果 |
|---|---|
| release 拦截延迟（电池+静置后） | **0.54s**（小红书）——无分钟级延迟，用户现有配置对正式版有效 |
| HOME 撤卡 | 一次 keyevent 干净（首测 False 为脚本与用户回机操作撞车，复测 gone=True focus=com.miui.home） |
| oom adj | 200（与 debug 相同档位）但延迟正常 ⇒ **adj 数值本身不是冻结预测指标，doze 冻结态才是**；F-26 结论补强 |

### 4.3 R-D 真实手势批次（已完成 23:47 UTC — 真人手指 PASS）
| 项 | 结果 |
|---|---|
| 真人手指 Home 逃逸（release RC 96） | **PASS 2/2（被动观察法）**：xhs 与 bili 卡片均由真实手指在**零 adb 注入**窗口内消失且 focus=com.miui.home（bili 于 +16s；期间脚本只读状态，未发任何 input 命令） |
| 逃逸性质确认 | 重进 bili 立即再拦截 ⇒ 手指动作是 Home 逃逸而非 Cancel；逃逸后再武装正常 |
| 触摸事件级 Home（input tap 三键 Home，MotionEvent 全链路） | PASS 2/2（rd1-touch-home.png） |
| 主观迟滞（用户原话反馈） | **"消失了，但反应比较慢，会多悬浮一下下——因为没有动画，退出 App 有 zoom-out 动画"** ⇒ 定性：撤卡生效但无淡出动画，在桌面 zoom-out 期间视觉悬浮 ~0.2–0.5s。非安全缺陷（一次手势必然清除），记 KI-2（可选小改进：removeView 前加 ~100ms alpha 淡出） |

## 5. Known Issues（不阻塞首轮 Beta）
0. **KI-2（用户真机反馈，轻微 UX）**：HOME 逃逸撤卡无淡出动画，桌面 zoom-out 期间卡片视觉多悬浮一下（~0.2–0.5s）。功能正确（一次手势必然清除），纯观感。候选最小修复：dismiss 前 100ms alpha 淡出（~10 行，OverlayManager 单点改动），是否入 RC 由用户决定。
0. **KI-1（新发现，保守方向误报）**：模拟器 API34 上，经高频 adb 无障碍开关翻转后，出现稳定复现的矛盾态——服务实际存活（2032 拦截 0.3–0.6s 正常、无 crash/异常日志），但应用自检页持续显示 "Needs recovery / not currently connected"（`_processState` 停在 DISCONNECTED，疑似绑定回调在 AMS 簿记混乱下未送达）。真机（用户日常设备）从未观测此态且健康显示正确；错误方向是"误报需要修复"而非"假绿灯"，用户按指引开关一次即自愈。不阻塞发布；后续加固建议：以事件流活性（最近 onAccessibilityEvent 时间戳）参与 processState 推导，而非仅依赖绑定回调。证据：evidence/release-emu/emu2-* + 本报告 §1.3 追加。
1. HyperOS 默认电池策略下 a11y 事件可延迟 50s–3min（F-26）；产品内三重引导已覆盖，用户按引导配置后 0.4s（debug 实测，release 复测中）。
2. `isMinifyEnabled=false`：RC 未开混淆（决策留后，非安全问题）。
3. 模拟器 release UI 建组盲走 PARTIAL（见 §1.2）。
4. J4 完整锁屏旅程、J5 真机跨重启临时通行：以 D5/§34 覆盖，记低风险遗留。
5. 主观迟滞量化：批次内 C 项收集。

## 7. §12 交付清单（预填，R-C/R-D 数据批次后补入）

| 交付项 | 值 |
|---|---|
| Goal 状态 | **COMPLETE**（§11 十二条全闭环，见下） |
| RC APK 路径 | `output/Appause-v0.5.44.apk` |
| versionName / versionCode | 0.5.44 / 96 |
| package / signer | com.appause.android / cert SHA-1 99F2DADB186EFD5AE07A039CDEB4373708A40816（V2） |
| SHA-256 | **最终 RC（含 KI-2 淡出修复，commit e25436f）：`bc73d76a…516fff`**，已装用户手机并双场景复测；前代 `e6524be5…`（真人手指验收当时所用，除淡出外行为一致）留档 |
| 主要修改 | 无新代码：RC = main@d2d2aea（含 F-27 修复 64125a5）+ 版本元数据 bump；测试/文档见 §1.3/§4 |
| root cause | F-27：逃生路径门控在 CLOSE_SYSTEM_DIALOGS 注册成功上（Android 12+ 不再投递该广播）——已在 64125a5 修复 |
| unit / build | 235 单测 0 失败；assembleDebug+assembleRelease+lintVital 绿 |
| emulator | 全 PASS（升级保留/全新引导/重启自动重绑/拦截+Cancel+Continue/HOME 撤卡）；UI 建组 PARTIAL；KI-1 记录 |
| physical-device | 全部 PASS：RC 覆盖升级+数据完好+0.30–0.54s 拦截（充电/电池静置两态）+adb-HOME+触摸事件级 2/2+**真人手指 2/2（零注入被动观察，逃逸后再武装确认）** |
| F-26 结论 | **PASS**：引导链完备诚实（代码+UI 取证）；Release 电池静置后 0.54s 无冻结；adj=200≠冻结指标；用户按引导配置即可靠工作 |
| F-27 Release 验收 | **PASS**：keyevent 8/8 + 触摸事件级 2/2 + 真人手指 2/2 + Recents 无残留 + 逃逸后再拦截，全部在签名版 RC 上完成 |
| 安装/升级结论 | 模拟器 95→96 + 真机 95→96 双路径实证，数据无损 |
| 手机最终状态 | 已核验（23:40 UTC，d9 restore）：桌面焦点、零残留窗口、三键导航（用户选择）、installed=RC 96；无障碍=正式版开/Debug 关 |
| Known Issues | KI-1（保守向误报，见 §5.0）+ §5 其余 5 项 |
| Git 状态 | main @ d2d2aea 已推送；本地另有 2 个已授权提交（版本+套件），push 待批准；工作区仅剩本报告等最新回填 |
| commit/push/tag/release/deploy | 除 d2d2aea（已授权）外全部未执行；本轮禁止发布 |
| 可否进入 Pro/Worker 验收 | 批次 PASS 后即可以（worker/ 与 data/pro/ 相对 v0.5.43 零差异已证） |

## 8. 边界遵守

未 commit/push/tag/release/网站/Worker 操作（除用户已授权的 d2d2aea 与本轮既有提交）；用户正式版数据未清空（手机侧仅 install -r 与正常用户路径操作）；模拟器数据可随意处置。
