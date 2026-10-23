# scripts/

Developer tooling. Nothing here ships in the APK, and CI does not run any of it.

| Path | What it is |
|---|---|
| `release/` | Tools used to build a release and its assets |
| `stress/` | Device/emulator test drivers for interception behaviour |
| `worker/` | Acceptance notes for the Pro activation Worker |
| `launch-emulator.bat` | Starts the local AVD (paths are machine-specific) |

## release/

- `make_webp.py` — generates the `-360` / `-720` WebP variants the landing pages serve. Run this after replacing a screenshot.
- `make_og_card.py` — generates `images/og-card*.png` and the favicon set.
- `probe_redeem.sh` — checks that the Pro activation chain still works end to end.
- `adb_install_and_screenshot.sh` — installs a build on the attached device and captures screenshots.

## stress/

All drivers assume `adb` on `PATH`, a booted API 26+ device or emulator, and that
they are run **from this directory** — they import the shared helpers by bare
module name, so `python p1_journey_matrix.py` works while
`python scripts/stress/p1_journey_matrix.py` does not.

| Prefix | Meaning |
|---|---|
| `p1`–`p11` | Scenario suites. Repeatable: journey matrix, lifecycle chaos, pass expiry, re-remind timing, persistence, group mutation, escape stress, randomized walk, long cycle, mixed journeys, real process death. |
| `d7`, `d8` | Batches run on a physical phone (escape safety, real-app journeys). |
| `release_smoke`, `w4_prod_trial_smoke` | Release gates: run before publishing a build. |
| `r2_button_scan` | Scans where the pause overlay's buttons actually sit. |
| `*_lib.py`, `setup_device.py`, `uinav.py`, `ui_stress.py`, `analyze.py` | Shared helpers and the long-standing UI stress driver. |
| `campaign.xml` | Device matrix used by the batch runners. |

`setup_device.py` prepares a fresh device (grants, groups, seed data);
`goalb_seed.py` seeds groups and temporary passes into the debug build.

### One trap worth knowing

Do not verify accessibility state with `uiautomator dump` (or Appium, or any tool
that connects UiAutomation). Connecting UiAutomation destroys every third-party
`AccessibilityService` on the device for about two seconds, so the app correctly
reports "enabled but not connected" and you will chase a bug that does not exist.
Use `adb exec-out screencap -p > shot.png` and read the image instead.

## Removed drivers

One-off repro and batch scripts written for a specific release or bug (F-03,
F-05, REC-BURST, Goal-B, RC 0.5.44) were removed in October 2026 to keep this
directory readable. They are still reachable:

```bash
git log --diff-filter=D --name-only -- scripts/stress   # find the commit
git show <commit>^:scripts/stress/<name>.py
```
