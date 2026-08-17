package com.zenmode.app.system.launcher

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.zenmode.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Decodes the home wallpaper for drawing.
 *
 * The image behind this URI is **not trusted**. It is whatever file the user
 * picked through the document picker, and a file can claim any dimensions it
 * likes: a 30 KB PNG can declare 100000 × 100000 and, decoded literally, would
 * ask for 40 GB. The home screen is the one screen that must always work, so
 * every decode here is bounded before a single pixel is allocated —
 * see [WallpaperDecodeLimits].
 *
 * Decoding happens off the main thread, and every failure — deleted image,
 * revoked permission, corrupt file, hostile file — returns null so the home
 * screen falls back to black instead of crashing.
 */
@Singleton
class WallpaperImageLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * The last decode, remembered by URI *and* requested size.
     *
     * Failures are remembered too. Without that, an image that cannot be decoded
     * would be re-decoded on every draw, which turns one hostile file into a
     * permanent drain rather than a single rejected attempt.
     */
    private var cacheKey: CacheKey? = null
    private var cachedImage: ImageBitmap? = null

    /**
     * @return the decoded wallpaper, or null when it cannot be read or is not
     *   safe to decode. A null answer is a normal outcome, not an error state.
     */
    suspend fun load(uriString: String, targetWidth: Int, targetHeight: Int): ImageBitmap? {
        val key = CacheKey(uriString, targetWidth, targetHeight)
        if (cacheKey == key) return cachedImage

        val decoded = withContext(ioDispatcher) { decode(uriString, targetWidth, targetHeight) }
        cacheKey = key
        cachedImage = decoded
        return decoded
    }

    /** Drops the cached bitmap, e.g. when the user picks a different image. */
    fun invalidate() {
        cacheKey = null
        cachedImage = null
    }

    private fun decode(uriString: String, targetWidth: Int, targetHeight: Int): ImageBitmap? {
        if (!isUsableUriString(uriString)) return null

        return decodeSafely("wallpaper") {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uriString))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false

                // The header has been read but nothing has been allocated yet.
                // This is the only moment at which the size can be bounded, so
                // the decision is made here, from the declared dimensions.
                val size = WallpaperDecodeLimits.decodeSize(
                    sourceWidth = info.size.width,
                    sourceHeight = info.size.height,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                ) ?: throw UnusableImageException(
                    "Refusing an image that declares ${info.size.width}x${info.size.height}",
                )
                decoder.setTargetSize(size.width, size.height)
            }.asImageBitmap()
        }
    }
}

/** The image's own header is unusable, so nothing should be allocated for it. */
internal class UnusableImageException(message: String) : RuntimeException(message)

/** What a decode was narrowed down to, in pixels. */
internal data class DecodeSize(val width: Int, val height: Int)

private data class CacheKey(val uri: String, val targetWidth: Int, val targetHeight: Int)

/**
 * How large a wallpaper decode is ever allowed to be.
 *
 * Deliberately free of Android types: the sizing decision is the security
 * decision, so it is a pure function that can be tested exhaustively on the JVM
 * against adversarial dimensions, rather than something only a device can prove.
 */
internal object WallpaperDecodeLimits {

    /**
     * No decoded bitmap may exceed this on either axis.
     *
     * This is what defends against the extreme aspect ratios. A 1080 × 100000
     * image scaled only to "cover the screen" would still be 1080 × 100000 —
     * about 432 MB — because it already covers the width. Capping each axis
     * independently makes that impossible whatever the other axis says.
     */
    const val MAX_DIMENSION = 4096

    /**
     * No decoded bitmap may exceed this many pixels.
     *
     * At 4 bytes per pixel (`ARGB_8888`) this is a 32 MB ceiling. Both caps are
     * needed: [MAX_DIMENSION] alone still permits 4096 × 4096 ≈ 67 MB.
     */
    const val MAX_PIXELS = 8_000_000L

    /**
     * The size this image should be decoded at, or null when it should not be
     * decoded at all.
     *
     * Scaling is "cover" — enough to fill the target without upscaling — and is
     * then clamped by both caps. The caps always win, so an absurd source
     * produces a small bitmap rather than a large one; a wallpaper that ends up
     * visibly downscaled is a far better outcome than an out-of-memory kill on
     * the home screen.
     *
     * A non-positive target means the screen size is not known yet, in which
     * case no cover scaling is applied and only the caps constrain the result.
     */
    fun decodeSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): DecodeSize? {
        // A header that reports nothing usable is refused outright: there is no
        // safe size to pick, and guessing would defeat the point of the check.
        if (sourceWidth <= 0 || sourceHeight <= 0) return null

        val cover = if (targetWidth > 0 && targetHeight > 0) {
            min(
                1.0,
                maxOf(
                    targetWidth.toDouble() / sourceWidth,
                    targetHeight.toDouble() / sourceHeight,
                ),
            )
        } else {
            // Never upscale; without a known target there is nothing to cover.
            1.0
        }

        var width = scaled(sourceWidth, cover, roundUp = true)
        var height = scaled(sourceHeight, cover, roundUp = true)

        // Cap each axis. Rounding down here, so a cap can only ever be met.
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            val axisScale = min(
                MAX_DIMENSION.toDouble() / width,
                MAX_DIMENSION.toDouble() / height,
            )
            width = scaled(width, axisScale, roundUp = false)
            height = scaled(height, axisScale, roundUp = false)
        }

        // Then cap the area. `width` and `height` are each at most MAX_DIMENSION
        // by now, so the product cannot overflow a Long.
        val pixels = width.toLong() * height.toLong()
        if (pixels > MAX_PIXELS) {
            val areaScale = sqrt(MAX_PIXELS.toDouble() / pixels.toDouble())
            width = scaled(width, areaScale, roundUp = false)
            height = scaled(height, areaScale, roundUp = false)
        }

        return DecodeSize(width, height)
    }

    /** Always at least one pixel: `setTargetSize(0, …)` is rejected by the decoder. */
    private fun scaled(value: Int, scale: Double, roundUp: Boolean): Int {
        val exact = value.toDouble() * scale
        val rounded = if (roundUp) ceil(exact) else floor(exact)
        return rounded.coerceIn(1.0, Int.MAX_VALUE.toDouble()).toInt()
    }
}

/**
 * Whether a stored wallpaper reference is worth handing to the decoder at all.
 *
 * Cheap, and it keeps blank or obviously broken references from reaching
 * `ImageDecoder`, where they would surface as a caught exception per draw.
 */
internal fun isUsableUriString(uriString: String?): Boolean =
    !uriString.isNullOrBlank() && uriString.contains(':')

/**
 * Runs a decode and turns *any* failure into null.
 *
 * `OutOfMemoryError` is caught alongside the exceptions on purpose. The size
 * caps exist so it cannot happen, but if it ever did, the home screen dying is
 * a worse outcome than a black background — and this is the one place in the
 * app where that trade is worth making.
 */
internal fun <T : Any> decodeSafely(what: String, block: () -> T): T? = try {
    block()
} catch (e: OutOfMemoryError) {
    Log.w(TAG, "Ran out of memory decoding the $what image", e)
    null
} catch (e: Exception) {
    // Deleted, permission revoked, corrupt, hostile, or simply not an image.
    Log.w(TAG, "Could not decode the $what image", e)
    null
}

private const val TAG = "ZenWallpaperLoader"
