# Zen Launcher — gesture device checklist

Gesture *rules* are unit-tested. How they feel, and whether they fight Android's
own gestures, cannot be tested on the JVM at all — Robolectric has no real touch
pipeline, no system gesture regions and no OEM behaviour.

**Nothing here has been run.** Every box is unticked on purpose.

```bash
./gradlew :app:installDebug
```

Set Zen Launcher as the home app first (Settings → Zen Launcher → Set as
default launcher), then press Home.

> Device: ______________  Android: ______  Skin: ______________
> Navigation mode: ☐ gestures  ☐ three-button

## Launcher gestures

| # | Step | Expected | Pass |
|---|------|----------|------|
| 1 | Press Home | Zen Launcher home: clock, date, favourites, three buttons | ☐ |
| 2 | Swipe up from the middle of the screen | App drawer rises from the bottom, once | ☐ |
| 3 | Swipe up again, slowly and only ~1 cm | **Nothing happens** — below the threshold | ☐ |
| 4 | Swipe up quickly several times in a row | One drawer open per swipe. No flicker, no double-navigation | ☐ |
| 5 | Tap APPS | Drawer opens — the non-gesture equivalent | ☐ |
| 6 | From the drawer, use the system Back gesture | Returns to home, sliding down | ☐ |
| 7 | Long press empty space on home | App settings open | ☐ |
| 8 | Tap SETTINGS | Same screen — the non-gesture equivalent | ☐ |
| 9 | Long press a pinned app on home | It unpins, with a message | ☐ |
| 10 | Long press that app in the drawer | It pins again | ☐ |

## Android's own gestures must be untouched

This is the section that matters most.

| # | Step | Expected | Pass |
|---|------|----------|------|
| 11 | Swipe in from the **left edge** on home | Android Back — *not* the drawer | ☐ |
| 12 | Swipe in from the **right edge** on home | Android Back — *not* the drawer | ☐ |
| 13 | Swipe up from the very **bottom edge** on home | Android Home — *not* the drawer | ☐ |
| 14 | Swipe up from the bottom edge and hold | Android Recents — *not* the drawer | ☐ |
| 15 | Open an app, then use each system gesture | All behave exactly as before Zen Launcher was installed | ☐ |
| 16 | Pull down from the top of home | Notification shade opens (Android's, untouched) | ☐ |
| 17 | Switch to three-button navigation, repeat 2, 6, 7 | Everything still works; Back button returns from the drawer | ☐ |

## Zen Mode

| # | Step | Expected | Pass |
|---|------|----------|------|
| 18 | Start a Zen session, press Home | Minimal Zen presentation: clock, date, countdown | ☐ |
| 19 | Swipe up on home during the session | **Nothing** — no drawer | ☐ |
| 20 | Long press empty space during the session | **Nothing** — no settings | ☐ |
| 21 | Look for favourites during the session | None shown | ☐ |
| 22 | End the session, press Home | Gestures, favourites and buttons all return | ☐ |

## Accessibility

| # | Step | Expected | Pass |
|---|------|----------|------|
| 23 | TalkBack on, navigate home | Every action reachable by focus and double-tap; no gesture required | ☐ |
| 24 | TalkBack: focus a pinned app | Announced with "Long press to unpin from home" | ☐ |
| 25 | Largest font and display size | Buttons and favourites still fit and are still tappable | ☐ |
| 26 | Small screen / split screen if supported | Threshold still feels right; nothing clipped | ☐ |

## What to watch for

- **The drawer opening by accident** while tapping a favourite means the
  threshold or touch slop is wrong.
- **A system gesture opening the drawer** is the most serious possible failure
  here — report it immediately with the device and navigation mode.
- **Two drawer opens from one swipe** would mean the single-fire guard failed.
- **Jank during the slide** on a low-end device is worth noting.
