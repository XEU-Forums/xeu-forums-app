package dev.xeu.forums.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.xeu.forums.data.AppSettings
import dev.xeu.forums.data.ThemeMode

/** Brand orange and the tones around it. */
object Brand {
    val Orange = Color(0xFFFF6900)
    val OrangeDeep = Color(0xFFE55300)
    val OrangeSoft = Color(0xFFFF8A3D)
}

/** Extra brand colors Material's scheme doesn't have slots for. */
data class Accents(
    val heroGradient: Brush,
    val onHero: Color,
    val staff: Color,
    val success: Color,
    val dark: Boolean,
)

val LocalAccents = staticCompositionLocalOf {
    Accents(Brush.linearGradient(listOf(Brand.OrangeSoft, Brand.OrangeDeep)), Color.White, Brand.Orange, Color(0xFF2E7D32), false)
}

private val Light = lightColorScheme(
    primary = Color(0xFFE05A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCA),
    onPrimaryContainer = Color(0xFF6A2200),
    secondary = Color(0xFF77574A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4D8),
    onSecondaryContainer = Color(0xFF5D4034),
    tertiary = Color(0xFF66602F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFE4A8),
    onTertiaryContainer = Color(0xFF4E481A),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFFFFBF9),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFFFBF9),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF5DED5),
    onSurfaceVariant = Color(0xFF54433C),
    outline = Color(0xFF87736B),
    outlineVariant = Color(0xFFE6D3CB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF4EF),
    surfaceContainer = Color(0xFFFCEEE8),
    surfaceContainerHigh = Color(0xFFF7E8E1),
    surfaceContainerHighest = Color(0xFFF1E2DB),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color(0xFF4A1800),
    primaryContainer = Color(0xFF7A2E00),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFE7BDAD),
    onSecondary = Color(0xFF442A1F),
    secondaryContainer = Color(0xFF4A3329),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD3C78E),
    onTertiary = Color(0xFF373106),
    tertiaryContainer = Color(0xFF4E481A),
    onTertiaryContainer = Color(0xFFEFE4A8),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF14100E),
    onBackground = Color(0xFFEFE0DA),
    surface = Color(0xFF14100E),
    onSurface = Color(0xFFEFE0DA),
    surfaceVariant = Color(0xFF54433C),
    onSurfaceVariant = Color(0xFFD8C2B9),
    outline = Color(0xFFA08D85),
    outlineVariant = Color(0xFF3A2F2A),
    surfaceContainerLowest = Color(0xFF0F0B09),
    surfaceContainerLow = Color(0xFF1C1714),
    surfaceContainer = Color(0xFF211B18),
    surfaceContainerHigh = Color(0xFF2B2420),
    surfaceContainerHighest = Color(0xFF362E2A),
)

/** Monet neutrals and containers from the wallpaper, with the orange brand accent on top. */
private fun ColorScheme.withOrange(dark: Boolean): ColorScheme =
    if (dark) copy(
        primary = Dark.primary, onPrimary = Dark.onPrimary,
        primaryContainer = Dark.primaryContainer, onPrimaryContainer = Dark.onPrimaryContainer,
        inversePrimary = Light.primary, surfaceTint = Dark.primary,
    ) else copy(
        primary = Light.primary, onPrimary = Light.onPrimary,
        primaryContainer = Light.primaryContainer, onPrimaryContainer = Light.onPrimaryContainer,
        inversePrimary = Dark.primary, surfaceTint = Light.primary,
    )

val supportsDynamicColor get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(lineHeight = 24.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun XeuTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && supportsDynamicColor -> {
            val monet = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (settings.orangeAccent) monet.withOrange(dark) else monet
        }
        dark -> Dark
        else -> Light
    }
    val accents = Accents(
        heroGradient = if (dark) Brush.linearGradient(listOf(Color(0xFFB34300), Color(0xFF6E2400)))
        else Brush.linearGradient(listOf(Brand.OrangeSoft, Brand.OrangeDeep)),
        onHero = Color.White,
        staff = if (dark) Color(0xFFFF8A3D) else Color(0xFFD04F00),
        success = if (dark) Color(0xFF81C995) else Color(0xFF2E7D32),
        dark = dark,
    )

    // Status/navigation bar icons follow the chosen theme, not just the system one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalAccents provides accents) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Monospace-free small caps-ish label used for section headers. */
val SectionLabelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
