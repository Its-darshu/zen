package com.zenmode.app.di

import com.zenmode.app.core.time.SystemZenClock
import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.logic.ZenSessionStateMachine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindZenClock(impl: SystemZenClock): ZenClock

    companion object {

        /**
         * The state machine is stateless, so one instance serves the whole app.
         * Pausing stays switched off for the MVP.
         */
        @Provides
        @Singleton
        fun provideZenSessionStateMachine(): ZenSessionStateMachine = ZenSessionStateMachine(
            pauseSupported = ZenSessionStateMachine.PAUSE_SUPPORTED_IN_MVP,
        )
    }
}
