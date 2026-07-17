package com.example.kyoteiai.ui.theme

import androidx.compose.ui.graphics.Color

// ───── ダークモード用の色（BargainChecker 統一デザイン：緑アクセント） ─────
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1E1E1E)

// アクセント色（旧シアン #00D4FF → 緑 #4CAF50 系へ統一）
val AccentGreen = Color(0xFF4CAF50)
val AccentGreenDark = Color(0xFF388E3C)
val AccentGreenContainer = Color(0xFF242424)

val TextPrimary = Color(0xFFE0E0E0)
val TextSecondary = Color(0xFF9E9E9E)

// ───── ライトモード用の色（白背景・濃い緑アクセント） ─────
val LightBackground = Color(0xFFF7F9F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE8F1E9)

val LightPrimary = AccentGreenDark
val LightPrimaryContainer = Color(0xFFC8E6C9)

val LightTextPrimary = Color(0xFF101828)
val LightTextSecondary = Color(0xFF566174)

// ───── 状態色システム（重要度: 通常→注意→警戒→情報→最重要） ─────
// 灰 → 黄 → 橙 → 青 → 赤。残量や充電状態の重要度を色で伝えるために使う。
val StatusNormal = Color(0xFF9E9E9E)    // 通常（情報なし・問題なし）
val StatusCaution = Color(0xFFFFC107)   // 注意（やや低下）
val StatusWarning = Color(0xFFFF9800)   // 警戒（低下）
val StatusInfo = Color(0xFF2196F3)      // 情報（充電中など）
val StatusCritical = Color(0xFFF44336)  // 最重要（残量わずか）
