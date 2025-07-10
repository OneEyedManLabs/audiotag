package org.oneeyedmanlabs.audiotag.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeOption {
    LIGHT,
    DARK,
    SYSTEM,
    HIGH_CONTRAST_LIGHT,
    HIGH_CONTRAST_DARK
}

data class AppTheme(
    val name: String,
    val option: ThemeOption
) {
    companion object {
        val availableThemes = listOf(
            AppTheme("Light", ThemeOption.LIGHT),
            AppTheme("Dark", ThemeOption.DARK),
            AppTheme("System", ThemeOption.SYSTEM),
            AppTheme("High Contrast Light", ThemeOption.HIGH_CONTRAST_LIGHT),
            AppTheme("High Contrast Dark", ThemeOption.HIGH_CONTRAST_DARK)
        )
    }
}

// Standard light and dark schemes with better contrast
private val AppLightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = LightBlue40,
    // Better contrast for text
    onSurfaceVariant = Color(0xFF444444),
    outline = Color(0xFF777777)
)

private val AppDarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = LightBlue80,
    // Better contrast for text
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF999999)
)

// High contrast schemes - pure black and white with bold emphasis
private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color.Black,
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF8F8F8),
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color.Black,
    secondaryContainer = Color(0xFFE8E8E8),
    onSecondaryContainer = Color.Black,
    tertiaryContainer = Color(0xFFF0F0F0),
    onTertiaryContainer = Color.Black
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    tertiary = Color.White,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color.White,
    outline = Color.White,
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color.White,
    tertiaryContainer = Color(0xFF1F1F1F),
    onTertiaryContainer = Color.White
)

fun getColorScheme(
    themeOption: ThemeOption,
    systemDarkTheme: Boolean,
    dynamicColor: Boolean,
    context: Context
): ColorScheme {
    return when (themeOption) {
        ThemeOption.LIGHT -> AppLightColorScheme
        ThemeOption.DARK -> AppDarkColorScheme
        ThemeOption.SYSTEM -> {
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (systemDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                systemDarkTheme -> AppDarkColorScheme
                else -> AppLightColorScheme
            }
        }
        ThemeOption.HIGH_CONTRAST_LIGHT -> HighContrastLightColorScheme
        ThemeOption.HIGH_CONTRAST_DARK -> HighContrastDarkColorScheme
    }
}

@Composable
fun AudioTagTheme(
    themeOption: ThemeOption = ThemeOption.SYSTEM,
    // Dynamic color is available on Android 12+ for system theme only
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val colorScheme = getColorScheme(themeOption, systemDarkTheme, dynamicColor, context)
    
    // Use enhanced typography for high contrast themes
    val typography = if (themeOption == ThemeOption.HIGH_CONTRAST_LIGHT || themeOption == ThemeOption.HIGH_CONTRAST_DARK) {
        HighContrastTypography
    } else {
        Typography
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}