package com.example.kyoteiai.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 2026-11-11 共通部品の試し版（APP_RULES F4：1本で試して実機で見比べ、承認されたら原本 UiKit.kt.template へ）。
 * 配られた UiKit.kt は書き換えない。承認されたら原本に入れて配り直し、このファイルは消す。
 *
 *  ① 主役の数字が空（Fmt.EMPTY「—」）のとき、36sp の太字で描くと知らせより目立っていた
 *     → 小さく・細く・控えめな色で描く（行の高さは数字のときと同じにして、面の高さを揺らさない）
 *  ② 知らせ（StatusNotice）の「再試行」を、画面下の知らせ（AppSnackbarHost・H6）と同じ「太字＋枠線」にそろえる
 */

/** 見本の画面で「今の形」と見比べるための切り替え（true で原本の部品のまま描く）。本物の画面は既定の false */
val LocalKitTrialOff = staticCompositionLocalOf { false }

/** ① 主役の数字の試し版。空のときだけ描き方を変え、それ以外は原本の HeroStat のまま */
@Composable
fun HeroStatTrial(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    sub: String? = null,
    tone: Tone? = null,
) {
    if (LocalKitTrialOff.current || value != Fmt.EMPTY) {
        HeroStat(label = label, value = value, modifier = modifier, unit = unit, sub = sub, tone = tone)
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 左：ラベルと補足（原本と同じ）
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 右：空の印「—」。36sp の太字をやめ 24sp・普通の太さ・補足と同じ控えめな色にする。
        // 行の高さ（40sp）は数字のときと同じ＝値が入っても面の高さが変わらない
        Text(
            text = value,
            fontSize = 24.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            // 読み上げでは「ダッシュ」でなく意味を言う
            modifier = Modifier.semantics { contentDescription = "なし" },
        )
    }
}

/** ② 知らせの試し版。「再試行」だけを画面下の知らせ（AppSnackbarHost）と同じ太字＋枠線の丸いボタンにする */
@Composable
fun StatusNoticeTrial(
    title: String,
    detail: String,
    tone: Tone = Tone.CAUTION,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (LocalKitTrialOff.current) {
        StatusNotice(title, detail, tone, actionLabel, onAction, modifier)
        return
    }
    val fg = Sem.fg(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = toneCardColor(tone),
        border = BorderStroke(PanelBorderWidth, fg.copy(alpha = 0.45f)),
    ) {
        Row(
            // 右の余白を 4dp→8dp（枠の付いたボタンが面の枠に貼り付いて見えないように）。
            // 12dp にすると「⚠ 予想を取得できませんでした」（14字）が2行に折れた（2026-11-11 実機）＝増やす幅は4dpまで
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (tone == Tone.INFO || tone == Tone.OK || tone == Tone.NORMAL) title else "⚠ $title",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (actionLabel != null && onAction != null) {
                // 見た目は高さ32dpの丸い枠（画面下の知らせと同じ形・枠は段階の色60%）。
                // 押せる範囲は Material の最小48dpが自動で確保される（APP_RULES C5）
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.height(32.dp),
                    shape = CircleShape,
                    border = BorderStroke(PanelBorderWidth, fg.copy(alpha = 0.60f)),
                    colors = ButtonDefaults.textButtonColors(contentColor = fg),
                    // 左右の内側の余白は原本の TextButton と同じ12dp（ボタンの幅を今より広げない＝題名を折らない）
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
