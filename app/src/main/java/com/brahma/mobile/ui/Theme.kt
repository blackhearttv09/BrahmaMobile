package com.brahma.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import java.util.Locale

object BColors {
    val Bg1 = Color(0xFF0B0F2A)
    val Bg2 = Color(0xFF1B1146)
    val Bg3 = Color(0xFF0A0A1C)
    val Accent = Color(0xFF7C5CFF)
    val Accent2 = Color(0xFF00E5FF)
    val Pink = Color(0xFFFF4D9D)
    val Green = Color(0xFF38EF9B)
    val TextHi = Color(0xFFF4F6FF)
    val TextLo = Color(0xB3F4F6FF)
}

/** Tiny bilingual helper: shows Bengali when the phone language is Bengali. */
fun tr(en: String, bn: String): String = if (Locale.getDefault().language == "bn") bn else en

@Composable
fun BrahmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BColors.Accent,
            secondary = BColors.Accent2,
            tertiary = BColors.Pink,
            background = BColors.Bg1,
            surface = BColors.Bg2,
            onPrimary = Color.White,
            onBackground = BColors.TextHi,
            onSurface = BColors.TextHi
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides BColors.TextHi, content = content)
    }
}
