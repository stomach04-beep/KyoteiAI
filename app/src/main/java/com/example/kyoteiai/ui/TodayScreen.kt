package com.example.kyoteiai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kyoteiai.data.Feed
import com.example.kyoteiai.data.OddsLogStore
import com.example.kyoteiai.data.PickLogRepository
import com.example.kyoteiai.data.RacePred
import com.example.kyoteiai.data.RunLogStore
import com.example.kyoteiai.data.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 今日タブの並べ替え方法 */
private enum class SortMode(val label: String) {
    DEADLINE("締切順"),      // 締切時刻の早い順（既定）
    PROB("確信度順"),        // 本命の1着確率が高い順
    CPRIME("◎C'優先")        // C'成立レースを上に（その中は締切順）
}

/**
 * 「今日」タブ：本日のレースを一覧表示する。
 *
 * - 上部に「狙い目のみ」トグル（is_hot でフィルタ）とソート切替チップ。
 * - 並べ替えは 締切順／確信度順／◎C'優先 の3種類。
 * - どのソートでも締切を過ぎたレースは末尾へ回す（薄く表示）。
 * - 各行タップでレース詳細へ遷移。
 */
@Composable
fun TodayScreen(
    state: FeedUiState,
    hotOnly: Boolean,
    onHotOnlyChange: (Boolean) -> Unit,
    onRaceClick: (RacePred) -> Unit
) {
    // ── C'成立レースの集合（picks.json から本日分を読む）──────────
    //  EvPickWorkerが締切前オッズでC'条件（EV≥1.2・確率≥0.2）成立を観測すると
    //  picks.json に記録される。それをここで読み、一覧に「◎C'」バッジを出す。
    //  今日タブを開き直すたびに読み直す（Workerの新しい観測を反映）。
    val context = LocalContext.current
    var cprimeKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 並べ替え方法（既定は締切順）
    var sortMode by remember { mutableStateOf(SortMode.DEADLINE) }
    // 画面を開いている間は30秒ごとに読み直す。
    // Workerが締切3分前の再判定で新しく「確定」を記録しても、開きっぱなしの画面に
    // ◎C'マークが反映されない問題への対策（ローカルファイル読込なので負荷はほぼゼロ。
    // 画面を離れるとこのループは自動で止まる）
    LaunchedEffect(state.feed?.date) {
        val date = state.feed?.date ?: return@LaunchedEffect
        while (true) {
            cprimeKeys = withContext(Dispatchers.IO) {
                PickLogRepository.load(context)
                    .filter { it.date == date }
                    .map { "${it.stadium}-${it.raceNo}" }
                    .toSet()
            }
            kotlinx.coroutines.delay(30_000)
        }
    }

    // ── 収集ヘルス（run_log.json / odds_log.json の読み手）─────────────
    //  これまでこの2つのファイルは「書く一方」で、収集が止まっていても
    //  端末の画面からは分からなかった（データは残っているのに読み手が居なかった）。
    //  60秒ごとに読み直す（ローカルファイルのみ・ネット不使用）。
    //  記録するのは Worker＝このプロセスの外なので、画面側から定期的に
    //  読み直さないと表示だけ古いままになる（feedback_stateflow_out_of_band_reload）。
    var healthText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            healthText = withContext(Dispatchers.IO) {
                val s = RunLogStore.todayStats(context)
                val total = OddsLogStore.count(context)
                "今日: 実行${s.runs}回・予約${s.scheduled}・記録${s.snapOk}・失敗${s.snapFail}" +
                    (if (s.lastAt != null) "・最終${s.lastAt}" else "・未実行") +
                    "（累計${total}件）"
            }
            kotlinx.coroutines.delay(60_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── 収集ヘルス1行 ────────────────────────────────────────
        //  今日の収集が生きているかをここだけで判断できるようにする。
        //  実行0回・記録0件が続いていたら、その日の締切前オッズは失われつつある
        healthText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // ── 「狙い目のみ」トグル行 ───────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "狙い目のみ表示",
                    style = MaterialTheme.typography.bodyLarge
                )
                val hotCount = state.feed?.hotCount ?: 0
                Text(
                    text = "AIが高確信と判断したレース（本日 ${hotCount} 件）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = hotOnly, onCheckedChange = onHotOnlyChange)
        }

        when {
            // 読み込み中
            state.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // 取得失敗
            state.feed == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.errorMessage ?: "データがありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 一覧表示
            else -> {
                // 取得に失敗すると assets のサンプル(2026-07-08固定)にフォールバックする。
                // サンプルも正常にパースされて feed != null になるため、以前は17日前の
                // 固定データが「本日のレース」として無警告で表示されていた。
                // 締切判定は時刻しか見ない（日付を見ない）ので見た目では気づけない。
                // 自動通知側は feed.date != today で弾いているが、手動で開くこの画面だけが
                // 素通りしていたため、日付が今日でなければ必ず警告を出す。
                if (state.feed.date != java.time.LocalDate.now().toString()) {
                    Text(
                        text = "⚠ 表示中のデータは ${state.feed.date} 付です（最新を取得できていません）。" +
                            "本日のレースではないため、締切表示も当てになりません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                // C'成立があるときだけ凡例を出す（普段は場所を取らない）
                if (cprimeKeys.isNotEmpty()) {
                    Text(
                        text = "◎C' = 締切前オッズで買い条件成立（EV≥1.2・確率≥0.2、本日${cprimeKeys.size}レース）",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                // ── ソート切替チップ（締切順／確信度順／◎C'優先）──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortMode.entries.forEach { mode ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { sortMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }
                RaceList(
                    feed = state.feed,
                    hotOnly = hotOnly,
                    cprimeKeys = cprimeKeys,
                    sortMode = sortMode,
                    onRaceClick = onRaceClick
                )
            }
        }
    }
}

/** レース一覧本体（締切済みはどのソートでも末尾）。cprimeKeys = C'成立レースの "場-R" 集合 */
@Composable
private fun RaceList(
    feed: Feed,
    hotOnly: Boolean,
    cprimeKeys: Set<String>,
    sortMode: SortMode,
    onRaceClick: (RacePred) -> Unit
) {
    // フィルタ（狙い目のみ）
    val filtered = if (hotOnly) feed.races.filter { it.isHot } else feed.races

    // 並べ替え：どのソートでも「まだ締切前」を先、「締切済み」を後ろに固定し、
    // その中を選択されたソートキーで並べる
    val now = System.currentTimeMillis()
    val base = compareBy<RacePred> { TimeUtil.isPast(it.deadline, now) }
    val sorted = when (sortMode) {
        // 締切時刻の早い順（既定）
        SortMode.DEADLINE -> filtered.sortedWith(base.thenBy { it.deadline })
        // 本命の1着確率が高い順（同率は締切順）
        SortMode.PROB -> filtered.sortedWith(
            base.thenByDescending { it.topProb }.thenBy { it.deadline }
        )
        // C'成立レースを最上位に（締切済みでも上。C'は締切直前に成立するため
        // 「締切済みは末尾」を優先するとC'が全部下に沈んでしまう）。
        // C'内・非C'内はそれぞれ「締切前→締切済み」→締切順で並べる
        SortMode.CPRIME -> filtered.sortedWith(
            compareByDescending<RacePred> { cprimeKeys.contains("${it.stadium}-${it.raceNo}") }
                .thenBy { TimeUtil.isPast(it.deadline, now) }
                .thenBy { it.deadline }
        )
    }

    if (sorted.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (hotOnly) "狙い目レースはありません" else "レースがありません",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp, vertical = 8.dp
        )
    ) {
        items(sorted) { race ->
            RaceRow(
                race = race,
                past = TimeUtil.isPast(race.deadline, now),
                cprime = cprimeKeys.contains("${race.stadium}-${race.raceNo}"),
                onClick = { onRaceClick(race) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * レース1行のカード。
 * 左：場名 R番号・締切時刻・本命名／右：確信度バッジ。
 * 締切済みは全体を薄く表示（feedback_compose_last_checked_pattern の考え方）。
 */
@Composable
private fun RaceRow(
    race: RacePred,
    past: Boolean,
    cprime: Boolean,
    onClick: () -> Unit
) {
    // 締切済みは薄く、狙い目は縁を強調
    val alpha = if (past) 0.5f else 1f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (race.isHot && !past)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 艇番（本命）＋テキスト
            LaneSquare(lane = race.topLane)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${race.stadiumName} ${race.raceNo}R",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (past) "締切済み ${race.deadline}" else "締切 ${race.deadline}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (past) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "本命 ${race.topLane}号艇 ${race.topName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // ◎C'バッジ（締切前オッズで買い条件が成立したレース。深緑＝レース詳細と同色）
            if (cprime) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1B5E20))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "◎C'",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            // 確信度バッジ
            Box(contentAlignment = Alignment.Center) {
                // 締切済みは薄くする（Boxごとにalphaは掛けられないので色で対応済み）
                ConfidenceBadge(topProb = race.topProb)
            }
        }
    }
}
