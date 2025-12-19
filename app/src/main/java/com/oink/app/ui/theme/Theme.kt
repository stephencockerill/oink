package com.oink.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Oink Theme - Modern Pink + Teal color scheme
 *
 * Key design decisions:
 * - Primary (pink): Main brand color, pig elements, primary actions
 * - Secondary (teal): Accents, streaks, secondary actions
 * - Success (green): Gains, +$5, exercise logged states
 * - Warning (amber): Losses, ÷2 penalties (not punitive red)
 * - Error (red): Only for actual errors, destructive actions
 *
 * Typography:
 * - Poppins: Display/headings - friendly, rounded
 * - Inter: Body/labels - clean, readable
 */

// =============================================================================
// LIGHT COLOR SCHEME
// =============================================================================
private val LightColorScheme = lightColorScheme(
    // Primary - Coral Pink
    primary = OinkPink,
    onPrimary = OnOinkPink,
    primaryContainer = OinkPinkContainer,
    onPrimaryContainer = OnOinkPinkContainer,

    // Secondary - Teal
    secondary = OinkTeal,
    onSecondary = OnOinkTeal,
    secondaryContainer = OinkTealContainer,
    onSecondaryContainer = OnOinkTealContainer,

    // Tertiary - Purple
    tertiary = OinkPurple,
    onTertiary = OnOinkPurple,
    tertiaryContainer = OinkPurpleContainer,
    onTertiaryContainer = OnOinkPurpleContainer,

    // Error - Red (for actual errors only)
    error = OinkError,
    onError = OnOinkError,
    errorContainer = OinkErrorContainer,
    onErrorContainer = OnOinkErrorContainer,

    // Backgrounds & Surfaces
    background = OinkBackground,
    onBackground = OnOinkBackground,
    surface = OinkSurface,
    onSurface = OnOinkSurface,
    surfaceVariant = OinkSurfaceVariant,
    onSurfaceVariant = OnOinkSurfaceVariant,
    outline = OinkOutline,

    // Additional surface colors
    surfaceTint = OinkPink,
    inverseSurface = OinkBackgroundDark,
    inverseOnSurface = OnOinkBackgroundDark,
    inversePrimary = OinkPinkDarkTheme,
    outlineVariant = Color(0xFFE2E8F0)
)

// =============================================================================
// DARK COLOR SCHEME
// =============================================================================
private val DarkColorScheme = darkColorScheme(
    // Primary - Coral Pink (lighter for dark mode)
    primary = OinkPinkDarkTheme,
    onPrimary = OnOinkPinkDark,
    primaryContainer = OinkPinkContainerDark,
    onPrimaryContainer = OnOinkPinkContainerDark,

    // Secondary - Teal
    secondary = OinkTealDarkTheme,
    onSecondary = OnOinkTealDark,
    secondaryContainer = OinkTealContainerDark,
    onSecondaryContainer = OnOinkTealContainerDark,

    // Tertiary - Purple
    tertiary = OinkPurpleDarkTheme,
    onTertiary = OnOinkPurpleDark,
    tertiaryContainer = OinkPurpleContainerDark,
    onTertiaryContainer = OnOinkPurpleContainerDark,

    // Error
    error = OinkErrorDark,
    onError = OnOinkErrorDark,
    errorContainer = OinkErrorContainerDark,
    onErrorContainer = OnOinkErrorContainerDark,

    // Backgrounds & Surfaces
    background = OinkBackgroundDark,
    onBackground = OnOinkBackgroundDark,
    surface = OinkSurfaceDark,
    onSurface = OnOinkSurfaceDark,
    surfaceVariant = OinkSurfaceVariantDark,
    onSurfaceVariant = OnOinkSurfaceVariantDark,
    outline = OinkOutlineDark,

    // Additional surface colors
    surfaceTint = OinkPinkDarkTheme,
    inverseSurface = OinkBackground,
    inverseOnSurface = OnOinkBackground,
    inversePrimary = OinkPink,
    outlineVariant = Color(0xFF475569)
)

// =============================================================================
// THEME COMPOSABLE
// =============================================================================

/**
 * Oink theme composable.
 *
 * @param darkTheme Whether to use dark theme (defaults to system setting)
 * @param dynamicColor Whether to use Android 12+ dynamic colors (disabled to keep brand)
 * @param content The content to apply the theme to
 */
@Composable
fun OinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep our brand colors
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set transparent status/nav bars for edge-to-edge
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OinkTypography,
        content = content
    )
}

// =============================================================================
// EXTENDED COLORS (for non-Material3 semantic colors)
// =============================================================================

/**
 * Extended colors for Oink-specific semantic meanings.
 * Use these for gains/losses instead of error colors.
 */
object OinkColors {
    // Success states - use for exercise logged, gains, +$5
    val success = OinkSuccess
    val successContainer = OinkSuccessContainer
    val onSuccess = OnOinkSuccess
    val onSuccessContainer = OnOinkSuccessContainer

    // Warning states - use for missed days, losses, ÷2 (not punitive!)
    val warning = OinkWarning
    val warningContainer = OinkWarningContainer
    val onWarning = OnOinkWarning
    val onWarningContainer = OnOinkWarningContainer

    // For dark mode
    val successDark = OinkSuccessDark
    val successContainerDark = OinkSuccessContainerDark
    val warningDark = OinkWarningDark
    val warningContainerDark = OinkWarningContainerDark
}
