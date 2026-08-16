package com.zenmode.app.di

import android.content.Context
import androidx.room.Room
import com.zenmode.app.data.local.dao.BlockedAppDao
import com.zenmode.app.data.local.dao.SessionDao
import com.zenmode.app.data.local.database.ZenDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideZenDatabase(@ApplicationContext context: Context): ZenDatabase =
        Room.databaseBuilder(context, ZenDatabase::class.java, ZenDatabase.NAME)
            // Upgrades get real migrations. A *downgrade* only happens when a user
            // installs an older build over a newer one, where there is no schema to
            // migrate back to; dropping the local history is the only option left.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideSessionDao(database: ZenDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideBlockedAppDao(database: ZenDatabase): BlockedAppDao = database.blockedAppDao()
}
