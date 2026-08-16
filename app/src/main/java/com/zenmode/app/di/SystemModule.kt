package com.zenmode.app.di

import com.zenmode.app.system.AndroidSessionAlarmScheduler
import com.zenmode.app.system.AndroidZenServiceController
import com.zenmode.app.system.SessionAlarmScheduler
import com.zenmode.app.system.ZenNotifications
import com.zenmode.app.system.ZenNotifier
import com.zenmode.app.system.ZenServiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the Android implementations of the system ports Zen Mode coordinates. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {

    @Binds
    @Singleton
    abstract fun bindSessionAlarmScheduler(
        impl: AndroidSessionAlarmScheduler,
    ): SessionAlarmScheduler

    @Binds
    @Singleton
    abstract fun bindZenServiceController(impl: AndroidZenServiceController): ZenServiceController

    @Binds
    @Singleton
    abstract fun bindZenNotifier(impl: ZenNotifications): ZenNotifier
}
