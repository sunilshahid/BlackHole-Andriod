package com.blackhole.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

data class BlackHoleThemeColors(
    val isDark: Boolean,
    val background: Color,
    val cardBackground: Color,
    val cardSurface: Color,
    val borderSubtle: Color,
    val borderGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentViolet: Color = AccentViolet,
    val accentCyan: Color = AccentCyan,
    val accentGreen: Color = AccentGreen,
    val accentRed: Color = AccentRed
)

val LocalAppColors = staticCompositionLocalOf {
    BlackHoleThemeColors(
        isDark = true,
        background = BlackSpace,
        cardBackground = CardBackground,
        cardSurface = CardSurface,
        borderSubtle = BorderSubtle,
        borderGlow = BorderGlow,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textMuted = TextMuted
    )
}

val AppTheme: BlackHoleThemeColors
    @Composable
    get() = LocalAppColors.current

@Composable
fun BlackHoleTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    val colors = if (isDark) {
        BlackHoleThemeColors(
            isDark = true,
            background = Color(0xFF030712),
            cardBackground = Color(0xFF0F1322),
            cardSurface = Color(0xFF13182C),
            borderSubtle = Color(0x1FFFFFFF),
            borderGlow = Color(0x4D8B5CF6),
            textPrimary = Color(0xFFF9FAFB),
            textSecondary = Color(0xFF9CA3AF),
            textMuted = Color(0xFF6B7280)
        )
    } else {
        BlackHoleThemeColors(
            isDark = false,
            background = Color(0xFFF4F6F9),
            cardBackground = Color(0xFFFFFFFF),
            cardSurface = Color(0xFFF8FAFC),
            borderSubtle = Color(0xFFE2E8F0),
            borderGlow = Color(0x330D9488),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            textMuted = Color(0xFF64748B)
        )
    }

    val materialColors = if (isDark) {
        darkColorScheme(
            primary = AccentViolet,
            secondary = AccentCyan,
            tertiary = AccentGreen,
            background = colors.background,
            surface = colors.cardBackground,
            surfaceVariant = colors.cardSurface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            error = AccentRed
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0D9488),
            secondary = Color(0xFF0284C7),
            tertiary = Color(0xFF10B981),
            background = colors.background,
            surface = colors.cardBackground,
            surfaceVariant = colors.cardSurface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            error = AccentRed
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
