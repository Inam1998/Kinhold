package com.inamibrahim.kinhold.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The few choices that belong to the phone and not to the tree: theme and accent colour. They are kept in a small
 * preferences file, never in the tree database, so a backup or restore never changes how the app looks.
 */
object AppSettings {
    private const val FILE = "kinhold-settings"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_ACCENT = "accent"

    var mode by mutableStateOf(ThemeMode.Dark)
        private set

    private var context: Context? = null

    /** Reads the saved choices. Call once before anything is drawn. An unknown or missing value falls back to the default. */
    fun load(appContext: Context) {
        context = appContext.applicationContext
        val prefs = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.Dark
        Palette.accent = AccentChoice.entries.firstOrNull { it.name == prefs.getString(KEY_ACCENT, null) } ?: AccentChoice.Green
    }

    fun chooseMode(value: ThemeMode) {
        mode = value
        save()
    }

    fun chooseAccent(value: AccentChoice) {
        Palette.accent = value
        save()
    }

    private fun save() {
        context?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_MODE, mode.name)
            ?.putString(KEY_ACCENT, Palette.accent.name)
            ?.apply()
    }
}
