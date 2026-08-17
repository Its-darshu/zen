package com.zenmode.app.di

import com.zenmode.app.data.local.packages.AndroidInstalledAppsRepository
import com.zenmode.app.data.permission.AndroidAccessibilityPermissionMonitor
import com.zenmode.app.data.repository.BlockedAppRepositoryImpl
import com.zenmode.app.data.repository.FavoriteAppsRepositoryImpl
import com.zenmode.app.data.repository.RecentAppsRepositoryImpl
import com.zenmode.app.data.repository.SessionRepositoryImpl
import com.zenmode.app.data.repository.SettingsRepositoryImpl
import com.zenmode.app.data.repository.WallpaperRepositoryImpl
import com.zenmode.app.data.repository.ZenModeRepositoryImpl
import com.zenmode.app.domain.permission.AccessibilityPermissionMonitor
import com.zenmode.app.domain.repository.BlockedAppRepository
import com.zenmode.app.domain.repository.FavoriteAppsRepository
import com.zenmode.app.domain.repository.InstalledAppsRepository
import com.zenmode.app.domain.repository.RecentAppsRepository
import com.zenmode.app.domain.repository.SessionRepository
import com.zenmode.app.domain.repository.SettingsRepository
import com.zenmode.app.domain.repository.WallpaperRepository
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

    @Binds
    @Singleton
    abstract fun bindFavoriteAppsRepository(
        impl: FavoriteAppsRepositoryImpl,
    ): FavoriteAppsRepository

    @Binds
    @Singleton
    abstract fun bindWallpaperRepository(impl: WallpaperRepositoryImpl): WallpaperRepository

    @Binds
    @Singleton
    abstract fun bindRecentAppsRepository(impl: RecentAppsRepositoryImpl): RecentAppsRepository

    @Binds
    @Singleton
    abstract fun bindInstalledAppsRepository(
        impl: AndroidInstalledAppsRepository,
    ): InstalledAppsRepository

    @Binds
    @Singleton
    abstract fun bindAccessibilityPermissionMonitor(
        impl: AndroidAccessibilityPermissionMonitor,
    ): AccessibilityPermissionMonitor
}
