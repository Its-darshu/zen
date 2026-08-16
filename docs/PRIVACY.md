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
| Your settings: default duration, clock and date visibility, 24-hour clock, call button, confirmations, notification preference | DataStore | To remember your preferences |

Statistics and streaks are calculated from your session history each time they
are shown. They are not stored separately, which is why clearing your history
also resets them — the app says so before you confirm.

Android's own backup may include this data if you have device backup enabled.
That is between you and your Android backup settings; the app adds nothing to it.

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

## Permissions

| Permission | Used for |
|---|---|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeping a running session alive and visible |
| `SCHEDULE_EXACT_ALARM` | Ending a session at the moment it is due |
| `RECEIVE_BOOT_COMPLETED` | Restoring a session interrupted by a restart |
| `POST_NOTIFICATIONS` | The ongoing-session and completion notifications |

The app reads the list of apps that have a launcher icon, so you can choose
which to block. This uses a narrow `<queries>` declaration rather than
`QUERY_ALL_PACKAGES`. The list is read when you open the blocked-apps screen and
is not stored; only your own selection is saved.

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
- Delete everything: uninstall the app.

## Questions

This document describes the app as built in this repository. If the code and
this document ever disagree, the code is what runs — please open an issue.
