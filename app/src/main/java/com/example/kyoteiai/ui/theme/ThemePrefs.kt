package com.example.kyoteiai.ui.theme

import android.content.Context
import android.content.SharedPreferences

/**
 * テーマモード（ライト/ダーク/システム）の永続化。
 * 既存の Prefs と干渉しないよう独立した SharedPreferences ファイルを使う。
 */
object ThemePrefs {
    private const val FILE = "theme_prefs"
    const val KEY = "theme_mode"
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val DEFAULT = MODE_DARK

    // 配色（カラーパレット）。値は AppPalettes の id
    const val KEY_PALETTE = "theme_palette"
    const val DEFAULT_PALETTE = "midnight"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(ctx: Context): String = prefs(ctx).getString(KEY, DEFAULT) ?: DEFAULT
    fun set(ctx: Context, mode: String) {
        prefs(ctx).edit().putString(KEY, mode).apply()
    }

    // 配色の取得／保存
    fun getPalette(ctx: Context): String =
        prefs(ctx).getString(KEY_PALETTE, DEFAULT_PALETTE) ?: DEFAULT_PALETTE
    fun setPalette(ctx: Context, paletteId: String) {
        prefs(ctx).edit().putString(KEY_PALETTE, paletteId).apply()
    }
}
