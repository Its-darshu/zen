# Security audit — remediation report

**Date:** 2026-08-17
**Scope:** the six verified findings raised against Zen Mode / Zen Launcher.
**Branch:** `main`, uncommitted. Nothing in this pass has been committed or pushed.

> **Note on this file.** No `SECURITY_AUDIT_REPORT.md` existed in the repository
> when this work started — verified with `ls`, `find . -iname "*security*"`, and
> by listing `docs/` and the root `*.md` files. This report is therefore new,
> written against the findings as they were supplied. It records what was
> actually changed and verified; nothing here is inferred or assumed fixed.

## Summary

| # | Finding | Severity | Status |
|---|---|---|---|
| 1 | Release build signed with the debug key | P0 | **FIXED** |
| 2 | Unbounded wallpaper image decoding (memory DoS) | P0 | **FIXED** |
| 3 | Persisted URI permissions taken but never released | P1 | **FIXED** |
| 4 | Backup posture inconsistent with a local-only app | P1 | **FIXED** |
| 5 | Privacy documentation no longer matches the implementation | P2 | **FIXED** |
| 6 | Stale release artifact | P2 | **FIXED** |

**Verification:** `clean` → `testDebugUnitTest --rerun-tasks` → **488 tests, 0
failures, 0 errors, 0 skipped** → `assembleDebug` → `assembleDebugAndroidTest`,
all green. 33 of those 488 tests are new in this pass. `assembleRelease` fails
by design without credentials, and succeeds when they are supplied.

---

## 1. Release build signed with the debug key — P0 — FIXED

### What was wrong

```kotlin
release {
    // Signed with the debug key so that a release build is verifiable locally.
    signingConfig = signingConfigs.getByName("debug")
}
```

The Android debug key is generated with a published password (`android` /
`androiddebugkey`) and is present on every machine with an SDK install. An APK
signed with it can be re-signed and replaced by anybody, and Google Play rejects
it. Worse than the artifact being unusable is that it *looked* usable: the build
succeeded and produced something called `app-release.apk`.

### What changed

**`app/build.gradle.kts`**

- Credentials are read from Gradle properties **or** environment variables:
  `ZEN_RELEASE_STORE_FILE`, `ZEN_RELEASE_STORE_PASSWORD`,
  `ZEN_RELEASE_KEY_ALIAS`, `ZEN_RELEASE_KEY_PASSWORD`. Nothing is hardcoded.
- The `release` signing config is created **only** when all four are present.
  A partial config would fail late, inside the signing task, with a message
  about a null password rather than about the missing setup.
- `buildTypes.release.signingConfig` is left `null` when they are absent. There
  is no fallback of any kind — the debug config is no longer referenced anywhere
  in the file.
- A new task, `:app:verifyReleaseSigning`, fails the build with the exact
  property names and where to put them. It is wired to `assembleRelease`,
  `bundleRelease`, `packageRelease` and `packageReleaseBundle`, so calling a
  lower-level packaging task directly does not slip past it. It also fails if
  `ZEN_RELEASE_STORE_FILE` points at something that is not a file.
- Debug builds are untouched and still need no setup.

**`.gitignore`** — now refuses `*.jks`, `*.keystore`, `keystore.properties`,
`signing.properties`, `release-signing.properties`.

**`README.md`** — the old paragraph telling the reader the release was
debug-signed is replaced by a "Release signing" section with the four names, an
example invocation, and the instruction to keep credentials in
`~/.gradle/gradle.properties` or a CI secret store.

### Security reasoning

Failing loudly is the entire point. An unsigned-but-successful build, or a
debug-signed one, is a trap: it produces an artifact that a hurried person will
ship. The build now refuses to produce anything rather than produce something
misleading.

### Evidence

Without credentials:

```
Execution failed for task ':app:verifyReleaseSigning'.
> Release signing credentials are unavailable, so this release build was stopped.
  ...
  Missing: ZEN_RELEASE_STORE_FILE, ZEN_RELEASE_STORE_PASSWORD, ZEN_RELEASE_KEY_ALIAS, ZEN_RELEASE_KEY_PASSWORD
```

No APK was produced.

With credentials supplied (see §6 for how they were obtained), the build
succeeded and `apksigner verify --print-certs` reported one signer with the
supplied certificate — **not** the debug certificate — using APK Signature
Scheme v2 and v3.

### No keystore was created in or committed to this repository

The signing path was verified with a throwaway 30-day key generated in a scratch
directory outside the project, deleted immediately afterwards. It is not a
production key, it never entered the repository, and no keystore, password or
alias appears anywhere in the tree (`git ls-files` and a filesystem search both
confirm this).

### Remaining risk

**A real release cannot be built until someone creates a production keystore.**
That is the intended state, not an oversight, but it is work that still has to
happen before distribution — and the key must then be backed up, because losing
it means never being able to update the app on Play again.

---

## 2. Unbounded wallpaper image decoding — P0 — FIXED

### What was wrong

`WallpaperImageLoader.sampleSize()` halved the decode size only while **both**
axes still exceeded the target:

```kotlin
while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight)
```

The `&&` is the defect. An image only has to be small on **one** axis to escape
downsampling entirely. The example from the finding, 1080 × 100000, already
"fits" a 1080-wide screen, so the loop never ran once and the decoder was asked
for the full 108 million pixels — about **432 MB** at `ARGB_8888`. There was no
absolute pixel cap and no dimension cap at all, so the ceiling was whatever the
file claimed.

The input is untrusted. The image is whatever file the user picked in a document
picker; declared dimensions cost nothing to write, so a few kilobytes of crafted
PNG could kill the app on the one screen that has nowhere to fall back to — the
home screen of the device's launcher.

### What changed

**`app/src/main/java/com/zenmode/app/system/launcher/WallpaperImageLoader.kt`**
— rewritten around three pieces:

`WallpaperDecodeLimits` — a pure object, free of Android types, holding the
whole sizing decision:

- `MAX_DIMENSION = 4096` — neither axis may exceed this, whatever the other axis
  says. This is what defeats the extreme aspect ratios.
- `MAX_PIXELS = 8_000_000` — a 32 MB ceiling at 4 bytes per pixel. Both caps are
  needed: the dimension cap alone still permits 4096 × 4096 ≈ 67 MB.
- `decodeSize(...)` scales "cover the target, never upscale", then clamps by
  both caps, and the caps always win. It returns `null` for a header that
  reports non-positive dimensions, and never returns a zero on either axis
  (`setTargetSize(0, …)` is rejected by the decoder).

The sizing decision is made inside `onHeaderDecoded`, which is after the header
is read and **before** any pixels are allocated. `decoder.setTargetSize(...)` is
what makes the decode happen at the reduced size, rather than decoding large and
scaling down.

`decodeSafely(...)` — turns any failure into `null`: `Exception` for deleted,
revoked, corrupt or non-image files, and `OutOfMemoryError` as a backstop. The
caps are what make the latter unreachable; catching it anyway is the difference
between a black background and the launcher dying.

`isUsableUriString(...)` — blank or scheme-less references never reach
`ImageDecoder` at all.

The cache now keys on **URI and requested size**, and **remembers failures**.
Previously a null result was not cached, so an image that could not be decoded
was re-attempted on every draw; one hostile file became a permanent drain rather
than a single rejected attempt.

### Tests added

`app/src/test/java/com/zenmode/app/system/launcher/WallpaperDecodeLimitsTest.kt`
— 19 tests, deliberately **plain JVM tests, not Robolectric**. Robolectric's
`ContentResolver` and `ImageDecoder` are permissive stubs that do not reproduce
real decoding, so asserting against them would prove nothing. The sizing policy
is pure, so it is tested directly and exhaustively instead.

Covering every case the finding asked for:

| Case required | Test |
|---|---|
| Normal image | `a screen-sized image is decoded as it is`, `an ordinary phone photo is scaled down to cover the screen`, `a smaller image is never upscaled` |
| Very large image | `a very large image is brought inside the caps` |
| Extremely wide | `an extremely wide image cannot exceed the dimension cap` |
| Extremely tall | `an extremely tall image cannot be decoded at full resolution` — asserts the 1080 × 100000 case by name |
| Huge pixel count | `a huge pixel count is capped by area even when both axes look reasonable` |
| Malformed image | `a header reporting no usable dimensions is refused outright`, `a malformed image becomes null rather than a crash` |
| Decoder failure | `a decoder failure becomes null rather than a crash`, `a revoked permission becomes null…`, `running out of memory becomes null rather than killing the home screen` |
| Invalid URI | `a missing or blank reference is never decoded`, `something that is not a uri at all is never decoded` |

Plus `no declared size, however hostile, escapes both caps`, which runs a table
of adversarial dimensions — including `Int.MAX_VALUE × Int.MAX_VALUE` — through
the policy both with and without a known screen size, and asserts both caps hold
every time.

### Device-only verification

Real `ContentResolver` and real `ImageDecoder` behaviour on real files cannot be
proven on the JVM. Steps 27–33 were added to
`docs/LAUNCHER_WALLPAPER_MANUAL_TEST_CHECKLIST.md`, with commands to generate
the hostile test files and a `dumpsys meminfo` check. **These have not been
run** — every box is unticked, on purpose.

### Remaining risk

The caps are a policy choice. 4096 px / 8 MP keeps ordinary phone photos looking
correct while bounding the worst case at 32 MB; a device with an unusually large
display could see slight softening on an extreme-aspect image. That is the
intended trade and it is documented in the code.

---

## 3. Persisted URI permissions never released — P1 — FIXED

### What was wrong

`WallpaperController.persistReadAccess()` was called every time an image was
picked. `releaseReadAccess()` existed but **was never called from anywhere** —
confirmed by grep. Three separate problems followed:

1. **Accumulation.** Every image the user ever tried left a permanent grant. A
   `takePersistableUriPermission` grant survives reboots and lasts until it is
   released or the app is uninstalled, so the app kept standing read access to
   documents it had no reason to touch. Android caps how many such grants an app
   may hold, so eventually picking a new wallpaper starts failing silently.
2. **The return value was ignored.** `persistReadAccess` returns a `Boolean`, and
   `updateWallpaper.setHome(uri)` ran regardless. A URI the app could not keep
   reading was stored anyway, and would fail on every draw after the next reboot.
3. **Clearing released nothing**, so turning a wallpaper off left the grant
   behind with nothing pointing at it.

### What changed

**New: `app/src/main/java/com/zenmode/app/system/launcher/WallpaperUriPermissions.kt`**

`WallpaperUriPermissionPolicy` — pure, no Android types — decides what to release:

- `staleUri(previous, slot, newUri)` returns the grant to hand back, and returns
  `null` when:
  - the slot held nothing — which is also what makes a second clear a no-op
    rather than a **double release**;
  - the URI has not actually changed — releasing and immediately re-taking is a
    window in which access can be lost for no benefit;
  - **the other wallpaper still points at the same image** — the grant is shared,
    and releasing it would break that one at the next reboot.
- `unusedClaim(current, uri)` returns a grant that was taken for an operation
  that then failed and was never stored, unless the settings already rely on it.

**`SettingsViewModel.kt`** — the ordering is now correct in every path:

- `onHomeWallpaperPicked` / `onLockWallpaperPicked`: read the previous settings
  → **persist the new grant first** → if that fails, tell the user and store
  nothing → otherwise store, then release what the slot used to hold.
- `onLockWallpaperPicked` additionally releases the just-taken grant when the
  system refuses the wallpaper, since nothing was stored to justify keeping it.
- `onHomeWallpaperCleared` / `onLockWallpaperCleared`: release on clear, subject
  to the same still-in-use check.
- Failure is now reported instead of being silent. `onHomeWallpaperPicked` gained
  the `onResult` callback the lock variant already had, and `SettingsScreen`
  surfaces the message. The picker closing with nothing happening and no
  explanation was itself a defect — the user has every reason to believe the
  wallpaper changed.

**`LauncherHomeViewModel.onWallpaperUnavailable()`** — when an undecodable image
is dropped, its grant is released too, through the same policy.

Every release goes through `WallpaperController.releaseReadAccess`, which wraps
the call in `runCatching`, so a provider that has gone away or a grant already
revoked cannot throw.

### Tests added

`app/src/test/java/com/zenmode/app/system/launcher/WallpaperUriPermissionPolicyTest.kt`
— 14 pure JVM tests, one per rule:

- replacing home / lock releases the image it replaced
- changing one wallpaper never releases the other one's image
- an image both wallpapers use is kept when only one changes
- re-picking the same image releases nothing
- clearing releases the image that was in use
- **clearing an empty slot releases nothing, so a second clear is a no-op** (the
  double-release case)
- clearing a shared image keeps it for the wallpaper still using it
- a blank stored reference is never released
- a switched-off wallpaper still gives its grant back
- a grant taken for a failed change is handed straight back
- a failed change keeps a grant that home or lock already relies on
- a blank claim is never released

### Testability, stated honestly

The **decision** — which is where every requirement in the finding lives — is
pure and fully proven. The **effect** is a one-line call into
`ContentResolver.releasePersistableUriPermission`, which no JVM test can
exercise faithfully: Robolectric's `ContentResolver` accepts URIs no real device
would. Steps 34–40 of the wallpaper checklist verify the real grant list with
`adb shell dumpsys activity permissions`, including the shared-image case.
**These have not been run.**

### Remaining risk

The real release call is device-unverified, as above. It is a single wrapped
call and cannot throw, so the worst realistic failure is a grant that is not
released — the pre-existing behaviour — rather than one released wrongly.

---

## 4. Backup posture — P1 — FIXED

### What was inspected first

Neither specification requires backup. `ZEN_MODE_COMPLETE_PROJECT_SPEC.md` §30
says the app "should be privacy-first" with local database, settings and
statistics, and no cloud backend. `ZEN_LAUNCHER_COMPLETE_SPEC.md` §15 says "the
launcher remains local" and forbids cloud sync. Neither mentions backup,
restore, or migrating to a new device. `README.md` mentions restore only in the
sense of restoring a session after a reboot.

The previous configuration was `android:allowBackup="true"` with rules that
explicitly **included** `domain="database"` and `domain="file" path="datastore"`
for cloud backup and device transfer — that is, it opted the Room database and
every DataStore file **in**.

### What that meant in practice

Android Auto Backup copies app data to the user's Google Drive. What would have
been uploaded is: session history (when the user chose to stop using their
phone), the blocked-app list (which apps they find distracting), pinned apps,
the launcher's recent-app list (apps they opened), and wallpaper references.
That is a behavioural profile, and it left the device.

The wallpaper URIs would not even have worked: a `content://` reference is
backed by a persisted permission grant tied to that install, so a restored
reference is one the new device cannot open.

### Decision, and why it is the safer one

Backup is **off on both routes**. Since the specification does not require it,
the safer option was taken as instructed.

- **`android:allowBackup="false"`** in `AndroidManifest.xml`.
- **`res/xml/backup_rules.xml`** (Android 11 and below) — every domain excluded.
  `allowBackup="false"` already settles it there; the explicit exclusions mean
  flipping that flag back on cannot silently start uploading anything.
- **`res/xml/data_extraction_rules.xml`** (Android 12+) — `cloud-backup` and
  `device-transfer` both exclude every domain, including the device-encrypted
  ones. These are separate decisions and are made separately: on Android 12 and
  above `allowBackup="false"` does **not** by itself disable device-to-device
  transfer, so that block is what actually decides it.

Device-to-device transfer never touches a server, so excluding it is the closer
call. It is excluded because the wallpaper references would arrive broken, and
because the rest is a behavioural record nothing in the specification asked to
move anywhere.

All three files carry comments explaining the reasoning and the cost, so that
whoever changes this next reads the argument rather than guessing at it.

### The cost, stated rather than hidden

**No Zen Mode data follows the user to a new phone.** A new device starts with
empty history, no blocked apps and no pinned apps. This is documented for the
user in `docs/PRIVACY.md` and in `README.md`, in those words.

### Evidence

The merged debug manifest and the release APK's binary manifest both report
`android:allowBackup=false`.

---

## 5. Privacy documentation — P2 — FIXED

`docs/PRIVACY.md` was written in Phase 6 and predated favourites, the launcher's
recent-app list, wallpapers and `SET_WALLPAPER`. It described an app that no
longer existed, and one of its statements — that Android backup "may include
this data if you have device backup enabled" — is now wrong in the other
direction.

### What the rewrite adds or corrects

- **Favourites** — pinned package names, in DataStore (`favorite_packages`).
- **The launcher's recent-app list** — named explicitly as *a record of apps you
  opened*, written to disk, surviving reboot. It says it records only apps
  launched from Zen Launcher, holds package names and nothing else (no times, no
  durations, no counts), is capped at 12, and how to clear it. It is not
  described as harmless, because it is a genuine if short usage history.
- **Wallpaper URIs** — stored as references, never as images; nothing copied or
  uploaded; a lasting read permission for exactly one file; **the permission is
  handed back when the wallpaper is replaced or cleared** (finding 3); choosing
  a wallpaper does not grant access to the photo library.
- **`SET_WALLPAPER`** — added to the permission table, described as a normal
  permission with no runtime prompt, used only for the lock-screen wallpaper and
  only when asked, granting no read access to the existing wallpaper.
- **Backup** — a new section replacing the old paragraph: both routes off,
  nothing uploaded, nothing transferred, and the cost spelled out.
- **The accessibility service** — its section is unchanged because it remains
  accurate (`canRetrieveWindowContent="false"`, `typeWindowStateChanged` only,
  foreground package compared and discarded, nothing written to disk, inactive
  outside a session). One clarification was added: the launcher's recent-app
  list is written by the *launcher*, from taps inside it, and the accessibility
  service contributes nothing to it — because the two could otherwise be
  conflated by a reader.
- **Your control** — clearing the recent list and removing a wallpaper choice
  (and its permission) added.

`README.md`'s privacy summary and permission table were corrected to match.

Nothing in the document claims a capability the app does not have, and nothing
claims data is untouched where the implementation touches it.

---

## 6. Release artifact consistency — P2 — FIXED

The release APK on disk was built on 17 Aug at 00:38, before all of the launcher
work, and was signed with the debug key. It was inside `app/build/` and so never
committed, but it was a stale artifact that could be mistaken for a current
release.

`./gradlew clean` removed it. The release path was then rebuilt from clean and
verified.

### Verified on the freshly built release APK

| Check | Result |
|---|---|
| Signer | The supplied certificate, **not** the debug certificate. One signer; APK Signature Scheme v2 and v3 present |
| Permissions | Exactly the six intended, plus AndroidX's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| `INTERNET` / `QUERY_ALL_PACKAGES` | Absent |
| `SET_WALLPAPER` | Present, as intended |
| Launcher activity | `com.zenmode.app.ZenLauncherActivity` with `MAIN` + `HOME` + `DEFAULT`; `MainActivity` with `MAIN` + `LAUNCHER`; the third `LAUNCHER` reference is the `<queries>` block, not a third entry point |
| `allowBackup` | `false` |
| R8 | Ran — `mapping.txt` produced, one 2.6 MB `classes.dex`, APK 2.9 MB against 66 MB unminified debug |
| Lock-screen bypass | `setShowWhenLocked` absent from the release dex; the only source occurrence is the comment in `MainActivity.kt` explaining why it must stay absent |

### Final state on disk

The verification APK was **deleted** along with its mapping output. It was signed
with a throwaway key, and leaving it in place would recreate exactly the
confusion this finding was about. There is deliberately no release artifact on
disk; produce one with real credentials when distributing.

---

## Security regression checks

| Check | Result |
|---|---|
| No `INTERNET` permission | Confirmed — merged manifest and release APK |
| No `QUERY_ALL_PACKAGES` | Confirmed — only the narrow `<queries>` block, unchanged |
| No unnecessary permissions | Six, unchanged in count and identity from before this pass |
| No hidden Android APIs | Confirmed |
| No reflection | No `java.lang.reflect`, `Class.forName`, `getDeclaredMethod`, `setAccessible`, `Runtime.getRuntime` or `ProcessBuilder` anywhere in `app/src/main` |
| No new network dependencies | No Retrofit / OkHttp / Ktor / Volley / Firebase / analytics / crash reporting in the version catalog or build files |
| No debug signing fallback | `signingConfigs.getByName("debug")` no longer appears in `app/build.gradle.kts` |
| No secrets committed | No keystore or properties file in the tree or in `git ls-files`; `.gitignore` now refuses them |
| Domain layer free of Android | No `import android` / `import androidx` under `domain/` |
| No second timer | `ZenTimer` remains the only one; no `CountDownTimer`, `Timer()` or `scheduleAtFixedRate` in the app |
| No second blocker | `ZenAccessibilityService` remains the only `AccessibilityService` |
| Accessibility not expanded | `accessibility_service_config.xml` untouched — still `typeWindowStateChanged` only, `canRetrieveWindowContent="false"` |
| Lock-screen fix intact | `setShowWhenLocked` / `setTurnScreenOn` absent from source and from the release dex |

---

## Test and build results

All run with `JAVA_HOME=/home/jocky/android-studio/jbr` (JDK 21).

| Command | Result |
|---|---|
| `./gradlew clean` | ✅ |
| `./gradlew :app:testDebugUnitTest --rerun-tasks` | ✅ **488 tests, 0 failures, 0 errors, 0 skipped** |
| `./gradlew :app:assembleDebug` | ✅ |
| `./gradlew :app:assembleDebugAndroidTest` | ✅ compiles |
| `./gradlew :app:assembleRelease` (no credentials) | ✅ **fails as designed**, naming all four missing properties; no APK produced |
| `./gradlew :app:assembleRelease` (credentials supplied) | ✅ builds and signs correctly |

Unit tests went from 455 to 488; the 33 new ones are the decode-limit,
decode-failure, URI-validation and URI-permission-policy suites.

---

## Files changed

**Security fixes**

- `app/build.gradle.kts` — release signing config, `verifyReleaseSigning`
- `.gitignore` — keystores and signing properties
- `app/src/main/java/com/zenmode/app/system/launcher/WallpaperImageLoader.kt` — rewritten
- `app/src/main/java/com/zenmode/app/system/launcher/WallpaperUriPermissions.kt` — new
- `app/src/main/java/com/zenmode/app/feature/settings/SettingsViewModel.kt` — URI lifecycle, failure reporting
- `app/src/main/java/com/zenmode/app/feature/settings/SettingsScreen.kt` — surfaces the home-wallpaper failure message
- `app/src/main/java/com/zenmode/app/feature/launcher/home/LauncherHomeViewModel.kt` — releases the grant when dropping a dead image
- `app/src/main/AndroidManifest.xml` — `allowBackup="false"`
- `app/src/main/res/xml/backup_rules.xml` — exclude everything
- `app/src/main/res/xml/data_extraction_rules.xml` — exclude everything, both routes

**Tests**

- `app/src/test/java/com/zenmode/app/system/launcher/WallpaperDecodeLimitsTest.kt` — new, 19 tests
- `app/src/test/java/com/zenmode/app/system/launcher/WallpaperUriPermissionPolicyTest.kt` — new, 14 tests

**Documentation**

- `docs/PRIVACY.md` — rewritten to match the implementation
- `docs/LAUNCHER_WALLPAPER_MANUAL_TEST_CHECKLIST.md` — decode-limit and URI-permission device steps (27–40)
- `README.md` — release signing, `SET_WALLPAPER`, backup posture, test count
- `SECURITY_AUDIT_REPORT.md` — this file

Nothing outside these files was touched. No refactoring, no new features, no
change to Zen Mode behaviour, and no launcher UX change beyond surfacing the one
error message that finding 3 requires.

---

## Remaining risks

1. **No production keystore exists.** Intended, but release builds are blocked
   until one is created — and it must then be backed up, since losing it makes
   future Play updates impossible.
2. **Device-only checks have not been run.** Steps 27–40 of the wallpaper
   checklist — real hostile images and the real persisted-grant list — are
   unticked. The pure logic behind both is proven; its interaction with a real
   `ContentResolver` is not.
3. **Instrumentation tests have still never executed.** Pre-existing: the device
   dropped off USB during the only attempt. `assembleDebugAndroidTest` compiles;
   `connectedDebugAndroidTest` has not run.
4. **Backup is off, so there is no data migration path.** An accepted trade,
   documented for the user. If migration is ever wanted, the right shape is an
   explicit user-initiated export, not re-enabling Auto Backup.
5. **The decode caps are a judgement call.** 4096 px per axis and 8 MP bound the
   worst case at 32 MB while leaving ordinary photos correct. An unusually large
   display could show slight softening on extreme-aspect images.
