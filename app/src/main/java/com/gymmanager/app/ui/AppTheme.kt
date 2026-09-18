package com.gymmanager.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gymmanager.app.R
import com.gymmanager.app.auth.ThemeMode

private val GymProOrange = Color(0xFFFF7800)
private val GymProOrangeDark = Color(0xFFFF8A1A)
private val LightBackground = Color(0xFFF7F7F7)
private val DarkBackground = Color(0xFF0D0D0D)
private val DarkSurface = Color(0xFF171717)
private val DarkSurfaceVariant = Color(0xFF242424)

val WildcardSemiItalic = FontFamily(
    Font(R.font.wildcard_semital, FontWeight.SemiBold)
)

private val GymProTypography = Typography().copy(
    displayLarge = TextStyle(fontFamily = WildcardSemiItalic, fontWeight = FontWeight.SemiBold, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = WildcardSemiItalic, fontWeight = FontWeight.SemiBold, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = WildcardSemiItalic, fontWeight = FontWeight.SemiBold, fontSize = 36.sp)
)

@Composable
fun GymManagerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = GymProOrangeDark,
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF3A1D00),
            onPrimaryContainer = Color(0xFFFFDCC2),
            secondary = Color(0xFFBDBDBD),
            background = DarkBackground,
            onBackground = Color(0xFFF2F2F2),
            surface = DarkSurface,
            onSurface = Color(0xFFF2F2F2),
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = Color(0xFFD0D0D0)
        )
    } else {
        lightColorScheme(
            primary = GymProOrange,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE1CC),
            onPrimaryContainer = Color(0xFF351000),
            secondary = Color(0xFF5F5F5F),
            background = LightBackground,
            onBackground = Color(0xFF171717),
            surface = Color.White,
            onSurface = Color(0xFF171717),
            surfaceVariant = Color(0xFFE9E9E9),
            onSurfaceVariant = Color(0xFF4A4A4A)
        )
    }
    MaterialTheme(colorScheme = scheme, typography = GymProTypography, content = content)
}
