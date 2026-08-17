package com.zenmode.app.data.local.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recent-app list's storage format.
 *
 * Order is the meaning here, so these tests exist mainly to pin down that
 * encoding and decoding preserve it, and that malformed storage degrades to an
 * empty list rather than to a crash on the home screen.
 */
class RecentAppsEncodingTest {

    @Test
    fun `order survives a round trip`() {
        val packages = listOf("com.b", "com.a", "com.c")

        val restored = RecentAppsDataSource.decode(RecentAppsDataSource.encode(packages))

        assertEquals(packages, restored)
    }

    @Test
    fun `an empty list round trips to empty`() {
        assertTrue(RecentAppsDataSource.decode(RecentAppsDataSource.encode(emptyList())).isEmpty())
    }

    @Test
    fun `missing storage reads as empty, not as an error`() {
        assertTrue(RecentAppsDataSource.decode(null).isEmpty())
        assertTrue(RecentAppsDataSource.decode("").isEmpty())
    }

    @Test
    fun `blank and whitespace entries are discarded`() {
        val restored = RecentAppsDataSource.decode("com.a\n\n   \ncom.b")

        assertEquals(listOf("com.a", "com.b"), restored)
    }

    @Test
    fun `duplicates are collapsed, keeping the first position`() {
        val restored = RecentAppsDataSource.decode("com.a\ncom.b\ncom.a")

        assertEquals(listOf("com.a", "com.b"), restored)
    }

    @Test
    fun `the list is capped so history cannot grow forever`() {
        val many = (1..40).map { "com.example.app$it" }

        val encoded = RecentAppsDataSource.encode(many)

        assertEquals(RecentAppsDataSource.MAX_ENTRIES, RecentAppsDataSource.decode(encoded).size)
    }

    // ---- recency ordering ----

    @Test
    fun `a newly opened app goes to the front`() {
        val updated = RecentAppsDataSource.withMostRecent(listOf("com.a", "com.b"), "com.c")

        assertEquals(listOf("com.c", "com.a", "com.b"), updated)
    }

    @Test
    fun `reopening an app moves it to the front rather than duplicating it`() {
        val updated = RecentAppsDataSource.withMostRecent(listOf("com.a", "com.b"), "com.b")

        assertEquals(listOf("com.b", "com.a"), updated)
        assertEquals(2, updated.size)
    }

    @Test
    fun `reopening the front app leaves the order alone`() {
        val updated = RecentAppsDataSource.withMostRecent(listOf("com.a", "com.b"), "com.a")

        assertEquals(listOf("com.a", "com.b"), updated)
    }

    @Test
    fun `recording past the cap drops the oldest entry`() {
        val full = (1..RecentAppsDataSource.MAX_ENTRIES).map { "com.app$it" }

        val updated = RecentAppsDataSource.withMostRecent(full, "com.newest")

        assertEquals(RecentAppsDataSource.MAX_ENTRIES, updated.size)
        assertEquals("com.newest", updated.first())
        assertTrue("The oldest entry should be gone", "com.app${RecentAppsDataSource.MAX_ENTRIES}" !in updated)
    }

    @Test
    fun `a blank package name is never recorded`() {
        val current = listOf("com.a")

        assertEquals(current, RecentAppsDataSource.withMostRecent(current, "   "))
        assertEquals(current, RecentAppsDataSource.withMostRecent(current, ""))
    }
}
