package com.oink.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color palette for Oink.
 *
 * Modern Pink + Teal palette that's vibrant, energetic, and modern.
 * The pink is coral-toned (not baby pink), teal provides contrast,
 * and we use amber for warnings (not punitive red).
 */

// =============================================================================
// PRIMARY - Coral Pink (Main brand color, buttons, pig elements)
// =============================================================================
val OinkPink = Color(0xFFFF6B9D)           // Primary coral pink
val OinkPinkLight = Color(0xFFFFB8CC)      // Lighter variant
val OinkPinkDark = Color(0xFFE85A8A)       // Darker variant
val OinkPinkContainer = Color(0xFFFFE4EC)  // Container/background
val OnOinkPink = Color(0xFFFFFFFF)         // Text on pink
val OnOinkPinkContainer = Color(0xFF4A1025) // Text on pink container

// Dark mode primary
val OinkPinkDarkTheme = Color(0xFFFFB1C8)
val OinkPinkContainerDark = Color(0xFF8E3A5A)
val OnOinkPinkDark = Color(0xFF5E1133)
val OnOinkPinkContainerDark = Color(0xFFFFE4EC)

// =============================================================================
// SECONDARY - Teal (Accent color, secondary actions, streaks)
// =============================================================================
val OinkTeal = Color(0xFF20C997)           // Vibrant teal
val OinkTealLight = Color(0xFF6EE7C2)      // Lighter variant
val OinkTealDark = Color(0xFF17A47A)       // Darker variant
val OinkTealContainer = Color(0xFFD1FAE5)  // Container/background
val OnOinkTeal = Color(0xFFFFFFFF)         // Text on teal
val OnOinkTealContainer = Color(0xFF064E3B) // Text on teal container

// Dark mode secondary
val OinkTealDarkTheme = Color(0xFF6EE7C2)
val OinkTealContainerDark = Color(0xFF065F46)
val OnOinkTealDark = Color(0xFF064E3B)
val OnOinkTealContainerDark = Color(0xFFD1FAE5)

// =============================================================================
// TERTIARY - Purple (Optional accent)
// =============================================================================
val OinkPurple = Color(0xFF8B5CF6)
val OinkPurpleContainer = Color(0xFFEDE9FE)
val OnOinkPurple = Color(0xFFFFFFFF)
val OnOinkPurpleContainer = Color(0xFF4C1D95)

// Dark mode tertiary
val OinkPurpleDarkTheme = Color(0xFFA78BFA)
val OinkPurpleContainerDark = Color(0xFF5B21B6)
val OnOinkPurpleDark = Color(0xFF2E1065)
val OnOinkPurpleContainerDark = Color(0xFFEDE9FE)

// =============================================================================
// SUCCESS - Green (Gains, positive feedback, +$5, exercise logged)
// =============================================================================
val OinkSuccess = Color(0xFF10B981)        // Vibrant success green
val OinkSuccessLight = Color(0xFF34D399)   // Lighter
val OinkSuccessContainer = Color(0xFFD1FAE5) // Background
val OnOinkSuccess = Color(0xFFFFFFFF)
val OnOinkSuccessContainer = Color(0xFF064E3B)

// Dark mode success
val OinkSuccessDark = Color(0xFF34D399)
val OinkSuccessContainerDark = Color(0xFF065F46)
val OnOinkSuccessDark = Color(0xFF064E3B)
val OnOinkSuccessContainerDark = Color(0xFFD1FAE5)

// =============================================================================
// WARNING - Amber (Losses, penalties, ÷2 - not punitive, just clear)
// =============================================================================
val OinkWarning = Color(0xFFF59E0B)        // Clear amber
val OinkWarningLight = Color(0xFFFBBF24)   // Lighter
val OinkWarningContainer = Color(0xFFFEF3C7) // Background
val OnOinkWarning = Color(0xFFFFFFFF)
val OnOinkWarningContainer = Color(0xFF78350F)

// Dark mode warning
val OinkWarningDark = Color(0xFFFBBF24)
val OinkWarningContainerDark = Color(0xFFB45309)
val OnOinkWarningDark = Color(0xFF78350F)
val OnOinkWarningContainerDark = Color(0xFFFEF3C7)

// =============================================================================
// ERROR - Red (Errors, destructive actions only)
// =============================================================================
val OinkError = Color(0xFFEF4444)
val OinkErrorContainer = Color(0xFFFEE2E2)
val OnOinkError = Color(0xFFFFFFFF)
val OnOinkErrorContainer = Color(0xFF7F1D1D)

// Dark mode error
val OinkErrorDark = Color(0xFFFCA5A5)
val OinkErrorContainerDark = Color(0xFFB91C1C)
val OnOinkErrorDark = Color(0xFF7F1D1D)
val OnOinkErrorContainerDark = Color(0xFFFEE2E2)

// =============================================================================
// NEUTRALS - Background, surface, text
// =============================================================================
// Light mode
val OinkBackground = Color(0xFFF8FAFC)     // Light gray background
val OinkSurface = Color(0xFFFFFFFF)        // White cards/surfaces
val OinkSurfaceVariant = Color(0xFFF1F5F9) // Subtle surface variation
val OnOinkBackground = Color(0xFF1E293B)   // Dark slate - primary text
val OnOinkSurface = Color(0xFF1E293B)      // Primary text on surface
val OnOinkSurfaceVariant = Color(0xFF475569) // Secondary text (slate gray)
val OinkOutline = Color(0xFFCBD5E1)        // Borders/dividers

// Dark mode
val OinkBackgroundDark = Color(0xFF0F172A)  // Dark slate background
val OinkSurfaceDark = Color(0xFF1E293B)     // Dark surface
val OinkSurfaceVariantDark = Color(0xFF334155)
val OnOinkBackgroundDark = Color(0xFFF1F5F9)
val OnOinkSurfaceDark = Color(0xFFF1F5F9)
val OnOinkSurfaceVariantDark = Color(0xFF94A3B8)
val OinkOutlineDark = Color(0xFF475569)

// =============================================================================
// LEGACY COLORS (keeping for backward compatibility during migration)
// These map to new semantic colors
// =============================================================================
val MoneyGreen = OinkSuccess              // Use for cash-related success
val MoneyGreenDark = OinkSuccessDark
val SuccessLight = OinkSuccess            // Exercise logged
val SuccessDark = OinkSuccessDark
val SuccessContainerLight = OinkSuccessContainer
val SuccessContainerDark = OinkSuccessContainerDark
val CoinGold = OinkWarning                // Coin/money accent
