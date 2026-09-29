# 落地页目录

正式页面仍在仓库根目录：英文 [`index.html`](../../index.html)，中文 [`zh.html`](../../zh.html)。GitHub Pages 从 `main` 根目录发布；这里所有 HTML 都是归档草稿，不是线上页面。

> 收敛版（第二批）在 [`../landing-v2/`](../landing-v2/)。本目录保留草稿原貌，不再改动；有采用意向的方向会在 `landing-v2/` 里出一份定向优化的版本。

## 草稿页

| 文件 | 方向 | 状态 |
| --- | --- | --- |
| [`index.html`](index.html) | 早期 A/B 方案总览 | 仅作 gallery 索引 |
| [`zh-a.html`](zh-a.html) | 中文浅色极简 | 未采用 |
| [`zh-b.html`](zh-b.html) | 中文深色营销风 | 未采用 |
| [`en-a.html`](en-a.html) | 英文浅色极简 | 未采用 |
| [`en-b.html`](en-b.html) | 英文深色粗体 | 未采用 |
| [`zh-refined.html`](zh-refined.html) | 综合候选：流程清楚、真实界面、渐进建立信任 | 新候选，待评审 |
| [`zh-c.html`](zh-c.html) | 中文「打开之前，先停十秒」 | 未采用的后续候选 |
| [`almanac.html`](almanac.html) | 长篇使用手册 / Almanac | 概念稿 |
| [`contrast.html`](contrast.html) | 对照同一晚上的两种使用经过 | 概念稿 |
| [`timeline.html`](timeline.html) | 以五秒停顿和拦截流程为主线 | 概念稿 |
| [`android-flow.html`](android-flow.html) | G-android：Android 产品流程清晰度 | 视觉评审第 3 名；最能讲清产品流程 |
| [`companion.html`](companion.html) | F-companion：温和的陪伴语气 | 视觉评审第 4 名；保留温和的视觉与产品语气 |
| [`editorial.html`](editorial.html) | D-editorial：编辑式排版 | 视觉评审第 5 名；参考字体与留白 |
| [`receipt.html`](receipt.html) | 收银小票（Pricing Ledger / 热敏小票 / 档案紫） | 新候选；2026-09-28 实测评审第 1 名候选 timeline 之后的新方向；四档视口 0 溢出 |
| [`catalog.html`](catalog.html) | 卡纸目录（Doc Portal 左栏常驻目录 / 米纸 + 苔绿） | 收敛版：按「浅纸底 + 低饱和强调色」偏好整合，九章一次讲全；四档视口 0 溢出 |

## 视觉评审记录

最近一次桌面与手机视觉比较的排序为：当前根目录 `zh.html` 第一、`zh-a.html` 第二、G-android 第三、F-companion 第四、D-editorial 第五。G-android 对 Android 产品流程表达最清楚；F-companion 保留温和的视觉与产品语气；D-editorial 可参考字体与留白。比较结果用于方向参考，不代表草稿获准上线。

2026-09-28 对全部 12 个落地页草稿做了带截图的实测评审（双视口渲染 + 版本号与代码核对），排序：timeline 第一、contrast 第二、almanac 第三（桌面端）、zh-c 第四、G-android 第五。评审证据在 `output/draft-review/` 与 `output/draft-shots/`。`almanac.html` 的 390px 视口横向溢出（文档宽 747px）实测**仍未修复**，根因是窄屏下 `table{min-width:38rem}` 把 `.chapter__body` 这个 grid 项撑开，`.tablewrap{overflow-x:auto}` 失效。另发现 `zh-a.html` / `zh-b.html` 内嵌的国内网盘直链带明文 DOWNLOAD_TOKEN，且已进入 git 暂存区，提交前需处理。

同日按评审者口味（只看风格与布局）复排：`timeline` > `almanac` = `zh-refined` > `companion`；`contrast` 的深底 + 信号橙被判为压迫感过强。规律是**浅纸底 + 低饱和强调色**被接受，深底 + 高饱和红橙被否。据此新出 `catalog.html`（Doc Portal / 卡纸目录）作为收敛方向，并把 `timeline.html` 的「先等五秒」改为「先等十秒」（对齐 `GroupEditorViewModel.kt` 默认值 10 秒），同时把真机数据的版本归属（v0.5.44 记录 / v0.5.45 发布）写清。

## 审计记录

- [中文落地页优化建议](../landing-reviews/zh-landing-optimization.md)
- [2026-09-25 中文页审计](../landing-reviews/zh.html-audit-2026-09-25.md)

## 归档说明

- 早期 `landing/` 页面迁入本目录时，指向根目录图片和隐私页的相对路径同步增加了一层 `../`。
- `almanac.html`、`contrast.html`、`timeline.html` 从仓库根目录的概念目录合并到这里；其图片与隐私链接已同步调整。草稿内容和正式根页面均未因此改版。
- D/F/G 原型从临时评审目录复制到此处；图片直接复用仓库 `images/` 中与临时素材哈希一致的图标和截图，未复制重复资产。临时原件保留。
- 各草稿文案可能含旧版本号、旧产品描述或未采用的视觉建议；修改前请以当前 README、产品实现和根目录正式页面核对。

