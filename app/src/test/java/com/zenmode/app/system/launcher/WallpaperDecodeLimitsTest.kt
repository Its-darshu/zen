package com.zenmode.app.system.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The bound on wallpaper decoding.
 *
 * The wallpaper comes from a file the user picked, and a file's header can claim
 * any dimensions at all — the declared size costs nothing to write and is not
 * evidence of anything. These tests exist because that claim is the input to a
 * memory allocation, so the sizing decision is a security decision.
 *
 * They run as plain JVM tests on purpose. Robolectric's `ContentResolver` and
 * `ImageDecoder` are permissive stubs that do not reproduce real decoding, so
 * proving anything through them would prove nothing; the policy is pure, and is
 * therefore tested directly and exhaustively.
 */
class WallpaperDecodeLimitsTest {

    // A typical portrait phone.
    private val screenWidth = 1080
    private val screenHeight = 2400

    private fun size(sourceWidth: Int, sourceHeight: Int) =
        WallpaperDecodeLimits.decodeSize(sourceWidth, sourceHeight, screenWidth, screenHeight)

    private fun assertWithinLimits(size: DecodeSize?) {
        assertNotNull("An image the app agreed to decode must have a size", size)
        size!!
        assertTrue("Width ${size.width} exceeds the cap", size.width <= WallpaperDecodeLimits.MAX_DIMENSION)
        assertTrue("Height ${size.height} exceeds the cap", size.height <= WallpaperDecodeLimits.MAX_DIMENSION)
        assertTrue(
            "${size.width}x${size.height} exceeds the pixel cap",
            size.width.toLong() * size.height <= WallpaperDecodeLimits.MAX_PIXELS,
        )
        assertTrue("A decode size must be at least one pixel", size.width >= 1 && size.height >= 1)
    }

    // ---- ordinary images ----

    @Test
    fun `a screen-sized image is decoded as it is`() {
        assertEquals(DecodeSize(1080, 2400), size(1080, 2400))
    }

    @Test
    fun `a smaller image is never upscaled`() {
        // Upscaling would spend memory inventing detail that is not in the file.
        assertEquals(DecodeSize(540, 960), size(540, 960))
    }

    @Test
    fun `an ordinary phone photo is scaled down to cover the screen`() {
        val size = size(4032, 3024)!!

        assertWithinLimits(size)
        // Covers the screen in both directions, so nothing is left unpainted.
        assertTrue(size.width >= screenWidth)
        assertTrue(size.height >= screenHeight)
        assertTrue("A 12 MP photo should not be decoded at full size", size.width < 4032)
    }

    // ---- large and adversarial images ----

    @Test
    fun `a very large image is brought inside the caps`() {
        assertWithinLimits(size(12000, 9000))
    }

    @Test
    fun `an extremely wide image cannot exceed the dimension cap`() {
        val size = size(100_000, 1080)!!

        assertWithinLimits(size)
        assertEquals(WallpaperDecodeLimits.MAX_DIMENSION, size.width)
    }

    @Test
    fun `an extremely tall image cannot be decoded at full resolution`() {
        // The case the audit named: covering a 1080-wide screen needs no
        // horizontal scaling at all, so a "cover the screen" rule on its own
        // would happily decode 1080 x 100000 — about 432 MB.
        val size = size(1080, 100_000)!!

        assertWithinLimits(size)
        assertEquals(WallpaperDecodeLimits.MAX_DIMENSION, size.height)
        assertTrue(
            "1080x100000 must not survive as a full-resolution bitmap",
            size.width.toLong() * size.height < 1080L * 100_000,
        )
    }

    @Test
    fun `a huge pixel count is capped by area even when both axes look reasonable`() {
        // 4096 x 4096 passes the dimension cap and is still about 67 MB, which
        // is why the area cap exists as well.
        val size = WallpaperDecodeLimits.decodeSize(20_000, 20_000, 0, 0)!!

        assertWithinLimits(size)
        assertTrue(size.width < WallpaperDecodeLimits.MAX_DIMENSION)
    }

    @Test
    fun `no declared size, however hostile, escapes both caps`() {
        val hostile = listOf(
            1 to 1,
            1 to Int.MAX_VALUE,
            Int.MAX_VALUE to 1,
            Int.MAX_VALUE to Int.MAX_VALUE,
            100_000 to 100_000,
            1080 to 100_000,
            100_000 to 1080,
            65_535 to 65_535,
            3 to 2_000_000,
            2_000_000 to 3,
        )

        hostile.forEach { (width, height) ->
            assertWithinLimits(size(width, height))
            // Also with no known screen size, which is the state during the
            // launcher's first frame.
            assertWithinLimits(WallpaperDecodeLimits.decodeSize(width, height, 0, 0))
        }
    }

    // ---- malformed headers ----

    @Test
    fun `a header reporting no usable dimensions is refused outright`() {
        assertNull(size(0, 1080))
        assertNull(size(1080, 0))
        assertNull(size(0, 0))
        assertNull(size(-1, -1))
    }

    @Test
    fun `an unknown screen size still bounds the decode`() {
        val size = WallpaperDecodeLimits.decodeSize(50_000, 50_000, -1, -1)

        assertWithinLimits(size)
    }

    @Test
    fun `a decode size is never zero on either axis`() {
        // Extreme ratios round towards zero on the short axis; the decoder
        // rejects a zero-sized target, so it must never be produced.
        assertWithinLimits(size(1, 100_000))
        assertWithinLimits(size(100_000, 1))
        assertTrue(size(1, 100_000)!!.width >= 1)
        assertTrue(size(100_000, 1)!!.height >= 1)
    }
}

/**
 * What happens when the decoder itself fails.
 *
 * Whatever goes wrong, the home screen falls back to black. It never crashes,
 * because the home screen is the one screen with nowhere to fall back to.
 */
class DecodeSafelyTest {

    @Test
    fun `a successful decode is returned unchanged`() {
        assertEquals("decoded", decodeSafely("wallpaper") { "decoded" })
    }

    @Test
    fun `a decoder failure becomes null rather than a crash`() {
        assertNull(decodeSafely<String>("wallpaper") { throw IOException("stream closed") })
    }

    @Test
    fun `a malformed image becomes null rather than a crash`() {
        assertNull(
            decodeSafely<String>("wallpaper") {
                throw UnusableImageException("Refusing an image that declares 0x0")
            },
        )
    }

    @Test
    fun `a revoked permission becomes null rather than a crash`() {
        assertNull(decodeSafely<String>("wallpaper") { throw SecurityException("no grant") })
    }

    @Test
    fun `running out of memory becomes null rather than killing the home screen`() {
        // The caps are what make this unreachable; catching it is the backstop.
        assertNull(decodeSafely<String>("wallpaper") { throw OutOfMemoryError() })
    }
}

/** Which stored references are worth handing to the decoder at all. */
class UsableUriStringTest {

    @Test
    fun `a content uri is usable`() {
        assertTrue(isUsableUriString("content://com.android.providers.media.documents/document/1"))
    }

    @Test
    fun `a missing or blank reference is never decoded`() {
        assertTrue(!isUsableUriString(null))
        assertTrue(!isUsableUriString(""))
        assertTrue(!isUsableUriString("   "))
    }

    @Test
    fun `something that is not a uri at all is never decoded`() {
        assertTrue(!isUsableUriString("not-a-uri-at-all"))
    }
}
