# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

People who want to reduce unconscious openings of a small number of distracting entertainment apps for their own use.

## Product Purpose

Appause is an Android focus and self-control app. It adds a short pause before user-selected apps open so the user has a moment to decide whether to continue or return to the home screen.

## Positioning

Appause supports a user's own choices. It is not parental control, enterprise management, a security product, or a tool for monitoring other people.

## Operating Context

The user chooses target apps, places them in groups, and sets a wait time. When a selected app comes to the foreground, Appause shows a pause screen with a countdown and the options to continue or cancel and return home.

## Capabilities and Constraints

- The core app is free. The public beta has no subscription, and no paid version is planned.
- Pro is a set of experimental features. A user may opt in to one seven-day Pro trial per device.
- After the trial ends, the user may request a lifetime activation code through feedback. The developer issues these codes manually.
- Current Pro experiments include unlimited groups, re-remind, a custom pause prompt, and custom open reasons.
- The AccessibilityService detects the foreground package name and is configured with `canRetrieveWindowContent=false`. Describe this capability accurately as an accessibility feature.
- Minimum supported Android version is Android 8.0 (API 26).

## Evidence on Hand

- Product description and current feature notes: `README.md`.
- Current Chinese landing-page copy: `zh.html`.
- Current Pro screen and entitlement behavior: `app/src/main/java/com/appause/android/ui/pro/ProScreen.kt` and `app/src/main/java/com/appause/android/ui/pro/ProViewModel.kt`.
- Real Chinese app captures: `images/screenshots/zh/home.png` and `images/screenshots/zh/pause.png`.
- No verified product-effect metrics or user testimonials were present in the sources reviewed; do not invent them.

## Product Principles

- The user chooses which apps to manage and how long to pause.
- Appause makes an automatic opening visible; the user decides whether to continue.
- Keep the core experience free and describe experimental Pro access and its manual lifetime-code path plainly.
