# Privacy

Zen Mode is a local app. It has no server, no account, and no way to send your
data anywhere.

## What is collected

Nothing is collected. Nothing is transmitted. There is no analytics SDK, no
crash reporter, no telemetry and no advertising identifier.

The app does not hold the `INTERNET` permission, so it is not merely a promise
not to send data — Android will not let it.

## What is stored, and where

Everything below lives in the app's private storage on your device, and is
removed when you uninstall the app.

| Data | Where | Why |
|---|---|---|
| Your sessions: start time, end time, planned and actual length, outcome, how many apps were selected | Room database (`sessions`) | History, statistics and streaks |
| The apps you chose to block: package name, app name, on/off | Room database (`blocked_apps`) | So blocking knows what to block |
| Your settings: default duration, clock and date visibility, 24-hour clock, call button, confirmations, notification preference, strict mode | DataStore | To remember your preferences |
| The apps you pinned to the launcher home screen: package names only | DataStore (`favorite_packages`) | To draw your home screen |
| The apps you opened **from Zen Launcher**: package names, most recent first, at most 12 | DataStore (`recent_packages`) | The launcher's "Recently opened" list |
| Your wallpaper choices: a reference to each image, and whether each is switched on | DataStore (`wallpaper_home_uri`, `wallpaper_lock_uri`) | To redraw the wallpaper after a restart |

Two of those deserve more than a table row.

**The launcher's recent-app list is a record of apps you opened.** It is written
to disk, it survives a reboot, and it is a genuine — if short — history of your
phone use. It exists because the screen showing it would otherwise be empty:
Android gives a third-party launcher no way to read the system's own recent
apps. It only ever records apps launched *from Zen Launcher*, never apps you
open any other way, and it holds nothing but package names — no times, no
durations, no counts. It is capped at 12 entries, so the oldest drops off. You
can empty it at any time with **CLEAR LIST** on that screen, and remove a single
entry by long-pressing its card.

**Wallpaper choices are stored as references, not as pictures.** No image is
ever copied, uploaded or kept by the app. What is stored is a `content://` URI
pointing at the file you picked, plus a lasting read permission that Android
grants for that one file so the wallpaper still works after a restart. When you
replace or clear a wallpaper, that permission is handed back — the app does not
keep standing read access to images it no longer uses. Choosing a wallpaper does
not give the app access to your photo library; the document picker hands over
exactly one file.

Statistics and streaks are calculated from your session history each time they
are shown. They are not stored separately, which is why clearing your history
also resets them — the app says so before you confirm.

## Backup: nothing leaves this device

Android backup is **switched off** for this app, on every route:

- **Google Drive backup** (Android Auto Backup) is off. Your session history,
  blocked-app list, pinned apps, recent-app list and settings are never uploaded
  to Google's servers.
- **Phone-to-phone transfer** during new-device setup is off too. On Android 12
  and above this is a separate switch from cloud backup, and it is set
  separately.

This is a deliberate trade, and it has a cost worth knowing about: **none of
your Zen Mode data follows you to a new phone.** Set up on a new device and you
start from an empty history, no blocked apps and no pinned apps. That was chosen
over the alternative, because what this app stores — when you chose to stop
using your phone, which apps you find distracting, which apps you opened — is a
behavioural record, and neither the app's specification nor any feature needs it
to leave the device.

Wallpaper references would not have survived the trip anyway: the permission
that makes a `content://` URI readable is tied to this device and this install.

## The accessibility service

App blocking needs Android's accessibility access, which you grant — and can
revoke — in Android's settings, never inside this app.

**What it observes:** the package name of the app currently in the foreground,
and only while a Zen session is running.

**What it does with it:** compares it against the list of apps you selected. If
it matches, you are returned to the Zen screen. That is the entire operation.

**What it never does:**

- It does not read your screen contents. The service declares
  `canRetrieveWindowContent="false"`, so Android does not grant it that ability.
- It does not read your messages, emails or notifications.
- It does not read anything you type, including passwords.
- It does not record the screen or take screenshots.
- It does not keep any history of which apps you opened. The foreground package
  is compared and discarded; it is never written to disk.
- It does nothing whatsoever when no session is running.

The launcher's recent-app list described above is written by the *launcher*,
from apps you tapped inside it. The accessibility service is not involved in it
and contributes nothing to it.

## Permissions

| Permission | Used for |
|---|---|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeping a running session alive and visible |
| `SCHEDULE_EXACT_ALARM` | Ending a session at the moment it is due |
| `RECEIVE_BOOT_COMPLETED` | Restoring a session interrupted by a restart |
| `SET_WALLPAPER` | Changing the **lock-screen** wallpaper, only when you ask |
| `POST_NOTIFICATIONS` | The ongoing-session and completion notifications |

`SET_WALLPAPER` is a normal permission: there is no runtime prompt, and it grants
no access to any image you have not chosen. It is used for one thing — replacing
the device's lock-screen wallpaper, which is a system-wide change, and which the
app only does after telling you that is what will happen. It does not let the
app *read* your current wallpaper; Android does not allow that, which is also
why clearing the lock wallpaper returns it to Android's default rather than to
whatever you had before. The launcher's own home wallpaper needs no permission
at all: the app simply draws the image you picked.

The app reads the list of apps that have a launcher icon, so you can choose
which to block and which to pin. This uses a narrow `<queries>` declaration
rather than `QUERY_ALL_PACKAGES`. The list is read when you open a screen that
needs it and is not stored; only your own selections are saved.

Zen Mode does **not** request internet, telephony, contacts, location, storage,
usage statistics or overlay permissions.

## Phone calls

The call button opens your dialer with `ACTION_DIAL`. The app does not hold
`CALL_PHONE`, never places a call itself, never fills in a number, and never
touches calls in progress. Emergency calling is never affected, and the dialer
is never blocked during a session.

## Your control

- Turn off app blocking any time: Android Settings → Accessibility → Zen Mode.
- Delete your history: Settings → Clear history.
- Empty the launcher's recent list: Recently opened → CLEAR LIST.
- Remove a wallpaper choice, and the read permission with it: Settings →
  Wallpaper → clear.
- Delete everything: uninstall the app.

## Questions

This document describes the app as built in this repository. If the code and
this document ever disagree, the code is what runs — please open an issue.
