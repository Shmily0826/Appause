# Release Acceptance Matrix — APPAUSE_ANDROID_RELEASE_READINESS_V1 (2026-09-21)

RC candidate: `output/Appause-v0.5.44.apk` — `com.appause.android`, versionCode **96**, versionName **0.5.44**
SHA-256 `e6524be5024337bcd9496d26e6d81a8e0ce217ba5287fe66206fcc7a4f455097`
Signer cert SHA-1 `99F2DADB186EFD5AE07A039CDEB4373708A40816` (= same key that signed every published release through v0.5.43; on-device 95 accepted `install -r`).
Source: main @ d2d2aea + version-metadata-only bump (product code IDENTICAL to the 95 build that passed the 8/8 HyperOS release smoke).

## 1. Covered by existing evidence (do NOT re-run)

| Item | Evidence |
|---|---|
| F-27 fixed in signed release build, adb-HOME escape, re-arm, Recents no-residue, zero residual windows | release_smoke 8/8 PASS (build 95 from same code; evidence/goalB + TEST_REPORT §35; GOAL_B_REPORT D7 P3/P4 on fixed debug) |
| Manual finger BACK escape | Goal B D3 4/4 PASS (debug, same escape paths) + user A-confirmation post-fix |
| Escape battery P1–P8, journeys J1/J2/J3/J6, Phase C C1/C2/C3 | Goal B (GOAL_B_REPORT §2–4) |
| Upgrade install keeps appops trio + a11y binding (MIUI reinstall ≠ uninstall) | this session, device dumpsys |
| Signing continuity + `install -r` upgrade 95→on-device | Success + versionCode check |
| Unit gates | 235 tests 0 fail; lintVitalRelease PASS |
| Debug-only isolation (structural) | app/src/release stubs inert (Diagnostics destination = Unit; DebugActivationStore always None; ProDebugTools renders nothing); DiagnosticsScreen exists only under app/src/debug |
| Secrets leak scan of RC APK | zip listing clean (no .dev.vars/.jks/keystore/properties beyond app-metadata) |
| F-26 mitigation works (debug) | adj 200 → user set 无限制+自启动 → 0.4 s intercepts (F-26 record) |

## 2. Emulator must-add (release RC build, isolated from debug app) — DONE 2026-09-21, all PASS except noted PARTIAL/KI-1 (see RELEASE_READINESS_REPORT.md §1.2)

## 3. Physical device must-add (RC 96, batched) — auto part DONE (install/latency/adj/home-dismiss PASS); user part (idle probe + real-finger gestures) PENDING

## 4. User-operated only — PENDING (GOAL_R_MANUAL.md ready)

| Item | Why |
|---|---|
| Fresh install → first launch → FULL onboarding (incl. battery step entry points) | never done on a RELEASE package |
| Permission-denied states + recovery cards; enabled → home | release path |
| Create group → intercept → Cancel / Continue (release, emulator) | release path |
| 95 → 96 overwrite upgrade: Room rows + DataStore settings survive | §11.4 |
| Reboot persistence on emulator | basic |

## 3. Physical device must-add (RC 96, batched)

| Item | Why |
|---|---|
| Install RC 96 file over user's 95 (upgrade path on real hardware, data survives — user's own groups) | §11.2/11.4; checklist §6 "install the final artifact" |
| REAL finger Home gesture dismiss ×2 + subjective latency feel | adb keyevent ≠ finger; §5 demands the distinction on release |
| F-26 RELEASE evidence: current adj/intercept latency of release build under the user's actual battery settings; whether the release app's own checklist shows green truthfully | debug evidence cannot substitute (§6) |

## 4. User-operated only (one short batch, prepared before calling user)

Real finger gestures (above), subjective jank/latency rating, confirming MIUI 无限制+自启动 state for the RELEASE package (or letting us observe the checklist status), restoring their own navigation preference if we changed it. Everything else ADB-automatable.

## 5. Non-blocking for first Beta → Known Issues candidates

- ③ F-26 default-policy minute-level latency measurement (optional; only if user reverts to optimized)
- J4 full lock/wake journey (D5/device #34 covers temp-pass-through-lockscreen on the same code path; PARTIAL risk accepted)
- J5 cross-reboot temp-pass (D4 emulator covers logic; device reset risk documented)
- `isMinifyEnabled=false` (no obfuscation — decision deferred, not a safety issue)
- C5 subjective latency quantification (folded into batch as a quick rating)
- output/ old-APK accumulation (cleanup is destructive; needs approval)

## 6. Boundary decisions checked

- No GitHub Release/tag/push/website in this Goal (§10) — commits need approval.
- No production Worker/activation touched; Pro files untouched this session.
- User release data never cleared; no uninstall of the phone's release app.
- No new keep-alive architecture: reuse existing onboarding battery step + setup checklist + warning card (`isIgnoringBatteryOptimizations`, real API, MIUI 无限制 maps to it).
