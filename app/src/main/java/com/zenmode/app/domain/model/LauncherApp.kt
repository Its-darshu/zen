package com.zenmode.app.domain.model

/**
 * An app as the launcher shows it: something the user can open, plus whether
 * they have pinned it to the home screen.
 *
 * Only the package name and label are held. Icons are loaded by the UI when a
 * row is actually drawn and are never part of this model — a `Drawable` is a
 * platform object with a `Context` behind it, and it has no business in the
 * domain layer or in a list that outlives the screen.
 */
data class LauncherApp(
    val packageName: String,
    val appName: String,
    val isFavorite: Boolean = false,
)
