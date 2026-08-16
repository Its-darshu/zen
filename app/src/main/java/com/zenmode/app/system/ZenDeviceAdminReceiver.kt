package com.zenmode.app.system

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The device-admin component, required only so this app can be made a *device
 * owner* on a dedicated device.
 *
 * It declares no policies. Zen Mode does not want to manage passwords, wipe
 * data, or lock the screen — the only capability it needs from device ownership
 * is being allow-listed for lock task mode, which needs no policy declaration.
 *
 * Provisioning is deliberate and manual: see `docs/STRICT_MODE_SETUP.md`. It
 * cannot happen without the device's owner running an explicit ADB command on a
 * device with no accounts, and it can always be undone.
 */
class ZenDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device admin disabled; strict mode falls back to screen pinning")
    }

    companion object {
        private const val TAG = "ZenDeviceAdmin"

        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, ZenDeviceAdminReceiver::class.java)
    }
}
