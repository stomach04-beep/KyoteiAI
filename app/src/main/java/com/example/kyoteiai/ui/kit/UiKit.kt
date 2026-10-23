package com.example.kyoteiai.ui.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.example.kyoteiai.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 表示の決まり（全アプリ共通の UI 部品）— 2026-10-23 導入。原本は _shared\UiKit.kt.template。
 *
 * ⚠ アプリの中のコピーを直接直さない。直すのは原本で、そのあと各アプリへ配り直す。
 *
 * === なぜ要るか ===
 * 35本を点検したら、赤の意味がアプリごとに逆（上昇・強い買い・危険・期限切れ）、
 * 色を画面ごとに直書き（ダークで白い板が浮く）、桁区切りや日付の形がばらばら、だった。
 * 見た目の決まりを1か所に置き、各アプリはここを参照する（同じ意味の値を2か所に書かない）。
 *
 * === 決まり ===
 *  1. 色は3つの役割に分ける
 *     ・良し悪し … Tone（危険=赤／警戒=橙／注意=黄／情報=青／良好=緑／通常=灰）だけで表す
 *     ・上げ下げ … 色を付けず ▲▼ の記号で表す（Fmt.change）。赤は「危険」専用
 *     ・合図（買い・点灯）… テーマの primary 1色（ここでは定義しない）
 *  2. 文字は4段：主役の数字 36sp（1画面に1つ・HeroStat）／見出し 18sp／本文 14sp／補足 12sp
 *  3. 数字は桁区切り・右寄せ・等幅（tnum）。円は「12,345円」、空欄は「—」1種類（Fmt）
 *  4. 説明文・凡例はふだん畳む（InfoToggle）
 *  5. 見た目の骨格（2026-10-23 追加・おしゃれ案）：影と区切り線を使わず、面の明るさ1段と
 *     すき間で分ける（Panel）。角丸は12dp。見出しは背景と同じ色の1段（ScreenHeader）。
 *     数字と英字だけ Inter（NumFont）。投資の一覧には1色の小さな推移グラフ（Sparkline）
 */

// ═══════════════════════════════════════════════════════════════
// 1. 色の意味（良し悪しだけ。上げ下げ・合図には使わない）
// ═══════════════════════════════════════════════════════════════

/** 状態の「良し悪し」の段階。上から重い順 */
enum class Tone { CRIT, WARN, CAUTION, INFO, OK, NORMAL }

object Sem {
    // 明るい地の上で読める文字色（白地で 6.2 以上・札の地の上でも 4.9 以上。2026-10-23 計算）
    private val LIGHT = mapOf(
        Tone.CRIT to Color(0xFFB71C1C),
        Tone.WARN to Color(0xFFA8380A),
        Tone.CAUTION to Color(0xFF755600),
        Tone.INFO to Color(0xFF18609C),
        Tone.OK to Color(0xFF256628),
        Tone.NORMAL to Color(0xFF5A616B),
    )

    // 暗い地の上で読める文字色（#1E1E1E で 6.5 以上・札の地の上でも 4.7 以上。2026-10-23 計算）
    private val DARK = mapOf(
        Tone.CRIT to Color(0xFFFF7A72),
        Tone.WARN to Color(0xFFFF9A5C),
        Tone.CAUTION to Color(0xFFE8C35A),
        Tone.INFO to Color(0xFF6FB3F0),
        Tone.OK to Color(0xFF7FCF86),
        Tone.NORMAL to Color(0xFFA1A8B0),
    )

    /** いまのテーマが暗いか（アプリ内のライト/ダーク切替に追従するため背景の明るさで判定） */
    @Composable
    fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    /** 文字・点・線に使う色 */
    @Composable
    fun fg(tone: Tone): Color = (if (isDark()) DARK else LIGHT).getValue(tone)

    /** 札やカードの地の色（文字色を薄く敷いたもの。ダークでも白い板にならない） */
    @Composable
    fun bg(tone: Tone): Color =
        fg(tone).copy(alpha = if (isDark()) 0.20f else 0.10f)
            .compositeOver(MaterialTheme.colorScheme.surface)
}

// ═══════════════════════════════════════════════════════════════
// 2. 数字・日時・空欄の書き方
// ═══════════════════════════════════════════════════════════════

object Fmt {
    /** 値が無いときの記号（1種類だけ。単位を抱えた「--%」は作らない） */
    const val EMPTY = "—"

    // 数字の書き方は日本の慣習（, 区切り・. 小数点）に固定する
    private val LOCALE: Locale = Locale.JAPAN
    private const val MINUS = "−"   // 全角幅のマイナス（+ と同じ幅に見える）

    /** 桁区切りつきの数字（例: 38,412.55） */
    fun num(value: Double, decimals: Int = 0): String =
        if (value.isNaN() || value.isInfinite()) EMPTY
        else String.format(LOCALE, "%,.${decimals}f", value).replace("-", MINUS)

    /** 符号つきの数字（例: +215.30 / −12.40 / 0.00） */
    fun signed(value: Double, decimals: Int = 0): String = when {
        value.isNaN() -> EMPTY
        value > 0 -> "+" + num(value, decimals)
        else -> num(value, decimals)
    }

    /** 値＋単位。円・% は詰め、それ以外は半角空白を挟む（例: 151.82円 / 4.33% / 75.30 USD/bbl） */
    fun withUnit(value: Double, decimals: Int, unit: String): String {
        if (value.isNaN()) return EMPTY
        val n = num(value, decimals)
        return when {
            unit.isEmpty() -> n
            unit == "円" || unit == "%" || unit == "倍" -> n + unit
            else -> "$n $unit"
        }
    }

    /** 円（例: 12,345円） */
    fun yen(value: Double): String = withUnit(value, 0, "円")

    /** 符号つきの円（例: +12,345円 / −800円） */
    fun signedYen(value: Double): String = if (value.isNaN()) EMPTY else signed(value, 0) + "円"

    /** 符号つきの%（例: +0.56%） */
    fun pct(value: Double, decimals: Int = 2): String = if (value.isNaN()) EMPTY else signed(value, decimals) + "%"

    /** 上げ下げ（色は付けず記号で示す。例: ▲215.30 / ▼12.40 / 変わらず） */
    fun change(delta: Double, decimals: Int): String = when {
        delta.isNaN() -> EMPTY
        delta > 0 -> "▲" + num(delta, decimals)
        delta < 0 -> "▼" + num(abs(delta), decimals)
        else -> "変わらず"
    }

    private val HM = DateTimeFormatter.ofPattern("HH:mm", LOCALE)
    private val MDE_HM = DateTimeFormatter.ofPattern("M/d(E) HH:mm", LOCALE)
    private val MDE = DateTimeFormatter.ofPattern("M/d(E)", LOCALE)
    private val YMD = DateTimeFormatter.ofPattern("yyyy/M/d", LOCALE)

    /** 日時は今との距離で書き分ける：今日 07:12／今年 10/22(水) 07:12／それ以前 2025/3/4 */
    fun dateTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0L) return EMPTY
        val zone = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return when {
            t.toLocalDate() == today -> t.format(HM)
            t.year == today.year -> t.format(MDE_HM)
            else -> t.format(YMD)
        }
    }

    /** 日付だけ：今年 10/22(水)／それ以前 2025/3/4 */
    fun date(d: LocalDate, today: LocalDate = LocalDate.now()): String =
        if (d.year == today.year) d.format(MDE) else d.format(YMD)

    /** 経過時間（例: たった今 / 12分前 / 3時間前 / 2日前） */
    fun ago(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0L) return EMPTY
        val min = (nowMs - epochMs) / 60_000L
        return when {
            min < 1 -> "たった今"
            min < 60 -> "${min}分前"
            min < 60 * 24 -> "${min / 60}時間前"
            else -> "${min / (60 * 24)}日前"
        }
    }
}

/** 数字の幅をそろえる（縦に並べたとき桁がずれない） */
fun TextStyle.tnum(): TextStyle = merge(TextStyle(fontFeatureSettings = "tnum"))

/**
 * 数字と英字だけに使う書体 Inter（res/font に同梱・約0.3MB×2。日本語は端末の書体のまま）。
 * 日本語の書体は1種類で数MBあるので入れない。原本は _shared\fonts（SIL OFL 1.1）。
 */
val NumFont = FontFamily(
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** 数字用の書体＋桁の幅をそろえる（主役の数字・一覧の値に使う） */
fun TextStyle.num(): TextStyle = merge(TextStyle(fontFamily = NumFont, fontFeatureSettings = "tnum"))

// ═══════════════════════════════════════════════════════════════
// 3. 部品：主役の数字・状態の札・畳める説明
// ═══════════════════════════════════════════════════════════════

/**
 * 主役の数字（1画面に1つだけ）。
 *  ・label … 何の数字か（14sp）
 *  ・value … 数字そのもの（36sp・太字・等幅）。単位は unit に分けると数字だけ大きくなる
 *  ・sub   … 補足（12sp。更新時刻・内訳など）
 *  ・tone  … 良し悪しを数字の色で示したいときだけ指定（通常は文字色のまま）
 */
@Composable
fun HeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    sub: String? = null,
    tone: Tone? = null,
) {
    val valueColor = tone?.let { Sem.fg(it) } ?: MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 左：ラベルと補足（長くても数字を押しつぶさないよう weight を付ける）
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
        // 右：数字（右寄せ・1行。単位は小さく添える）
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                style = TextStyle.Default.num(),
                maxLines = 1,
                softWrap = false,
            )
            if (!unit.isNullOrBlank()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** 状態の札（例: 「危険 1」）。地は薄い色、文字は濃い色で、明暗どちらでも読める */
@Composable
fun ToneChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.tnum(),
        fontWeight = FontWeight.Bold,
        color = Sem.fg(tone),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Sem.bg(tone))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * 畳める説明（凡例・使い方・検証の根拠など、ふだん見なくてよい文章用）。
 * 見出しの行全体が押せる（高さ48dp以上）。開閉の状態は画面の回転でも保つ。
 */
@Composable
fun InfoToggle(
    title: String,
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { open = !open },
        ) {
            Text(
                text = (if (open) "▾ " else "ⓘ ") + title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = open) { content() }
    }
}

// ═══════════════════════════════════════════════════════════════
// 4. 見た目の骨格（面のカード・見出し1段・小さな推移グラフ）
// ═══════════════════════════════════════════════════════════════

/** 角丸の大きさ（全アプリ共通。札だけは丸く RoundedCornerShape(50)） */
val PanelShape = RoundedCornerShape(12.dp)

/**
 * 面のカード。影も枠線も付けず、背景より1段明るい（ダークでは1段明るい／ライトでは白い）面で区切る。
 * 一覧はこれを 6〜8dp のすき間で縦に並べる（行と行の間に区切り線を引かない）。
 *  ・emphasized … 主役の数字など1画面に1つだけ目立たせる面（もう1段明るい surfaceVariant）
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val color = if (emphasized) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = PanelShape, color = color, content = content)
    } else {
        Surface(modifier = modifier, shape = PanelShape, color = color, content = content)
    }
}

/**
 * 画面の見出し（背景と同じ色の1段）。色の帯と TopAppBar を重ねる作りをやめるための部品。
 *  ・title … アプリ名や画面名（20sp・太字）
 *  ・sub   … 更新時刻など（12sp・補足色）
 *  ・actions … 右端のボタン（更新など。アイコンボタンを置く）
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/**
 * 小さな推移グラフ（48×16dp・線1本・1色）。軸も目盛りも描かない。
 * 上げ下げで色を変えない（▲▼に色を付けない決まりと同じ）。点が2つ未満なら何も描かない。
 *  ・values … 古い順の値
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    height: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val pts = values.filter { !it.isNaN() && !it.isInfinite() }
    if (pts.size < 2) return
    Canvas(modifier = modifier.size(width, height)) {
        val min = pts.min()
        val max = pts.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stroke = 1.5.dp.toPx()
        // 線の太さの半分だけ内側に描いて、上端・下端が切れないようにする
        val top = stroke / 2
        val h = size.height - stroke
        val path = Path()
        pts.forEachIndexed { i, v ->
            val x = size.width * i / (pts.size - 1)
            val y = top + h * (1 - ((v - min) / span)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // 最新の点だけ小さな丸で示す
        val lastY = top + h * (1 - ((pts.last() - min) / span)).toFloat()
        drawCircle(color, radius = stroke * 1.2f, center = Offset(size.width, lastY))
    }
}
