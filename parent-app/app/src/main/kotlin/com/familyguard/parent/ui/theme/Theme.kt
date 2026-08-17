package com.familyguard.parent.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = SoftNeutralSurface,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = SkyBlueSecondary,
    secondaryContainer = SkyBlueSecondaryContainer,
    tertiary = CalmGreenGood,
    tertiaryContainer = CalmGreenContainer,
    error = AmberAttention,
    errorContainer = AmberAttentionContainer,
    background = SoftNeutralBackground,
    surface = SoftNeutralSurface,
    onBackground = SoftNeutralOnBackground,
    onSurface = SoftNeutralOnBackground,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryContainer,
    onPrimary = TealOnPrimaryContainer,
    primaryContainer = TealPrimary,
    onPrimaryContainer = TealPrimaryContainer,
    secondary = SkyBlueSecondaryContainer,
    secondaryContainer = SkyBlueSecondary,
    tertiary = CalmGreenContainer,
    tertiaryContainer = CalmGreenGood,
    error = AmberAttentionContainer,
    errorContainer = AmberAttention,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnBackground,
)

/**
 * Deliberately does NOT default to dynamic color (Material You) even though it's
 * available on API 31+: a stable, designed "calm teal" palette is part of the family
 * safety / digital-wellbeing tone requirement, rather than inheriting whatever wallpaper
 * color the device happens to have (which could easily land on an alarming red/orange).
 */
@Composable
fun FamilyGuardParentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ParentTypography,
        content = content,
    )
}
