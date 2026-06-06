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


<claude-mem-context>
# Memory Context

# [ScreenshotX] recent context, 2026-06-06 12:15am EDT

Legend: 🎯session 🔴bugfix 🟣feature 🔄refactor ✅change 🔵discovery ⚖️decision 🚨security_alert 🔐security_note
Format: ID TIME TYPE TITLE
Fetch details: get_observations([IDs]) | Search: mem-search skill

Stats: 50 obs (15,555t read) | 372,802t work | 96% savings

### Jun 5, 2026
1688 5:05p ✅ Localization Strings Added for Scroll Capture Feature
1689 " ✅ Korean Localization Added for Scroll Capture Feature
1690 5:06p ✅ Scroll Capture Feature Successfully Compiled
1691 5:09p 🔴 ScrollCaptureAccessibilityService Fixed to Use Fixed Scroll Distance
1692 5:11p 🔴 ImageStitcher Overlap Search Window Tuned for Fixed Scroll Distance
1693 " 🔴 ImageStitcher Fallback Overlap Updated to Match Search Window
1694 " 🔴 ImageStitcher Fixed to Crop Status Bar from Bottom Frame
1695 " 🔴 ScrollCaptureSession Enforces MAX_SCROLLS Limit
1696 5:12p ✅ Scroll Capture Implementation Builds Successfully with All Fixes
1697 " 🔵 Accessibility Service Permission Guidance Not Yet Implemented in MainActivity
1698 5:13p ✅ MainActivity MainScreen Composable Extended for Accessibility Service Support
1699 " ✅ MainActivity MainScreen UI Implements Accessibility Service Help Banner
1700 5:14p ✅ MainActivity Added hasAccessibilityService State Field
1701 " ✅ MainActivity Wires Accessibility Service State and Settings Callback to MainScreen
1704 9:11p 🔵 Infinite Loop Bug in Scroll Capture Mode Exit
1705 9:12p ✅ Plan Updated with Done Button Infinite Loop Root Cause Analysis
1706 " ⚖️ Fix Strategy for Scroll Mode Done Button Infinite Loop
1707 9:25p 🔵 Overlay capture button layout structure examined
1708 " 🔵 Overlay button visual styling uses gradient with shine effect
1709 9:28p ⚖️ Scroll capture UX redesigned to use separate bottom toolbar instead of mode-swapped floating button
1710 9:30p ✅ Bottom toolbar UX refined with category label, tap-outside dismiss, and integrated status
1711 " ✅ Toolbar implementation details specified with touch-interceptor and dynamic count updates
1712 9:31p 🔵 Examined existing overlay status chip background styling before creating scroll toolbar drawable
1713 " 🟣 Created scroll_toolbar_background drawable for bottom toolbar styling
1714 " 🟣 Created overlay_scroll_toolbar layout with scroll-more, category label, and done buttons
1715 " 🔵 Examined FloatingCaptureOverlay structure to identify refactoring scope
1716 9:32p 🔄 FloatingCaptureOverlay refactored to use separate scroll toolbar overlay instead of mode-switching
1717 " ✅ Removed overlayScrollMoreButton from floating capture overlay layout
1718 " 🔵 Examined ScreenshotService scroll flow to plan API migration
1719 " 🔄 ScreenshotService refactored to use showScrollToolbar/hideScrollToolbar API
1720 9:33p 🔵 Build failed: missing selectableItemBackgroundBorderless attribute in toolbar layout
1721 " 🔴 Fixed missing selectableItemBackgroundBorderless attribute by using framework version
1723 9:43p ✅ Created circular button drawable for improved toolbar button legibility
1724 " ✅ Created accent variant circular button drawable for primary toolbar actions
1725 9:44p ✅ Updated scroll toolbar layout with larger buttons and improved legibility
1726 9:47p 🟣 Scroll capture functionality implementation
1727 9:48p ✅ Major version bump to 2.0.0 for ScreenshotX
1728 " 🔵 Scroll capture module architecture revealed
1729 " 🔄 Scroll capture completion simplified to avoid duplicate feedback
S583 Implement scroll capture feature and optimize UX feedback flow (Jun 5 at 9:49 PM)
1730 9:49p ✅ Removed redundant save chip feedback from scroll capture completion
S584 Improve scroll capture completion UX by displaying a polished "Saved in {category}" confirmation message in the toolbar before dismissing (Jun 5 at 9:49 PM)
1731 9:53p 🟣 Added scroll toolbar confirmation message state
1732 9:54p 🟣 Integrated scroll toolbar confirmation message into save flow
1733 " ✅ Added SAVED_MESSAGE_DURATION_MS timing constant
S585 Fixed confirmation message sizing to match button option dimensions in scroll toolbar (Jun 5 at 9:54 PM)
1734 10:02p ✅ Added LinearLayout import to FloatingCaptureOverlay
1735 " 🔴 Fixed confirmation message size in scroll toolbar
S586 Fixed tiny confirmation message size by preserving toolbar dimensions and centering content (Jun 5 at 10:03 PM)
1736 10:06p ✅ Enhanced scroll toolbar layout dimensions for confirmation message
1737 " 🔄 Simplified toolbar width preservation by relying on XML minWidth
S587 Fixed tiny confirmation message in scroll toolbar to match button option dimensions (Jun 5 at 10:06 PM)
S588 Fixed scroll toolbar confirmation message sizing to match button option dimensions (Jun 5 at 10:07 PM)
S589 Fixed tiny confirmation message sizing + configured CAVEMAN FULL MODE session hook (Jun 5 at 10:07 PM)
1738 10:09p ✅ Configured CAVEMAN FULL MODE via SessionStart hook
S590 Verified SessionStart hook system; confirmed caveman FULL mode + claude-mem plugin coexist without conflict (Jun 5 at 10:09 PM)
S591 Refined confirmation message sizing approach: removed forced dimensions, increased text prominence for natural layout (Jun 5 at 10:09 PM)
1739 10:10p ✅ Adjusted layout strategy: removed minWidth, increased maxWidth constraint
1740 10:11p ✅ Increased confirmation message text size to 20sp for better prominence
S592 Evaluated memory system options: claude-mem plugin vs native auto-memory; assessed tradeoff between features and overhead (Jun 5 at 10:11 PM)
**Investigated**: Claude Code native memory capabilities (CLAUDE.md manual editing, auto-memory per-project storage, # shortcut, /memory command); claude-mem plugin features (structured observations, searchable corpus, semantic search, background worker, context injection)

**Learned**: Native auto-memory suitable for session-to-session fact retention with zero extra overhead; claude-mem provides semantic search and timeline reports but incurs background worker startup cost and context-injection token cost each SessionStart; both systems independent and non-conflicting; native memory sufficient for live iteration + frequent commits workflow

**Completed**: Analysis of memory system architecture and tradeoffs; determination that native auto-memory likely sufficient for project's workflow pattern

**Next Steps**: Await user decision: disable claude-mem plugin and enable native auto-memory for reduced overhead, or maintain both systems for semantic search and timeline capabilities


Access 373k tokens of past work via get_observations([IDs]) or mem-search skill.
</claude-mem-context>