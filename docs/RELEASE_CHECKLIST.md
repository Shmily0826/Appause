# Appause 发版清单（Release Checklist）

> 面向"准备给陌生用户使用"的发版流程。每次发 release 前**从上到下过一遍**，
> 全部勾完才打包。当前公开版本为 v0.5.40 / versionCode 92，改配置后请同步更新。

## 1. 版本与构建配置

- [ ] `app/build.gradle.kts`：`versionCode` +1（当前 92 / v0.5.40），`versionName` 按语义更新
  - debug flavor 的 `versionNameSuffix = "-debug"` 依赖 **更高的** versionCode 保证共存，勿降
- [ ] minSdk 26 / targetSdk 未被意外改动；新增依赖在 version catalog（`gradle/libs.versions.toml`），未硬编码版本号
- [ ] `./gradlew assembleDebug` 与 `./gradlew assembleRelease` 均通过（需 `JAVA_HOME=D:\Dev-Setup\jdk`）

## 2. 安全与隐私（每次必查）

- [ ] `worker/.dev.vars`（JWT 私钥 + ADMIN_KEY）不在 `git status` 里（`.gitignore` 已修复行尾注释 bug，但要防止未来再犯）
- [ ] `signing.properties` / `*.jks` / `*.keystore` 未被暂存（`.gitignore` 有规则，确认未被 `git add -f` 过）
- [ ] `git log --diff-filter=A -- worker/.dev.vars signing.properties` 为空——历史上从未提交过密钥
- [ ] `canRetrieveWindowContent = false` 仍为 false（`AndroidManifest.xml` 的 service 配置），只读包名
- [ ] 新增代码没有引入网络上报/统计 SDK；反馈只走系统 Intent（`ui/feedback`）
- [ ] `PRIVACY.md` / `privacy-policy.html` 与实际行为一致（新权限、新数据流都要补进去）

## 3. 功能行为确认

- [ ] 拦截链路（`docs/INTERCEPTION_PROTOCOL.md` §7 状态机）没有被本次改动破坏：
      打开 grouped 应用 → 暂停屏 → Continue 进应用 → 退出 3 分钟后 re-arm → 再开再拦
- [ ] Cancel 回到桌面（不是留在目标应用）
- [ ] 应用内切换（相册/分享面板等）不重新弹暂停屏
- [ ] Home/Back/Recents 能正常逃出暂停屏（overlay 不遮三键导航区）
- [ ] 无障碍服务被关闭后，主页出现红色恢复卡片；重新开启后消失（AccessibilityHealthState）
- [ ] 临时通行（5/15/30 分钟）到期后重新拦截
- [ ] Pro 功能（re-remind、自定义文案/理由）在未解锁时正确锁定
- [ ] 中英文（values / values-zh）字符串都补齐，无缺翻译

## 4. 测试门禁

- [ ] `./gradlew testDebugUnitTest` 全绿（数量以当前运行结果为准）
- [ ] 模拟器冒烟：安装 → onboarding → 建组 → 拦截 → 暂停屏操作（每轮发版至少一遍）
- [ ] 真机冒烟（小米 HyperOS 优先）：同上 + P0 导航逃生（Home/Back/Recents 物理按键）
      模拟器结果**永远不得**记为真机结果（PROGRESS.md 惯例）
- [ ] 测试结果按日期追加到 `TEST_REPORT.md`（PASS/FAIL/NOT TESTED，append-only，不删旧记录）

## 5. Debug 与 Release 隔离

- [ ] Diagnostics 界面只存在于 debug flavor（`com.appause.android.debug`），release 无入口
- [ ] Pro on/off、Restart onboarding 等测试开关未被接入任何产品流程
- [ ] release 构建不含 debug-only 资源（`3b65b68` 已隔离，改资源后复查）
- [ ] 日志：`AppLogger.d` 的敏感路径（包名决策日志）在 release 可接受（本地 logcat，无上传），但确认没有新增可含个人数据的日志

## 6. 打包与命名

- [ ] `./gradlew assembleRelease` 产出 `app/build/outputs/apk/release/app-release.apk`
- [ ] `python scripts/make_release.py` 复制为 `output/Appause-v<版本>.apk`（脚本读 gradle 里的版本号，勿手改名）
- [ ] 手动安装这个最终 APK 做一次冒烟（装的是"用户拿到的那个文件"，不是 debug 包）
- [ ] 旧版本 APK 不再堆积在 `output/`（只留最新 release + 当前 debug）

## 7. 发布物料

- [ ] `RELEASE_NOTES.md` 追加本版说明（面向用户语言，不写内部 Task ID）
- [ ] GitHub Pages（`index.html` / `privacy-policy.html`）版本信息、下载说明与新版一致
- [ ] `INSTALL.md` 安装步骤仍准确（权限授予路径、使用情况访问说明）
- [ ] 应用内 onboarding / About 的版本号显示正确（如有）
- [ ] 截图（`output/screenshots/`，会发布到 Pages）与新 UI 一致

## 8. Git 与收尾

- [ ] 工作区干净：`git status` 无未预期的改动；`build/`、`.gradle/`、APK 不入库
- [ ] 提交信息描述 what + why；按 AGENTS.md §10，**commit / push / tag / release 每一步都要用户单独授权**
- [ ] `PROGRESS.md` 追加本阶段记录
- [ ] （可选）`METRICS.md` 周快照照常更新

## 9. 发布后

- [ ] 记录发版时间与 versionCode，便于回溯 CrashLog / PersistentLog 的用户反馈
- [ ] 遇到 OEM 相关 bug 时：先在 `docs/INTERCEPTION_PROTOCOL.md` §9 找已有风险条目，再开新修复任务
