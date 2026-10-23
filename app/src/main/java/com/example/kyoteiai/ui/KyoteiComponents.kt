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
import com.example.kyoteiai.ui.kit.num

// ───────────────────────────────────────────────────────────────
// 競艇UIの共通部品（確信度バッジ・艇色・確率バーなど）
// ───────────────────────────────────────────────────────────────

/** 本命勝率(0.0〜1.0)から確信度バッジの記号を返す（◎/○/なし） */
fun confidenceMark(topProb: Double): String = when {
    topProb >= 0.70 -> "◎"
    topProb >= 0.55 -> "○"
    else -> ""
}

/**
 * 確信度バッジ。例「◎ 72%」／「○ 58%」／「40%」。
 * 2026-10-23 表示の決まり UiKit：本命の強弱は primary 1色の濃淡で示す
 * （以前の緑・青・灰の直書きは、C'バッジの深緑と見分けがつかなかった）。
 *   ◎ → primary の塗り
 *   ○ → 文字だけ＋primary の細枠（狙い目カードの primaryContainer 地に埋もれないよう塗らない）
 *   印なし → 補足色の文字だけ
 */
@Composable
fun ConfidenceBadge(topProb: Double) {
    val pct = (topProb * 100).toInt()
    val mark = confidenceMark(topProb)
    val label = if (mark.isEmpty()) "${pct}%" else "$mark ${pct}%"
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val (bg, fg, borderColor) = when (mark) {
        "◎" -> Triple(scheme.primary, scheme.onPrimary, null)
        "○" -> Triple(Color.Transparent, scheme.primary, scheme.primary)
        else -> Triple(Color.Transparent, scheme.onSurfaceVariant, null)
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.num(),
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1
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
            // 枠線はテーマの色（2026-10-23 UiKit。艇色そのものは競艇の決まりなので変えない）
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
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
 * 勝率を表す横棒バー（0.0〜1.0）。艇色で塗り、下地はテーマの surfaceVariant。
 * 2026-10-23 UiKit：固定の薄いグレー(E0E0E0)だとダークで白い板が浮き、
 * ライトでは1号艇の白いバーと見分けにくかったためテーマ色にした。
 */
@Composable
fun ProbabilityBar(prob: Double, lane: Int, modifier: Modifier = Modifier) {
    val fraction = prob.coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(5.dp)
                )
        )
    }
}
