package com.example.kyoteiai.ui.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.isSpecified
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 *     ・上げ下げ … ▲▼ の記号（Fmt.change）＋色。＋＝赤、−＝青（日本の株価の板と同じ向き）。
 *       色は Sem.sign(値) だけで塗る（アプリごとに色の関数を作らない。2026-10-25 利用者が確定。
 *       正本 _shared\APP_RULES.md の B3）。危険の赤と見分けるため、危険は札や⚠の印＋文字で出す
 *     ・合図（買い・点灯）… テーマの primary 1色（ここでは定義しない）
 *  2. 文字は4段：主役の数字 36sp（1画面に1つ・HeroStat）／見出し 18sp／本文 14sp／補足 12sp
 *  3. 数字は桁区切り・右寄せ・等幅（tnum）。円は「12,345円」、空欄は「—」1種類（Fmt）
 *  4. 説明文・凡例はふだん畳む（InfoToggle）
 *  5. 見た目の骨格（2026-10-23 追加・おしゃれ案）：影と区切り線を使わず、面の明るさ1段と
 *     すき間で分ける（Panel）。角丸は12dp。見出しは背景と同じ色の1段（ScreenHeader）。
 *     例外：見出し（とすぐ下のタブの列）と中身の境目には1本の線を引く（HeaderDivider・2026-10-24 ユーザー指示）
 *     例外2：Panel には細い枠線（1dp・補足色45%）を引く（2026-10-28。面の明るさの差だけでは境目が見えなかった）
 *     数字と英字だけ Inter（NumFont）。投資の一覧には1色の小さな推移グラフ（Sparkline）
 */

// ═══════════════════════════════════════════════════════════════
// 1. 色の意味（良し悪し。前からある損益・前日比の色分けを残すときもここの色を使う）
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

    /**
     * 損益・前日比・増減の色（＋＝赤、−＝青、0・値なし＝補足色）。2026-10-25 APP_RULES.md B3。
     * 赤・青は Tone.CRIT／Tone.INFO と同じ色（明暗それぞれで読める濃さを計算済みのもの）を使う。
     * 表示の桁で丸めて 0 になる値は、呼ぶ側で丸めてから渡す（「+0.0%」が赤くならないように）
     */
    @Composable
    fun sign(value: Double?): Color = when (val t = signTone(value)) {
        Tone.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> fg(t)
    }

    /**
     * ＋−の向きを Tone で返す（札 ToneChip や Sem.bg で地を塗るとき用。＋＝CRIT（赤）、−＝INFO（青）、0・値なし＝NORMAL）。
     * 向きを決める所はここ1か所だけ（sign・signArgb もここを呼ぶ）
     */
    fun signTone(value: Double?): Tone = when {
        value == null || value.isNaN() || value == 0.0 -> Tone.NORMAL
        value > 0 -> Tone.CRIT
        else -> Tone.INFO
    }

    /**
     * ウィジェット（RemoteViews）用の ＋−の色（Composable の外で使える ARGB の Int）。
     * @param dark ウィジェットの地が暗いか
     */
    fun signArgb(value: Double?, dark: Boolean, decimals: Int? = null): Int =
        argb(signTone(if (decimals == null) value else roundShown(value, decimals)), dark)

    /** 表示の桁（小数 decimals 桁）で丸めてから色を決める版。「▲0.00」「+0.0%」を赤や青にしない */
    @Composable
    fun sign(value: Double?, decimals: Int): Color = sign(roundShown(value, decimals))

    /** 表示の桁で丸めた値（四捨五入。String.format と同じ丸め方） */
    fun roundShown(value: Double?, decimals: Int): Double? =
        if (value == null || value.isNaN() || value.isInfinite()) value
        else java.math.BigDecimal(value).setScale(decimals, java.math.RoundingMode.HALF_UP).toDouble()

    /** Tone の色を ARGB の Int で返す（ウィジェットなど Composable の外で使う） */
    fun argb(tone: Tone, dark: Boolean): Int = (if (dark) DARK else LIGHT).getValue(tone).toArgb()

    /** 整数版（円・件数の増減） */
    @Composable
    fun sign(value: Long?): Color = sign(value?.toDouble())

    /** 整数版（Int。Kotlin は Int を Long? に自動では渡せないので別に置く） */
    @Composable
    fun sign(value: Int?): Color = sign(value?.toDouble())

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

    /** 上げ下げを記号つきの文字にする（例: ▲215.30 / ▼12.40 / 変わらず）。色は呼び出し側が決める（前からの色分けは残す） */
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

/** Panel の枠線の太さ（2026-10-28 追加。全アプリ共通。太さを変えるときはここ1か所だけ直して配り直す） */
val PanelBorderWidth = 1.dp

/**
 * Panel の枠線の色。補足の文字色を45%で敷いたもの（HeaderDivider と同じ濃さ）。
 * 2026-10-28 追加：背景と面の明るさの差は配色によって約1.1倍しかなく、生活タスクボードで
 * 「各タスクの枠と境目がわかりにくい」と指摘されたため。primary（合図の色）や Tone の色は枠に使わない
 */
@Composable
fun panelBorderColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

/**
 * 面のカード。影は付けず、背景より1段明るい（ダークでは1段明るい／ライトでは白い）面と細い枠線で区切る。
 * 一覧はこれを 6〜8dp のすき間で縦に並べる（行と行の間に区切り線を引かない）。
 *  ・emphasized … 主役の数字など1画面に1つだけ目立たせる面（もう1段明るい surfaceVariant）
 *  ・bordered … 枠線を引くか（既定：ふつうの面は引く／主役の面は面の明るさで分かるので引かない）。
 *    Panel の中に Panel を入れるときや、ダイアログ・下から出る板の中では内側を bordered = false にする
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    bordered: Boolean = !emphasized,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val color = if (emphasized) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val border = if (bordered) BorderStroke(PanelBorderWidth, panelBorderColor()) else null
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = PanelShape, color = color, border = border, content = content)
    } else {
        Surface(modifier = modifier, shape = PanelShape, color = color, border = border, content = content)
    }
}

/**
 * 画面の見出し（背景と同じ色の1段＋下に1本の線）。色の帯と TopAppBar を重ねる作りをやめるための部品。
 * 2026-10-24 ユーザー指示「アプリ名など上の部分とそれ以降の境目がわかるように」で、下に区切りの線を足した
 * （影と区切り線をやめた決まりの例外。見出しの色を変える案は、配色によって背景との差が出ず、
 *  ステータスバーの下まで色を伸ばすにはアプリごとの直しが要るので採らなかった・Fable 相談）。
 *  ・title … アプリ名や画面名（20sp・太字）
 *  ・sub   … 更新時刻など（12sp・補足色）
 *  ・actions … 右端のボタン（更新など。アイコンボタンを置く）
 *  ・divider … 下の線。すぐ下にタブの列があるときは false にし、タブの下の線を境目にする
 *  ・onBack … 設定画面など1段深い画面の「戻る」矢印（2026-10-25 APP_RULES.md A5。null なら出さない）。
 *            端末の戻る操作は呼ぶ側の BackHandler で受ける
 *  ・leading … 名前の左に置くもの（2026-11-01 追加。アプリアイコンは HeaderAppIcon で渡す。
 *             UiKit からはアプリの R.mipmap を引けないので、呼ぶ側が中身を渡す）
 *  ・transparent … true で自分の背景を塗らない（HeaderBackdrop の絵の上に置くとき。2026-11-01 追加）
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    divider: Boolean = true,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    transparent: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bg = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background
    Column(modifier = modifier.fillMaxWidth().background(bg)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            // 矢印は線で描く（Material Icons を入れていないアプリが5本あり、UiKit から Icons を使うと配った先でビルドが落ちるため）
            val arrowColor = MaterialTheme.colorScheme.onBackground
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "戻る" }) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val u = size.width / 24f
                    val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawLine(arrowColor, Offset(19f * u, 12f * u), Offset(5f * u, 12f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
                    val head = Path().apply {
                        moveTo(11f * u, 6f * u); lineTo(5f * u, 12f * u); lineTo(11f * u, 18f * u)
                    }
                    drawPath(head, arrowColor, style = stroke)
                }
            }
        }
        if (leading != null) {
            // アプリアイコンなど。名前との間は12dp
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            // 名前が1行に収まらないときは 20sp から 1sp ずつ縮める（HeaderTitleMinSp まで。それでも長ければ…で切る）。
            // 2026-11-01：左にアプリアイコンを置くと「AutoTrader Vesper」が切れたため。
            // 右のボタンが3つある AppManager は 16sp でも「アプリマネージ…」と切れたので、下限を 13sp に下げた（ユーザー指示「名前が消えるのを防いで」）
            var titleSize by remember(title) { mutableStateOf(20f) }
            Text(
                text = title,
                fontSize = titleSize.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { r ->
                    if ((r.hasVisualOverflow || r.isLineEllipsized(0)) && titleSize > HeaderTitleMinSp) titleSize -= 1f
                },
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
        if (transparent) {
            // 絵の上に置くときは、右端のボタン（歯車・更新など）の後ろに背景色をぼかして敷き、絵を沈める
            // （2026-11-01 ユーザー指示「歯車ボタンなどもかぶっていたら調整」）
            val scrim = MaterialTheme.colorScheme.background
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.drawBehind {
                    val r = size.maxDimension * 0.85f
                    drawCircle(
                        Brush.radialGradient(listOf(scrim.copy(alpha = 0.88f), scrim.copy(alpha = 0.6f), Color.Transparent), center = center, radius = r),
                        radius = r,
                        center = center,
                    )
                },
            ) { actions() }
        } else {
            actions()
        }
    }
    if (divider) HeaderDivider()
    }
}

/**
 * 見出しと中身の境目の線（1dp・横いっぱい）。ScreenHeader の下に自動で入る。
 * TopAppBar の画面やタブの列の下など、ScreenHeader を使わない所でも同じ線を引くときに呼ぶ
 * （タブの列は TabRow(divider = { HeaderDivider() }) で）。
 * 色は補足の文字色を45%で敷いたもの。outlineVariant は配色ごとに濃さが違い、実機で「控えめすぎる」線だった
 */
@Composable
fun HeaderDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
}

/**
 * HeaderBackdrop の絵の上に置くタブの列に敷く、背景色の濃さ（2026-11-01）。
 * 自前の TabRow を使うアプリも containerColor = background.copy(alpha = HeaderTabScrimAlpha) でこの値を使う。
 * 0.72 では重なった絵がほとんど見えなかったので、ユーザー指示「かかっている部分をもう少し見えるように」で 0.5、
 * さらに「もっと透明度をあげて」で 0.3 にした（2026-11-01）
 */
const val HeaderTabScrimAlpha = 0.3f

/** 見出しの名前を縮めるときの下限（sp）。決まり C1「12sp 未満は使わない」の内側で 13sp（2026-11-01） */
const val HeaderTitleMinSp = 13f

/** HeaderBackdrop の中がこの高さを超えたら「下にタブの列がある」とみなす（見出しの段だけなら約60〜80dp）。2026-11-01 */
val HeaderBackdropTabsMinHeight = 96.dp

/**
 * タブがあるとき、絵の下端を上に詰める量（2026-11-01）。
 * タブの列の高さ（48dp）ぶん詰めると上に寄りすぎたので、ユーザー指示で半分（24dp）にした＝絵はタブの列の上半分まで下がる
 */
val HeaderBackdropTabsLift = 24.dp

/** 見出しの右端のボタン（歯車1つ＋余白）の幅。タブがあるとき、縮めた絵をこのぶん手前に寄せる。2026-11-01 */
val HeaderBackdropActionsWidth = 56.dp

/**
 * 見出しとタブの列の後ろに、アプリごとの絵（Gemini で描いた横長の背景）を敷く入れ物（2026-11-01 追加・案3-3）。
 * 中に ScreenHeader(transparent = true) と ScreenTabs(transparent = true) を縦に並べて使う。
 *  ・art … 背景の絵（res/drawable-nodpi/header_backdrop.png など）。null なら背景色だけ
 *  ・artOffsetY … 絵を上下にずらす量（＋で下、−で上）。絵の主役の位置がアプリごとに違うときの微調整（2026-11-01 追加）
 *  ・artEndPadding … 絵を右端からこの幅だけ手前（左）に寄せる。右上のボタンが多くて主役がかぶるアプリ用（null＝既定）
 *  ・artLift … タブがあるとき絵の下端を上に詰める量（null＝既定 HeaderBackdropTabsLift）。0 にすると絵がタブの下端まで来て大きくなる
 *  ・artAlpha … 絵全体の濃さ（1＝そのまま、小さいほど透ける）。アプリごとに絵を薄くしたいとき用（2026-11-02 追加）
 * 絵は右に寄せ、文字のある左側と下の境目に向かって背景色へ溶かす（文字が読めるように・境目の線は残る）。
 * 絵は暗い配色向けに描いてあるので、明るい配色のときはそのまま敷かず（明るい地に暗い絵はにじみに見える）、
 * 絵の明るさを濃さに変えて primary 1色で塗った「透かし模様」にする（暗い地は透明・光る線や形だけ残る）。
 * 2026-11-01 ユーザーが案L2（メインの色1色）を選択。primary は合図の色でもあるが、透かしは薄い飾りで文字や札には使わない
 */
@Composable
fun HeaderBackdrop(
    art: Painter?,
    modifier: Modifier = Modifier,
    artOffsetY: Dp = 0.dp,
    artEndPadding: Dp? = null,
    artLift: Dp? = null,
    artAlpha: Float = 0.85f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    // 明るい配色のときだけ、絵を primary 1色の透かし模様に変える色の変換。
    // 透明度＝(明るさ − 35) × 1.7（0〜255 で頭打ち）、色＝primary。暗い地は透明になり、光る線や形だけが残る
    val primary = MaterialTheme.colorScheme.primary
    val lightFilter = if (bg.luminance() >= 0.5f) {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, primary.red * 255f,
                    0f, 0f, 0f, 0f, primary.green * 255f,
                    0f, 0f, 0f, 0f, primary.blue * 255f,
                    0.51f, 1.003f, 0.187f, 0f, -59.5f,
                )
            )
        )
    } else {
        null
    }
    Box(modifier = modifier.fillMaxWidth().background(bg)) {
        if (art != null) {
            // ずらした絵が見出しの外（状態バー側）へはみ出さないよう、入れ物の範囲で切る
            BoxWithConstraints(Modifier.matchParentSize().clipToBounds()) {
                // 中の高さが HeaderBackdropTabsMinHeight を超えるときは、見出しの下にタブの列がある。
                // そのときは絵の下端を HeaderBackdropTabsLift だけ上に詰め、高さに合わせて縮めて右に寄せる
                // （2026-11-01 ユーザー指示「タブと大きく被っているものは小さくするか上に調整」。例 NotiSweeper。
                //  タブの高さぶん詰めたら上に行きすぎたので、修正前との中間＝半分にした）
                val withTabs = maxHeight > HeaderBackdropTabsMinHeight
                val artHeight = if (withTabs) maxHeight - (artLift ?: HeaderBackdropTabsLift) else maxHeight
                // 右端から手前へ寄せる幅。指定があればそれ、無ければタブがあるときだけ歯車の幅ぶん
                val endPad = artEndPadding ?: if (withTabs) HeaderBackdropActionsWidth else 0.dp
                Image(
                    painter = art,
                    contentDescription = null,
                    contentScale = if (withTabs) ContentScale.FillHeight else ContentScale.Crop,
                    alignment = Alignment.CenterEnd,
                    colorFilter = lightFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artHeight)
                        .offset(y = artOffsetY)
                        // 縮めた絵は右端の歯車の真後ろに隠れるので、歯車の幅ぶん手前（左）へ寄せる
                        .padding(end = endPad)
                        // 溶かす処理（DstIn）は、絵だけを別の層に描いてから効かせる
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen, alpha = artAlpha)
                        .drawWithContent {
                            drawContent()
                            // 左3割は消し、右へ向かって濃くする（手前へ寄せたときは右端も短く溶かして切れ目を見せない）
                            drawRect(
                                if (endPad > 0.dp) Brush.horizontalGradient(0.28f to Color.Transparent, 0.78f to Color.Black, 0.93f to Color.Black, 1f to Color.Transparent)
                                else Brush.horizontalGradient(0.28f to Color.Transparent, 0.78f to Color.Black),
                                blendMode = BlendMode.DstIn,
                            )
                            // 上端と下端を溶かす。タブがあるときは、タブと重なる下の部分を下に行くほど段階的に透明にする。
                            // 薄くしすぎて絵が見えなかったので、ユーザー指示「かかっている部分をもう少し見えるように」で下端まで少し残す
                            val vertical = if (withTabs) {
                                Brush.verticalGradient(
                                    0f to Color.Transparent, 0.12f to Color.Black, 0.6f to Color.Black,
                                    // 「もっと透明度をあげて」（2026-11-01）で下の部分の消え方をさらにゆるめ、下端も3割残す
                                    0.75f to Color.Black.copy(alpha = 0.85f), 0.9f to Color.Black.copy(alpha = 0.6f), 1f to Color.Black.copy(alpha = 0.3f),
                                )
                            } else {
                                Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent)
                            }
                            drawRect(vertical, blendMode = BlendMode.DstIn)
                        },
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * 見出しの左に置くアプリアイコン（36dp・角丸10dp・細い縁取り）。2026-11-01 追加・案3。
 * 呼ぶ側で ScreenHeader(leading = { HeaderAppIcon(painterResource(R.mipmap.ic_launcher)) }) と渡す
 */
@Composable
fun HeaderAppIcon(icon: Painter) {
    val shape = RoundedCornerShape(10.dp)
    Image(
        painter = icon,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f), shape),
    )
}

/**
 * 見出しのすぐ下の横タブの列（2026-10-25 APP_RULES.md A2）。地は背景と同じ色、下に境目の線。
 * 見出しは ScreenHeader(divider = false) にして、線はこの列の下の1本だけにする。
 * タブ名は全角4文字まで・2〜5枚（決まり A3）。横スワイプは呼ぶ側の HorizontalPager で受ける。
 * 既存アプリで6枚以上残る場合だけ、文字を潰さないよう横に送れる列にする（新しいアプリでは作らない）
 */
@Composable
fun ScreenTabs(
    titles: List<String>,
    selected: Int,
    // true で列の背景を半透明にする（HeaderBackdrop の絵の上に置くとき。2026-11-01 追加）。
    // 全く塗らないとタブの文字の後ろに絵が来て読みにくいので、背景色を7割ほど敷いて絵をうっすら透かす
    transparent: Boolean = false,
    // タブの列の右端に置くもの（歯車など。2026-11-02 追加）。タブとは別の48dpのボタンで、選択状態には関わらない
    trailing: (@Composable () -> Unit)? = null,
    onSelect: (Int) -> Unit,
) {
    val container = if (transparent) MaterialTheme.colorScheme.background.copy(alpha = HeaderTabScrimAlpha) else MaterialTheme.colorScheme.background
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { i, t ->
            Tab(
                selected = selected == i,
                onClick = { onSelect(i) },
                text = { Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
    if (trailing != null) {
        // 右端にボタンを置くときは、地の色と境目の線を列全体に引く（TabRow の線だとボタンの手前で切れるため）
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(container),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabRow(
                    selectedTabIndex = selected,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    divider = {},
                    tabs = tabs,
                )
                trailing()
            }
            HeaderDivider()
        }
    } else if (titles.size <= 5) {
        TabRow(
            selectedTabIndex = selected,
            containerColor = container,
            divider = { HeaderDivider() },
            tabs = tabs,
        )
    } else {
        ScrollableTabRow(
            selectedTabIndex = selected,
            containerColor = container,
            edgePadding = 8.dp,
            divider = { HeaderDivider() },
            tabs = tabs,
        )
    }
}

/**
 * タブの中の先頭の細い行（更新時刻・件数などの一言＋右端のボタン）。
 * 見出しはアプリで1つにし、タブごとの情報と操作はここに置く（2026-10-25 APP_RULES.md A2）
 */
@Composable
fun TabInfoBar(text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/**
 * 設定を開く歯車のボタン（見出しの右端に置く。2026-10-25 APP_RULES.md A5）。
 * 歯車は線と円で描く（Material Icons を入れていないアプリが5本あり、UiKit から Icons を使うと配った先でビルドが落ちるため）
 */
@Composable
fun GearButton(onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.onBackground
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "設定" }) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val u = size.width / 24f
            val center = Offset(12f * u, 12f * u)
            // 歯を8本（中心から外へ太い線）
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val dx = kotlin.math.cos(a).toFloat()
                val dy = kotlin.math.sin(a).toFloat()
                drawLine(
                    c,
                    Offset(center.x + dx * 6.5f * u, center.y + dy * 6.5f * u),
                    Offset(center.x + dx * 9.5f * u, center.y + dy * 9.5f * u),
                    strokeWidth = 3f * u,
                    cap = StrokeCap.Butt,
                )
            }
            // 本体の輪（中は空ける）
            drawCircle(c, radius = 5.5f * u, center = center, style = Stroke(width = 2.6f * u))
        }
    }
}

/** 推移の線を描けるだけの点があるか（判定はここ1か所。空きを詰めるかを呼び出し側で決めるときもこれを使う） */
fun sparkDrawable(values: List<Double>): Boolean = values.count { !it.isNaN() && !it.isInfinite() } >= 2

/**
 * 小さな推移グラフ（既定 48×16dp・線1本・1色）。軸も目盛りも描かない。
 * 線の色は上げ下げで変えない（向きは ▲▼ と Sem.sign の色で伝える）。
 *  ・values … 古い順の値
 *  ・width … Dp.Unspecified を渡すと横いっぱい（2枚並びのカードの下に置くとき用）
 *  ・fill … 線の下を同じ色の薄い面（16%）で塗る（2026-11-11 追加。色つきのカードの中など、少し大きく見せる所で使う）
 *  ・keepSpace … 点が2つ未満のとき、同じ大きさの空きを置く（false なら何も置かない＝今までどおり）
 * 2026-11-11：終点の丸が右端・上下で切れていたので、丸の半径ぶん内側に描くようにした
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    height: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fill: Boolean = false,
    keepSpace: Boolean = false,
) {
    val sized = if (width.isSpecified) modifier.size(width, height) else modifier.fillMaxWidth().height(height)
    if (!sparkDrawable(values)) {
        if (keepSpace) Spacer(modifier = sized)
        return
    }
    val pts = values.filter { !it.isNaN() && !it.isInfinite() }
    Canvas(modifier = sized) {
        val min = pts.min()
        val max = pts.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stroke = 1.5.dp.toPx()
        val dot = stroke * 1.2f
        val top = dot
        val h = size.height - dot * 2
        val w = size.width - dot
        fun px(i: Int) = w * i / (pts.size - 1)
        fun py(v: Double) = top + h * (1 - ((v - min) / span)).toFloat()
        val line = Path()
        pts.forEachIndexed { i, v -> if (i == 0) line.moveTo(px(i), py(v)) else line.lineTo(px(i), py(v)) }
        if (fill) {
            // 線の形をなぞって下端で閉じ、同じ色の薄い面で塗る
            val area = Path().apply {
                pts.forEachIndexed { i, v -> if (i == 0) moveTo(px(i), py(v)) else lineTo(px(i), py(v)) }
                lineTo(w, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, color.copy(alpha = color.alpha * 0.16f))
        }
        drawPath(line, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // 最新の点だけ小さな丸で示す
        drawCircle(color, radius = dot, center = Offset(w, py(pts.last())))
    }
}

// ═══════════════════════════════════════════════════════════════
// 5. 要注意の物だけ目立たせる部品（2026-11-11・APP_RULES.md G）
//    内訳の帯／色を敷いたカード／その上の札／並べ方の骨組み（大きいカード・色つきの行・まとめた面）
//    手本は MacroAlert v2.20 の ui/HomeIndicatorList.kt。並べ方は実機で3回直して決めた形なので、
//    アプリ側で組み直さず下の入れ物（ToneCardWide・ToneCardHalf・ToneRow・CalmGroup）に中身を差し込む
// ═══════════════════════════════════════════════════════════════

/**
 * 色を敷いたカードの地の濃さ（暗い表示／明るい表示）。変えるときはこの2つだけ直して配り直す。
 * 2026-11-11 計算（ミッドナイトの面 #121A2B・白の上、WCAG のコントラスト比）：
 *  ・暗い 0.24 だと注意（黄）のカードの上で ＋の赤 3.9・補足の文字 3.9 と 4.5 に届かなかった
 *  ・暗い 0.16 なら 赤 4.9〜5.4／青 5.5〜6.1／警戒の橙 5.9〜6.5／補足 4.8〜5.3
 *  ・明るい 0.14 は 赤・青・橙 5.1〜5.3／補足 4.9〜5.1
 * 札 ToneChip の地（Sem.bg＝暗い 0.20・明るい 0.10）とは役割が別（札は中に文字1つ、カードは小さい色文字が載る）
 */
const val TONE_CARD_ALPHA_DARK = 0.16f
const val TONE_CARD_ALPHA_LIGHT = 0.14f

/** 色を敷いたカードの角丸（ふつうの面 PanelShape の 12dp より大きくして、別格の面だと分かるようにする） */
val ToneCardShape = RoundedCornerShape(16.dp)

/** 色を敷いたカードの地の色（濃さは上の定数2つだけで決める） */
@Composable
fun toneCardColor(tone: Tone): Color =
    Sem.fg(tone)
        .copy(alpha = if (Sem.isDark()) TONE_CARD_ALPHA_DARK else TONE_CARD_ALPHA_LIGHT)
        .compositeOver(MaterialTheme.colorScheme.surface)

/**
 * 内訳を横1本の帯で見せる（高さ8dp・角丸4dp・区切り3dp）。主役の数字の下に置き、札の文字を読む前に割合が分かるようにする。
 *  ・parts … 並べる順の（色の役割, 件数）。0件の区画は描かない
 *  ・幅は件数に比例。ただし1件でも見えるよう、どの区画も6dpより細くしない
 *  ・目立たせない区画（正常など）は呼び出し側が Tone.NORMAL を渡す（その区画だけ薄く描く）
 */
@Composable
fun ToneDistBar(parts: List<Pair<Tone, Int>>, modifier: Modifier = Modifier) {
    val shown = parts.filter { it.second > 0 }
    if (shown.isEmpty()) return
    // 色は描画の外で取っておく（Canvas の中では Sem.fg を呼べない）
    val colors = shown.map { (tone, _) ->
        if (tone == Tone.NORMAL) Sem.fg(tone).copy(alpha = 0.35f) else Sem.fg(tone)
    }
    val counts = shown.map { it.second }
    Canvas(modifier = modifier.fillMaxWidth().height(8.dp)) {
        val gap = 3.dp.toPx()
        val minW = 6.dp.toPx()
        val radius = CornerRadius(4.dp.toPx())
        val usable = size.width - gap * (counts.size - 1)
        if (usable <= 0f) return@Canvas
        // まず件数に比例で割り、細すぎる区画を最小幅に引き上げ、残りの幅をほかの区画で比例に分け直す
        val total = counts.sum().toFloat()
        val raw = counts.map { usable * it / total }
        val small = raw.map { it < minW }
        val restWidth = usable - minW * small.count { it }
        val restTotal = counts.filterIndexed { i, _ -> !small[i] }.sum().toFloat()
        val widths = counts.mapIndexed { i, n ->
            when {
                small[i] -> minW
                restTotal > 0f && restWidth > 0f -> restWidth * n / restTotal
                else -> raw[i]
            }
        }
        var x = 0f
        widths.forEachIndexed { i, w ->
            drawRoundRect(color = colors[i], topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = radius)
            x += w + gap
        }
    }
}

/**
 * 色を敷いたカード（要注意の物など、目を向けてほしい物だけに使う。1画面の大半をこれにしない）。
 * 地は tone の色を薄く敷いた面、枠は同じ色の細い線。押せるときは onClick を渡す
 */
@Composable
fun ToneCard(
    tone: Tone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val color = toneCardColor(tone)
    val border = BorderStroke(1.dp, Sem.fg(tone).copy(alpha = 0.55f))
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = ToneCardShape, color = color, border = border, content = content)
    } else {
        Surface(modifier = modifier, shape = ToneCardShape, color = color, border = border, content = content)
    }
}

/**
 * 色を敷いたカードの上に置く札。ToneChip は地がカードと同じ色で溶けてしまうので、
 * 地をふつうの面の色（surface）にして、文字だけ tone の色にする。危険の札は文字に ⚠ を付ける（APP_RULES.md B4）
 */
@Composable
fun ToneLabel(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Sem.fg(tone),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 組の小見出し（「要注意」「正常」など。12sp・太字・補足色） */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** 「押すと開く」の印（行にもカードにも同じ物を使う） */
@Composable
fun Chevron(modifier: Modifier = Modifier) {
    Text(text = "›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** 色つきのカードの頭（札と「›」の段 → 名前。名前は2行まで）。ToneCardWide・ToneCardHalf の中で使う */
@Composable
private fun ToneCardHead(label: String, name: String, tone: Tone, clickable: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ToneLabel(text = label, tone = tone)
        Spacer(modifier = Modifier.weight(1f))
        if (clickable) Chevron()
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = name,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 横いっぱいの大きいカード。上の段＝札＋名前（幅いっぱい）、下の段＝左に texts（値・増減・時刻）・右に trailing（推移の線など）。
 * 名前の横に線を置くと長い名前が数文字だけ2行目に落ちたので、この形に固定した（2026-11-11 実機）
 */
@Composable
fun ToneCardWide(
    tone: Tone,
    label: String,
    name: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    texts: @Composable ColumnScope.() -> Unit,
) {
    ToneCard(tone = tone, modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            ToneCardHead(label = label, name = name, tone = tone, clickable = onClick != null)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), content = texts)
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/**
 * 2枚並びの大きいカードの1枚。札＋名前 → texts → bottom（推移の線など。下端に寄せるので左右のカードでそろう）。
 * ToneCardGrid の half の中で、渡された modifier を付けて使う。長い値は texts の側で2行まで折り返す
 */
@Composable
fun ToneCardHalf(
    tone: Tone,
    label: String,
    name: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bottom: @Composable () -> Unit = {},
    texts: @Composable ColumnScope.() -> Unit,
) {
    ToneCard(tone = tone, modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(14.dp)) {
            ToneCardHead(label = label, name = name, tone = tone, clickable = onClick != null)
            texts()
            // 値が折り返した側だけ下の物の位置が下がるので、下端に寄せて左右でそろえる
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(8.dp))
            bottom()
        }
    }
}

/**
 * 大きいカードの並べ方。1枚→横いっぱい／2枚→横に2枚／3枚以上→奇数なら先頭1枚を横いっぱい、残りを2枚ずつ。
 *  ・wide(i) … i 番目を横いっぱいで描く（ToneCardWide）
 *  ・half(i, modifier) … i 番目を2枚並びで描く（ToneCardHalf に modifier を渡す。高さがそろう）
 * 大きいカードにするのは重い段階だけ（APP_RULES.md G2）。中で BoxWithConstraints を使うと高さの計算で落ちる
 */
@Composable
fun ToneCardGrid(
    count: Int,
    modifier: Modifier = Modifier,
    wide: @Composable (Int) -> Unit,
    half: @Composable (Int, Modifier) -> Unit,
) {
    if (count <= 0) return
    val wideFirst = count % 2 == 1
    val rest = (if (wideFirst) 1 else 0) until count
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (wideFirst) wide(0)
        rest.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                pair.forEach { i -> half(i, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/**
 * 色を敷いた通常の高さの行（軽い段階＝「注意」など、件数が多くなりうる物）。
 * 上の段＝札＋名前（行の幅いっぱい）、下の段＝左に sub（時刻など。長いときに縮むのはここ）・右に trailing（線・値・「›」）。
 * 札を時刻と同じ段に置くと時刻が切れ、名前の右に置くと名前が2文字まで削られたので、この形に固定した（2026-11-11 実機）
 */
@Composable
fun ToneRow(
    tone: Tone,
    label: String,
    name: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    sub: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    ToneCard(tone = tone, modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            ) {
                ToneLabel(text = label, tone = tone)
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) { sub() }
                trailing()
            }
        }
    }
}

/**
 * 目立たせない物（正常など）を1つの面にまとめる。行の間は細い線（面をやめた代わりの境目。APP_RULES.md C4 の例外）。
 *  ・row(i) … i 番目の行を描く（行全体を押せるようにするのは呼び出し側。高さ56dp以上）
 */
@Composable
fun CalmGroup(count: Int, modifier: Modifier = Modifier, row: @Composable (Int) -> Unit) {
    if (count <= 0) return
    Panel(modifier = modifier.fillMaxWidth()) {
        Column {
            for (i in 0 until count) {
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                row(i)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 6. 状態の出し方（失敗の知らせ・読み込みの印・数字の動き・読み上げ説明・画面下の知らせ）
//    2026-11-11 RainAlert v1.20 で試作し、利用者が承認して原本へ移した（APP_RULES.md 3-c）。
//    出典は UI_REFERENCE.md の 5・11章（動き）、30・34章（グラフ・読み込み・スナックバー）、31章（失敗の出し方）。
//    手本は RainAlert の HomeScreen（取得の状態の判定 forecastFetchStatus と知らせの文 forecastNotice）
// ═══════════════════════════════════════════════════════════════

// ────────────────────────────────────────────────────────────────────
// 1) 短い読み込みでは印を出さない（34章：M3 は 200ms 未満は出さない／31章：NN/g は1秒未満は出さない）
//    どちらにするかは利用者の判断待ち（検討書 7章の2）。値はこの定数1か所だけで決める
// ────────────────────────────────────────────────────────────────────
// 2026-11-11 利用者決定：1秒（APP_RULES H3）
const val LOADING_SHOW_DELAY_MS = 1000L

/**
 * [loading] が [delayMs] 以上続いたときだけ [content]（読み込みの印）を出す。
 * すぐ終わる取得で印がちらっと出て消える「ちらつき」をなくす。印は画面に1か所だけ置く（34章）
 */
@Composable
fun DelayedLoading(
    loading: Boolean,
    delayMs: Long = LOADING_SHOW_DELAY_MS,
    content: @Composable () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(delayMs)
            show = true
        } else {
            show = false
        }
    }
    // 出るとき・消えるときは透明度だけを変える（色・透明度の動きははね返らせない＝5章）
    AnimatedVisibility(visible = show && loading, enter = fadeIn(), exit = fadeOut()) {
        content()
    }
}

// ────────────────────────────────────────────────────────────────────
// 2) 失敗の知らせ（31章：何が起きたかを平易に＋次の一手。前回の値は消さずに知らせる）
//    例外の文（e.message）・エラー番号は画面に出さない。記録（更新履歴）にだけ残す
// ────────────────────────────────────────────────────────────────────

/**
 * 取得に失敗したことを、データのそばに1枚で知らせる。
 * @param title 何が起きたか（例：「予報を取得できませんでした」）。危険の段階なら ⚠ を先頭に付ける（APP_RULES B4）
 * @param detail 今どうなっているか＋原因の手がかり（例：「12:41 の予報を表示中。電波の届く所で再試行してください。」。2行に収まる長さにする）
 * @param actionLabel 次の一手のボタンの文字（null ならボタンなし）
 */
@Composable
fun StatusNotice(
    title: String,
    detail: String,
    tone: Tone = Tone.CAUTION,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val fg = Sem.fg(tone)
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        // 地の濃さは5章の toneCardColor だけで決める。アプリごとに濃さを書かない（APP_RULES G4）
        color = toneCardColor(tone),
        // 段階の色で細い枠を引く（2026-11-11 見た目の点検：明るい表示では薄い地が背景に溶けて境目が見えなかった。
        // 太さと濃さは Panel の枠と同じ1dp・45%）
        border = androidx.compose.foundation.BorderStroke(PanelBorderWidth, fg.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // 失敗・停止の知らせは段階によらず ⚠ を付ける（APP_RULES B4：危険は印＋文字。＋の赤や雨の青と見分けるため）
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
                // 押す所は48dp以上（TextButton の既定の高さで満たす・APP_RULES C5）
                TextButton(onClick = onAction) {
                    Text(actionLabel, fontWeight = FontWeight.Bold, color = fg)
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
// 3) 数字の切り替わり（5・11章：増えたら下から上へ、減ったら上から下へ短く動かす。
//    全部の数字に掛けず「変わったことを伝えたい主役の数字」だけに使う）
// ────────────────────────────────────────────────────────────────────

/**
 * 整数の値が変わったときだけ、上下に短くすべらせて切り替える。
 * 初めて描くときは動かさない（AnimatedContent は最初の表示を動かさない）。
 * @param format 値を文字にする関数（例：{ "$it%" }）。整形は呼び出し側の1か所で決める
 */
@Composable
fun AnimatedNumber(
    value: Int,
    format: (Int) -> String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        // 数字は右寄せ（APP_RULES C3）。桁数が変わる切り替え（70%→100%）の間も右端をそろえ、
        // 古い数字が横へずれて見えないようにする（2026-11-11 実機の録画で確認）
        contentAlignment = Alignment.CenterEnd,
        transitionSpec = {
            // 増えた → 新しい数字が下から上がってくる／減った → 上から下りてくる
            val up = targetState > initialState
            // 桁数が増える切り替えの間、入れ物が古い幅のまま新しい数字の左端（100% の「1」）を切っていたので、
            // 大きさの変化では切り取らない（SizeTransform(clip = false)）
            // 古い数字を先に短く消し、少し遅れて新しい数字を出す（同時だと半分ずつ透けて重なり、にじんで見えた）
            ((slideInVertically { h -> if (up) h / 2 else -h / 2 } + fadeIn(tween(durationMillis = 220, delayMillis = 90))) togetherWith
                (slideOutVertically { h -> if (up) -h / 2 else h / 2 } + fadeOut(tween(durationMillis = 90))))
                .using(SizeTransform(clip = false))
        },
        label = "AnimatedNumber",
    ) { v ->
        Text(
            text = format(v),
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ────────────────────────────────────────────────────────────────────
// 4) グラフ・図の読み上げ説明（30・34章：要点＋見せる理由を一文で。数値を全部は並べない）
//    見た目は変わらない。中の部品を1つの説明にまとめて読ませる
// ────────────────────────────────────────────────────────────────────

/** グラフや時間ごとの並びに、読み上げ用の一文を付ける */
fun Modifier.chartSummary(summary: String): Modifier =
    // 子（時刻・%・気温・絵）の読み上げを消して、この一文だけを読ませる（mergeDescendants だと子が後ろに続いて読まれた・2026-11-11 コード点検）
    clearAndSetSemantics { contentDescription = summary }

// ────────────────────────────────────────────────────────────────────
// 5) 画面の操作の結果は画面下の知らせ（スナックバー）で出す（34章）
//    文は1行（入りきらない分は…）・ボタンは1つまで・ボタンなしは自動で消える。同時に1つだけ。
//    裏の処理（サービス・タイル）から出す Toast や、別の画面（端末の設定）へ移る直前の案内の Toast は残す
// ────────────────────────────────────────────────────────────────────

/** 画面のどこからでも知らせを出すための入口。AppRoot の Scaffold で1つだけ用意する */
class UiMessages(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    /**
     * 知らせを出す。前の知らせが出ていれば先に閉じる（同時に1つだけ）
     * @param actionLabel ボタンの文字（null ならボタンなし＝自動で消える）
     */
    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                // ボタンなしは短く（約4秒）で自動で消す（34章：4〜10秒）。
                // ボタンありも約10秒で自動で消す。※34章の M3 は「ボタン付きは押されるか閉じられるまで残す」で、ここは逆。
                //   ✕を外したため、残す作りだと通信が戻った後も・画面を移った後も消えなくなる（2026-11-11 コード点検）。
                //   2026-11-11 利用者決定「約10秒で消す」（APP_RULES H6）
                // 閉じる✕は付けない（操作は1つだけ＝34章。✕があると文が語の途中で2行に折れた・2026-11-11 実機）
                duration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

/** 下の層の画面から UiMessages を取り出す（用意されていなければ null＝何もしない） */
val LocalUiMessages = staticCompositionLocalOf<UiMessages?> { null }

/**
 * 画面下の知らせの置き場。色はアプリの配色から決める（2026-11-11 実機で確認）。
 * 既定の SnackbarHost のままだと、配色に inverse 系の色が無いため Material3 の既定の薄紫になり、
 * 紙の帳簿・ミッドナイトのどちらの配色からも浮いて見えた。
 * 地＝文字の色（onSurface）・文字とボタン＝面の色（surface）で、面の色を反転させた形にする。
 *
 * ボタン（「再試行」など）は太字＋細い枠線・両端の丸い形（2026-11-11 RainAlert v1.23 で3案を試し利用者が決定）。
 * 文と同じ色・同じ太さだとボタンに見えなかった。色は足さない（配色が多く、primary 系は地の上で沈む配色が出る）。
 * M3 の「文字だけのボタン」からは外れる（APP_RULES H6）
 */
@Composable
fun AppSnackbarHost(host: SnackbarHostState) {
    androidx.compose.material3.SnackbarHost(host) { data ->
        val cs = MaterialTheme.colorScheme
        val label = data.visuals.actionLabel
        androidx.compose.material3.Snackbar(
            // 外側の12dpは自分で足す。中身を渡すだけの形（snackbarData を渡す）は部品の中で足すが、
            // ボタンと文を自分で描くこの形は足さないので、書かないと角丸のまま画面の端に付く（2026-11-11 コード点検）
            modifier = Modifier.padding(12.dp),
            shape = PanelShape,
            containerColor = cs.onSurface,
            contentColor = cs.surface,
            action = if (label == null) null else {
                {
                    // 押したら必ず performAction（呼ばないと UiMessages.show に結果が返らず、ボタンが黙って効かない）。
                    // 見た目の高さは32dp（上下と右のすき間をそろえる）。押せる範囲は Material3 が48dpに広げる（APP_RULES C5・実機の座標で確認）
                    TextButton(
                        onClick = { data.performAction() },
                        modifier = Modifier.height(32.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        border = BorderStroke(PanelBorderWidth, cs.surface.copy(alpha = 0.60f)),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = cs.surface),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                    ) {
                        Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            },
        ) {
            // 文は1行（APP_RULES H6）。入りきらない分は…で切る（ボタンは右に残る）
            Text(data.visuals.message, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
