package com.example.kyoteiai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ───────────────────────────────────────────────────────────────
// 競艇UIの共通部品（確信度バッジ・艇色・確率バーなど）
// ───────────────────────────────────────────────────────────────

// 確信度バッジの色（本命勝率で3段階）
//   0.70以上 → 緑（◎ 本命鉄板）
//   0.55以上 → 青（○ やや堅い）
//   それ未満 → 灰（印なし）
private val BadgeGreen = Color(0xFF2E7D32)
private val BadgeBlue = Color(0xFF1565C0)
private val BadgeGray = Color(0xFF757575)

/** 本命勝率(0.0〜1.0)から確信度バッジの記号を返す（◎/○/なし） */
fun confidenceMark(topProb: Double): String = when {
    topProb >= 0.70 -> "◎"
    topProb >= 0.55 -> "○"
    else -> ""
}

/** 本命勝率からバッジ背景色を返す */
fun confidenceColor(topProb: Double): Color = when {
    topProb >= 0.70 -> BadgeGreen
    topProb >= 0.55 -> BadgeBlue
    else -> BadgeGray
}

/**
 * 確信度バッジ。例「◎ 72%」（緑）／「○ 58%」（青）／「40%」（灰）。
 */
@Composable
fun ConfidenceBadge(topProb: Double) {
    val pct = (topProb * 100).toInt()
    val mark = confidenceMark(topProb)
    val label = if (mark.isEmpty()) "${pct}%" else "$mark ${pct}%"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(confidenceColor(topProb))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// 艇番ごとの艇色（競艇の公式カラー）
//   1=白, 2=黒, 3=赤, 4=青, 5=黄, 6=緑
private val LaneColors = mapOf(
    1 to Color(0xFFFFFFFF),
    2 to Color(0xFF000000),
    3 to Color(0xFFE53935),
    4 to Color(0xFF1E88E5),
    5 to Color(0xFFFDD835),
    6 to Color(0xFF43A047)
)

/** 艇番から艇色を返す（未知の番号は灰） */
fun laneColor(lane: Int): Color = LaneColors[lane] ?: Color(0xFF9E9E9E)

/**
 * 艇番を表す色付きの四角（数字入り）。
 * 白・黄など明るい色のときは文字を黒、それ以外は白で読みやすくする。
 */
@Composable
fun LaneSquare(lane: Int) {
    val bg = laneColor(lane)
    // 白(1)・黄(5)は明るいので文字色を黒に
    val textColor = if (lane == 1 || lane == 5) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = lane.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * 勝率を表す横棒バー（0.0〜1.0）。艇色で塗り、背景は薄いグレー。
 */
@Composable
fun ProbabilityBar(prob: Double, lane: Int, modifier: Modifier = Modifier) {
    val fraction = prob.coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFFE0E0E0))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(laneColor(lane))
                .border(
                    // 白艇は背景に埋もれるので枠線を付ける
                    if (lane == 1) 1.dp else 0.dp,
                    Color(0xFFBDBDBD),
                    RoundedCornerShape(5.dp)
                )
        )
    }
}
