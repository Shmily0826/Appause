#!/usr/bin/env python3
"""Fetch Appause GitHub metrics and append a daily snapshot.

Free, dependency-free (Python standard library only). Designed to run locally
or inside GitHub Actions.

What it collects (all public / aggregate, no user or device data):
  - stars, forks, watchers, open issues  (repo endpoint)
  - cumulative Release asset downloads    (releases endpoint)
  - 14-day views & clones                 (traffic endpoints, needs a token
                                           with push access to the repo)

Traffic (views/clones) is only available with a token that has repo/push
scope. Without one we fall back to the public stats above.

Outputs:
  - METRICS.md  : a human-readable markdown table (great for the README /
                  portfolio / interview).
  - METRICS.csv : the same data, machine-readable for charts.

Env vars:
  METRICS_REPO   repo slug, default "Shmily0826/Appause"
  GITHUB_TOKEN   optional PAT / workflow token for traffic data
"""
import csv
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

REPO = os.environ.get("METRICS_REPO", "Shmily0826/Appause")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
API = "https://api.github.com"
OUT_MD = "METRICS.md"
OUT_CSV = "METRICS.csv"
TODAY = datetime.now(timezone.utc).strftime("%Y-%m-%d")

HEADERS = [
    "date",
    "stars",
    "forks",
    "watchers",
    "open_issues",
    "release_downloads",
    "views_14d",
    "clones_14d",
]


def get(path):
    req = urllib.request.Request(
        API + path,
        headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "appause-metrics",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    if TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def safe_get(path):
    try:
        return get(path)
    except Exception as e:  # noqa: BLE001 - we degrade gracefully
        print("WARN: %s -> %s" % (path, e), file=sys.stderr)
        return None


def main():
    repo = safe_get("/repos/" + REPO)
    stars = (repo or {}).get("stargazers_count")
    forks = (repo or {}).get("forks_count")
    watchers = (repo or {}).get("subscribers_count")
    issues = (repo or {}).get("open_issues_count")

    dl = 0
    rels = safe_get("/repos/%s/releases?per_page=100" % REPO)
    if rels:
        for r in rels:
            for a in r.get("assets", []):
                dl += a.get("download_count", 0)

    views = clones = None
    if TOKEN:
        v = safe_get("/repos/%s/traffic/views" % REPO)
        if v:
            views = sum(x.get("count", 0) for x in v.get("views", []))
        c = safe_get("/repos/%s/traffic/clones" % REPO)
        if c:
            clones = sum(x.get("count", 0) for x in c.get("clones", []))
    else:
        print(
            "INFO: GITHUB_TOKEN not set; skipping traffic (views/clones). "
            "Set it to enable 14-day views/clones.",
            file=sys.stderr,
        )

    row = {
        h: v
        for h, v in zip(
            HEADERS, [TODAY, stars, forks, watchers, issues, dl, views, clones]
        )
    }

    # Load existing snapshots (dedupe by date, keep sorted).
    rows = []
    if os.path.exists(OUT_CSV):
        with open(OUT_CSV, newline="", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                rows.append(r)
    rows = [r for r in rows if r["date"] != TODAY]
    rows.append(row)
    rows.sort(key=lambda r: r["date"])

    with open(OUT_CSV, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=HEADERS)
        w.writeheader()
        for r in rows:
            w.writerow(r)

    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.write("# Appause — GitHub 项目指标 (Project Metrics)\n\n")
        f.write(
            "> 自动采集：GitHub Actions 每周日 UTC 0 点运行 "
            "`scripts/github_metrics.py`，也可本地手动跑。\n"
        )
        f.write(
            "> 隐私说明：仅统计**公开仓库的聚合指标**（star / fork / 下载 / "
            "流量），不采集任何用户或设备数据，与 Appause 隐私优先的定位一致。\n\n"
        )
        f.write("| " + " | ".join(HEADERS) + " |\n")
        f.write("|" + "|".join(["---"] * len(HEADERS)) + "|\n")
        for r in rows:
            f.write(
                "| "
                + " | ".join("" if r[h] is None else str(r[h]) for h in HEADERS)
                + " |\n"
            )
        f.write("\n## 字段说明\n")
        f.write(
            "- `views_14d` / `clones_14d`：GitHub Traffic 最近 14 天聚合"
            "（需带 `GITHUB_TOKEN` 才能获取，否则留空）。\n"
        )
        f.write(
            "- `release_downloads`：所有 Release 资产（APK）的累计下载次数。\n"
        )
        f.write(
            "- 数据可用于了解项目热度，也是作品集 / 面试的量化素材。\n"
        )

    print("OK: wrote %s (%d snapshots)" % (OUT_MD, len(rows)))


if __name__ == "__main__":
    main()
