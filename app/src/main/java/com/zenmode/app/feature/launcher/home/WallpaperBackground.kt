package com.zenmode.app.feature.launcher.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.zenmode.app.core.designsystem.ZenBlack
import com.zenmode.app.system.launcher.WallpaperImageLoader

/**
 * Draws the chosen wallpaper behind the launcher, or black when there is none.
 *
 * The image is decoded off the main thread and downsampled to the space it will
 * actually occupy, so a 50-megapixel photo does not become a 200 MB bitmap on
 * the one screen that must never fail to draw.
 *
 * A wallpaper that cannot be read — deleted, permission revoked, corrupt — is
 * not an error state: the background stays black and [onUnavailable] lets the
 * caller forget the dead reference and tell the user once.
 */
@Composable
fun WallpaperBackground(
    wallpaperUri: String?,
    imageLoader: WallpaperImageLoader,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ZenBlack)) {
        if (wallpaperUri != null) {
            val density = LocalDensity.current
            val targetWidth = with(density) { maxWidth.roundToPx() }
            val targetHeight = with(density) { maxHeight.roundToPx() }

            val image by produceState<ImageBitmap?>(
                initialValue = null,
                wallpaperUri,
                targetWidth,
                targetHeight,
            ) {
                val loaded = imageLoader.load(wallpaperUri, targetWidth, targetHeight)
                value = loaded
                if (loaded == null) onUnavailable()
            }

            image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    // Decorative: the launcher's content carries the meaning.
                    contentDescription = null,
                    // Crop preserves the aspect ratio and fills the screen;
                    // stretching a photo to fit would look wrong.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}
