# Appause 海外收款路线（Merchant of Record / MoR）

> 适用场景：面向**英语用户**（Instagram / TikTok / YouTube / 游戏等）卖 Appause Pro 买断激活码。
> 配套文档：`docs/afdian-domestic-route.md`（国内 · 爱发电）。
> 研究日期：2026-08-17；**2026-08-19 复核修正**（平台对比与接入策略）。
> 费率 / 支持国 / 政策会变动，注册前以各平台官网为准。

---

## 1. 为什么走 MoR，而不是直接 Stripe

Stripe 与公司注册、账户资格、费用及税务要求会随地区和时间变化。作者当前在
新西兰学习，使用 Stripe 或成立公司是否符合签证条件，应先由持牌移民顾问与
会计师确认。本文件不再用固定费用或确定性结论代替平台审核和专业意见。

**MoR（Merchant of Record）平台 = 法律上的卖家**：
- 平台通常作为面向消费者的销售主体，但卖家是否可用个人身份申请仍取决于
  所在地、证件、银行账户、税务身份和平台审核；
- **自动替你收全球 VAT / GST / 美国销售税**并申报——否则你得在 30+ 国注册税号；
- 付款方式、退款、欺诈、发票全由平台处理。

MoR 不能改变签证和所得税规则。收到 MoR 打款是否构成工作、自雇或经营收入，
必须以新西兰持牌移民顾问和会计师针对本人情况给出的意见为准。

---

## 2. 你能当卖家吗？（决定性门槛 — 已确认开放）

作者目前在新西兰居住并持中国证件。以下平台公开材料显示可能支持相关地区，
但最终资格以注册时的 KYC、银行与税务资料审核为准：

| 平台 | 卖家国支持 | 判定依据 |
|---|---|---|
| **Lemon Squeezy** | ✅ 银行打款官方列表含 **New Zealand**；PayPal 打款 200+ 国 | 注册国 / 打款国 |
| **Dodo Payments** | ✅ **New Zealand** 与 **China** 均在接受列表 | 按**证件签发国**（中国护照即中国，算支持） |
| **Paddle** | ✅ 支持 200+ 国，含新西兰 / 中国 | 注册国 |

→ 平台地区列表不等于账号必然获批。注册前应向平台书面确认，并完成签证与税务咨询。

> ⚠️ **注册主体的真实性**（2026-08-19 补充）：商家所在地、实际居住地址、银行账户与税务身份必须**真实且一致**——
> 不能用「NZ 居住地」虚构注册以绕开中国主体。中国护照 + NZ 地址/银行能否过审，**须在注册前向平台书面确认**（工单/邮件留档），否则可能触发账户冻结或延迟出金。

---

## 3. 平台对比（Paddle vs Lemon Squeezy：并行申请，按审核与提现结果决策）

> **2026-08-19 复核修正**：不再把 Paddle 定性为「纯退路」——Paddle 官方明确支持**软件 License / 下载 / 一次性数字商品**交付（webhook 交付）。
> 两家都可能审核、补资料或延迟激活，**不预设哪家一定过**。真正的第一决策标准：
> **谁先完成审核 + 绑定提现 + 成功跑通一笔真实测试交易，就先上线谁。**

| 平台 | 费率 | 付款方式 | 原生 license key | 审批 / 出金 |
|---|---|---|---|---|
| **Lemon Squeezy** | 5% + $0.50（另有国际 / PayPal / 订阅附加费；低价买断固定费占比高） | 卡 / PayPal / Apple Pay / Google Pay（据称含 Alipay·WeChat，仅一次性付款） | ✅ 有，但 **key 由 LS 自生成**；能否导入自有码池需注册时实测 | 自助注册为主，但**新店仍可能审核 / 延迟激活**；出金每月 1st & 15th；2024 被 Stripe 收购、路线图不确定 |
| **Paddle** | 5% + $0.50（标准 Checkout；汇兑 / 银行费视出金路径，约 1–2%，非固定） | 卡 / 钱包 / 本地方法 | 需自建（官方支持软件 License 交付） | **有审批流程**（产品 + 网站 + ToS / Privacy / Refund），可能被拒 / 补资料；出金**每月一次、余额 ≥ $100**（wire / Payoneer）；200+ 国税务、最稳 |
| **Dodo Payments** | 4% + $0.40 | 卡 / 钱包（国际） | ✅ 有 | 较新（2024），生态小但专为 indie 做；按证件国判定 |
| **Gumroad** | 10% | 卡 / PayPal | 部分 | 自带流量但费率高 |

**决策流程：**
1. 两平台**并行申请**——共用同一套英文落地页 + ToS / Privacy / Refund 三页（一次做好，两边通用）。
2. 谁先过审核、绑定好提现（NZ 银行 / Payoneer / PayPal）、并**跑通一笔真实测试交易**（付款→拿码→解锁→退款），就先上线谁。
3. 只是想快速验证「有没有人愿意付费」→ 先试 LS；LS 审核 / 提现不顺 → 直接转 Paddle。
4. 长期经营 + 买断价 ≥ $10 → Paddle 与 LS 皆可；追求极致稳定 / 企业级账单 → Paddle。

> 备注：Lemon Squeezy 部分来源称支持 Alipay / WeChat Pay（一次性付款）——若属实，LS 可**一并覆盖国内用户**。注册时实测核实。

---

## 4. 技术对接：首选 LS 原生 License Key（不建自有码池、不进客户邮箱）

> **2026-08-19 定案（用户修正）**：优先采用 **Lemon Squeezy 原生 License Key**，而不是「LS 生成后转自有码 + webhook + 邮件」。
> 理由：① **Worker 边界 = 零 PII**（项目规则：不接收 / 存储用户个人资料、零遥测）——「webhook 带客户邮箱 → Worker → Resend 发邮件」直接踩线；
> ② 免维护海外码池、订单→码映射、邮件基础设施；③ License 的生成 / 实例管理 / 退款作废由 LS 负责。

### 首选方案（LS 原生 License Key）

```
LS 付款
→ LS 自动生成 License Key（放进订单邮件 + My Orders 页面）
→ 用户在 Appause 输入 LS License Key
→ App 调 Worker /api/redeem-ls（key + 设备指纹）
→ Worker 调 LS License API activate 校验 / 激活（服务端验证，不暴露给客户端）
→ 成功 → Worker 用同一把私钥签 Appause 设备绑定 JWT（与爱发电 /api/redeem 同格式）
→ App 验签解锁（ServerKeys 内嵌公钥，验签逻辑零改动）
```

**不需要**：海外码池、Worker 发邮件、Resend、Worker 存客户邮箱、订单→自有码映射。

**要点：**
- LS 实例上限按商品可配（默认 5）→ **设为 3**，与买断「最多 3 台设备」一致；换机 = App 内解绑 → LS `deactivate`。
- 服务端校验 + IP 限流（LS `activate` 是免鉴权 API，防爆破）。
- JWT 仍由 Worker 签发、格式与国内一致 → App 只需在 Pro 页加一个「输入 License Key」入口（按语言 / 区域显示），国内仍走激活码。
- 退款撤销（离线 JWT 的固有边界）：LS 退款 / 作废 key 后，**新实例无法再激活**；已签发的离线 JWT 依然有效。MVP 接受，量起来再考虑联网状态校验。
- 若最终走 Paddle：同样优先评估其原生 license key 能力，「服务端校验 → Worker 签 JWT」结构不变，避免 webhook 传邮箱。

### 备选（自有码池 + webhook）——仅当未来有必须自有码的业务理由
- LS webhook（`order_created` / `license_key_created`）→ 校验 `X-Signature`（HMAC-SHA256）→ **order_id 幂等** → 按需签发。
- ⚠️ 两个坑：① webhook payload 带客户邮箱 → 进 Worker 即违反零 PII 边界；② 需处理重复投递、退款撤销、订单状态校验。
- LS 官方明确：**确认页跳转不能作为唯一交付依据**，正式履约要走 webhook。
- **结论：不推荐**。

> 抽象流水线（不变）：`payment provider → verified purchase → issue license → revoke / refund`
> - verified purchase = LS License API 激活成功
> - issue license = Worker 签 JWT（唯一签发源）
> - revoke / refund = LS 退款作废（新实例不可激活）+ App 内 deactivate 解绑

---

## 5. 产品前置（真正的工作量在这，不在支付）

Appause 现在是**中文 / 小红书·bilibili 向**。做海外 = 面向英语用户，需要：

1. **英文 UI 复核**：默认 `values/` 已是英文，`values-zh/` 提供简体中文；发布前继续检查 Pro、Onboarding、Diagnostics、暂停提示语与商店描述是否完整一致。
2. **海外 app 默认分组**：默认分组从 `小红书 / bilibili` 改为 `Instagram / TikTok / YouTube`（或让用户自选）。
   - ⚠️ AGENTS.md 规定：**不要硬编码具体包名**（如 `com.zhiliaoapp.musically`）——用用户自选 + 预设可读名，包名运行时取。
3. **英文落地页**：GitHub Pages 英文版（同时当 Stripe/LS 前置的 ToS / Privacy 页）。
4. **隐私政策英文版**：MoR 结账需合规页（我们已有 `PRIVACY.md` / `privacy-policy.html`，补英译）。

这是本地化工作，量大但直白（抽字符串 + 翻译 + 默认分组配置）。

---

## 6. 签证定性（唯一非技术不确定项）

MoR 平台是法律卖家，你收的是打款，不是自己开公司。但 NZ 学生签对「经营生意 / 收入性质」有潜在限制。
→ 把 LS/Paddle 的条款 + 你的签证 work 条款发给持牌移民顾问，问一句：
**"Receiving payouts from a Merchant of Record platform (as the supplier, not the legal seller) on a student visa — does this count as self-employment / business income I must declare or avoid?"**
拿到口头/邮件结论即可定路线。

---

## 7. 上线清单（并行申请版）

- [ ] 问持牌移民顾问签证定性（第 6 节）
- [ ] 英文落地页 + ToS / Privacy / Refund 三页上线（两平台共用）
- [ ] 并行注册 **Lemon Squeezy** + **Paddle**（个人主体）
  - **NZ 注册 = 待平台书面确认**：商家所在地 / 实际居住地址 / 银行 / 税务身份须真实一致；「中国护照 + NZ 地址/银行」能否过审，以官方工单 / 邮件答复为准，未确认前不以 NZ 身份收款
- [ ] 各平台绑定提现（NZ 银行 / Payoneer / PayPal）
- [ ] 建「Appause Pro — Lifetime」一次性商品，定价 **$9.99–14.99**；**开启 License Key 激活，实例上限设 3**
- [ ] LS 实测四项：审核通过、NZ 银行提现、一次 **Sandbox 付款**、**License API 激活/校验**
- [ ] Worker：`/api/redeem-ls`（服务端调 LS License API 激活 → 签 JWT）+ IP 限流（第 4 节）
- [ ] App：Pro 页加「输入 License Key」入口（按语言 / 区域显示；国内仍走激活码）
- [ ] 复核默认英文资源 + 海外默认分组（第 5 节）
- [ ] Pro 页加「Get Pro」按钮 → 跳 LS 结账链接
- [ ] **不生成海外码池、不写 webhook**——账户与提现路径确认后，再做最小 License API 适配

---

## 8. 与国内路线的关系

- **并存不冲突**：国内用爱发电（支付宝/微信，无执照），海外用 LS/Dodo（国际卡，个人即可）。
- 码池各自独立：国内（爱发电）与海外（webhook 按需签发）用不同批次，KV 打 notes 标签区分，互不混用。
- 若 LS 实测支持 Alipay/WeChat → 可只保留 LS 一个平台覆盖两边，省一套。
- 优先级建议：国内与海外路线都先完成平台资格、签证和税务确认，再开放正式销售。
