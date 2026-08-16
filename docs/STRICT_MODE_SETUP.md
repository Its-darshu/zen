# Strict mode — what Android actually allows

Zen Mode has two enforcement levels. The difference is not a setting in this
app; it is a property of the device, decided by Android.

| | Normal mode | Strict mode (screen pinning) | Strict mode (kiosk) |
|---|---|---|---|
| Available on | any phone | any phone | **provisioned device only** |
| Blocked apps redirect | ✅ | ✅ | ✅ |
| Home leaves the session | yes | no | no |
| Recents leaves the session | yes | no | no |
| User can escape | freely | hold **Back + Overview** | power menu / ADB only |
| Lock screen on wake | yes | yes | **no — keyguard is disabled** |
| Fingerprint unlock leads elsewhere | yes | yes | n/a, there is no lock screen |
| Requires factory reset to set up | no | no | **usually yes** |

## The honest summary

**On an ordinary phone with a Google account, kiosk mode is not available.**
Android reserves it for dedicated devices, and provisioning requires a device
with no accounts — which in practice means a factory reset. This is a
deliberate platform restriction, not something the app can work around, and
Zen Mode does not try to.

What you get without provisioning is **screen pinning**, which is real and
useful: Home and Recents stop leaving the session. Android always keeps its own
way out (hold Back and Overview together) and shows the user how. That escape
cannot be removed by any app, including this one.

## What Android does not permit, at all

These are asked for often. None of them is possible for a normal app, and none
is attempted here:

- **Rejecting fingerprint or PIN unlock.** Biometric and credential
  authentication belong to the OS. An app cannot see an unlock attempt, cannot
  refuse one, and cannot make one fail. Zen Mode does not touch biometrics.
- **Replacing or suppressing the secure lock screen.** `disableKeyguard()` has
  never worked on a secure keyguard and is deprecated. Only a device owner can
  disable the keyguard, and only inside lock task mode.
- **Preventing the app from being uninstalled or force-stopped.** Always
  available to the user through Android's settings.
- **Blocking Android Settings.** Zen Mode refuses to do this on purpose: it is
  how you turn the accessibility service off.

## What the kiosk path *does* solve

On a properly provisioned device, `LOCK_TASK_FEATURE_KEYGUARD` is **off** by
default while a task is locked. That means there is no lock screen at all
during a session — the screen goes off, you wake it, and you are back in the
session. That is the legitimate answer to "don't let me escape by unlocking
with my fingerprint": not by fighting the fingerprint, but by there being
nothing to unlock into.

Zen Mode leaves `LOCK_TASK_FEATURE_GLOBAL_ACTIONS` **on**, so the power menu
still works. Removing it would make a device genuinely unrecoverable, which is
not something this app is willing to ship.

## Provisioning a test device as device owner

⚠️ **This wipes accounts and normally needs a factory-reset device.** Do it on a
spare phone or an emulator, not your daily driver.

### Requirements

1. The device has **no accounts at all** — no Google account, no Xiaomi/Samsung
   account, nothing. `dpm set-device-owner` fails with
   `Not allowed to set the device owner because there are already several users`
   or `...because there are accounts on the device` otherwise.
2. The device is **not already** managed by another device owner.
3. USB debugging is on and the app is installed.

### Commands

```bash
# 1. Install the app first — the component must exist.
./gradlew :app:installDebug

# 2. Confirm there are no accounts. Anything other than an empty result blocks it.
adb shell dumpsys account | grep "Account {"

# 3. Provision. Note the applicationId differs between build types:
#    debug   -> com.zenmode.app.debug
#    release -> com.zenmode.app
adb shell dpm set-device-owner com.zenmode.app.debug/com.zenmode.app.system.ZenDeviceAdminReceiver

# 4. Verify.
adb shell dumpsys device_policy | grep -A3 "Device Owner"
```

For a **release** build the command is:

```bash
adb shell dpm set-device-owner com.zenmode.app/com.zenmode.app.system.ZenDeviceAdminReceiver
```

After provisioning, open Zen Mode once. It calls `setLockTaskPackages` on
launch, which is what turns screen pinning into a real kiosk. Settings → Strict
mode will then describe the device as a dedicated device.

### Undoing it

```bash
# Removes device-owner status. The app immediately falls back to screen pinning.
adb shell dpm remove-active-admin com.zenmode.app.debug/com.zenmode.app.system.ZenDeviceAdminReceiver
```

If that is refused on your Android version, a factory reset always clears it.

## Getting out of a session

In order of ordinariness:

1. **Wait.** The timer ends the session. This is the intended path — sessions
   are capped at 12 hours.
2. **The administrative escape.** Long-press the `Z E N   M O D E` label on the
   Zen screen, then confirm. Deliberately unadvertised, deliberately two steps.
   The session is recorded as cancelled.
3. **Screen pinning escape.** Hold Back and Overview together. Android's own,
   always present in pinned mode.
4. **ADB.** `adb shell am force-stop com.zenmode.app.debug`
5. **Uninstall.** `adb uninstall com.zenmode.app.debug`, or from Android
   settings when not in kiosk mode.
6. **Reboot.** The power menu stays available in every mode. After boot, the
   session is restored if it has not expired — but the device is not held until
   you open the app again.

There is deliberately no scenario in which a user cannot recover their own
device.
