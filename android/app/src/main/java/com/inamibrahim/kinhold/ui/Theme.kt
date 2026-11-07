package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inamibrahim.kinhold.R

// The look is the shared one used by the other apps (see DESIGN-SYSTEM.md); only the accent differs.

/** Every colour the app uses, for one theme and accent. */
class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val text: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerSurface: Color,
)

val DarkColors = AppColors(
    background = Color(0xFF0B1220),
    surface = Color(0xFF172339),
    surfaceHigh = Color(0xFF22314F),
    outline = Color(0xFF26354F),
    accent = Color(0xFF4ADE80),
    accentSoft = Color(0xFFBBF7D0),
    accentDeep = Color(0xFF16A34A),
    onAccent = Color(0xFF0B1220),
    text = Color(0xFFE8EEF8),
    textMuted = Color(0xFF8DA2C0),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    dangerSurface = Color(0xFF2E1517),
)

/** Darker accents than the dark theme, because the bright ones are too faint on white. */
val LightColors = AppColors(
    background = Color(0xFFF4F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE8EEF6),
    outline = Color(0xFFD3DDEA),
    accent = Color(0xFF136B34),
    accentSoft = Color(0xFF15803D),
    accentDeep = Color(0xFF14532D),
    onAccent = Color(0xFFFFFFFF),
    text = Color(0xFF0E1A2B),
    textMuted = Color(0xFF5A6B84),
    success = Color(0xFF047857),
    warning = Color(0xFF92400E),
    danger = Color(0xFFB91C1C),
    dangerSurface = Color(0xFFFEE2E2),
)

enum class ThemeMode { Dark, Light, System }

/** The colour for buttons, selection and highlights. Green is the default; the rest wait for a picker in Settings. */
enum class AccentChoice(
    val label: String,
    val dark: List<Color>,  // bright, soft, deep, text on bright
    val light: List<Color>,
) {
    Green("Green", listOf(Color(0xFF4ADE80), Color(0xFFBBF7D0), Color(0xFF16A34A), Color(0xFF0B1220)), listOf(Color(0xFF136B34), Color(0xFF15803D), Color(0xFF14532D), Color(0xFFFFFFFF))),
    Teal("Teal", listOf(Color(0xFF2DD4BF), Color(0xFF99F6E4), Color(0xFF0D9488), Color(0xFF0B1220)), listOf(Color(0xFF0F766E), Color(0xFF0B857A), Color(0xFF115E59), Color(0xFFFFFFFF))),
    Blue("Blue", listOf(Color(0xFF60A5FA), Color(0xFFBFDBFE), Color(0xFF2563EB), Color(0xFF0B1220)), listOf(Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFF1E40AF), Color(0xFFFFFFFF))),
    Violet("Violet", listOf(Color(0xFFA78BFA), Color(0xFFDDD6FE), Color(0xFF7C3AED), Color(0xFF0B1220)), listOf(Color(0xFF6D28D9), Color(0xFF7C3AED), Color(0xFF5B21B6), Color(0xFFFFFFFF))),
    Rose("Rose", listOf(Color(0xFFFB7185), Color(0xFFFECDD3), Color(0xFFE11D48), Color(0xFF0B1220)), listOf(Color(0xFFBE123C), Color(0xFFE11D48), Color(0xFF9F1239), Color(0xFFFFFFFF))),
    Amber("Amber", listOf(Color(0xFFFBBF24), Color(0xFFFDE68A), Color(0xFFD97706), Color(0xFF0B1220)), listOf(Color(0xFF9A4A06), Color(0xFFB45309), Color(0xFF7C2D12), Color(0xFFFFFFFF)));

    /** The theme's colours with this accent swapped in. */
    fun applyTo(base: AppColors, isDark: Boolean): AppColors {
        val (bright, soft, deep, on) = if (isDark) dark else light
        return AppColors(
            base.background, base.surface, base.surfaceHigh, base.outline, bright, soft, deep, on,
            base.text, base.textMuted, base.success, base.warning, base.danger, base.dangerSurface,
        )
    }
}

/**
 * Every screen reads its colours from here, so switching [dark] repaints the whole app. [dark] and [accent] are
 * Compose state, so anything that reads a colour updates on its own.
 */
object Palette {
    var dark by mutableStateOf(true)
    var accent by mutableStateOf(AccentChoice.Green)

    private val cache = HashMap<Pair<AccentChoice, Boolean>, AppColors>()

    val colors: AppColors get() = cache.getOrPut(accent to dark) { accent.applyTo(if (dark) DarkColors else LightColors, dark) }

    val Background get() = colors.background
    val Surface get() = colors.surface
    val SurfaceHigh get() = colors.surfaceHigh
    val Outline get() = colors.outline
    val Accent get() = colors.accent
    val AccentSoft get() = colors.accentSoft
    val AccentDeep get() = colors.accentDeep
    val OnAccent get() = colors.onAccent
    val Text get() = colors.text
    val TextMuted get() = colors.textMuted
    val Danger get() = colors.danger
}

/** Fixed colours that tell kinds of things apart. They do not follow the accent. */
object Tones {
    val Sky get() = if (Palette.dark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val Rose get() = if (Palette.dark) Color(0xFFF472B6) else Color(0xFFBE185D)
    val Coral get() = if (Palette.dark) Color(0xFFFF9A7A) else Color(0xFFC2410C)
    val Amber get() = if (Palette.dark) Color(0xFFFBBF24) else Color(0xFF92400E)
}

/** Inter (SIL Open Font License), bundled so the app looks the same on every phone. */
@OptIn(ExperimentalTextApi::class)
val AppFont: FontFamily = FontFamily(
    listOf(
        FontWeight.Normal to 400,
        FontWeight.Medium to 500,
        FontWeight.SemiBold to 600,
        FontWeight.Bold to 700,
    ).map { (weight, value) ->
        Font(R.font.inter, weight, variationSettings = FontVariation.Settings(FontVariation.weight(value)))
    },
)

private fun schemeFor(colors: AppColors, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, primaryContainer = colors.accentDeep, onPrimaryContainer = colors.accentSoft,
        secondary = colors.accentSoft, background = colors.background, onBackground = colors.text,
        surface = colors.surface, onSurface = colors.text, surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textMuted,
        outline = colors.outline, outlineVariant = colors.outline,
        error = colors.danger, onError = colors.background, errorContainer = colors.dangerSurface, onErrorContainer = colors.danger,
    )
} else {
    lightColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, primaryContainer = colors.accentDeep, onPrimaryContainer = colors.accentSoft,
        secondary = colors.accentSoft, background = colors.background, onBackground = colors.text,
        surface = colors.surface, onSurface = colors.text, surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textMuted,
        outline = colors.outline, outlineVariant = colors.outline,
        error = colors.danger, onError = colors.onAccent, errorContainer = colors.dangerSurface, onErrorContainer = colors.danger,
    )
}

private fun style(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
    TextStyle(fontFamily = AppFont, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacing.sp)

private val KinholdTypography = Typography(
    displaySmall = style(34, FontWeight.Bold, 40, -0.5),
    headlineMedium = style(28, FontWeight.Bold, 34, -0.3),
    headlineSmall = style(24, FontWeight.SemiBold, 30, -0.2),
    titleLarge = style(20, FontWeight.SemiBold, 26),
    titleMedium = style(16, FontWeight.SemiBold, 22, 0.1),
    titleSmall = style(14, FontWeight.SemiBold, 20, 0.1),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 21),
    bodySmall = style(12, FontWeight.Normal, 17),
    labelLarge = style(15, FontWeight.SemiBold, 20, 0.2),
    labelMedium = style(12, FontWeight.Medium, 16, 0.3),
)

private val KinholdShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Whether the app should be dark for [mode] on a phone whose own setting is [systemDark]. */
fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
    ThemeMode.System -> systemDark
}

@Composable
fun KinholdTheme(mode: ThemeMode = ThemeMode.Dark, content: @Composable () -> Unit) {
    val dark = isDark(mode, isSystemInDarkTheme())
    // Set before anything draws, then kept in step if the choice or the phone's setting changes.
    if (Palette.dark != dark) Palette.dark = dark
    SideEffect { if (Palette.dark != dark) Palette.dark = dark }
    val accent = Palette.accent
    val scheme = remember(dark, accent) { schemeFor(accent.applyTo(if (dark) DarkColors else LightColors, dark), dark) }
    MaterialTheme(colorScheme = scheme, typography = KinholdTypography, shapes = KinholdShapes, content = content)
}
