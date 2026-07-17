package com.example.kyoteiai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * アプリの「配色（カラーパレット）」定義（全アプリ共通・自己完結）。
 *
 * 従来は緑固定だったアクセントを、スタイリッシュな6配色から選べるようにした。
 * ライト/ダークの切替（ThemePrefs.MODE_*）とは独立した軸で、
 * 「配色 × 明暗」の組み合わせで最終的な色が決まる。
 *
 * このファイルは Color.kt 等に依存せず単独で完結する（どのアプリにもそのまま置ける）。
 * 文字色などの中立色はここで定義し、色味の出る部分(primary/container/背景/面)だけ配色ごとに差し替える。
 */

// 全配色で共通の中立色（本文・サブ文字）
private val NeutralDarkText = Color(0xFFE5E7EB)
private val NeutralDarkSub = Color(0xFF9CA3AF)
private val NeutralLightText = Color(0xFF101828)
private val NeutralLightSub = Color(0xFF566174)

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
) = Seed(
    primary, onPrimary, container, onContainer, secondary,
    background, surface, surfaceVariant, NeutralDarkText, NeutralDarkSub,
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
) = Seed(
    primary, onPrimary, container, onContainer, secondary,
    background, surface, surfaceVariant, NeutralLightText, NeutralLightSub,
)

private fun Seed.toDarkScheme(): ColorScheme = darkColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = container, onPrimaryContainer = onContainer,
    secondary = secondary, onSecondary = onPrimary,
    background = background, onBackground = onSurface,
    surface = surface, onSurface = onSurface,
    surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
)

private fun Seed.toLightScheme(): ColorScheme = lightColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = container, onPrimaryContainer = onContainer,
    secondary = secondary, onSecondary = onPrimary,
    background = background, onBackground = onSurface,
    surface = surface, onSurface = onSurface,
    surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
)

/** 選択可能な1配色。previewA/B/C は設定画面に並べる色見本（左から 主役・補助・地色）。 */
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

    // ① ミッドナイト：濃紺ネイビー × シアン（既定）
    private val MIDNIGHT = AppPalette(
        id = "midnight", label = "ミッドナイト", description = "濃紺 × シアン",
        previewA = Color(0xFF38BDF8), previewB = Color(0xFF22D3EE), previewC = Color(0xFF0A0F1C),
        darkScheme = darkSeed(
            primary = Color(0xFF38BDF8), container = Color(0xFF0F2A43), secondary = Color(0xFF22D3EE),
            background = Color(0xFF0A0F1C), surface = Color(0xFF111827), surfaceVariant = Color(0xFF1B2536),
        ).toDarkScheme(),
        lightScheme = lightSeed(
            primary = Color(0xFF1D4ED8), container = Color(0xFFDBEAFE), secondary = Color(0xFF0EA5E9),
            background = Color(0xFFF4F7FF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE5EDFB),
        ).toLightScheme(),
    )

    // ② グラファイト：チャコール × 琥珀(アンバー)
    private val GRAPHITE = AppPalette(
        id = "graphite", label = "グラファイト", description = "チャコール × 琥珀",
        previewA = Color(0xFFF5B301), previewB = Color(0xFFFBBF24), previewC = Color(0xFF161617),
        darkScheme = darkSeed(
            primary = Color(0xFFF5B301), container = Color(0xFF2A2A2A), secondary = Color(0xFFFBBF24),
            background = Color(0xFF0C0C0D), surface = Color(0xFF161617), surfaceVariant = Color(0xFF232325),
            onPrimary = Color(0xFF1A1505),
        ).toDarkScheme(),
        lightScheme = lightSeed(
            primary = Color(0xFFB7791F), container = Color(0xFFFBEFCE), secondary = Color(0xFF6B7280),
            background = Color(0xFFFAFAF9), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFECEBE8),
            onContainer = Color(0xFF7A4F12),
        ).toLightScheme(),
    )

    // ③ インディゴ：藍 × ラベンダー
    private val INDIGO = AppPalette(
        id = "indigo", label = "インディゴ", description = "藍 × ラベンダー",
        previewA = Color(0xFF818CF8), previewB = Color(0xFFA78BFA), previewC = Color(0xFF0B0B16),
        darkScheme = darkSeed(
            primary = Color(0xFF818CF8), container = Color(0xFF25254A), secondary = Color(0xFFA78BFA),
            background = Color(0xFF0B0B16), surface = Color(0xFF15152A), surfaceVariant = Color(0xFF20203A),
        ).toDarkScheme(),
        lightScheme = lightSeed(
            primary = Color(0xFF4F46E5), container = Color(0xFFE0E7FF), secondary = Color(0xFF7C3AED),
            background = Color(0xFFF6F6FE), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFECECFB),
        ).toLightScheme(),
    )

    // ④ ティール：青緑 × シアン
    private val TEAL = AppPalette(
        id = "teal", label = "ティール", description = "青緑 × シアン",
        previewA = Color(0xFF2DD4BF), previewB = Color(0xFF14B8A6), previewC = Color(0xFF08120F),
        darkScheme = darkSeed(
            primary = Color(0xFF2DD4BF), container = Color(0xFF0F2E2B), secondary = Color(0xFF14B8A6),
            background = Color(0xFF08120F), surface = Color(0xFF0F1B19), surfaceVariant = Color(0xFF182826),
        ).toDarkScheme(),
        lightScheme = lightSeed(
            primary = Color(0xFF0D9488), container = Color(0xFFCCFBF1), secondary = Color(0xFF0891B2),
            background = Color(0xFFF1FAF8), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE1F2EF),
        ).toLightScheme(),
    )

    // ⑤ クリムゾン：ワインレッド × ローズ
    private val CRIMSON = AppPalette(
        id = "crimson", label = "クリムゾン", description = "ワインレッド × ローズ",
        previewA = Color(0xFFFB7185), previewB = Color(0xFFF472B6), previewC = Color(0xFF140A0D),
        darkScheme = darkSeed(
            primary = Color(0xFFFB7185), container = Color(0xFF3A1620), secondary = Color(0xFFF472B6),
            background = Color(0xFF140A0D), surface = Color(0xFF1E1115), surfaceVariant = Color(0xFF2A1A1F),
        ).toDarkScheme(),
        lightScheme = lightSeed(
            primary = Color(0xFFBE123C), container = Color(0xFFFFE4E6), secondary = Color(0xFFDB2777),
            background = Color(0xFFFFF5F6), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF7E3E6),
        ).toLightScheme(),
    )

    // ⑥ モノクローム：無彩（白 × 黒）
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
            background = Color(0xFFFAFAFA), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFECECEC),
            onContainer = Color(0xFF111827),
        ).toLightScheme(),
    )

    // ===== 背景白ベースのカラフル配色（黒を使わず、白地に2色を効かせる） =====
    // これらは明暗トグルに関係なく「常に白背景」にしている（ライト/ダークどちらでも白地）。
    // 白地に映える非黒の2色（主役×補助）を組み合わせた、明るくカラフルな系統。

    // ⑦ コーラル：白 × サンゴ色 × ティール
    private val CORAL = run {
        val white = lightSeed(
            primary = Color(0xFFFB6F61), container = Color(0xFFFFE3DF), secondary = Color(0xFF0EA5A4),
            background = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFFFF1EF),
            onContainer = Color(0xFFB23A2E),
        ).toLightScheme()
        AppPalette(
            id = "coral", label = "コーラル", description = "白地 × サンゴ×ティール",
            previewA = Color(0xFFFB6F61), previewB = Color(0xFF0EA5A4), previewC = Color(0xFFFFFFFF),
            darkScheme = white, lightScheme = white,
        )
    }

    // ⑧ サクラ：白 × 桜ピンク × バイオレット
    private val SAKURA = run {
        val white = lightSeed(
            primary = Color(0xFFEC4899), container = Color(0xFFFCE7F3), secondary = Color(0xFF8B5CF6),
            background = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFFBEAF3),
            onContainer = Color(0xFF9D174D),
        ).toLightScheme()
        AppPalette(
            id = "sakura", label = "サクラ", description = "白地 × ピンク×紫",
            previewA = Color(0xFFEC4899), previewB = Color(0xFF8B5CF6), previewC = Color(0xFFFFFFFF),
            darkScheme = white, lightScheme = white,
        )
    }

    // ⑨ マンゴー：白 × オレンジ × マゼンタ
    private val MANGO = run {
        val white = lightSeed(
            primary = Color(0xFFF97316), container = Color(0xFFFFEDD5), secondary = Color(0xFFEC4899),
            background = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFFFF4E6),
            onContainer = Color(0xFF9A3412),
        ).toLightScheme()
        AppPalette(
            id = "mango", label = "マンゴー", description = "白地 × オレンジ×マゼンタ",
            previewA = Color(0xFFF97316), previewB = Color(0xFFEC4899), previewC = Color(0xFFFFFFFF),
            darkScheme = white, lightScheme = white,
        )
    }

    // ⑩ スカイ：白 × スカイブルー × コーラル
    private val SKY = run {
        val white = lightSeed(
            primary = Color(0xFF0EA5E9), container = Color(0xFFE0F2FE), secondary = Color(0xFFFB7185),
            background = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEFF8FF),
            onContainer = Color(0xFF075985),
        ).toLightScheme()
        AppPalette(
            id = "sky", label = "スカイ", description = "白地 × 青空×コーラル",
            previewA = Color(0xFF0EA5E9), previewB = Color(0xFFFB7185), previewC = Color(0xFFFFFFFF),
            darkScheme = white, lightScheme = white,
        )
    }

    /** 設定画面に並べる順。先頭(ミッドナイト)が既定。後半⑦〜⑩は白背景のカラフル系。 */
    val ALL: List<AppPalette> = listOf(
        MIDNIGHT, GRAPHITE, INDIGO, TEAL, CRIMSON, MONO,
        CORAL, SAKURA, MANGO, SKY,
    )

    /** id から配色を引く。不明な id は既定(先頭)にフォールバック。 */
    fun byId(id: String?): AppPalette = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
