package dev.xeu.community.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF6700)

private val Light = lightColorScheme(
    primary = Color(0xFFB64A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCA),
    onPrimaryContainer = Color(0xFF3A1300),
    secondary = Color(0xFF77574A),
    tertiary = Color(0xFF66602F),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB690),
    onPrimary = Color(0xFF5E2200),
    primaryContainer = Orange,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFE7BDAC),
    tertiary = Color(0xFFD2C88C),
    background = Color(0xFF151210),
    surface = Color(0xFF151210),
)

@Composable
fun XeuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
