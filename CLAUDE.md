# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Screenshot3 is an Android app (package `com.tools.screenshot3`) that captures screenshots via MediaProjection and organizes them into named collections (folders). It targets Samsung devices primarily but falls back to generic file browsers. Supports English and Korean localization. An optional scroll-capture mode auto-scrolls the foreground app and stitches frames into one long image.

## Build Commands

```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew test                   # Unit tests
./gradlew test --tests "com.tools.screenshot3.ClassName.methodName"  # Single test
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
./gradlew lint                   # Lint checks
```

APK output is renamed to `screenshot_manager-{buildType}-{versionName}_{versionCode}.apk`. Version code is auto-generated from build timestamp (`MMddyyHHmm`).

## Architecture

Single-module app (`app/`) using Jetpack Compose for UI and Kotlin coroutines throughout.

### Core Flow

1. **MainActivity** — Compose UI for managing collections, starting/stopping capture, browsing/zipping screenshots. Handles runtime permission chain: overlay → storage → notification → MediaProjection.

2. **ScreenshotService** — Foreground service (`mediaProjection` type) that owns the capture session lifecycle. Manages the floating overlay button and routes capture results (direct save or preview-then-decide).

3. **ScreenCaptureManager** (singleton `object`) — Central state holder and capture engine. Manages `MediaProjection`, `VirtualDisplay`, `ImageReader`. Handles:
   - Capture → save to MediaStore (`Pictures/Screenshot3/{collection}/`)
   - Preview mode with pending capture accept/reject
   - Folder item counting via MediaStore queries
   - Zip export of collections to `Download/ScreenshotCollections/`
   - Dual code paths: MediaStore (API 29+) and legacy file I/O (pre-29)

4. **FloatingCaptureOverlay** (singleton `object`) — System overlay window (`TYPE_APPLICATION_OVERLAY`) with a draggable capture button. Touch handling distinguishes click vs drag.

5. **CapturePreviewActivity** — Shows captured screenshot for user confirmation before saving. Sends accept/reject decision back to ScreenshotService via intent action.

6. **CollectionRepository** — SharedPreferences-backed storage for user-created custom collections (key-label pairs, `||`-delimited encoding).

7. **LocaleHelper** — Manual locale management (English/Korean) via SharedPreferences + `attachBaseContext` override pattern.

### Scroll Capture (`scroll/` package)

Opt-in (setup-card toggle → `ScreenCaptureManager.scrollCaptureEnabled` pref, default off). Engages only when the toggle is on **and** the accessibility service is enabled; otherwise capture is a normal one-shot snap.

- **ScrollCaptureAccessibilityService** — `AccessibilityService` with `canPerformGestures="true"` (config in `res/xml/accessibility_service_config.xml`). Companion `instance` reference; `scrollDown()` dispatches a swipe gesture via `dispatchGesture()` wrapped in `suspendCancellableCoroutine`. `isEnabled(context)` checks `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
- **ImageStitcher** — stateless. `stitch(top, bottom, statusBarHeight)` finds vertical overlap by sampled-row SAD (search window brackets the gesture's ~60% overlap), crops the status bar from the appended frame, composites. Guards: 20,000px max height.
- **ScrollCaptureSession** — holds the growing stitched bitmap; `scrollAndCapture()` runs scroll → settle delay → `captureToBitmap()` → stitch → recycle. Enforces `MAX_SCROLLS`. `saveResult()` persists via `ScreenCaptureManager.saveStitchedBitmap()`.

The scroll session is orchestrated by **ScreenshotService** (`enterScrollCaptureMode`/`handleScrollMore`/`handleScrollDone`) and surfaced through a **bottom toolbar** shown by `FloatingCaptureOverlay.showScrollToolbar()` — a separate `WindowManager` overlay (distinct from the floating button) with scroll-more/done buttons, a category label, and a full-screen transparent backdrop where tapping outside = done. On done it morphs into a centered "Saved in {category}" message before dismissing.

### Key Patterns

- State exposed via `StateFlow` from `ScreenCaptureManager`; Compose UI collects via `collectAsState()`.
- Screenshots saved to MediaStore under `Pictures/Screenshot3/{subdirectory}/`. Subdirectory names are sanitized to lowercase alphanumeric with underscores/hyphens.
- The app has 19 built-in collection categories (movies, food, shopping, etc.) plus user-defined custom collections.
- Two capture modes controlled by `requiresConfirmation` preference: instant save, or preview-then-confirm.
- User preferences live in `ScreenCaptureManager` as `StateFlow` + SharedPreferences pairs (`requiresConfirmation`, `scrollCaptureEnabled`), each with an `update…(context, value)` setter. Mirror this pattern when adding settings.
- Per-capture success feedback is a transient chip via `FloatingCaptureOverlay.showStatus()`, not a Toast.

## Tech Stack

- Kotlin 2.0.21, AGP 8.13.0, Gradle 8.13
- Min SDK 24, Target SDK 36, Compile SDK 36
- Jetpack Compose (BOM 2024.09.00) with Material3
- Version catalog at `gradle/libs.versions.toml`
