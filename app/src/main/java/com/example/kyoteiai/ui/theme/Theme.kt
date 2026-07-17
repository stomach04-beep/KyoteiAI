package com.example.kyoteiai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ダーク配色（デフォルト）。アクセントは緑（BargainChecker 統一）
private val DarkColors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color.White,
    primaryContainer = AccentGreenContainer,
    onPrimaryContainer = TextPrimary,
    secondary = AccentGreenDark,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary
)

// ライト配色。アクセントは濃い緑
private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightTextPrimary,
    secondary = AccentGreenDark,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary
)

/**
 * テーマ。themeMode = "system" / "light" / "dark"
 */
@Composable
fun KyoteiAITheme(
    themeMode: String = ThemePrefs.DEFAULT,
    paletteId: String = ThemePrefs.DEFAULT_PALETTE,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemePrefs.MODE_LIGHT -> false
        ThemePrefs.MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }
    // 選択中の配色（AppPalettes）を取得し、明暗に応じて dark/light を使い分ける。
    // 旧 DarkColors/LightColors（緑固定）は未使用になったが参照用に残置。
    val palette = AppPalettes.byId(paletteId)
    MaterialTheme(
        colorScheme = if (useDark) palette.darkScheme else palette.lightScheme,
        typography = Typography,
        content = content
    )
}
