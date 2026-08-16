package com.zenmode.app

import android.app.Application
import android.util.Log
import com.zenmode.app.di.ApplicationScope
import com.zenmode.app.system.ZenModeManager
import com.zenmode.app.system.ZenNotifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ZenApplication : Application() {

    @Inject lateinit var zenModeManager: ZenModeManager

    @Inject lateinit var notifications: ZenNotifications

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()

        // Whatever happened while the app was not running — the process was
        // killed, the service was stopped, an alarm was missed — this brings
        // Android's state back in line with the session table. It is safe to run
        // on every launch: it only ever moves towards what the database says.
        applicationScope.launch {
            runCatching { zenModeManager.recover() }
                .onFailure { Log.w(TAG, "Could not reconcile the active session on launch", it) }
        }
    }

    private companion object {
        const val TAG = "ZenApplication"
    }
}
