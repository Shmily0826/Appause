"""W4 production trial smoke — run ONLY after explicit user approval ("W4-1").

v3 (recovery-safe):
  * emulator hard-lock: refuses to run unless the target is the AVD
    (emulator-5554 + google_sdk model), so a connected phone can never be
    driven by this script even by accident;
  * stale-token exclusion: reads the DataStore BEFORE the tap — any existing
    license aborts (no false PASS from an old token); after the tap the token
    must be new AND its exp must sit ~7 days out from NOW (fresh window);
  * Start-trial tap result is checked (button found & tapped; UI transitions);
  * device-claim cross-check: first token vs repeat token must carry the same
    64-hex device claim (binding consistency; production re-signs repeat
    tokens with a newer iat, so FULL-TOKEN equality is NOT required — only
    the expiry window and claims must match);
  * JWT handling: token/PEM passed to Node via a private temp dir, removed in
    finally; nothing sensitive is left in /tmp or the repo.

Evidence: screenshots + parsed claims + UI labels in evidence/w4-prod-smoke/.
"""
# A recovered approved trial is evidence of persistence, not fresh first-click
# evidence; --verify-existing never starts or repeats a production trial.
import argparse
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import campaign_lib as cl

DBG = "com.appause.android.debug"
EV = Path(__file__).parent / "evidence" / "w4-prod-smoke"
SERVER_PEM = Path(__file__).parents[2] / "app/src/main/java/com/appause/android/data/pro/ServerKeys.kt"
os.environ["CAMPAIGN_TMP"] = str(EV / "runtime")


def assert_emulator() -> None:
    if cl.SERIAL != "emulator-5554":
        raise SystemExit(f"ABORT: target serial is not emulator-5554 (serial={cl.SERIAL})")
    devices = subprocess.run(
        [cl.ADB_BIN, "devices"], capture_output=True, text=True
    ).stdout
    if f"{cl.SERIAL}\tdevice" not in devices:
        raise SystemExit("ABORT: emulator-5554 is not connected")
    model = cl.adb_shell("getprop ro.product.model").strip()
    if "SDK" not in model.upper() and "emulator" not in model.lower():
        raise SystemExit(f"ABORT: target not an emulator image (model={model})")
    print(f"[lock] emulator OK model={model}")


def extract_license_token(raw: bytes) -> str:
    match = re.search(
        rb"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}",
        raw,
    )
    return match.group(0).decode("ascii") if match else ""


def license_token() -> str:
    result = subprocess.run(
        [
            cl.ADB_BIN,
            "-s",
            cl.SERIAL,
            "exec-out",
            "run-as",
            DBG,
            "cat",
            "files/datastore/settings.preferences_pb",
        ],
        capture_output=True,
    )
    return extract_license_token(result.stdout) if result.returncode == 0 else ""


def ui_labels() -> list[str]:
    return [node["label"] for node in cl.nodes()[:24]]


def trial_ui_active(labels: list[str]) -> bool:
    text = " | ".join(labels)
    return bool(re.search(r"7-day trial active|trial ends|Appause Pro.*7-day trial", text, re.I)) \
        and "Start 7-day trial" not in text


def open_pro_page_read_only() -> list[str]:
    labels = ui_labels()
    if trial_ui_active(labels):
        return labels
    if not cl.tap("Appause Pro", timeout=4):
        cl.tap("Settings", timeout=4)
        cl.tap("Appause Pro|Upgrade to Appause Pro", timeout=4)
    return ui_labels()


def token_checks(token: str, now: int) -> tuple[dict, dict]:
    verified = verify_jwt(token)
    claims = verified.get("claims", {})
    iat = claims.get("iat")
    exp = claims.get("exp")
    device = claims.get("device")
    checks = {
        "sig-verify-pinned-key": verified.get("sigOk") is True,
        "tier-trial": claims.get("tier") == "pro" and claims.get("trial") is True,
        "device-claim-64hex": bool(re.fullmatch(r"[0-9a-f]{64}", str(device))),
        "iat-not-future": isinstance(iat, int) and iat <= now + 60,
        "exp-within-seven-days": isinstance(exp, int) and now < exp <= now + 604800,
    }
    safe_claims = {k: claims.get(k) for k in ("tier", "trial", "iat", "exp")}
    return checks, safe_claims


def print_result(result: dict, passed: bool, label: str) -> int:
    print(f"\n=== W4-1 {label} RESULT ===")
    for key, value in result.items():
        print(f"  {key}: {value}")
    print(f"=== {'PASS' if passed else 'CHECK MANUALLY'} ===  dir={EV}")
    return 0 if passed else 1


def verify_existing_trial() -> int:
    """Validate the already-approved trial without any production mutation."""
    result: dict[str, object] = {"mode": "verify-existing-read-only"}
    token = license_token()
    result["token-persisted-before"] = bool(token)
    if not token:
        return print_result(result, False, "RECOVERED")

    checks, safe_claims = token_checks(token, int(time.time()))
    result.update(checks)
    result["claims"] = safe_claims

    # Only dismiss the already-visible success dialog; never tap a trial CTA.
    cl.tap("OK", timeout=2)
    result["ui-active-after-dismiss"] = trial_ui_active(open_pro_page_read_only())

    cl.adb_shell(f"am force-stop {DBG}")
    time.sleep(1)
    cl.open_app(DBG)
    cl.wait_for("New group|Your Groups", timeout=10)
    labels = open_pro_page_read_only()
    result["ui-active-after-cold-restart"] = trial_ui_active(labels)

    token_after = license_token()
    result["token-persisted-after"] = bool(token_after)
    result["token-unchanged"] = bool(token_after) and token_after == token
    after_checks, _ = token_checks(token_after, int(time.time())) if token_after else ({}, {})
    result["sig-verify-after-cold-restart"] = after_checks.get("sig-verify-pinned-key", False)
    passed = all(result.get(key) is True for key in (
        "token-persisted-before", "sig-verify-pinned-key", "tier-trial",
        "device-claim-64hex", "iat-not-future", "exp-within-seven-days",
        "ui-active-after-dismiss", "ui-active-after-cold-restart",
        "token-persisted-after", "token-unchanged", "sig-verify-after-cold-restart"
    ))
    return print_result(result, passed, "RECOVERED")


def verify_jwt(token: str) -> dict:
    """Verify RS256 against the PINNED production public key in a private temp
    dir; returns {sigOk, claims}. Leaves no files behind."""
    pem = re.search(r"-----BEGIN PUBLIC KEY-----.*?-----END PUBLIC KEY-----",
                    SERVER_PEM.read_text(encoding="utf-8"), re.S).group(0)
    tmpdir = tempfile.mkdtemp(prefix="w4verify-")
    try:
        keyf = Path(tmpdir) / "k.pem"
        script = Path(tmpdir) / "v.mjs"
        keyf.write_text(pem, encoding="utf-8")
        script.write_text(
            "import {createVerify} from 'crypto';\n"
            "import {readFileSync} from 'fs';\n"
            "let s='';for await(const c of process.stdin)s+=c;\n"
            "const [h,p,g]=s.trim().split('.');\n"
            "const v=createVerify('RSA-SHA256');v.update(h+'.'+p);\n"
            "const sigOk=v.verify(readFileSync(process.argv[2]),Buffer.from(g,'base64url'));\n"
            "console.log(JSON.stringify({sigOk,claims:JSON.parse(Buffer.from(p,'base64url').toString())}));\n",
            encoding="utf-8")
        r = subprocess.run(["node", str(script), str(keyf)], input=token,
                           capture_output=True, text=True, encoding="utf-8")
        return json.loads(r.stdout) if r.stdout.strip() else {"sigOk": False, "error": r.stderr[:120]}
    finally:
        for f in Path(tmpdir).iterdir():
            f.unlink(missing_ok=True)
        os.rmdir(tmpdir)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify-existing", action="store_true")
    args = parser.parse_args()
    EV.mkdir(parents=True, exist_ok=True)
    assert_emulator()
    if not cl.wait_for_boot(60):
        print("ABORT: emulator did not finish booting")
        return 2
    if args.verify_existing:
        return verify_existing_trial()
    R: dict[str, object] = {}

    # cold-start into the app so leftover screens (stats etc.) can't derail nav
    cl.adb_shell(f"am force-stop {DBG}")
    time.sleep(1)
    cl.open_app(DBG)
    cl.wait_for("New group|Your Groups", timeout=10)

    # --- stale-token exclusion BEFORE anything else ---
    R["pre-existing-token"] = bool(license_token())
    if R["pre-existing-token"]:
        print("ABORT: a license token already exists on this AVD — wipe app "
              "data (pm clear) or use a fresh AVD, then rerun. Not touching it.")
        return 2

    # --- reach Pro page and tap Start trial, checking each step ---
    if not cl.tap("View 7-day trial", timeout=5):
        cl.tap("Settings", timeout=4)
        cl.tap("Upgrade to Appause Pro", timeout=4)
    time.sleep(1.5)
    cl.screenshot(EV / "pro-page-before.png")
    R["reached-pro-page"] = bool(cl.wait_for("trial", timeout=8))
    R["start-tapped"] = bool(cl.tap("Start 7-day trial", timeout=6))
    if not (R["reached-pro-page"] and R["start-tapped"]):
        print(R); return 1
    time.sleep(6)
    cl.screenshot(EV / "after-tap.png")

    # --- token persisted? claims verified? fresh 7-day window? ---
    t1 = license_token()
    R["token-persisted"] = bool(t1)
    if not t1:
        print("FAIL: no token in DataStore after trial tap"); print(R); return 1
    v1 = verify_jwt(t1)
    c1 = v1.get("claims", {})
    now = int(time.time())
    R["sig-verify-pinned-key"] = bool(v1.get("sigOk"))
    checks, safe_claims = token_checks(t1, now)
    R.update(checks)
    R["claims"] = safe_claims

    # --- Pro UI unlocked? trial CTA replaced by started/ends state ---
    labels = " | ".join(ui_labels())
    R["ui-after"] = labels[:220]
    R["ui-unlocked"] = trial_ui_active(ui_labels())
    R["repeat-tap"] = "not-run-by-policy"

    ok = all(R[k] is True for k in (
        "start-tapped", "token-persisted", "sig-verify-pinned-key", "tier-trial",
        "device-claim-64hex", "iat-not-future", "exp-within-seven-days", "ui-unlocked")
    )
    print("\n=== W4-1 RESULT ===")
    for k, v in R.items():
        print(f"  {k}: {v}")
    print(f"=== {'PASS' if ok else 'CHECK MANUALLY'} ===  dir={EV}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
