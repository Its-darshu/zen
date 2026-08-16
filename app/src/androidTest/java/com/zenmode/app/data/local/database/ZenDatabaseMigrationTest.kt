package com.zenmode.app.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration harness for [ZenDatabase].
 *
 * At version 1 there is nothing to migrate yet, so this test validates that the
 * exported schema matches the compiled entities — which is what every future
 * migration will be tested against. When version 2 arrives, add a test that
 * creates a version 1 database, runs the migration and validates it here.
 *
 * Requires a connected device or emulator: `./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ZenDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ZenDatabase::class.java,
    )

    @Test
    fun version1SchemaMatchesTheExportedSchema() {
        helper.createDatabase(TEST_DB, ZenDatabase.VERSION).close()
        helper.runMigrationsAndValidate(TEST_DB, ZenDatabase.VERSION, true).close()
    }

    private companion object {
        const val TEST_DB = "zen-migration-test.db"
    }
}
