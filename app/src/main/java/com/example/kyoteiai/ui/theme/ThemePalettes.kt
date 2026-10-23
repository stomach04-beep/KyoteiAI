package com.example.kyoteiai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/**
 * アプリの「配色（カラーパレット）」定義（全アプリ共通・自己完結）。原本は _shared\ThemePalettes.kt.template。
 *
 * ライト/ダークの切替（ThemePrefs.MODE_*）とは独立した軸で、「配色 × 明暗」で最終的な色が決まる。
 *
 * === 2026-10-23 の見直し（表示の決まり UiKit・おしゃれ案）===
 *  ・配色を11 → 3つに絞った：ミッドナイト（計器盤・投資向き）／紙の帳簿（生活向き）／モノクローム。
 *    基調色が赤・桃・橙・黄の配色（クリムゾン・コーラル・サクラ・マンゴー・グラファイト）は、
 *    「危険＝赤／注意＝黄」の色とまぎれるので外した。外した配色を選んでいた人は byId で既定に戻る
 *  ・カードの地（surfaceContainer*）を配色から決めるようにした。指定しないと Material3 既定の
 *    薄紫がかった灰色になり、配色を選んでもカードだけ色味が合わなかった
 *  ・区切り線・グラフの目盛りの色（outlineVariant）は、補足色を面に薄く重ねた色（面と同じ色にすると目盛りが消えた）
 *  ・surfaceContainerHighest（スイッチのオフの溝・既定の Card の地）は1段上の面 surfaceVariant
 *  ・角丸は AppShapes（12dp）にそろえる（Theme.kt の MaterialTheme に渡す）
 */

// 全配色で共通の中立色（本文・サブ文字）
private val NeutralDarkText = Color(0xFFE6EAF2)
private val NeutralDarkSub = Color(0xFF94A3B8)
private val NeutralLightText = Color(0xFF101828)
private val NeutralLightSub = Color(0xFF566174)

/** 角丸の大きさ（部品ごとのばらつきをなくす。札など丸いものは個別に RoundedCornerShape(50)） */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

// 1配色ぶんの「種(seed)」。ここから ColorScheme を組み立てる
private class Seed(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
)

private fun darkSeed(
    primary: Color,
    container: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    onPrimary: Color = Color.White,
    onContainer: Color = NeutralDarkText,
    onSurface: Color = NeutralDarkText,
    onSurfaceVariant: Color = NeutralDarkSub,
) = Seed(
    primary, onPrimary, container, onContainer, secondary,
    background, surface, surfaceVariant, onSurface, onSurfaceVariant,
)

private fun lightSeed(
    primary: Color,
    container: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    onPrimary: Color = Color.White,
    onContainer: Color = primary,
    onSurface: Color = NeutralLightText,
    onSurfaceVariant: Color = NeutralLightSub,
) = Seed(
    primary, onPrimary, container, onContainer, secondary,
    background, surface, surfaceVariant, onSurface, onSurfaceVariant,
)

// カードの地は「背景より1段明るい面（surface）」に統一。主役だけ surfaceVariant（Panel の emphasized）
private fun Seed.toDarkScheme(): ColorScheme = darkColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = container, onPrimaryContainer = onContainer,
    secondary = secondary, onSecondary = onPrimary,
    // 下タブの「選択中」の丸などに使われる。指定しないと Material3 既定の薄紫になる（2026-10-23 実機で発見）
    secondaryContainer = container, onSecondaryContainer = onContainer,
    background = background, onBackground = onSurface,
    surface = surface, onSurface = onSurface,
    surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = background, surfaceContainerLow = surface,
    surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surfaceVariant,
    outlineVariant = onSurfaceVariant.copy(alpha = 0.30f).compositeOver(surface),
)

private fun Seed.toLightScheme(): ColorScheme = lightColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = container, onPrimaryContainer = onContainer,
    secondary = secondary, onSecondary = onPrimary,
    // 下タブの「選択中」の丸などに使われる。指定しないと Material3 既定の薄紫になる（2026-10-23 実機で発見）
    secondaryContainer = container, onSecondaryContainer = onContainer,
    background = background, onBackground = onSurface,
    surface = surface, onSurface = onSurface,
    surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = surface, surfaceContainerLow = surface,
    surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surfaceVariant,
    outlineVariant = onSurfaceVariant.copy(alpha = 0.30f).compositeOver(surface),
)

/**
 * 1配色の定義。label は設定画面の表示名、previewA/B/C は選択チップの色見本。
 * dark/light どちらの ColorScheme を使うかは ThemePrefs の明暗モードで決まる。
 */
data class AppPalette(
    val id: String,
    val label: String,
    val description: String,
    val previewA: Color,
    val previewB: Color,
    val previewC: Color,
    val darkScheme: ColorScheme,
    val lightScheme: ColorScheme,
)

object AppPalettes {
    // ① ミッドナイト（計器盤）：濃紺 × シアン。投資・監視のアプリの既定
    private val MIDNIGHT = AppPalette(
        id = "midnight", label = "ミッドナイト", description = "濃紺 × シアン（計器盤）",
        previewA = Color(0xFF38BDF8), previewB = Color(0xFF1A2438), previewC = Color(0xFF0B1220),
        darkScheme = darkSeed(
            primary = Color(0xFF38BDF8), container = Color(0xFF0F2A43), secondary = Color(0xFF22D3EE),
            background = Color(0xFF0B1220), surface = Color(0xFF121A2B), surfaceVariant = Color(0xFF1A2438),
            onPrimary = Color(0xFF04121F),
        ).toDarkScheme(),
        lightScheme = lightSeed(
            primary = Color(0xFF1D4ED8), container = Color(0xFFDBEAFE), secondary = Color(0xFF0EA5E9),
            background = Color(0xFFF1F4F9), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE6ECF5),
        ).toLightScheme(),
    )

    // ② 紙の帳簿：温かい白 × ティール。生活のアプリの既定
    private val PAPER = AppPalette(
        id = "paper", label = "紙の帳簿", description = "温かい白 × ティール",
        previewA = Color(0xFF0F766E), previewB = Color(0xFFEFEDE7), previewC = Color(0xFFF7F6F2),
        darkScheme = darkSeed(
            primary = Color(0xFF5EC4B6), container = Color(0xFF1F3B37), secondary = Color(0xFF5EC4B6),
            background = Color(0xFF171614), surface = Color(0xFF211F1C), surfaceVariant = Color(0xFF2C2A26),
            onPrimary = Color(0xFF06201C),
            onSurface = Color(0xFFECE9E2), onSurfaceVariant = Color(0xFFA8A49B), onContainer = Color(0xFFECE9E2),
        ).toDarkScheme(),
        lightScheme = lightSeed(
            primary = Color(0xFF0F766E), container = Color(0xFFD7EFEA), secondary = Color(0xFF0F766E),
            background = Color(0xFFF7F6F2), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEFEDE7),
            onContainer = Color(0xFF0B4F49),
            onSurface = Color(0xFF1C1B1A), onSurfaceVariant = Color(0xFF6B6A66),
        ).toLightScheme(),
    )

    // ③ モノクローム：無彩（白 × 黒）
    private val MONO = AppPalette(
        id = "mono", label = "モノクローム", description = "無彩 (白 × 黒)",
        previewA = Color(0xFFE5E7EB), previewB = Color(0xFF9CA3AF), previewC = Color(0xFF000000),
        darkScheme = darkSeed(
            primary = Color(0xFFE5E7EB), container = Color(0xFF2B2B2B), secondary = Color(0xFF9CA3AF),
            background = Color(0xFF000000), surface = Color(0xFF121212), surfaceVariant = Color(0xFF1E1E1E),
            onPrimary = Color(0xFF111827),
        ).toDarkScheme(),
        lightScheme = lightSeed(
            primary = Color(0xFF111827), container = Color(0xFFE5E7EB), secondary = Color(0xFF4B5563),
            background = Color(0xFFF5F5F5), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFECECEC),
            onContainer = Color(0xFF111827),
        ).toLightScheme(),
    )

    /** 設定画面に並べる順 */
    val ALL: List<AppPalette> = listOf(MIDNIGHT, PAPER, MONO)

    /** id から配色を引く。不明な id（外した配色を選んでいた人を含む）はアプリの既定配色に戻す */
    fun byId(id: String?): AppPalette =
        ALL.firstOrNull { it.id == id }
            ?: ALL.firstOrNull { it.id == ThemePrefs.DEFAULT_PALETTE }
            ?: ALL.first()
}
