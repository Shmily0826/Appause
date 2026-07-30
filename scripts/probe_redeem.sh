#!/usr/bin/env bash
# probe_redeem.sh — 验证 Appause Pro 激活链是否打通。
#
# 用途：确认「app 内嵌的生产公钥 ↔ Cloudflare APPAUSE_PRIVATE_KEY 私钥 ↔ Worker 部署」
#       是否一一对应。能从 PC 跑，但需要两个只有设备/app 才有的输入：
#         CODE   —— 一个真实（未用/有效）的 Pro 兑换码
#         DEVICE —— 设备指纹 = SHA-256(本机 Keystore 公钥 DER)，由 app 在运行时计算
#                   见 app/.../data/pro/DeviceKeyStore.kt#getDeviceFingerprint
#
# 最省事的做法其实是：直接在手机 app 里用真实兑换码激活一次。
# 若激活成功 → 整条链已通，关卡0 彻底过。
# 本脚本用于「不装 app、只想从服务端确认」的场景（需先拿到 DEVICE）。
#
# 用法：
#   CODE=XXXX-XXXX DEVICE=<64位hex指纹> bash scripts/probe_redeem.sh
#
# 结果判读：
#   {"token":"..."}              → 成功！私钥已设且与 app 公钥匹配，链打通。
#   {"error":"invalid_code"}     → 兑换码无效/已用（链本身可能没问题，换有效码再试）。
#   {"error":"device_limit..."}  → 码有效但绑定设备数已满（链没问题）。
#   {"error":"APPAUSE_PRIVATE_KEY secret is not set"}
#                                → Worker 没设私钥！关卡0 未完成，去 Cloudflare 设 secret。
#   连接失败/超时                 → Worker 没部署，或网络屏蔽 *.workers.dev。

set -euo pipefail

WORKER="${WORKER_BASE_URL:-https://appause-pro-worker.rng2018520.workers.dev}"
CODE="${CODE:-}"
DEVICE="${DEVICE:-}"

if [[ -z "$CODE" || -z "$DEVICE" ]]; then
  echo "用法: CODE=<兑换码> DEVICE=<设备指纹> bash $0" >&2
  echo "(DEVICE 是 app 内 SHA-256(Keystore 公钥 DER) 的 64 位 hex，见 DeviceKeyStore.kt)" >&2
  exit 2
fi

echo "POST $WORKER/api/redeem"
curl -sS -m 20 -X POST -H "Content-Type: application/json" \
  -d "{\"code\":\"$CODE\",\"device\":\"$DEVICE\"}" \
  "$WORKER/api/redeem"
echo
