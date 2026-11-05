package com.resqmesh.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ResQMeshDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepSpaceBlack,
    primaryContainer = SurfaceCard,
    onPrimaryContainer = ElectricCyan,

    secondary = DeepBlueNeon,
    onSecondary = TextPrimary,
    secondaryContainer = SurfaceCardLight,
    onSecondaryContainer = ElectricCyan,

    tertiary = WarningAmber,
    onTertiary = DeepSpaceBlack,

    error = EmergencyRed,
    onError = TextPrimary,
    errorContainer = EmergencyRedDark,
    onErrorContainer = TextPrimary,

    background = DeepSpaceBlack,
    onBackground = TextPrimary,

    surface = SurfaceDarkVoid,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,

    outline = BorderSubtle,
    outlineVariant = BorderGlass
)

@Composable
fun ResQMeshTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepSpaceBlack.toArgb()
            window.navigationBarColor = DeepSpaceBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = ResQMeshDarkColorScheme,
        typography = ResQMeshTypography,
        content = content
    )
}
