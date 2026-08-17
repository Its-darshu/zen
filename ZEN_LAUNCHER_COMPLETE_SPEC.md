# ZEN LAUNCHER — Android System Launcher Specification

## Goal
Extend the existing Zen Mode Android app with an optional native Android HOME launcher. Do not rewrite or break the existing Zen Mode system.

**Stack:** Kotlin, Jetpack Compose, existing MVVM/Clean Architecture, Room, DataStore, Hilt.

## 1. Existing System
Before changing anything:
1. Inspect the repository.
2. Read `ZEN_MODE_COMPLETE_PROJECT_SPEC.md`.
3. Inspect Phase 5/6 system integration.
4. Build the current project.
5. Preserve the existing Zen timer, blocking, statistics, streaks, history, permissions, services and recovery.

## 2. Launcher
Register the app as an Android HOME-capable app using the official HOME/DEFAULT intent categories.

The user must explicitly choose Zen Launcher as the default launcher. Never silently replace the default launcher.

Add a launcher settings section showing the real default-launcher state.

## 3. Home Screen
Create a minimal, fast launcher home:
- User wallpaper or pure black background
- Clock
- Date
- Favorites
- App drawer entry
- Search
- Gesture-first navigation

Keep the existing monochrome Zen visual language. Avoid clutter, feeds, ads and unnecessary effects.

## 4. Wallpaper
Support independently:
- Home wallpaper: selected image / OFF
- Lock-screen wallpaper: selected image / OFF

Use Android Photo Picker or the appropriate document picker. Do not request broad storage permissions.

Persist only URI references and settings. Never upload images.

Handle deleted/revoked/unavailable wallpaper URIs gracefully.

### Lock-screen limitation
A normal launcher does not control Android's secure lock screen. Investigate official Android wallpaper APIs for lock-screen wallpaper. If separate lock-screen wallpaper is unsupported on a target/device, document the limitation and implement the strongest legitimate alternative. Never fake or replace the secure lock screen.

## 5. App Drawer
Implement:
- Installed apps
- App icons
- App names
- Search
- Alphabetical sorting
- Favorites
- Safe filtering of critical/system apps

Use Android package visibility correctly. Do not add `QUERY_ALL_PACKAGES` unless a future requirement genuinely requires it.

Apps launch using normal Android launch intents.

## 6. Favorites
Allow pinning apps to Home. Persist locally using DataStore or Room as appropriate.

## 7. Search
Local application search only. No web search and no networking.

## 8. Recents
Create an **iOS-inspired visual style** for the launcher-owned recent-app experience:
- stacked/overlapping cards
- app icon/name
- tap to return
- swipe navigation
- dismiss where Android legitimately permits it

### Critical limitation
A normal third-party launcher does not own Android's system Recents UI.

Investigate only public Android APIs available to the target SDK. Do not:
- use hidden APIs
- use reflection to bypass restrictions
- capture other apps' screens
- record screens
- use AccessibilityService as spyware
- request window-content access just to fake previews

If live task previews are unavailable, use a safe fallback such as app icon/name or a neutral representation. Document the limitation.

## 9. Gestures
Design the launcher for Android Gesture Navigation:
- Swipe up → app drawer
- Swipe down → appropriate launcher action where possible
- Horizontal swipe → launcher pages/recent cards where appropriate
- Long press → launcher customization/settings

Do not claim to change Android's system navigation mode. The launcher must work with Android gesture navigation and remain functional with three-button navigation.

Avoid conflicts with system gestures.

## 10. Zen Integration
When Zen Mode is inactive:
- normal launcher
- wallpaper
- favorites
- app drawer
- search
- recents
- gestures

When Zen Mode is active:
- reuse the existing Zen session/timer
- show minimal Zen presentation
- timer
- clock/date
- CALL
- existing blocking engine

Do not create a second timer.

Do not duplicate Zen session state.

## 11. Lockdown
Do not claim the launcher itself provides complete device lockdown.

For normal devices, use the existing legitimate AccessibilityService/blocking mechanism.

For dedicated/device-owner environments, use existing Lock Task/device-owner capabilities where appropriate.

Never bypass Android security.

## 12. Architecture
Recommended:

```text
feature/launcher/
  home/
  appdrawer/
  recents/
  wallpaper/
  settings/

system/launcher/
  DefaultLauncherChecker
  LauncherTaskProvider
  WallpaperController
  AppLauncher
```

Android APIs belong in Android-specific adapters.

Keep Android APIs out of:
- domain/model
- domain/logic
- domain/usecase
- domain/repository

## 13. Launcher State
Create launcher-specific state containing concepts such as:
- isDefaultLauncher
- wallpaper state
- favorites
- installed apps
- recent tasks
- zenModeActive
- gesture state

Keep platform-specific Android models out of domain where practical.

## 14. Settings
Add:

```text
ZEN LAUNCHER
  Set as default launcher

WALLPAPER
  Home wallpaper
  Lock-screen wallpaper

HOME
  Favorites
  App drawer
  Search

GESTURES
  Swipe up
  Swipe down
  Horizontal gestures

RECENTS
  Recent-app style
  Card layout
```

Every setting must have real behavior. No fake toggles.

## 15. Privacy
The launcher remains local.

Do not:
- upload wallpapers
- collect app usage remotely
- collect recent-app history remotely
- capture screens
- read typed text
- read notifications
- add analytics
- add ads
- add accounts
- add cloud sync

Do not add unnecessary permissions such as INTERNET, QUERY_ALL_PACKAGES, CALL_PHONE, READ_PHONE_STATE, contacts or location.

## 16. Performance
Launcher startup must be fast.
- Lazy-load app lists
- Cache app icons
- Avoid repeated PackageManager scans
- Avoid polling
- Avoid unnecessary services
- Avoid blocking the main thread
- Avoid per-second database work

## 17. Accessibility
Support:
- TalkBack
- large font scaling
- semantic labels
- minimum touch targets
- sufficient contrast

## 18. Testing
Unit test:
- wallpaper preference logic
- favorites
- app filtering
- search
- launcher state
- Zen integration
- gesture mapping
- default-launcher state

Use Robolectric/instrumentation where practical.

Physical-device validation is required for:
- default launcher behavior
- Home/system gestures
- lock-screen behavior
- wallpaper behavior
- recent-task behavior
- gesture navigation
- OEM-specific behavior

Never claim a physical test passed unless it actually executed.

## 19. Manual Test Checklist
Create `docs/LAUNCHER_MANUAL_TEST_CHECKLIST.md` covering:
1. Install
2. Set as default launcher
3. Home button/system gesture
4. Restart device
5. Home wallpaper ON/OFF
6. Lock wallpaper where supported
7. App drawer
8. Search
9. Launch app
10. Favorites
11. Recents
12. Switch recent apps
13. Dismiss recent card where supported
14. Launcher gestures
15. Android gesture navigation
16. Start Zen Mode
17. Zen presentation
18. Blocking
19. End Zen
20. Lock/unlock
21. Fingerprint behavior documented as Android-controlled
22. TalkBack
23. Large font
24. Deleted wallpaper
25. Revoked wallpaper URI
26. App uninstall
27. Device restart

## 20. Implementation Order

### L1 — Launcher foundation
- HOME activity
- default-launcher detection
- basic home
- navigation shell

### L2 — Apps
- installed app provider
- drawer
- search
- favorites
- launching

### L3 — Wallpaper
- home wallpaper
- picker
- URI persistence
- OFF state
- lock wallpaper investigation
- error handling

### L4 — Gestures
- launcher gestures
- gesture-navigation compatibility
- long press

### L5 — Recents
- investigate public task APIs
- recent task provider
- stacked card UI
- tap-to-return
- dismiss where supported
- safe fallback

### L6 — Zen integration
- active-session detection
- Zen presentation
- reuse timer
- reuse call flow
- reuse blocking

### L7 — Testing
- unit
- Robolectric
- instrumentation
- physical device

### L8 — Polish
- performance
- accessibility
- error states
- documentation

## 21. Definition of Done
- [ ] Selectable as default Android launcher
- [ ] Home works
- [ ] Home wallpaper works
- [ ] Home wallpaper OFF works
- [ ] Lock wallpaper works where officially supported
- [ ] Unsupported lock-wallpaper behavior documented
- [ ] App drawer works
- [ ] Search works
- [ ] Favorites work
- [ ] Apps launch
- [ ] Gestures work
- [ ] Android gesture navigation works
- [ ] Recents uses only legitimate public APIs
- [ ] iOS-inspired stacked visual style exists
- [ ] No screen capture for Recents
- [ ] Zen Mode integration works
- [ ] Existing blocking remains functional
- [ ] Existing timer remains the only timer
- [ ] No privacy regression
- [ ] No unnecessary permissions
- [ ] Physical-device behavior tested
- [ ] README updated

## 22. Non-goals
Do not implement:
- fake iOS OS
- fake secure lock screen
- fingerprint interception
- biometric manipulation
- hidden APIs
- screen recording
- spyware
- notification scraping
- cloud sync
- accounts
- ads
- analytics
- AI
- social features

## 23. Claude Execution Rules
You are extending an already-working Android application.

Do not rewrite the project.

At each launcher phase:
1. Inspect.
2. State the plan.
3. Implement only that phase.
4. Build.
5. Test.
6. Fix failures.
7. Report results.
8. Stop.

Never jump ahead.

When Android imposes a limitation:
- do not fake it
- do not bypass it
- do not hide it
- document it
- implement the strongest legitimate alternative

The final product should feel like:

> A minimal Android launcher designed around intentional phone use, with Zen Mode as its distraction-free focus environment.
