# ZEN MODE --- Complete End-to-End Android Project Specification

## 0. Project Identity

**Project name:** Zen Mode\
**Platform:** Android only\
**Primary goal:** Help users intentionally disconnect from distracting
phone usage for a chosen period while keeping essential calling
available.

### Core principle

> The user chooses a period of intentional phone freedom. During that
> period, distracting apps are blocked and the device presents an
> extremely minimal Zen screen.

This is a native Android project. Do **not** use Expo, React Native,
Flutter, or a web wrapper.

------------------------------------------------------------------------

# 1. Technology Stack

## 1.1 Core

-   Kotlin
-   Android Studio
-   Gradle Kotlin DSL
-   Android SDK
-   Jetpack Compose
-   Material 3

## 1.2 Architecture

Use:

-   Clean Architecture
-   MVVM
-   Repository pattern
-   Unidirectional data flow where practical

Recommended package structure:

``` text
com.zenmode.app
├── core
│   ├── common
│   ├── designsystem
│   ├── permissions
│   ├── time
│   └── utils
├── data
│   ├── local
│   │   ├── dao
│   │   ├── database
│   │   ├── entity
│   │   └── datastore
│   └── repository
├── domain
│   ├── model
│   ├── repository
│   └── usecase
├── service
│   ├── ZenAccessibilityService
│   ├── ZenForegroundService
│   └── BootReceiver
├── feature
│   ├── home
│   ├── timer
│   ├── zen
│   ├── statistics
│   ├── history
│   └── settings
└── MainActivity.kt
```

## 1.3 Android libraries

Use current stable compatible versions.

-   Jetpack Compose
-   Material 3
-   Navigation Compose
-   ViewModel
-   Kotlin Coroutines
-   Room
-   DataStore Preferences
-   Hilt
-   WorkManager where background scheduling is actually appropriate
-   AndroidX Core
-   Lifecycle components

Avoid unnecessary third-party dependencies.

------------------------------------------------------------------------

# 2. Android Requirements

## Minimum target

Target a modern Android version while maintaining reasonable
compatibility.

Recommended:

``` text
minSdk = 29
targetSdk = latest stable available during implementation
compileSdk = latest stable available during implementation
```

The implementation must respect modern Android background execution and
foreground-service restrictions.

------------------------------------------------------------------------

# 3. Product Requirements

## 3.1 User can start Zen Mode

User selects:

-   15 minutes
-   25 minutes
-   45 minutes
-   60 minutes
-   90 minutes
-   2 hours
-   Custom duration

Before starting:

1.  Show selected duration.
2.  Show what will happen.
3.  Ask for confirmation.
4.  Verify required permissions.
5.  Start Zen Mode.

------------------------------------------------------------------------

# 4. Zen Mode Behavior

Once Zen Mode starts:

## Block

The app must attempt to prevent access to distracting applications
selected by the user.

Examples:

-   Instagram
-   YouTube
-   WhatsApp
-   Telegram
-   Snapchat
-   Chrome
-   Games
-   Reddit
-   X
-   Facebook
-   Any user-selected application

Do not hardcode these applications.

The user must be able to create their own blocked-app list.

## Allow

Essential functions should remain available where technically and
legally possible:

-   Phone calls
-   Emergency functionality
-   Zen Mode itself
-   System-critical functionality

Do not attempt to interfere with emergency calls.

------------------------------------------------------------------------

# 5. Important Technical Reality

Android does not provide an ordinary third-party application with
unrestricted system-level control over the entire device.

Therefore:

-   Do not claim that the app can guarantee absolute device lockdown.
-   Use AccessibilityService only for the app-blocking mechanism where
    appropriate.
-   Respect Android platform restrictions.
-   Never disable security mechanisms.
-   Never attempt to bypass Android permission/security boundaries.
-   Emergency calls must remain possible.
-   System settings and protected system UI may not always be blockable.
-   The application must clearly disclose AccessibilityService usage to
    the user.

The goal is a strong distraction blocker, not a malicious device-locking
mechanism.

------------------------------------------------------------------------

# 6. AccessibilityService

Create:

``` text
ZenAccessibilityService : AccessibilityService
```

Responsibilities:

1.  Detect foreground application changes.
2.  Determine whether Zen Mode is active.
3.  Determine whether the foreground package is blocked.
4.  If blocked, redirect the user to the Zen screen.
5.  Avoid infinite redirect loops.
6.  Never interfere with emergency/system-critical flows.

Pseudo-flow:

``` text
Accessibility event
        ↓
Get foreground package
        ↓
Is Zen Mode active?
   ├── NO → ignore
   └── YES
         ↓
Is package allowed?
   ├── YES → ignore
   └── NO
         ↓
Show Zen Activity / blocking UI
```

Do not continuously launch activities in a loop.

Use debounce/cooldown logic.

------------------------------------------------------------------------

# 7. Zen Screen

The Zen screen is the core experience.

## Visual design

Pure minimal black interface.

Requirements:

-   Black background
-   White/light text
-   No unnecessary graphics
-   No feeds
-   No advertisements
-   No gamification during an active session
-   Large remaining timer
-   Current date
-   Current time
-   Simple call action
-   Minimal exit/stop affordance

Example:

``` text
┌─────────────────────────────┐
│                             │
│          17:42              │
│       AUG 16, 2026          │
│                             │
│                             │
│                             │
│         01:24:37            │
│                             │
│       Z E N   M O D E       │
│                             │
│                             │
│        📞  CALL              │
│                             │
└─────────────────────────────┘
```

------------------------------------------------------------------------

# 8. Timer

The timer must be reliable.

Store:

``` text
startedAt
endsAt
durationSeconds
remainingSeconds
status
```

Do NOT rely only on an in-memory countdown variable.

Use timestamps:

``` text
remaining = endsAt - currentTime
```

This allows the app to recover after:

-   Activity recreation
-   Screen rotation
-   Process recreation
-   Temporary service restart

When the timer reaches zero:

1.  End Zen Mode.
2.  Stop blocking.
3.  Save completed session.
4.  Update streak.
5.  Update total focus time.
6.  Show completion screen/notification.

------------------------------------------------------------------------

# 9. Zen Session States

Use explicit states:

``` text
IDLE
STARTING
ACTIVE
PAUSED
COMPLETING
COMPLETED
CANCELLED
```

For MVP, pausing should preferably be disabled.

A Zen session is intended to be continuous.

------------------------------------------------------------------------

# 10. Ending a Session

Normal completion:

``` text
Timer reaches zero
        ↓
Zen Mode OFF
        ↓
Save session as COMPLETED
        ↓
Update statistics
        ↓
Update streak
        ↓
Show completion screen
```

If user manually stops:

``` text
User chooses Stop
        ↓
Confirmation dialog
        ↓
Stop Zen Mode
        ↓
Save session as CANCELLED
```

Cancelled sessions should not count as completed focus sessions.

------------------------------------------------------------------------

# 11. Anti-Escape UX

The app should discourage accidental exits but must not trap the user.

For example:

``` text
Stop Zen Mode?

You still have 42:17 remaining.

[Continue Zen] [Stop]
```

Do not use deceptive UI.

Do not make it impossible for the user to regain control of their
device.

Emergency and essential phone functionality must remain accessible.

------------------------------------------------------------------------

# 12. Call Function

The Zen screen should provide a clear call action.

When pressed:

``` text
Zen Screen
    ↓
Call action
    ↓
Android dialer / call flow
```

Do not silently place calls.

Use the normal Android call/dialer mechanisms.

If direct calling permission is not available, open the dialer instead.

Never block emergency calling.

------------------------------------------------------------------------

# 13. Home Screen

Home screen should show:

``` text
ZEN MODE

Ready to focus?

[ 25 MIN ]
[ 45 MIN ]
[ 60 MIN ]
[ CUSTOM ]

Current streak
12 days

Total phone-free time
38h 42m

Sessions
47

[ START ZEN ]
```

Home screen should feel calm.

------------------------------------------------------------------------

# 14. Timer Selection Screen

Allow:

``` text
15 min
25 min
45 min
60 min
90 min
120 min
Custom
```

Custom picker:

``` text
Hours
Minutes
```

Validate:

-   Minimum duration
-   Maximum reasonable duration
-   No negative values
-   No zero duration

------------------------------------------------------------------------

# 15. Blocked Apps Screen

Show installed applications that can reasonably be selected.

Each row:

``` text
[App Icon] Instagram
           com.instagram.android

                         [ ON/OFF ]
```

Features:

-   Search
-   Select
-   Deselect
-   Select all eligible apps
-   Clear selection
-   Persist selection

Do not include critical/system applications by default.

------------------------------------------------------------------------

# 16. Settings

Settings should include:

## Focus

-   Default session duration
-   Blocked apps
-   Start confirmation
-   Completion notification

## Appearance

-   Black Zen screen
-   Clock visibility
-   Date visibility
-   24-hour clock

## Behavior

-   Call button
-   Exit confirmation
-   Accessibility status

## Statistics

-   Clear history
-   Reset statistics

Dangerous actions must require confirmation.

------------------------------------------------------------------------

# 17. Statistics

Show:

``` text
FOCUS STATS

🔥 Current streak
12 days

🏆 Best streak
21 days

⏱ Total focus
38h 42m

🎯 Sessions completed
47

📅 Average session
49 min
```

Additional useful metrics:

-   Today
-   This week
-   This month
-   All time

Keep charts minimal.

------------------------------------------------------------------------

# 18. Streak System

A streak is based on completed Zen sessions.

Suggested rule:

A user maintains a streak when they complete at least one Zen session on
consecutive calendar days.

Example:

``` text
Aug 14 → completed
Aug 15 → completed
Aug 16 → completed

Current streak = 3
```

If no completed session occurs on the next calendar day:

``` text
streak resets
```

Do not use device time manipulation as a trusted source for security.
The streak system is motivational, not security-critical.

Store:

``` text
currentStreak
bestStreak
lastCompletedDate
```

------------------------------------------------------------------------

# 19. Session History

Each session should store:

``` text
id
startedAt
endedAt
plannedDuration
actualDuration
status
blockedAppCount
```

History UI:

``` text
TODAY

60 min
Completed
17:00 → 18:00

YESTERDAY

45 min
Completed
19:10 → 19:55

AUG 13

25 min
Cancelled
14:20 → 14:31
```

Allow filtering:

-   All
-   Completed
-   Cancelled

------------------------------------------------------------------------

# 20. Database

Use Room.

## SessionEntity

``` kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val startedAt: Long,

    val endedAt: Long?,

    val plannedDurationSeconds: Long,

    val actualDurationSeconds: Long,

    val status: SessionStatus,

    val blockedAppCount: Int
)
```

If Room enum conversion is needed, use a TypeConverter.

------------------------------------------------------------------------

# 21. BlockedAppEntity

``` kotlin
@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val packageName: String,

    val appName: String,

    val enabled: Boolean
)
```

------------------------------------------------------------------------

# 22. App Settings

Use DataStore for lightweight settings.

Example:

``` text
defaultDuration
showClock
showDate
use24HourClock
completionNotification
confirmExit
```

Do not use SharedPreferences for new application settings unless there
is a strong reason.

------------------------------------------------------------------------

# 23. Repository Layer

Create interfaces in domain:

``` kotlin
interface SessionRepository
interface BlockedAppRepository
interface SettingsRepository
interface ZenModeRepository
```

Data layer implements them.

------------------------------------------------------------------------

# 24. Use Cases

Create focused use cases.

Examples:

``` text
StartZenSessionUseCase
StopZenSessionUseCase
GetActiveSessionUseCase
CompleteZenSessionUseCase
GetSessionHistoryUseCase
GetStatisticsUseCase
GetStreakUseCase
UpdateBlockedAppsUseCase
GetBlockedAppsUseCase
CheckAccessibilityPermissionUseCase
```

Avoid giant ViewModels containing all business logic.

------------------------------------------------------------------------

# 25. Zen Mode Engine

Create a central component:

``` text
ZenModeManager
```

Responsibilities:

-   Start session
-   Stop session
-   Determine active state
-   Persist current session
-   Expose active session state
-   Coordinate foreground service
-   Coordinate accessibility blocker

The manager should not contain UI code.

------------------------------------------------------------------------

# 26. Foreground Service

Use a foreground service for active Zen Mode if required by the chosen
Android implementation and current Android restrictions.

Example:

``` text
ZenForegroundService
```

Responsibilities:

-   Keep active session state available
-   Maintain an ongoing notification where required
-   Recover session state
-   Stop when session ends

Do not abuse foreground services.

Follow the exact foreground-service type and permission requirements for
the target Android version.

------------------------------------------------------------------------

# 27. Boot / Restart Recovery

Handle device restart carefully.

If a Zen session was active before a restart:

1.  Read persisted session.
2.  Calculate whether the timer has expired.
3.  If expired:
    -   complete session
4.  If still active:
    -   restore active state if Android permits
    -   restore service state where allowed

Do not assume background execution is guaranteed.

------------------------------------------------------------------------

# 28. Permission Onboarding

First launch should explain:

``` text
Zen Mode needs special Android access
to detect when distracting apps are opened
and return you to your focus screen.
```

Then:

``` text
[ Enable Accessibility ]
```

The app should detect whether the permission is enabled.

If not:

``` text
Accessibility permission required

Zen Mode cannot block selected apps yet.

[ Open Android Settings ]
```

Never trick the user into enabling AccessibilityService.

------------------------------------------------------------------------

# 29. Accessibility Disclosure

The app must clearly explain:

-   Why AccessibilityService is needed
-   What data is observed
-   How foreground-app detection works
-   What the app does with the information
-   That the user can disable the service from Android settings

Do not collect unnecessary personal data.

------------------------------------------------------------------------

# 30. Privacy

The application should be privacy-first.

Do not send app-usage data to a server.

Default architecture:

``` text
Phone
 └── Zen Mode
      ├── Local database
      ├── Local settings
      └── Local statistics
```

No account required for MVP.

No analytics required for MVP.

No cloud backend required for MVP.

------------------------------------------------------------------------

# 31. Navigation

Recommended routes:

``` text
/home
/timer
/zen
/apps
/statistics
/history
/settings
/settings/blocked-apps
/settings/permissions
```

Zen mode should be treated specially so users cannot casually navigate
back into normal app screens while an active session is running.

------------------------------------------------------------------------

# 32. UI Design System

## Colors

Primary:

``` text
Background: #000000
Primary text: #FFFFFF
Secondary text: #8A8A8A
Divider: #222222
```

Keep the UI monochrome.

## Typography

Use large typography for:

-   Remaining timer
-   Current time

Use medium/small typography for:

-   Date
-   Labels
-   Statistics

## Shapes

Prefer:

-   Rounded cards
-   Minimal borders
-   Large touch targets
-   No excessive shadows

------------------------------------------------------------------------

# 33. Accessibility

The app itself must support:

-   TalkBack
-   Large font scaling
-   Sufficient contrast
-   Semantic labels
-   Minimum touch target sizes
-   Content descriptions for icons

The Zen screen must remain readable at large font scales.

------------------------------------------------------------------------

# 34. Error Handling

Never crash because:

-   Accessibility permission is disabled
-   Usage access is unavailable
-   App is uninstalled
-   Database migration fails
-   Service stops
-   Activity is recreated
-   Device restarts

Provide meaningful error states.

Example:

``` text
Zen Mode needs Accessibility access.

[Fix Permission]
```

------------------------------------------------------------------------

# 35. App Uninstallation / Blocked App Changes

If a blocked app is uninstalled:

-   Remove it from active blocked-app selection where appropriate.

If a new app is installed:

-   Do not automatically block it unless the user enabled a clear policy
    for doing so.

------------------------------------------------------------------------

# 36. Security / Safety Rules

Never:

-   Disable Android security
-   Disable system settings through exploits
-   Hide the app from the user
-   Prevent emergency calls
-   Intercept private communications
-   Read message contents
-   Record screens
-   Capture passwords
-   Bypass Android permission systems
-   Use AccessibilityService for unrelated surveillance

The app exists only to help the user reduce distraction.

------------------------------------------------------------------------

# 37. Testing Strategy

## Unit tests

Test:

-   Timer calculations
-   Session state transitions
-   Streak calculation
-   Statistics
-   Repository logic
-   Settings

Examples:

``` text
Start session → ACTIVE
Expired timer → COMPLETED
Manual stop → CANCELLED
Two consecutive completed days → streak 2
Missing day → streak reset
```

## Android instrumentation tests

Test:

-   Navigation
-   Timer screen
-   Settings
-   Blocked-app selection
-   Database
-   Permission status UI

## Manual device tests

Test:

-   Screen off
-   Screen on
-   App switching
-   Home button
-   Recent apps
-   Device rotation
-   Device restart
-   Accessibility enabled/disabled
-   Calls
-   Emergency call behavior
-   Battery optimization behavior
-   Android versions

------------------------------------------------------------------------

# 38. MVP Definition

The MVP is complete when all of these work:

-   [ ] User can choose a duration
-   [ ] User can start Zen Mode
-   [ ] Active timer persists correctly
-   [ ] Black Zen screen appears
-   [ ] Current time appears
-   [ ] Current date appears
-   [ ] User-selected apps are blocked using the implemented Android
    mechanism
-   [ ] User can make calls through the supported Android call flow
-   [ ] User can stop a session with confirmation
-   [ ] Completed sessions are stored
-   [ ] Cancelled sessions are stored
-   [ ] Statistics are calculated
-   [ ] Streaks work
-   [ ] Session history works
-   [ ] Blocked apps can be configured
-   [ ] Accessibility permission onboarding works
-   [ ] App handles service/activity restart reasonably
-   [ ] No cloud backend is required

------------------------------------------------------------------------

# 39. Phase 2 Features

Only implement after MVP is stable.

Potential additions:

-   Scheduled Zen Mode
-   Quick Settings tile
-   Notification action
-   Focus presets
-   Daily focus goal
-   Weekly report
-   More detailed statistics
-   Widget
-   App-category blocking
-   Trusted contacts
-   Optional sound/vibration
-   Focus completion animation
-   Digital wellbeing comparisons

Do not let Phase 2 features delay MVP.

------------------------------------------------------------------------

# 40. Project Development Order

Claude must implement in this order:

## Phase 1 --- Project foundation

-   Create Android project
-   Configure Kotlin
-   Configure Compose
-   Configure Gradle
-   Configure package structure
-   Add dependencies
-   Create theme
-   Create navigation

## Phase 2 --- Data layer

-   Room database
-   DataStore
-   Entities
-   DAOs
-   Repositories
-   Migrations
-   Unit tests

## Phase 3 --- Core domain

-   Session model
-   Zen mode state machine
-   Timer logic
-   Streak logic
-   Statistics logic
-   Use cases
-   Unit tests

## Phase 4 --- UI

-   Home
-   Timer selection
-   Zen screen
-   Statistics
-   History
-   Settings
-   Blocked apps

## Phase 5 --- Android integration

-   AccessibilityService
-   Foreground service if required
-   Permission onboarding
-   App detection
-   App blocking
-   Call flow
-   Lifecycle/recovery

## Phase 6 --- Integration

Connect:

``` text
UI
 ↓
ViewModel
 ↓
Use Cases
 ↓
Repositories
 ↓
Room/DataStore
```

And:

``` text
Zen Mode Manager
 ↓
Foreground Service
 ↓
Accessibility Service
```

## Phase 7 --- Testing

Run all unit/instrumentation tests.

Then manually test on a physical Android device.

## Phase 8 --- Polish

-   Fix edge cases
-   Improve animations only where useful
-   Improve accessibility
-   Improve battery behavior
-   Improve error handling
-   Remove dead code
-   Remove unnecessary dependencies

## Phase 9 --- Release build

Generate:

``` text
debug APK
release APK
AAB
```

Do not publish anything automatically.

------------------------------------------------------------------------

# 41. Claude Code Rules

Claude must follow these rules throughout implementation:

1.  Do not replace Kotlin with another language.
2.  Do not introduce Expo.
3.  Do not introduce React Native.
4.  Do not introduce Flutter.
5.  Do not create a backend unless explicitly requested.
6.  Prefer Android/Jetpack libraries.
7.  Keep business logic outside composables.
8.  Keep ViewModels focused.
9.  Use dependency injection consistently.
10. Write tests for important business logic.
11. Never fake Android capabilities.
12. If an Android restriction prevents a requested feature, explain the
    restriction and implement the closest legitimate alternative.
13. Do not use exploits or security bypasses.
14. Do not collect unnecessary user data.
15. Do not hardcode package names when user configuration is expected.
16. Keep the app usable if special permissions are unavailable.
17. Do not silently change requirements.
18. Before adding a dependency, check whether AndroidX/Kotlin already
    provides the required functionality.
19. Keep APIs compatible with the selected minSdk and targetSdk.
20. Keep the project buildable after every major implementation stage.

------------------------------------------------------------------------

# 42. Definition of Done

The project is considered finished only when:

``` text
Android Studio opens project
        ↓
Gradle sync succeeds
        ↓
Build succeeds
        ↓
App installs on physical Android device
        ↓
User enables required permission
        ↓
User selects blocked apps
        ↓
User selects timer
        ↓
Zen Mode starts
        ↓
Zen screen appears
        ↓
Blocked app launch is intercepted
        ↓
User is returned to Zen experience
        ↓
Call flow remains available
        ↓
Timer reaches zero
        ↓
Zen Mode ends
        ↓
Session is saved
        ↓
Statistics update
        ↓
Streak updates
        ↓
History displays session
```

------------------------------------------------------------------------

# 43. README Requirements

Create a complete README containing:

-   Project overview
-   Screenshots section
-   Features
-   Architecture
-   Tech stack
-   Setup instructions
-   Android Studio requirements
-   Required permissions
-   AccessibilityService explanation
-   How app blocking works
-   Database structure
-   Testing
-   Build instructions
-   Known Android limitations
-   Privacy
-   License

------------------------------------------------------------------------

# 44. Final Expected User Experience

The user opens Zen Mode.

They see:

``` text
ZEN MODE

Ready?

25 MIN
45 MIN
60 MIN
CUSTOM
```

They choose:

``` text
60 MIN
```

They press:

``` text
START ZEN
```

The app confirms:

``` text
For the next 60 minutes:

Selected distracting apps will be blocked.

Calls remain available.

Ready?

[ START ]
```

Zen Mode starts.

The screen becomes:

``` text
17:42
AUG 16, 2026


00:59:58


ZEN MODE


📞 CALL
```

The user tries to open Instagram.

Zen Mode detects it and returns them to the Zen experience.

The user continues working.

After 60 minutes:

``` text
ZEN COMPLETE

You stayed away for

60 minutes

🔥 Streak: 13 days

[ DONE ]
```

The session is saved locally.

Statistics update automatically.

------------------------------------------------------------------------

# 45. First Implementation Task

Before writing large amounts of code:

1.  Inspect the current repository.
2.  Determine whether an Android project already exists.
3.  If it does not exist, create the native Kotlin Android project.
4.  Configure the package structure.
5.  Configure Compose.
6.  Configure Room/DataStore/Hilt only as needed.
7.  Create the initial navigation shell.
8.  Make sure the project builds successfully.
9.  Then implement the data/domain layers.
10. Then implement the UI.
11. Then implement Android system integration.
12. Run tests after each major stage.

Never generate the entire application blindly in one step.

Build incrementally and keep the repository buildable.

------------------------------------------------------------------------

# 46. Final Command for Claude

You are the primary implementation agent for this project.

Read this entire specification before changing the repository.

Your job is to implement the application **end-to-end**, not merely
create mock screens.

Prioritize:

``` text
Correct Android behavior
→ Reliable timer
→ Reliable app blocking
→ Data persistence
→ Clean architecture
→ Testing
→ UI polish
```

When a requested feature is impossible under Android's public APIs, do
not fake it. Explain the limitation in the implementation notes and
build the strongest legitimate alternative.

At every stage:

``` text
Inspect
→ Plan
→ Implement
→ Build
→ Test
→ Fix
→ Continue
```

Do not stop after creating the UI.

The final result must be a working native Android Zen Mode application.
