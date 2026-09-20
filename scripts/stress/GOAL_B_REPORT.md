# Goal B 最终报告 — APPAUSE_HYPEROS_DEVICE_SAFETY_V1

设备 Xiaomi 2410DPN6CC / HyperOS Android 16 (6036d5b) · 会话 2026-09-20 ·
基线 main @ 2c30a97（未 commit，见 §7）

## 1. 自动真机测试（Phase B，全部有 evidence/goalB/ 落盘）

| 探针 | 场景 | 结果 |
|------|------|------|
| P1.back-steady ×3 | 三键 Back 稳态逃逸 | PASS 3/3（backCB/dispatch 全触发） |
| P2.back-focuswindow | 焦点窗口内 Back | PASS（可逃逸） |
| P3.home-3button | Home 键逃逸+重拦 | PASS |
| P4.home-gesture-mode | 手势模式 Home 键逃逸+重拦 | PASS |
| P5.cancel-tap | Cancel→Room `cancelled` | PASS（复测，首 FAIL 为 WAL harness 问题） |
| P6.gesture-injection-noop | 注入边缘滑动不半触发 | PASS |
| P7.residual-clean | 会话结束零残留+服务存活 | PASS |
| P8.control-never-intercepted | 对照组零误拦 | PASS |
| J1 | Cancel→冷却→重进→重拦→Cancel | PASS |
| J2 | Continue→App 内 Back→Home→宽限期静默 | PASS |
| J3 | 双 App session 隔离 | PASS |
| J6 | cooldown 门控（早期 Continue 无效/到期有效） | PASS |

## 2. 人工参与（Phase C，一次集中批次）

| 项 | 用户动作 | 结果 |
|----|----------|------|
| C1 | 真实边缘 Back 滑 ×3 | 拦截层不 dismiss（与 D3 一致，设计预期，无意外逃逸） |
| C2 | 真实 Home 上滑 | **修复前 FAIL→F-27**；修复后单次上滑卡片同步消失，用户确认 |
| C3 | 焦点窗口内即时 Home 滑 ×2 | PASS 2/2（反应 103s/70s 均正常关闭） |
| J4 | 锁屏→唤醒→拦截 | PARTIAL（用户未实际锁屏；唤醒后拦截+pid 稳定已验，锁屏项遗留） |
| C5 | 主观体验 | 用户报告"有轻微迟滞/卡顿感"→ 记入风险清单（见 §6） |

## 3. 发现的真实产品问题

**F-27（A 类，已修复+双端验证）— Home 后拦截卡永久滞留桌面**
- 症状：手势 Home 回到桌面后 2032 卡片滞留 2+ 分钟不自清，用户"要滑两次"。
- 根因：Home 逃逸的三条关闭路径全部被 `!closeSystemDialogsReceiverRegistered`
  门控——押注 CLOSE_SYSTEM_DIALOGS 广播送达；Android 12+ 已停止向普通应用投递
  （AOSP-34 模拟器同样复现：注册成功、投递为零）。
- 修复（最小）：接收器降级为加速器，事件+UsageStats 确认关闭路径无条件运行；
  策略门（shouldConfirm/shouldDismissForConfirmedHome）与 Recents 护栏全部保留，
  未削弱任何 fail-closed/逃生/持久化属性。删除死标志变量。
- 验证：testDebugUnitTest+assembleDebug PASS；模拟器 HOME 关闭延迟 2.2s（修复前
  永不关闭）+ P9 3/3 + LEAVE-HOLD PASS；真机安装后用户单次上滑即时消失。

**harness 缺陷（B 类，全修）**：F-24（HyperOS dumpsys 块格式/focus 回退/陈旧行
arm/adb kill 无效，共 4 项）、F-25（重启重置 appops 三连）、F-26 补充（WAL 合并
读取）。**环境（C 类）**：F-26 HyperOS 冻结 FGS 进程至 adj=200（用户设"无限制+
自启动"后消失）——发布引导必须强调，产品文案已具备（onboarding 电池专步+警告卡）。

## 4. 修改文件

产品：`app/src/main/java/com/appause/android/service/AppauseAccessibilityService.kt`
（F-27 修复，4 处门控移除+死变量删除）
Harness/文档：device_lib.py、goalb_seed.py、d7_escape_safety.py、
d8_real_app_journeys.py、goalb_preflight.py、goalb_phase_c.py、
GOALB_READINESS.md、FINDINGS.md（F-24~F-27）、本文件

## 5. 各层验证

unit（testDebugUnitTest）PASS · build（assembleDebug）PASS ·
emulator（F-27 复现+修复验证+P9/LEAVE-HOLD 回归）PASS ·
device（D7 10 探针 + D8 4 journey + Phase C + F-27 修复安装复验）PASS

## 6. 剩余风险（带入下一阶段）

1. **F-27 回归监视**：真机 Recents 窥视路径依赖 burstTracker（接收器在 12+ 已死），
   下阶段建议模拟器+真机专项跑一次 Recents peek→返回。
2. 用户主观"轻微迟滞"——未量化，Release Readiness 阶段做打开延迟测量。
3. J4 锁屏唤醒真机项未完整执行（用户未锁屏）——D4 已覆盖重启路径，风险低。
4. J5（temp pass 跨重启）未跑——纯 harness 流程已备（Phase C 脚本内），低风险。
5. HyperOS 冻结行为：用户侧"无限制+自启动"是硬前提（引导已有，验证文案有效性 OK）。
6. 正式版 Appause 尚未安装本修复——**F-27 修复需随下个 release 出**（当前仅 debug 槽）。

## 7. Git 状态与批准请求

main @ 2c30a97（与 origin 一致）；工作区含 F-27 产品修复 + harness/文档改动，
**未 commit**。建议：单条 commit（fix(service): dismiss pause overlay on Home
without CLOSE_SYSTEM_DIALOGS + Goal-B harness），push 另行批准。

## 8. Goal B 验收结论

**最终状态：COMPLETE（收尾审计 2026-09-20 深夜）**

8 项停止条件逐项：
1. F-27 diff 已 review：4 处门控移除、死变量删除；策略门与 Recents 护栏保留；
   dismiss 幂等；无新增逃生/误关风险。观察项：Android<12 平台 poller 从"让位
   接收器"变为"参与判定"（双保险幂等）→ 交接清单。
2. 真机复验：用户单次 Home 上滑即时消失（A 确认）+ 窗口检测 focus=launcher、
   overlay=0、服务存活；修复前 FAIL 时间线（+38s overlay=True focus=home，
   2 分钟不自清）与修复后 PASS 分别存档。
3. P5 关闭为 harness 假阴性（WAL 未合并读取），13 条真机 cancelled/proceeded
   记录归档于 p5-closure-evidence.db；未重跑 D7。
4. D7 10/10、D8 J1/J2/J3/J6 PASS、Phase C 如实记录（C1 设计确认、C2 发现并
   验证修复、C3 2/2、J4 PARTIAL、C5 主观迟滞）——无冒充单次全绿。
5. F-26 交接：见 §9。
6. J4/J5 归属：见 §9。
7. 环境恢复：见 §6 + FINDINGS 收尾附录（两项残留均为惰性/用户侧一步开关）。
8. Git：见 §7。
9. 结论：**可以开始 Android Release Readiness**。唯一 release blocker：
   **F-27 修复必须进入首个 RC 构建**（当前正式版不含该修复）。

## 9. 交接到 Android Release Readiness 的验收清单

1. [BLOCKER] F-27 修复合入 release 构建，并在真机重装正式版后复验 Home 手势逃逸。
2. F-26 边界结论：Debug 槽在"未设无限制+自启动"时被 HyperOS 冻结（adj=200），
   事件延迟 50s–3min；用户设置后 preflight 实测拦截延迟 0.4s——**正常配置下
   核心拦截能力不受影响，非 release blocker**；但默认电池策略的真实用户会遭遇
   延迟拦截。验收项：a) 引导流程必须把"无限制+自启动"设为完成前硬步骤并验证
   生效；b) 在默认策略的干净配置文件上量化最坏拦截延迟；c) 验证 Release 版
   （非 debug 槽、非白名单）同样行为。
3. Recents 窥视回归（接收器在 12+ 已死，recentapps 时间戳不再更新，
   burstTracker/step6.5 为唯一守卫）+ Android<12 平台 poller 新参与判定的观察。
4. C5"轻微迟滞"量化（overlay 出现延迟、按钮响应延迟的 instrumented 测量）。
5. J4 锁屏→解锁→即时拦截真机项补测（Goal B PARTIAL）；J5 temp-pass 跨重启
   （模拟器 P3/P10-J4 已覆盖语义，真机侧仅剩重启保持性，D4 已证授权保持）——
   低风险，Release 阶段顺带补。
6. 用户侧待办：无障碍开关恢复（Debug OFF / 正式版 ON）——收尾最后一步。
