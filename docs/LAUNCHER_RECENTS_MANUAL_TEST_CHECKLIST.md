# Zen Launcher — recents device checklist

**Read this first, because it changes what the checklist can even ask for.**

Android does not let a third-party launcher build Recents. Verified against
`android.jar` for compileSdk 36:

- `getRecentTasks` / `getRunningTasks` — return only the caller's own tasks
  (since API 21) and are deprecated
- `getAppTasks` — own tasks only, by definition
- `TaskInfo` — no thumbnail field; there is **no public snapshot or thumbnail
  class in the SDK at all**
- `moveTaskToFront` — needs `REORDER_TASKS` and a task id we cannot obtain
- `UsageStatsManager` — would approximate a list, but needs usage-access
  permission, which the specification rules out for this purpose

So the launcher shows **the apps it opened itself**, and the screen says so.
There are no previews and no way to close another app's task. Steps below test
that honest feature — and, just as importantly, that Android's own Recents is
left completely alone.

**Nothing here has been run.** Every box is unticked on purpose.

```bash
./gradlew :app:installDebug
```

> Device: ______________  Android: ______  Skin: ______________

## The launcher's own list

| # | Step | Expected | Pass |
|---|------|----------|------|
| 1 | Press Home (Zen Launcher) | Home shows APPS / RECENT and ZEN MODE / SETTINGS | ☐ |
| 2 | Tap RECENT before opening anything | "No recent apps" — no fake cards | ☐ |
| 3 | Open App A from the drawer | App A opens | ☐ |
| 4 | Home, then open App B | App B opens | ☐ |
| 5 | Home → RECENT | Both listed, **App B first** | ☐ |
| 6 | Read the text at the top | States this is apps opened from Zen Launcher and that Android does not expose the system's recents | ☐ |
| 7 | Look at the cards | Stacked, overlapping, rounded, icon + name. **No screenshots of your apps** | ☐ |
| 8 | Tap App B's card | Returns to App B **where you left it** (Android resumes the existing task) | ☐ |
| 9 | Home → RECENT | App B is first again — reopening re-orders | ☐ |
| 10 | Open a third app, return to RECENT | Three cards, newest first, none duplicated | ☐ |
| 11 | Long press a card | It disappears from the list | ☐ |
| 12 | Check that app is still running (system Recents) | **Still running** — removal is list-only, exactly as the card says | ☐ |
| 13 | Tap CLEAR LIST | List empties; empty state returns | ☐ |
| 14 | Open ~15 different apps, return to RECENT | Capped at 12; oldest dropped | ☐ |
| 15 | Uninstall an app that is in the list, reopen RECENT | Its card is gone, nothing else lost | ☐ |
| 16 | Reboot, then open RECENT | The list survived | ☐ |

## Android's Recents must be untouched

| # | Step | Expected | Pass |
|---|------|----------|------|
| 17 | Use the system Recents gesture (swipe up and hold) | **Android's own Recents** appears, with real previews — not the launcher's screen | ☐ |
| 18 | Three-button mode: tap the Recents button | Android's Recents, unchanged | ☐ |
| 19 | Swipe an app away in Android's Recents | Works normally; the launcher does not interfere | ☐ |
| 20 | Android Back gesture on the launcher's RECENT screen | Returns to launcher home | ☐ |
| 21 | Android Home gesture from the RECENT screen | Goes to launcher home | ☐ |

## Zen Mode

| # | Step | Expected | Pass |
|---|------|----------|------|
| 22 | Start a Zen session, press Home | Minimal Zen presentation: clock, date, countdown | ☐ |
| 23 | Look for the RECENT button | **Gone** during a session | ☐ |
| 24 | Open the launcher's RECENT screen, then start a session from another device path | The screen closes itself back to home | ☐ |
| 25 | End the session, press Home | RECENT, APPS, favourites and gestures all return | ☐ |
| 26 | Confirm only one countdown exists | The launcher's matches the Zen screen exactly — it reads the same session | ☐ |

## Accessibility

| # | Step | Expected | Pass |
|---|------|----------|------|
| 27 | TalkBack: focus a card | Announced as "<App>, recently opened" | ☐ |
| 28 | TalkBack: open the actions menu on a card | Two custom actions: "Open <App>" and "Remove <App> from this list" — no long press needed | ☐ |
| 29 | Largest font and display size | Card names still readable; CLEAR LIST still reachable | ☐ |
| 30 | Fill the list to 12 and scroll | Cards scroll; CLEAR LIST stays visible at the bottom | ☐ |

## What to watch for

- **Any screenshot of another app appearing on a card** would mean something is
  capturing screens. It is not — but if you see one, report it immediately.
- **A card failing to return you to a running app** means the launch intent is
  not resuming the task; note the app and the OEM.
- **The launcher's screen appearing when you use the system Recents gesture**
  would mean it is intercepting a system gesture. It must not.
- **The list surviving a reboot** is the persistence check.
