package com.zenmode.app.core.time

import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** The real clock. The only implementation that reads the device time. */
@Singleton
class SystemZenClock @Inject constructor() : ZenClock {

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
