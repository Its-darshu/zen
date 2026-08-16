# Zen Mode — physical device validation

The automated suite (330 unit tests) covers the logic. This covers what it
cannot: granting an accessibility service, opening another app, rebooting
mid-session, and how any of it actually feels.

**Nothing here has been run.** No device was attached during development. Every
box is unticked on purpose.

## Setup

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build installs as `com.zenmode.app.debug`, so it can sit beside a
release build. For a genuine first-run test, uninstall any previous copy first:

```bash
adb uninstall com.zenmode.app.debug
```

Record the device, Android version and OEM skin — items 24, 25 and 31 behave
differently across manufacturers.

> Device: ______________  Android: ______  Skin: ______________

## Main procedure

| # | Step | Expected | Pass |
|---|------|----------|------|
| 1 | Fresh install (uninstall first) | Installs without warnings | ☐ |
| 2 | First launch | Onboarding appears, not the home screen | ☐ |
| 3 | Read the onboarding | It explains what accessibility access observes, what it never does, what it *cannot* do, and that notifications and exact alarms are optional. No pressure, no dark patterns | ☐ |
| 4 | Tap ENABLE ACCESSIBILITY → enable Zen Mode in Android's list | Android's own screen opens; the description there matches what the app said | ☐ |
| 5 | Return to the app with Back | The permission state has already updated — no restart needed | ☐ |
| 6 | Settings → Permissions | All three rows read correctly: app blocking ON, exact alarms and notifications as granted | ☐ |
| 7 | Settings → Blocked apps → search → enable 2–3 distracting apps | Search filters; toggles persist after leaving and returning; the count updates | ☐ |
| 8 | Home → 25 MIN → START ZEN → confirm | Confirmation lists what will be blocked and that calls stay available. Zen screen appears; ongoing notification shows a live countdown | ☐ |
| 9 | Open a blocked app from the launcher | Returned to the Zen screen within ~1s | ☐ |
| 10 | Watch carefully; open it twice more | Exactly one redirect each time. No flicker, no relaunch loop | ☐ |
| 11 | Open Android Settings | **Not** blocked. Accessibility settings remain reachable | ☐ |
| 12 | Open the dialer, and tap CALL on the Zen screen | Dialer opens, no number filled, nothing dialled. Dialer is never redirected | ☐ |
| 13 | Return to Zen Mode from recents | Countdown is correct — no jump, no reset | ☐ |
| 14 | Turn the screen off for 5+ minutes | — | ☐ |
| 15 | Wait | — | ☐ |
| 16 | Turn the screen on | Remaining time is exactly right (derived from timestamps, not counted) | ☐ |
| 17 | Let the session run out with the app **backgrounded** | Session ends on time without opening the app | ☐ |
| 18 | Check the shade | One completion notification; the ongoing one is gone. No duplicates | ☐ |
| 19 | Open the app → History | Listed as **Completed** with correct start/end times and duration | ☐ |
| 20 | Statistics | Total focus, session count and average all include it | ☐ |
| 21 | Statistics → streak | Current streak reflects today. Best streak is at least as large | ☐ |
| 22 | Start a session, then Stop → confirm | Confirmation says how much is left; STOP ends it, CONTINUE ZEN does not | ☐ |
| 23 | History and Statistics after cancelling | Listed as **Cancelled**. It did **not** raise the completed count, total focus or streak, and no completion notification appeared | ☐ |
| 24 | Start a session, reboot the device | — | ☐ |
| 25 | After boot, open the app | Session still running with correct remaining time. Exactly one session — no duplicate | ☐ |
| 26 | Stale alarm: start A, cancel A, immediately start B, wait past A's original end | B keeps running and is untouched. No completion notification for A | ☐ |
| 27 | Android Settings → Accessibility → disable Zen Mode | — | ☐ |
| 28 | Return to the app | Home shows the setup warning; the confirmation now reads "Start without blocking?" with a SET UP action | ☐ |
| 29 | Enable TalkBack; walk Home → Timer → Zen → Blocked apps → Settings | Every control is reachable and announced. The countdown reads as words ("25 minutes remaining"), not digits. Switches announce their state | ☐ |
| 30 | Settings → Display → largest font and display size | Nothing clipped or overlapping. The Zen screen stays readable; the custom duration steppers still fit | ☐ |
| 31 | Settings → Apps → Zen Mode → Alarms & reminders → deny, then start a session | The confirmation warns Android may delay the end. Permissions screen offers "Allow exact alarms". The session still completes | ☐ |

## Additional checks

| # | Step | Expected | Pass |
|---|------|----------|------|
| 32 | Force-stop the app mid-session, then reopen | Session recovered from the database with correct remaining time | ☐ |
| 33 | Deny notifications, run a short session | Everything works; only notifications are missing. Permissions screen says so | ☐ |
| 34 | Rotate the device on the Zen screen | Countdown unaffected | ☐ |
| 35 | Start a session with **no** apps selected | Allowed, but the confirmation says plainly that nothing will be blocked | ☐ |
| 36 | Uninstall a blocked app, reopen the blocked-apps screen | It disappears from the list; nothing else is lost | ☐ |
| 37 | Install a new app, reopen the blocked-apps screen | It appears **unblocked**. The app never adds anything on its own | ☐ |
| 38 | With a session running, reach the emergency dialer from the lock screen | Reachable and never redirected | ☐ |
| 39 | Settings → toggle each of the nine settings, then verify the behaviour it names | Every switch changes something real | ☐ |
| 40 | Settings → Clear history → confirm | History empties; statistics and streaks reset too, as the confirmation warned | ☐ |

## What to watch for

- **Battery**: an hour-long session should not appear as a heavy consumer. The
  service wakes about once a minute, not once a second.
- **Redirect loops**: any flicker or repeated relaunch of the Zen screen is a
  bug in the cooldown, not a cosmetic issue.
- **Duplicate sessions**: History must never show two overlapping sessions.
- **Silent failure**: every failure should produce a visible message. If
  something simply does not happen, that is a bug worth reporting.

## Reporting

For each failure, note: step number, device, Android version, what happened,
and whether it reproduces. `adb logcat -s ZenAlarm ZenService ZenAccessibility
ZenApplication SessionEndReceiver ZenBootReceiver` shows the app's own logs.
