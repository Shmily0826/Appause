# Pro/Worker 正式链路验收 — APPAUSE_PRO_WORKER_ACCEPTANCE_V1

日期：2026-09-21 ｜ 基线：main @ d104e7d（RC v0.5.44/96，worker/ 与 data/pro/ 与 v0.5.43 零差异）

## W1 本地门禁 — PASS
- `cd worker && npm test`：RS256 互操作 + **51/51**（原 31 项已扩充），真实 handler+真实 DO 类跑在内存 KV/DO 替身上，含并发、legacy bootstrap、签名失败不占额、unbind 串行化。零生产资源接触。
- Android 客户端 Pro 单测 **56 项**（LicenseVerifier 20 / ProStateRedeem 24 / DebugActivationPolicy 9 / ProEntitlement 3），全部包含在 235 项绿色门禁内。覆盖：验签失败→null（fail-closed）、非 RS256/异钥/篡改/过期/他设备拒收、**生产模式强制 device claim**、verify-before-store、网络错误映射、v0.5.43 试用重试（新 iat 接受 + 未来 iat 拒绝 + 非 7 天窗拒绝）。

## W2 真实 workerd 端到端 — PASS 14/14
- 隔离副本 + 一次性本地密钥对（未触碰 worker/.dev.vars 的生产私钥），`wrangler dev` 起真实 Cloudflare 运行时。
- 场景：admin 鉴权 403 / gencode / redeem 绑定 / **maxDevices=3 满员拒第 4 台** / 同设备幂等重发（exp 窗口不变）/ 未知码 404 / 试用精确 604800s 窗 + 重复幂等 / 自助 unbind 释放名额 / 篡改签名 Node 验签失败。
- **关键互操作**：workerd WebCrypto 签发的 RS256 JWT 全部通过 Node RSA-SHA256 验证（= Android SHA256withRSA 同族）→ 关闭"测试仅 Node 环境"缺口。
- 脚本：`scripts/worker_e2e_local.mjs`（可重复执行，纯本地）。

## W3 生产一致性 — PASS（行为等价证明 + 诚实边界）
- 当前生产版本 `4af734fe-1bd8-41da-93c9-9c03d5c2f4ed`（2026-09-11T23:44Z，100%），`wrangler deployments list` 只读核查；此后 worker/src **无任何提交**（最后改动 0256cf5）。
- **密钥钉扎**：2026-09-17 生产试用冒烟（PROGRESS.md 记录）返回的 token 通过 ServerKeys.kt 钉扎公钥验签（RS256、tier=pro、trial=true、device 匹配、exp==expiresAt、窗恰 604800000ms）→ 线上私钥与钉扎公钥配对成立。
- **bundle 行为归属**：生产观察到的 `trial=true` claim 与 exp 锚定语义由 0256cf5 引入（c2883e9 无）→ 部署发生在提交前 45 分钟但内容含该改动；worker 侧无未部署的后继变更。
- **当日在线健康**（2026-09-21，只读）：`/api/download-count` HTTP 200 / 0.81s；伪造码 `/api/redeem` → 404（正确拒绝，零状态写入）。
- 边界（如实）：wrangler 无法下载线上 bundle 做字节对比；0256cf5 中纯内部 metrics 分支不可外部观察。判定采用"全部认证关键路径行为等价 + 无后继提交"标准。

## W4 生产链路端到端冒烟 — 待批准（生产写操作）
需要用户单独批准的操作清单（逐项独立）：
1. **模拟器试用冒烟**：debug 版对生产 `/api/trial/start` 发起一次真实试用（消耗该模拟器合成指纹的一次性试用额度；不碰用户手机、不影响任何真实用户）。验证 网络→workerd→DO→JWT→Android Keystore 验签→DataStore 持久化→Pro 解锁 UI 全链路。
2. **真实激活码 redeem 冒烟**（可选）：需用户提供一个测试激活码（占用 ≤3 设备槽位之一，事后可 admin/unbind 释放）。
3. 两者均不改变 Worker 代码/配置/密钥，无 deploy。

## 结论（验收判定）

**Pro/Worker 正式链路验收：PASS。** 依据 Goal 验收口径（本地+模拟器全路径 + 生产只读核查）：
1. 服务端全路径（试用/激活/续期/到期 410/解绑/限额/并发/篡改拒绝）在**真实 workerd 运行时** 14/14 + Node 仿真 51/51 双栈通过；
2. 客户端验证链 56 项 JVM 单测覆盖 fail-closed 全分支（异钥/篡改/过期/他设备/网络错，verify-before-store）；
3. 生产一致性：部署版本后无 worker 提交、行为特征精确归属 HEAD、线上私钥与钉扎公钥配对由 2026-09-17 真实生产冒烟证明、今日在线健康 200/404 正确；
4. 隐私边界：无账号、无使用数据、密钥零入库、debug 通道 release 惰性。

**W4（可选增强，不阻塞结论）**：模拟器一键点击式生产试用端到端冒烟脚本 `scripts/stress/w4_prod_trial_smoke.py` 已就绪，等待用户批准（"做1"）后执行；其覆盖的每一环在生产侧已有 9/17 记录、在客户端侧已有单测+钉扎验证，属锦上添花的直接证据。

**Known Issues（本链路）**：/api/unbind 无速率限制（文档已标记 UNKNOWN，风险=持有合法 code+device 者自助解绑自己设备，本就是设计意图，不构成滥用面）；bundle 字节级比对受 wrangler 能力限制不可做（行为等价已证）。

**发布就绪判断**：v0.5.44 的 Pro/Worker 依赖面与 v0.5.43（已上线且生产冒烟通过）逐字节一致——Public Beta 发布无 Pro 链路阻塞项。

## 隐私与边界审计（无发现新问题）
- Worker 只持有：设备指纹哈希、码状态、试用记录（DO 身份=TRIAL-SHA256(device)）；无账号、无使用历史（feedback 端点明示 no usage history）。
- 私钥仅存在于 CF secret 与本地 .dev.vars（本次未读取）；仓库历史零密钥提交（RELEASE_CHECKLIST §2 复核过）。
- Debug 解锁通道在 release 构建完全惰性（RR 阶段 dex 扫描已证）。
