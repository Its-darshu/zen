# Zen Mode

A native Android focus app. You choose a length of time; for that time, the apps
you picked are kept out of reach and the phone shows a black screen with the
time, the date and how long is left.

Everything stays on the device. There is no account, no server, and the app has
no internet permission at all.

---

## Screenshots

> To be added after physical-device validation. The screens are: onboarding,
> home, timer selection, the Zen screen, blocked apps, statistics, history,
> settings and permissions.

---

## Features

- Preset sessions of 15, 25, 45, 60, 90 and 120 minutes, or a custom length up
  to 12 hours
- A deliberately empty Zen screen: clock, date, remaining time, and a call button
- App blocking driven by your own list — nothing is blocked by default and no
  app is ever added on the app's initiative
- Sessions survive backgrounding, screen-off, process death and reboot
- Local history, statistics (today / week / month / all time) and day streaks
- A quiet ongoing notification while a session runs, and one when it finishes
- Works without any special permission — you simply get fewer features, and the
  app says exactly which

## Architecture

```
Compose UI  →  ViewModel  →  Use case  →  Repository  →  Room / DataStore
                                ↑
                        ZenModeManager  →  foreground service
                                        →  exact alarm
                                        →  accessibility blocker
```

Clean architecture with a strict rule: **the domain layer has no Android
imports**. Models, the session state machine, the timer, streaks and statistics
are plain Kotlin, which is why most of the test suite runs on the JVM in
seconds. Android reaches the domain only through interfaces
(`AccessibilityPermissionMonitor`, `SessionAlarmScheduler`, `ZenServiceController`,
`ZenNotifier`).

```
com.zenmode.app
├── core/           designsystem, time (clock abstraction, formatting)
├── data/           Room, DataStore, package manager, permission monitor
├── domain/         model, logic, repository interfaces, use cases  (no Android)
├── feature/        one package per screen: UI state, ViewModel, screen
├── navigation/     routes and the single NavHost
├── service/        accessibility service, foreground service, receivers
└── system/         ZenModeManager and the Android ports it coordinates
```

The **database is the single source of truth** for a running session. There is
no second timer anywhere: remaining time is always derived as
`scheduledEndAt - now`, so it cannot drift.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore ·
Navigation Compose · Coroutines/Flow · JUnit4 · Robolectric

`minSdk 29` · `targetSdk 36` · `compileSdk 36` · AGP 8.13.2 · Gradle 8.13 ·
Kotlin 2.2.21

No image loader, no mocking library, no analytics, no networking library.

## Setup

Requires **Android Studio Ladybug or newer** and **JDK 17+** (the bundled JBR is
fine — JDK 21 is what this project is built against).

```bash
git clone https://github.com/Its-darshu/zen.git
cd zen
```

Create `local.properties` with your SDK path if Android Studio has not:

```properties
sdk.dir=/path/to/Android/Sdk
```

Then open the folder in Android Studio and let Gradle sync, or build from the
command line.

## Build

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK (R8, ~2.9 MB)
./gradlew :app:bundleRelease          # AAB
```

The release build is currently signed with the debug key so it can be installed
and tested locally. **Replace `signingConfig` in `app/build.gradle.kts` with a
real signing config before distributing.** No keystore is committed to this
repository, and none should be.

## Testing

```bash
./gradlew :app:testDebugUnitTest --rerun-tasks   # 330 tests, JVM only
./gradlew :app:connectedDebugAndroidTest         # needs a device or emulator
```

Unit tests cover the timer arithmetic, the session state machine, streaks,
statistics, the repositories against a real in-memory Room database, the
`ZenModeManager` coordination rules (including stale-alarm protection), the
alarm scheduler against a shadow `AlarmManager`, and every screen — Compose
tests run under Robolectric, so the UI suite needs no device.

Instrumentation tests in `ZenSystemIntegrationTest` cover what only a real
runtime can answer. Behaviour that needs a human — granting accessibility
access, opening a blocked app, rebooting mid-session — is in
[docs/MANUAL_TEST_CHECKLIST.md](docs/MANUAL_TEST_CHECKLIST.md).

## Permissions

| Permission | Why | If denied |
|---|---|---|
| `FOREGROUND_SERVICE` + `..._SPECIAL_USE` | Keeps a running session alive and visible | Sessions cannot run in the background |
| `SCHEDULE_EXACT_ALARM` | Ends a session at the moment it is due, even in Doze | Sessions still end, possibly a few minutes late — the app says so before you start |
| `RECEIVE_BOOT_COMPLETED` | Restores a session that was running when the device restarted | Recovery happens on next app launch instead |
| `POST_NOTIFICATIONS` | The ongoing and completion notifications | Everything works; you just do not see them |

Accessibility access is **not** a manifest permission — the user grants it in
Android's own settings and can revoke it there at any time.

Deliberately **not** requested: `INTERNET`, `CALL_PHONE`, `QUERY_ALL_PACKAGES`,
`PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, contacts, location, storage. A
unit test fails the build if any of them ever appears.

## How app blocking works

`ZenAccessibilityService` listens for one event type,
`TYPE_WINDOW_STATE_CHANGED`, and reads one field from it: the package name of
the app that came to the front. If a session is running and that package is on
your list, you are returned to the Zen screen once, after which a cooldown
prevents any repeat until you move elsewhere.

The service declares `canRetrieveWindowContent="false"`, so Android does not
permit it to read screen contents, text fields or anything you type — not as a
policy, but as a capability it does not have.

**Never interrupted, whatever your list says:** the phone dialer, the launcher,
Android Settings, the system UI, your keyboard, and Zen Mode itself. Blocking
Settings would take away your means of switching the service off; blocking the
dialer would come between you and a phone call.

## Database

Room, two tables, schema exported to `app/schemas`.

**`sessions`** — `id`, `startedAt`, `endedAt`, `plannedDurationSeconds`,
`actualDurationSeconds`, `status`, `blockedAppCount`.
Status is stored by name; an unrecognised value decays to `CANCELLED` rather
than crashing. Statistics and streaks are *derived* from this table, never
stored separately, so they cannot drift out of step with it.

**`blocked_apps`** — `packageName` (primary key), `appName`, `enabled`.
Switching an app off keeps the row, so the app remembers what you have already
considered.

Settings live in DataStore. A corrupt store resets to defaults instead of
crashing.

## Known Android limitations

These are properties of the platform, not bugs, and the app states them rather
than working around them:

1. **No app can lock down the device.** Blocking is a redirect, not a lock.
   Determined avoidance will always win, and it should.
2. **The accessibility service can be disabled at any time** in Android's
   settings — by design.
3. **Exact alarms may be denied.** Sessions still end; they may end late when
   the app is not running. The app warns before you start.
4. **Foreground services can be refused**, especially from a boot broadcast on
   Android 14/15. The session stays in the database and recovers on next launch;
   nothing pretends the restoration happened.
5. **Aggressive OEM battery managers** (Xiaomi, Oppo, Samsung and others) can
   kill the service. The alarm is the backstop.
6. **The redirect depends on background activity launch** being permitted for
   system-bound accessibility services. Some OEM builds restrict it.
7. **Android Settings and system screens are always reachable**, so app blocking
   can always be turned off mid-session.

## Privacy

Zen Mode collects nothing, stores everything locally, and has no way to send
data anywhere — it holds no `INTERNET` permission. See
[docs/PRIVACY.md](docs/PRIVACY.md).

## License

Not yet chosen. Until one is added, all rights are reserved by the author.
