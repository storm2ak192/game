package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val AlJasoosDarkColorScheme = darkColorScheme(
    primary = SpyGold,
    onPrimary = NoirMidnight,
    primaryContainer = SpyGoldContainer,
    onPrimaryContainer = SpyGoldBright,
    secondary = SpyCrimson,
    onSecondary = Color.White,
    secondaryContainer = SpyCrimsonContainer,
    onSecondaryContainer = Color(0xFFFFB4AB),
    tertiary = EmeraldConfirm,
    onTertiary = NoirMidnight,
    tertiaryContainer = EmeraldConfirmContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = NoirMidnight,
    onBackground = ParchmentWhite,
    surface = NoirSurface,
    onSurface = ParchmentWhite,
    surfaceVariant = NoirSurfaceVariant,
    onSurfaceVariant = MutedSlate,
    outline = OutlineNavy,
    error = Color(0xFFEF4444),
    onError = Color.White
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AlJasoosTheme(
    content: @Composable () -> Unit
) {
    // Enforce RTL layout direction across the entire Arabic experience
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = AlJasoosDarkColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
