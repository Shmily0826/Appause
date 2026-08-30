# Appause — GitHub 项目指标 (Project Metrics)

> 自动采集：GitHub Actions 每周日 UTC 0 点运行 `scripts/github_metrics.py`，也可本地手动跑。
> 隐私说明：仅统计**公开仓库的聚合指标**（star / fork / 下载 / 流量），不采集任何用户或设备数据，与 Appause 隐私优先的定位一致。

| date | stars | forks | watchers | open_issues | release_downloads | downloads_total | views_14d | clones_14d |
|---|---|---|---|---|---|---|---|---|
| 2026-07-28 | 0 | 0 | 0 | 0 | 0 |  |  |  |
| 2026-08-09 | 1 | 0 | 0 | 0 | 16 |  |  |  |
| 2026-08-16 | 1 | 0 | 0 | 0 | 16 |  |  |  |
| 2026-08-23 | 1 | 0 | 0 | 0 | 16 |  |  |  |
| 2026-08-30 | 1 | 0 | 0 | 0 | 16 |  |  |  |

## 字段说明
**第三方见证（GitHub 记录，可信、用户无法自行修改）：**
- `stars` / `forks` / `watchers` / `open_issues`：仓库公开指标。
- `release_downloads`：GitHub Release 资产（APK）的累计下载次数（GitHub 见证，可信）。
- `views_14d` / `clones_14d`：GitHub Traffic 最近 14 天聚合（需带 `GITHUB_TOKEN` 才能获取，否则留空）。
**自托管、仅供估算（非第三方见证，请勿当作精确头牌数）：**
- `downloads_total`：跨渠道累计安装总数，来自**自建** Worker 聚合计数器（仅一个数字、不含任何用户/设备信息）。因运行在自己账号下，有权限者可在后台改动或用脚本刷量，故只当作**近似下限（≥）**的内部参考，不具独立审计性。需设置 `WORKER_STATS_URL` 才能获取，否则留空。
- 简历 / 作品集建议以**第三方见证数**（GitHub Release 下载 + 酷安 / F-Droid 等平台下载量）相加为准；`downloads_total` 仅作补充与下限参考。
