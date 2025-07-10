package org.oneeyedmanlabs.audiotag.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.oneeyedmanlabs.audiotag.SettingsActivity

/**
 * Global theme manager that provides reactive theme state
 * Allows immediate theme switching without app restart
 */
object ThemeManager {
    
    private val _currentTheme = mutableStateOf(ThemeOption.SYSTEM)
    val currentTheme get() = _currentTheme.value
    
    fun initialize(context: Context) {
        _currentTheme.value = SettingsActivity.getThemeOption(context)
    }
    
    fun setTheme(context: Context, theme: ThemeOption) {
        _currentTheme.value = theme
        SettingsActivity.setThemeOption(context, theme)
    }
    
    fun getCurrentThemeState() = _currentTheme
}