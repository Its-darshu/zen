package com.zenmode.app.feature.blockedapps

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small in-memory cache so scrolling the list does not re-decode icons. */
private val iconCache = mutableMapOf<String, ImageBitmap?>()

/**
 * Draws an installed app's launcher icon.
 *
 * Loaded straight from the package manager off the main thread — no image
 * library, and nothing about the device's apps leaves the phone.
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = iconCache[packageName], packageName) {
        if (iconCache.containsKey(packageName)) {
            value = iconCache[packageName]
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) { loadIcon(context, packageName, size) }
        iconCache[packageName] = loaded
        value = loaded
    }

    Box(modifier = modifier.size(size.dp)) {
        icon?.let { bitmap ->
            Image(
                bitmap = bitmap,
                // The row already announces the app name.
                contentDescription = null,
                modifier = Modifier.size(size.dp),
            )
        }
    }
}

private fun loadIcon(context: Context, packageName: String, sizeDp: Int): ImageBitmap? = runCatching {
    val pixels = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    context.packageManager
        .getApplicationIcon(packageName)
        .toBitmap(width = pixels, height = pixels)
        .asImageBitmap()
}.getOrNull()
