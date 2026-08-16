package com.zenmode.app.di

import com.zenmode.app.data.repository.BlockedAppRepositoryImpl
import com.zenmode.app.data.repository.SessionRepositoryImpl
import com.zenmode.app.data.repository.SettingsRepositoryImpl
import com.zenmode.app.data.repository.ZenModeRepositoryImpl
import com.zenmode.app.domain.repository.BlockedAppRepository
import com.zenmode.app.domain.repository.SessionRepository
import com.zenmode.app.domain.repository.SettingsRepository
import com.zenmode.app.domain.repository.ZenModeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindZenModeRepository(impl: ZenModeRepositoryImpl): ZenModeRepository

    @Binds
    @Singleton
    abstract fun bindBlockedAppRepository(impl: BlockedAppRepositoryImpl): BlockedAppRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
