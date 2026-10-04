package com.cakeshop.manager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Raspberry = Color(0xFFB5194F)
val Pistachio = Color(0xFF2E6A47)
val Caramel = Color(0xFF9A5A0C)
val Blue = Color(0xFF3A3F8F)
val OkBg = Color(0xFFDCEBDD); val OkInk = Color(0xFF23552F)
val WarnBg = Color(0xFFFBE8C6); val WarnInk = Color(0xFF7A4A06)
val BadBg = Color(0xFFF9D8E2); val BadInk = Color(0xFF8E1240)

private val LightColors = lightColorScheme(
    primary = Raspberry,
    onPrimary = Color.White,
    secondary = Pistachio,
    background = Color(0xFFF7F2F5),
    surface = Color.White,
    error = BadInk
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE06A94),
    onPrimary = Color(0xFF3A0A1E),
    secondary = Color(0xFF6FAE86),
    background = Color(0xFF181115),
    surface = Color(0xFF241A20),
    error = Color(0xFFF6BFD1)
)

@Composable
fun CakeShopTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
