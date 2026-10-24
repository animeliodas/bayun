package io.github.animeliodas.bayun.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.animeliodas.bayun.R

/** Цвет интерфейса: светлая и тёмная схемы (тона Material 3 от одного исходного цвета). */
class Accent(val id: String, val label: Int, val light: ColorScheme, val dark: ColorScheme)

private val Amber = Accent("amber", R.string.accent_amber,
    lightColorScheme(primary = Color(0xFFB8762E), onPrimary = Color.White, primaryContainer = Color(0xFFFFDDB5),
        onPrimaryContainer = Color(0xFF2C1700), secondary = Color(0xFF6D5A45), secondaryContainer = Color(0xFFF3DFC4),
        onSecondaryContainer = Color(0xFF2A1E10), tertiary = Color(0xFF4F6B5A)),
    darkColorScheme(primary = Color(0xFFE6B06E), onPrimary = Color(0xFF3F2400), primaryContainer = Color(0xFF5B3A12),
        onPrimaryContainer = Color(0xFFFFDDB5), secondary = Color(0xFFD3C1A8), secondaryContainer = Color(0xFF4A3B2A),
        onSecondaryContainer = Color(0xFFF3DFC4), tertiary = Color(0xFFA9C9B4), background = Color(0xFF15130F),
        surface = Color(0xFF1C1915)))

private val Purple = Accent("purple", R.string.accent_purple,
    lightColorScheme(primary = Color(0xFF6750A4), onPrimary = Color.White, primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D), secondary = Color(0xFF625B71), secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B), tertiary = Color(0xFF7D5260)),
    darkColorScheme(primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72), primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF), secondary = Color(0xFFCCC2DC), secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8), tertiary = Color(0xFFEFB8C8), background = Color(0xFF141218),
        surface = Color(0xFF141218)))

private val Blue = Accent("blue", R.string.accent_blue,
    lightColorScheme(primary = Color(0xFF2F5DA8), onPrimary = Color.White, primaryContainer = Color(0xFFD7E3FF),
        onPrimaryContainer = Color(0xFF001B3F), secondary = Color(0xFF565F71), secondaryContainer = Color(0xFFDAE2F9),
        onSecondaryContainer = Color(0xFF131C2B), tertiary = Color(0xFF705575)),
    darkColorScheme(primary = Color(0xFFABC7FF), onPrimary = Color(0xFF002F65), primaryContainer = Color(0xFF1F4785),
        onPrimaryContainer = Color(0xFFD7E3FF), secondary = Color(0xFFBEC6DC), secondaryContainer = Color(0xFF3E4759),
        onSecondaryContainer = Color(0xFFDAE2F9), tertiary = Color(0xFFDDBCE0), background = Color(0xFF111318),
        surface = Color(0xFF111318)))

private val Pink = Accent("pink", R.string.accent_pink,
    lightColorScheme(primary = Color(0xFF984061), onPrimary = Color.White, primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3E001D), secondary = Color(0xFF74565F), secondaryContainer = Color(0xFFFFD9E2),
        onSecondaryContainer = Color(0xFF2B151C), tertiary = Color(0xFF7C5635)),
    darkColorScheme(primary = Color(0xFFFFB0C8), onPrimary = Color(0xFF5E1133), primaryContainer = Color(0xFF7B2949),
        onPrimaryContainer = Color(0xFFFFD9E2), secondary = Color(0xFFE2BDC6), secondaryContainer = Color(0xFF5A3F47),
        onSecondaryContainer = Color(0xFFFFD9E2), tertiary = Color(0xFFF0BC95), background = Color(0xFF191113),
        surface = Color(0xFF191113)))

private val Green = Accent("green", R.string.accent_green,
    lightColorScheme(primary = Color(0xFF3B6939), onPrimary = Color.White, primaryContainer = Color(0xFFBCF0B4),
        onPrimaryContainer = Color(0xFF002204), secondary = Color(0xFF53634E), secondaryContainer = Color(0xFFD6E8CE),
        onSecondaryContainer = Color(0xFF111F0F), tertiary = Color(0xFF38656A)),
    darkColorScheme(primary = Color(0xFFA1D39A), onPrimary = Color(0xFF0A390F), primaryContainer = Color(0xFF235024),
        onPrimaryContainer = Color(0xFFBCF0B4), secondary = Color(0xFFBACCB3), secondaryContainer = Color(0xFF3B4B38),
        onSecondaryContainer = Color(0xFFD6E8CE), tertiary = Color(0xFFA0CFD4), background = Color(0xFF10140F),
        surface = Color(0xFF10140F)))

val ACCENTS = listOf(Amber, Purple, Blue, Pink, Green)

/** «Как обои» (Material You) — Android 12+. */
val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Сепия: бумажный фон, акцент — выбранный цвет. */
private fun sepia(accent: ColorScheme) = accent.copy(
    background = Color(0xFFF4ECD8), surface = Color(0xFFF4ECD8), onBackground = Color(0xFF4A3B2C),
    onSurface = Color(0xFF4A3B2C), surfaceVariant = Color(0xFFE8DCC2), surfaceContainer = Color(0xFFEDE2CA),
    surfaceContainerLow = Color(0xFFF0E6CF), surfaceContainerHigh = Color(0xFFE9DDC3),
    surfaceContainerHighest = Color(0xFFE4D7BB), surfaceContainerLowest = Color(0xFFF8F1E1))

@Composable
fun isDarkTheme(theme: String): Boolean = when (theme) {
    "dark" -> true
    "light", "sepia" -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun BayunTheme(theme: String, accent: String = "amber", content: @Composable () -> Unit) {
    val dark = isDarkTheme(theme)
    val context = LocalContext.current
    val base = when {
        accent == "dynamic" && dynamicColorAvailable ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> (ACCENTS.firstOrNull { it.id == accent } ?: Amber).let { if (dark) it.dark else it.light }
    }
    MaterialTheme(colorScheme = if (theme == "sepia") sepia(base) else base, content = content)
}
