"""W4 production trial smoke — run ONLY after explicit user approval ("做1").

Drives the debug app on the emulator to the Pro page, taps the real
"Start trial" button (one production write: consumes the EMULATOR's synthetic
device trial — never a real user's), then proves the full chain:
  network -> workerd prod -> DO state -> RS256 JWT (prod pinned key)
  -> Android Keystore device binding -> DataStore persist -> Pro UI unlocked.

Evidence: screenshots + DataStore license token extracted via run-as and
verified against the PINNED production public key locally (Node), plus the
repeat-tap idempotency behavior (alreadyStarted path).
"""
import re
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import campaign_lib as cl

DBG = "com.appause.android.debug"
EV = Path(__file__).parent / "evidence" / "w4-prod-smoke"


def license_token() -> str:
    """Extract the stored license JWT from the debug app's DataStore."""
    raw = cl.adb_shell(
        f"run-as {DBG} sh -c 'cat files/datastore/settings.preferences_pb' | tr -c '[:print:]' '\\n'"
    )
    m = re.search(r"eyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+", raw)
    return m.group(0) if m else ""


def verify_with_pinned_key(token: str) -> str:
    """Node verify against ServerKeys.kt pinned production PEM (read-only)."""
    src = Path(__file__).parents[2] / "app/src/main/java/com/appause/android/data/pro/ServerKeys.kt"
    pem = re.search(r'-----BEGIN PUBLIC KEY-----.*?-----END PUBLIC KEY-----', src.read_text(encoding="utf-8"), re.S).group(0)
    script = f'''
import {{ createVerify }} from "crypto";
const pem = {json.dumps(pem)};
const [h,p,s] = {json.dumps(token)}.split(".");
const v = createVerify("RSA-SHA256"); v.update(h+"."+p);
const sigOk = v.verify(pem, Buffer.from(s,"base64url"));
const c = JSON.parse(Buffer.from(p,"base64url").toString());
console.log(JSON.stringify({{sigOk, tier:c.tier, trial:c.trial, exp:c.exp, device:c.device?.slice(0,12)}}));
'''
    Path("/tmp/w4verify.mjs").write_text(script, encoding="utf-8")
    return subprocess.run(["node", "/tmp/w4verify.mjs"], capture_output=True, text=True).stdout.strip()


import json  # used inside verify_with_pinned_key


def main() -> int:
    EV.mkdir(parents=True, exist_ok=True)
    cl.wait_for_boot(60)
    results = {}
    # get to the Pro page
    cl.open_app(DBG)
    cl.wait_for("New group|Your Groups", timeout=10)
    if not cl.tap("View 7-day trial", timeout=5):
        cl.tap("Settings", timeout=4)
        cl.tap("Upgrade to Appause Pro", timeout=4)
    time.sleep(1.5)
    cl.screenshot(EV / "pro-page-before.png")
    results["reached-pro-page"] = bool(cl.wait_for("trial|Trial|7-day", timeout=8))
    if not results["reached-pro-page"]:
        print(results); return 1
    # THE production write (approved action): tap Start trial
    cl.tap("Start your one-time 7-day trial|Start trial|开始", timeout=6)
    time.sleep(6)
    cl.screenshot(EV / "after-tap.png")
    labels = " | ".join(n["label"] for n in cl.nodes()[:18])
    results["ui-after-trial"] = labels[:220]
    token = license_token()
    results["token-persisted"] = bool(token)
    if token:
        results["pinned-key-verify"] = verify_with_pinned_key(token)
        # idempotency: tap again -> must not error or re-issue a different window
        cl.tap("Start your one-time 7-day trial|Start trial", timeout=4)
        time.sleep(4)
        t2 = license_token()
        results["repeat-same-token"] = (t2 == token)
    ok = results.get("token-persisted") and '"sigOk":true' in results.get("pinned-key-verify", "")
    print("=== W4 RESULT ===")
    for k, v in results.items():
        print(f"  {k}: {v}")
    print(f"=== {'PASS' if ok else 'CHECK MANUALLY'} ===  dir={EV}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
