package com.example.kyoteiai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kyoteiai.data.NotifLogRepository
import com.example.kyoteiai.data.NotifRecord
import com.example.kyoteiai.data.PickRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 「結果」タブ：締切を過ぎたレースの実際の着順と、AI本命が的中したかを表示する。
 *
 * - 上部に本日サマリ（的中 X / Y レース・的中率）。
 * - 各行：場名R・AI本命・実際の着順(1-2-3)・的中/不的中/結果待ちバッジ。
 * - 更新ボタンで再取得（取得済みはキャッシュ利用、未確定のみ再挑戦）。
 *
 * 実際のネットワーク取得・キャッシュ・判定は ResultViewModel が担う。
 * 本コンポーザブルは受け取った状態を描画するだけ（表示専用）。
 */
@Composable
fun ResultScreen(
    state: ResultUiState,
    feedLoaded: Boolean,
    onRefresh: () -> Unit
) {
    // 収支（実戦/C'仮想/A参考＋ステージ判定）のViewModel。
    // タブを開いたとき（初回コンポーズ時）に未突合分の結果突合→集計を1回走らせる
    val ledgerVm: LedgerViewModel = viewModel()
    val ledgerState by ledgerVm.uiState.collectAsState()
    LaunchedEffect(Unit) { ledgerVm.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── 本日サマリ + 更新ボタン ───────────────────────────────
        SummaryHeader(
            hitCount = state.hitCount,
            decidedCount = state.decidedCount,
            lastUpdated = state.lastUpdated,
            loading = state.loading,
            processedCount = state.processedCount,
            totalCount = state.totalCount,
            onRefresh = onRefresh
        )

        // ── 収支・履歴・通知ログ・レース結果を「1本のスクロール」に載せる ──
        //  以前は収支/履歴を固定領域に置いていたため、履歴を開くと画面に収まらず
        //  下が見えない・スワイプできない問題があった。LazyColumnのヘッダー項目に
        //  して全体が一緒にスクロールするよう修正
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 8.dp
            )
        ) {
            item { LedgerSection(ledger = ledgerState, resultRows = state.rows) }
            item { HistorySection(history = ledgerState.history) }
            item { NotificationLogSection() }
            item { Spacer(modifier = Modifier.height(4.dp)) }

            when {
                // フィード自体がまだ無い（今日タブが未取得）
                !feedLoaded -> item {
                    CenterHint("予想データがありません。先に「今日」タブを開いてください")
                }
                // 取得中（かつ表示できる行がまだ無い）
                state.loading && state.rows.isEmpty() -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                // 締切超過レースがまだ無い（本日これから）
                state.rows.isEmpty() -> item {
                    CenterHint("まだ終了したレースがありません")
                }
                // 一覧表示
                else -> items(state.rows) { row ->
                    ResultRowCard(row = row)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/** スクロール内に出す薄いヒント文（空状態用） */
@Composable
private fun CenterHint(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 収支セクション。実戦・C'仮想・A参考(今日)の3本を並べ、ステージ判定を添える。
 *
 * - 実戦/C'仮想は累計（ローカル記録ベース、決着済みのみ集計）
 * - A参考(今日)は結果タブの当日行から計算（本命ベタで¥100ずつ買ったと仮定。
 *   単勝払戻が取れた確定レースだけが対象）
 */
@Composable
private fun LedgerSection(ledger: LedgerUiState, resultRows: List<ResultRow>) {
    // A参考(今日): 単勝払戻が取れている確定レースだけで本命ベタを仮想集計
    val aDecided = resultRows.filter { it.result?.payoutWin != null }
    val aInvest = aDecided.size * 100
    val aRefund = aDecided.sumOf { row -> if (row.hit == true) row.result!!.payoutWin!! else 0 }
    val aRate = if (aInvest > 0) aRefund * 100.0 / aInvest else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),  // 横はLazyColumnのcontentPaddingが持つ
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                // 結果突合の実行中は「突合中…」を添える（この間の数字は暫定値）
                text = "収支（単勝・決着分のみ）" + if (ledger.loading) "　突合中…" else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            // 実戦（累計）
            LedgerLineView(
                label = "実戦",
                detail = "${ledger.real.points}点 的中${ledger.real.hits}" +
                    if (ledger.real.pending > 0) "（結果待ち${ledger.real.pending}）" else "",
                ratePercent = ledger.real.ratePercent,
                hasData = ledger.real.points > 0
            )
            // C'仮想（累計）
            LedgerLineView(
                label = "C'仮想",
                detail = "${ledger.virtual.points}点 的中${ledger.virtual.hits}" +
                    if (ledger.virtual.pending > 0) "（結果待ち${ledger.virtual.pending}）" else "",
                ratePercent = ledger.virtual.ratePercent,
                hasData = ledger.virtual.points > 0
            )
            // A参考（今日のみ）
            LedgerLineView(
                label = "A参考(今日)",
                detail = "${aDecided.size}点 本命ベタ仮定",
                ratePercent = aRate,
                hasData = aDecided.isNotEmpty()
            )

            // ステージ判定（購入方針 第2部の基準を機械適用した文言）
            if (ledger.stageText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ledger.stageText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 収支1行の表示。「ラベル 詳細 … 回収率XX%」。100%以上は緑・未満は既定色 */
@Composable
private fun LedgerLineView(label: String, detail: String, ratePercent: Double, hasData: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(86.dp)
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (hasData) "%.1f%%".format(ratePercent) else "—",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (hasData && ratePercent >= 100.0)
                Color(0xFF2E7D32)  // 100%以上＝緑（損益分岐超え）
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * EV通知履歴セクション（折りたたみ式・既定は閉じる。feedback_android_history_collapsible）。
 * Workerが観測したC'成立レース（＝EV通知の対象）を新しい順に表示する。
 * 各行: 日付 場R ◎艇番 / EV・確率×オッズ / 的中・外れ・結果待ちバッジ（的中は払戻額つき）。
 */
@Composable
private fun HistorySection(history: List<PickRecord>) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),  // 横はLazyColumnのcontentPaddingが持つ
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 見出し行（タップで開閉）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "◎確定レースの履歴（${history.size}件）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (expanded) "閉じる ▲" else "開く ▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                // この欄の読み方（前提の明記）
                Text(
                    text = "締切直前の再判定で「買い」と確定したレースの記録です。" +
                        "金額は「毎回¥100買っていたら」の仮想成績（実際に買ったかは関係ありません）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (history.isEmpty()) {
                    Text(
                        text = "まだ記録がありません。締切3分前の再判定で買い条件（EV≥1.2・確率≥0.2）を" +
                            "通過したレースがここに載ります。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // ── 日ごとにまとめて表示（新しい日が上）────────────────
                    //  各日の見出しに「件数・的中・回収率（¥100仮定）」のまとめを出し、
                    //  行は 的中 → 外れ → 結果待ち の順にソートして確認しやすくする
                    val byDate = history.groupBy { it.date }
                    val dates = byDate.keys.sortedDescending().take(7)  // 直近7日分
                    dates.forEachIndexed { di, date ->
                        if (di > 0) Spacer(modifier = Modifier.height(10.dp))
                        val recs = byDate.getValue(date)
                        DayHistoryBlock(date = date, records = recs)
                    }
                    if (byDate.size > 7) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "※ 直近7日分のみ表示（それ以前も記録は保持）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1日分のC'成立レースのまとめブロック。
 * 見出し = 日付＋その日のまとめ（件数・的中・回収率。¥100仮定・決着分のみ）。
 * 行     = 的中 → 外れ → 結果待ち の順にソート（同グループ内は時刻の新しい順）。
 */
@Composable
private fun DayHistoryBlock(date: String, records: List<PickRecord>) {
    // その日のまとめ（決着分のみ。¥100ずつ買った仮定の損益＝払戻合計−投資額）
    val settled = records.filter { it.settled }
    val hits = settled.count { it.won == true }
    val profit = settled.sumOf { it.refund } - settled.size * 100
    val pending = records.size - settled.size

    // 日付 "2026-07-12" → "7/12"
    val md = date.split("-").let {
        if (it.size == 3) "${it[1].trimStart('0')}/${it[2].trimStart('0')}" else date
    }

    // 見出し行: 日付＋その日の損益（±円）を主役に
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$md　${records.size}レース" +
                (if (settled.isNotEmpty()) "（的中$hits・外れ${settled.size - hits}" +
                    (if (pending > 0) "・待ち$pending" else "") + "）"
                 else "（全件結果待ち）"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        if (settled.isNotEmpty()) {
            Text(
                // 例: +¥110 / −¥390
                text = (if (profit >= 0) "+¥$profit" else "−¥${-profit}"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (profit >= 0) Color(0xFF2E7D32) else Color(0xFFB71C1C)
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))

    // 行のソート: 的中(0) → 外れ(1) → 結果待ち(2)、同グループ内は時刻の新しい順
    val sorted = records.sortedWith(
        compareBy<PickRecord> {
            when {
                it.settled && it.won == true -> 0
                it.settled -> 1
                else -> 2
            }
        }.thenByDescending { it.observedAt }
    )
    sorted.forEach { p ->
        HistoryRowView(p)
        Spacer(modifier = Modifier.height(6.dp))
    }
}

/**
 * ◎確定履歴の1行。
 * 1行目 = どのレースで何号艇か（＋確定時刻）、
 * 2行目 = なぜ買いだったか（AI確率とオッズのズレ＝期待値）、
 * 右    = 結果を±円で表示（¥100買った仮定。的中は +利益、外れは −¥100）。
 */
@Composable
private fun HistoryRowView(p: PickRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 例: 多摩川4R ◎1号艇（12:59 確定）
            Text(
                text = "${p.stadiumName}${p.raceNo}R ◎${p.lane}号艇（${p.observedAt} 確定）",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            // 例: AIは38%と予想、オッズ12.9倍 → 期待値5.03
            Text(
                text = "AIは%d%%と予想、オッズ%.1f倍 → 期待値%.2f".format(
                    (p.prob * 100).toInt(), p.odds, p.ev
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 結果バッジ（±円）: 的中=+利益（緑）/ 外れ=−¥100（赤）/ 結果待ち（灰）
        val (label, color) = when {
            !p.settled -> "結果待ち" to Color(0xFF757575)
            p.won == true -> "的中 +¥${p.refund - 100}" to Color(0xFF2E7D32)
            else -> "外れ −¥100" to Color(0xFFB71C1C)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(color)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * 通知ログ欄（折りたたみ式・既定は閉じる）。
 * アプリが送った通知（候補/確定/見送り/旧確信度）を、送信時の文面のまま
 * 新しい順に一覧表示する。「あの通知なんだったっけ」を後から確認する用途。
 */
@Composable
private fun NotificationLogSection() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<NotifRecord>>(emptyList()) }

    // 開くたびに最新のログを読み直す（ローカルファイルのみ・ネット不使用）
    LaunchedEffect(expanded) {
        if (expanded) {
            logs = withContext(Dispatchers.IO) { NotifLogRepository.load(context) }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 見出し行（タップで開閉）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "通知ログ（時系列）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)  // 長文でも開閉ラベルを押し出さない
                )
                Text(
                    text = if (expanded) "閉じる ▲" else "開く ▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                if (logs.isEmpty()) {
                    Text(
                        text = "まだ通知の記録がありません（この機能の追加後に送られた通知から記録されます）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 新しい順に最大100件表示
                    logs.take(100).forEach { log ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "${log.time}　${log.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (logs.size > 100) {
                        Text(
                            text = "※ 直近100件のみ表示（最大300件まで保持）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 画面中央にメッセージを1つ出す（空状態用） */
@Composable
private fun CenterMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 上部の本日サマリ。
 * 「本日の的中 X / Y レース（的中率 Z%）」＋最終取得時刻＋更新ボタン。
 */
@Composable
private fun SummaryHeader(
    hitCount: Int,
    decidedCount: Int,
    lastUpdated: String?,
    loading: Boolean,
    processedCount: Int,
    totalCount: Int,
    onRefresh: () -> Unit
) {
    // 的中率（結果確定レースが0のときは0%扱い）
    val rate = if (decidedCount > 0) (hitCount * 100 / decidedCount) else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "本日の的中 $hitCount / $decidedCount レース",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                // 取得中は進捗（m件中n件完了）を優先表示し、固まっていないことを伝える。
                //  取得が終われば的中率＋最終取得時刻に切り替わる。
                val subText = if (loading && totalCount > 0) {
                    "取得中 ${totalCount}件中 ${processedCount}件完了（的中率 ${rate}%）"
                } else {
                    "的中率 ${rate}%" +
                        (if (lastUpdated != null) "（最終取得 $lastUpdated）" else "")
                }
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 取得中はスピナー、そうでなければ更新ボタン
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = onRefresh) {
                    Text("更新")
                }
            }
        }
    }
}

/**
 * 結果1行のカード。
 * 左：AI本命の艇番・場名R・本命名／中：実際の着順(1-2-3、1着は艇色付き)／右：的中バッジ。
 * 的中=緑 / 不的中=灰 / 結果待ち=薄いグレー。
 */
@Composable
private fun ResultRowCard(row: ResultRow) {
    val race = row.race
    val result = row.result

    // カード背景：的中=薄緑 / 不的中や結果待ち=標準
    val containerColor = when (row.hit) {
        true -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── 左：AI本命艇番 + テキスト ──
            LaneSquare(lane = race.topLane)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${race.stadiumName} ${race.raceNo}R",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "AI本命 ${race.topLane}号艇 ${race.topName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 実際の着順（確定していれば表示）
                if (result != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ActualOrderRow(
                        first = result.first,
                        second = result.second,
                        third = result.third,
                        payout3t = result.payout3t
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "着順は結果待ちです",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // ── 右：的中バッジ ──
            HitBadge(hit = row.hit)
        }
    }
}

/**
 * 実際の着順を「1着-2着-3着」で表示する行。
 * 1着艇だけ艇色の四角を付けて分かりやすくし、続けて 2着・3着 を数字で示す。
 * 払戻金額が取れていれば末尾に添える。
 */
@Composable
private fun ActualOrderRow(first: Int, second: Int, third: Int, payout3t: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "着順 ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 1着だけ艇色の小さな四角で強調
        SmallLaneChip(lane = first)
        Text(
            text = " - $second - $third",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        // 3連単の払戻金額（取れたときだけ）
        if (payout3t != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "3連単 ${"%,d".format(payout3t)}円",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 1着艇を示す小さな艇色チップ（数字入り）。KyoteiComponents の laneColor を流用。 */
@Composable
private fun SmallLaneChip(lane: Int) {
    val bg = laneColor(lane)
    // 白(1)・黄(5)は明るいので文字を黒に
    val textColor = if (lane == 1 || lane == 5) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = lane.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// 的中バッジの色
private val HitGreen = Color(0xFF2E7D32)   // 的中=緑
private val MissGray = Color(0xFF757575)   // 不的中=灰

/**
 * 的中バッジ。
 *  true = 「的中」緑 / false = 「不的中」灰 / null = 「結果待ち」薄いグレー。
 */
@Composable
private fun HitBadge(hit: Boolean?) {
    val (label, bg, fg) = when (hit) {
        true -> Triple("的中", HitGreen, Color.White)
        false -> Triple("不的中", MissGray, Color.White)
        null -> Triple("結果待ち", Color(0xFFE0E0E0), Color(0xFF616161))
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}
