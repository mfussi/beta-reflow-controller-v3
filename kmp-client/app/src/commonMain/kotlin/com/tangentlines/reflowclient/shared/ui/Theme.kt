package com.tangentlines.reflowclient.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ---- Brand & palette
private val PrimaryOrange = Color(0xFFF26B1A)
private val PageBg       = Color(0xFFFEFAF7)
private val CardPeach    = Color(0xFFF7EFE8)
private val OutlineSoft  = Color(0xFFE8DDD4)
private val TextMain     = Color(0xFF222222)
private val SnackBg      = Color(0xFF1F1F1F)

// ---- Color schemes
private val AppLightColors = lightColorScheme(
    primary        = PrimaryOrange,
    onPrimary      = Color.White,
    secondary      = CardPeach,
    onSecondary    = TextMain,
    background     = PageBg,
    onBackground   = TextMain,
    surface        = PageBg,
    onSurface      = TextMain,
    surfaceVariant = CardPeach,
    outline        = PrimaryOrange,
    outlineVariant = OutlineSoft
)

private val AppDarkColors = darkColorScheme().copy(
    primary = PrimaryOrange
)

// ---- Shapes
private val AppShapes = Shapes(
    extraLarge = RoundedCornerShape(28.dp),
    large      = RoundedCornerShape(22.dp),
    medium     = RoundedCornerShape(16.dp),
    small      = RoundedCornerShape(12.dp),
    extraSmall = RoundedCornerShape(8.dp)
)

@Composable
private fun buildAppTypography(): Typography {
    val cizel    = createCizelFamily()
    val notosans = createNotosansFamily()

    // No colors here — let components pick from colorScheme
    return Typography(
        // Big, brandy headings
        displayLarge  = TextStyle(fontFamily = cizel,    fontWeight = FontWeight.W900),
        displayMedium = TextStyle(fontFamily = cizel,    fontWeight = FontWeight.W900),
        displaySmall  = TextStyle(fontFamily = cizel,    fontWeight = FontWeight.W800),

        headlineLarge = TextStyle(fontFamily = cizel,    fontWeight = FontWeight.W800),
        headlineMedium= TextStyle(fontFamily = cizel,    fontWeight = FontWeight.W800),
        headlineSmall = TextStyle(fontFamily = cizel,    fontWeight = FontWeight.W700),

        // Titles (cards, sections)
        titleLarge    = TextStyle(fontFamily = notosans, fontWeight = FontWeight.W700),
        titleMedium   = TextStyle(fontFamily = notosans, fontWeight = FontWeight.W600),
        titleSmall    = TextStyle(fontFamily = notosans, fontWeight = FontWeight.W600),

        // Body
        bodyLarge     = TextStyle(fontFamily = notosans, fontWeight = FontWeight.W400),
        bodyMedium    = TextStyle(fontFamily = notosans, fontWeight = FontWeight.W400),
        bodySmall     = TextStyle(fontFamily = notosans, fontWeight = FontWeight.W300),

        // Labels / buttons / chips
        labelLarge    = TextStyle(fontFamily = notosans, fontWeight = FontWeight.W600),
        labelMedium   = TextStyle(fontFamily = notosans, fontWeight = FontWeight.W600),
        labelSmall    = TextStyle(fontFamily = notosans, fontWeight = FontWeight.W500),
    )
}

@Composable
fun ReflowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) AppDarkColors else AppLightColors

    // Ensure Snackbar uses white text via inverse* slots
    val colors = base.copy(
        inverseSurface   = SnackBg,        // snackbar container
        inverseOnSurface = Color.White,    // snackbar text
        inversePrimary   = PrimaryOrange   // snackbar action color
    )

    MaterialTheme(
        colorScheme = colors,
        typography  = buildAppTypography(),
        shapes      = AppShapes,
        content     = content
    )
}