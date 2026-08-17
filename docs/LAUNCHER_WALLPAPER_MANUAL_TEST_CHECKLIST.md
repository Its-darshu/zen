# Zen Launcher — wallpaper device checklist

The wallpaper logic is unit-tested; what it *does to a real phone* is not, and
cannot be. Robolectric's `ContentResolver` and `WallpaperManager` are permissive
stubs that happily open URIs no real device would, so every denial path below
has to be checked by hand.

**Nothing here has been run.** Every box is unticked on purpose.

```bash
./gradlew :app:installDebug
```

> Device: ______________  Android: ______  Skin: ______________

## Home wallpaper

| # | Step | Expected | Pass |
|---|------|----------|------|
| 1 | Settings → Wallpaper → Home screen → pick an image | Picker opens (a document picker, not a permission prompt) | ☐ |
| 2 | Press Home | The image is behind the clock, filling the screen, not stretched | ☐ |
| 3 | Kill the launcher (swipe from recents) and press Home | Wallpaper still there | ☐ |
| 4 | Reboot the phone, press Home | **Wallpaper still there** — this is what the persisted URI grant is for | ☐ |
| 5 | Settings → Turn off home wallpaper | Launcher background is pure black | ☐ |
| 6 | Pick a different image | The new one replaces the old, no stale cached bitmap | ☐ |
| 7 | Open the picker and press Back without choosing | Nothing changes; no error | ☐ |
| 8 | Pick an image, then delete it from the gallery, then press Home | Black background, one message saying to choose another. **No crash, no blank freeze** | ☐ |
| 9 | Settings after step 8 | Home wallpaper reads as off, so it stops retrying a dead reference | ☐ |
| 10 | Pick a very large photo (48MP+) | Loads without stalling or an out-of-memory crash | ☐ |

## Lock-screen wallpaper

⚠️ **This changes your real device lock wallpaper and cannot be undone by the
app.** Read step 15 before starting.

| # | Step | Expected | Pass |
|---|------|----------|------|
| 11 | Settings → Wallpaper → Lock screen | The description warns it is a system-wide change that replaces the current one | ☐ |
| 12 | Pick an image | Applied without error | ☐ |
| 13 | Lock the phone | **Your image is the lock-screen wallpaper** | ☐ |
| 14 | Check the home screen wallpaper (MIUI/system, not the launcher) | Unchanged — only the lock wallpaper was touched | ☐ |
| 15 | Settings → Turn off lock wallpaper | Cleared. Android now mirrors the system home wallpaper. **It does not restore whatever you had before** — the app cannot read the old one to back it up | ☐ |
| 16 | If the device refuses (managed/OEM) | Row reads "Unavailable" and tapping does nothing. No false success message | ☐ |

## The lock screen itself must be untouched

| # | Step | Expected | Pass |
|---|------|----------|------|
| 17 | Lock the phone, press power | Normal lock screen appears | ☐ |
| 18 | Unlock with **fingerprint** | Works exactly as before | ☐ |
| 19 | Unlock with **PIN / password** | Works exactly as before | ☐ |
| 20 | Confirm no app screen appears over the keyguard | The wallpaper is a *picture*; the keyguard is Android's and is not drawn over | ☐ |

## Zen Mode

| # | Step | Expected | Pass |
|---|------|----------|------|
| 21 | With a home wallpaper set, start a Zen session, press Home | **Pure black** — the wallpaper is suppressed during a session | ☐ |
| 22 | End the session, press Home | Wallpaper returns | ☐ |
| 23 | During a session, confirm the countdown is the existing one | One timer, matching the Zen screen exactly | ☐ |

## Accessibility

| # | Step | Expected | Pass |
|---|------|----------|------|
| 24 | TalkBack on, walk the wallpaper settings | Every row announced with its real state; the wallpaper image itself is not announced (it is decorative) | ☐ |
| 25 | Largest font and display size | Wallpaper rows readable, nothing clipped | ☐ |
| 26 | With a bright wallpaper, read the clock and favourites | If white-on-photo is unreadable, that is a real finding — report it | ☐ |

## Decoding limits (security)

The sizing rules in `WallpaperDecodeLimits` are unit-tested exhaustively against
adversarial dimensions, and the error handling in `decodeSafely` is tested
directly. What no JVM test can prove is how a **real** `ContentResolver` and a
**real** `ImageDecoder` behave on a real file, so these are device checks.

Make the test files first, on a desktop:

```bash
# ~4 KB each, and both claim enormous dimensions
convert -size 1080x100000 xc:navy tall.png       # ImageMagick
convert -size 100000x1080 xc:navy wide.png
convert -size 20000x20000 xc:navy huge.png
head -c 40000 /dev/urandom > broken.jpg          # not an image at all
```

Copy them to the phone, then:

| # | Step | Expected | Pass |
|---|------|----------|------|
| 27 | Pick `tall.png` as the home wallpaper | Launcher draws something and **stays alive**. No freeze, no restart, no "Zen Mode keeps stopping" | ☐ |
| 28 | Watch memory while step 27 decodes (`adb shell dumpsys meminfo com.zenmode.app.debug`) | Java heap stays in the tens of MB. A jump of hundreds of MB is a failure | ☐ |
| 29 | Pick `wide.png` | Same: draws, survives | ☐ |
| 30 | Pick `huge.png` (400 MP) | Same: draws, survives | ☐ |
| 31 | Pick `broken.jpg` | Falls back to black and says the image is unavailable. **No crash** | ☐ |
| 32 | Rename a working wallpaper's file on the phone, then reboot and press Home | Black background, the message about choosing another image, no crash loop | ☐ |
| 33 | Repeat step 27 five times in a row | Memory does not climb with each attempt — the failure is remembered, not retried per frame | ☐ |

## URI permissions (security)

Grants must be handed back, and never the wrong one. `adb shell dumpsys
activity permissions` lists what the app holds; grep for `com.zenmode.app`.

| # | Step | Expected | Pass |
|---|------|----------|------|
| 34 | Set a home wallpaper, check the grant list | One persisted grant for that document | ☐ |
| 35 | Replace it with a different image, check again | Still **one** grant, for the new image. The old one is gone | ☐ |
| 36 | Clear the home wallpaper, check again | No grant for it | ☐ |
| 37 | Clear it a second time (or clear an empty slot) | No error, nothing else released | ☐ |
| 38 | Pick the **same** image for home and lock, then clear only home | The grant is still held — the lock wallpaper needs it | ☐ |
| 39 | Then reboot | The lock wallpaper still works | ☐ |
| 40 | Set a home wallpaper on a device where the picker returns a non-persistable URI (e.g. some cloud providers) | The app says the image was not saved, rather than storing a reference that dies at reboot | ☐ |

## What to watch for

- **A wallpaper that vanishes after reboot** means the persisted URI grant
  failed — the most likely real defect in this stage.
- **Any "applied" message with no visible change** is a lie and a bug.
- **Memory**: a huge photo should not spike the launcher's memory; the loader
  caps every decode at 4096 px per side and 8 million pixels, whatever the file
  claims, before anything is allocated.
- **A grant that outlives the wallpaper that needed it** is the leak this stage
  was fixed for; steps 34–39 are the ones that would catch a regression.
