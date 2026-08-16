package com.zenmode.app.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zenmode.app.data.local.database.ZenDatabase

/**
 * An in-memory [ZenDatabase] for tests: real Room, real SQL, no file on disk.
 */
fun createInMemoryDatabase(): ZenDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        ZenDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
