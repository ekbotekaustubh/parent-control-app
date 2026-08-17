package com.familyguard.child.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Apricot,
    onPrimary = Cream,
    primaryContainer = ApricotLight,
    onPrimaryContainer = Charcoal,
    secondary = SoftTeal,
    onSecondary = Cream,
    secondaryContainer = SoftTealLight,
    onSecondaryContainer = Charcoal,
    background = Cream,
    onBackground = Charcoal,
    surface = Cream,
    onSurface = Charcoal,
    surfaceVariant = ApricotLight,
    onSurfaceVariant = Slate,
    error = WarnAmber,
)

private val DarkColors = darkColorScheme(
    primary = Apricot,
    onPrimary = Charcoal,
    primaryContainer = Color(0xFF5A3B22),
    onPrimaryContainer = ApricotLight,
    secondary = SoftTeal,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = ApricotLight,
    surface = DarkSurface,
    onSurface = ApricotLight,
    error = WarnAmber,
)

@Composable
fun FamilyGuardChildTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChildTypography,
        content = content,
    )
}
