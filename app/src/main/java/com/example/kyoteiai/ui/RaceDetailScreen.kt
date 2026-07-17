package com.example.kyoteiai.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kyoteiai.data.BetRecord
import com.example.kyoteiai.data.BetRepository
import com.example.kyoteiai.data.EvPolicy
import com.example.kyoteiai.data.RacePred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * レース詳細画面：6艇を勝率の高い順に並べ、各艇に確率バーを表示する。
 * さらに公式オッズを取得してモデル確率と掛け合わせた期待値(EV)を表示する。
 *
 * 行の構成：艇番（艇色の四角）/ 確率バー / XX% 選手名 級別
 * システムの戻るボタンで一覧に戻す（feedback_android_backhandler）。
 *
 * @param feedDate フィードの日付（"2026-07-08"形式）。オッズURLの hd に使う。null なら今日。
 */
@Composable
fun RaceDetailScreen(
    race: RacePred,
    feedDate: String?,
    fetchOnOpen: Boolean = false,  // true=画面を開いた瞬間にオッズを自動取得（通知タップ経由）
    onBack: () -> Unit
) {
    // 戻るボタンで一覧へ戻す（これが無いと Activity 終了＝アプリが閉じる）
    BackHandler { onBack() }

    // オッズ・期待値の状態を持つ ViewModel（StateFlow を購読）
    val oddsVm: OddsViewModel = viewModel()
    val oddsState by oddsVm.uiState.collectAsState()

    // オッズURLに渡す日付8桁（"2026-07-08"→"20260708"。取れなければ今日）
    val dateYmd = rememberDateYmd(feedDate)

    // 別レースを開いたら前回のオッズ結果を消す（ViewModelは画面遷移で使い回されるため）。
    // 通知タップ経由（fetchOnOpen=true）のときはそのまま最新オッズを自動取得して
    // 開いた瞬間にEV・◎判定が見える状態にする（ネットワーク取得のみで副作用なし）
    LaunchedEffect(race.stadium, race.raceNo) {
        oddsVm.reset()
        if (fetchOnOpen) {
            oddsVm.fetch(race, dateYmd)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── ヘッダー帯（primaryContainer）：BargainChecker 統一デザイン ──
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("戻る") }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${race.stadiumName} ${race.raceNo}R",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "締切 ${race.deadline} ・ ${race.kind}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                // 本命の確信度バッジ
                ConfidenceBadge(topProb = race.topProb)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = "AI予想（勝率の高い順）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 勝率の高い順に並べ替えて表示
                    race.boats.sortedByDescending { it.prob }.forEachIndexed { index, boat ->
                        if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 艇番（色付き四角）
                            LaneSquare(lane = boat.lane)
                            Spacer(modifier = Modifier.width(12.dp))

                            // 確率バー＋数値＋選手名を縦に
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${boat.racerName}　${boat.clazz}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "${(boat.prob * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                ProbabilityBar(
                                    prob = boat.prob,
                                    lane = boat.lane,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "※ AIの予想確率です。舟券の的中を保証するものではありません。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 期待値（オッズ×モデル確率）セクション ──
            Spacer(modifier = Modifier.height(24.dp))
            ExpectedValueSection(
                race = race,
                dateYmd = dateYmd,
                state = oddsState,
                onFetch = { oddsVm.fetch(race, dateYmd) }
            )
        }
    }
}

/**
 * 期待値セクション。
 * 「オッズ取得して期待値を見る」ボタン→ローディング→結果テーブルを表示する。
 * テレボート起動ボタンも置く。
 */
@Composable
private fun ExpectedValueSection(
    race: RacePred,
    dateYmd: String,
    state: OddsUiState,
    onFetch: () -> Unit
) {
    val context = LocalContext.current

    Text(
        text = "期待値チェック（オッズ×AI確率）",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(8.dp))

    // 取得ボタン（ローディング中は無効化してスピナー表示）
    Button(
        onClick = onFetch,
        enabled = !state.loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("取得中…")
        } else {
            Text(if (state.loaded || state.notOnSale) "オッズを再取得する" else "オッズ取得して期待値を見る")
        }
    }

    // 取得時刻の表示
    if (state.fetchedAt != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "取得時刻 ${state.fetchedAt}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ── 未発売 ──
    if (state.notOnSale) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "まだ発売されていません。時間をおいて再取得してください。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // ── 結果（取得成功） ──
    if (state.loaded) {
        Spacer(modifier = Modifier.height(16.dp))

        // 注意書き（小さく）— 2026年6月・実オッズ4,613レースの検証値
        Text(
            text = "※ 6月実オッズ検証(4,613R): ◎1点買い(EV≥1.2・人気薄除外)122%、本命ベタ90.7%。" +
                "確定オッズ基準の楽観値のため実戦は下振れする。人気薄の高EVはノイズ疑い。少額推奨。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 単勝EV
        Text(
            text = "単勝の期待値",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                state.winRows.forEachIndexed { index, row ->
                    if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                    WinEvRowView(row)
                }
            }
        }

        // ── 購入記録（◎1点があるレースだけ表示）──
        // 「買った」を1タップで記録し、収支タブの実戦収支の材料にする
        val topPickRow = state.winRows.firstOrNull { it.isTopPick }
        if (topPickRow != null && topPickRow.odds != null && topPickRow.ev != null) {
            Spacer(modifier = Modifier.height(10.dp))
            BetRecordButton(race = race, dateYmd = dateYmd, pick = topPickRow)
        }

        // 3連単EV（上位10件）
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "3連単の期待値（上位10）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        // 2026-07-13 検証結果: 3連単EV買いは確定オッズ上限でも回収率12〜63%で不成立
        //（ハービル式の並び確率が市場に対して粗い）。EV買いの対象から除外し参考表示のみ
        Text(
            text = "※ 検証済み：3連単のEV買いは回収率12〜63%で非推奨（買い判定は出しません）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (state.trifectaRows.isEmpty()) {
                    Text(
                        text = "計算できる3連単オッズがありませんでした。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    state.trifectaRows.forEachIndexed { index, row ->
                        if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                        TrifectaEvRowView(row)
                    }
                }
            }
        }
    }

    // ── テレボート起動ボタン（常設） ──
    // 【重要・不変条件】外部アプリ（テレボート/ブラウザ）の起動は
    //   「ユーザーがこのボタンを実際にタップした瞬間」だけに限定する完全なワンショット。
    //   ・絶対に LaunchedEffect / remember{} の初期化 / state 監視 / composable 本体から
    //     startActivity を呼ばないこと。それらは再コンポーズ・画面回転・別アプリからの復帰・
    //     プロセス復元のたびに再実行され、テレボートが繰り返し前面に出てしまう
    //     （「ベットリストは破棄されました。再度ログインします」が毎回出る不具合の原因）。
    //   ・onClick ラムダはユーザーのタップでしか呼ばれないため、ここに置く限り復元での再発火は起きない。
    //   （復元・戻りで副作用が再発火する系の教訓：feedback_accessibility_autotap_onpause /
    //     feedback_ringtone_picker_back_result_ok）
    Spacer(modifier = Modifier.height(20.dp))
    OutlinedButton(
        onClick = {
            // ◎1点が出ていれば買い目をクリップボードへコピー（画面前面なので直接書ける）
            val pick = state.winRows.firstOrNull { it.isTopPick }
            if (pick != null) {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                val clip = "${race.stadiumName}${race.raceNo}R 単勝${pick.lane}号艇 ¥${EvPolicy.DEFAULT_BET_AMOUNT}"
                cm.setPrimaryClip(android.content.ClipData.newPlainText("kyotei_bet", clip))
            }
            // 該当レースの投票ページ（voteTagId付き）をブラウザで開く（クリック時のみ）
            val url = com.example.kyoteiai.data.OddsRepository.raceVoteUrl(
                race.stadium, race.raceNo, dateYmd
            )
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // ブラウザ等が無い端末でもクラッシュさせない
                Toast.makeText(context, "ブラウザが見つかりませんでした", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("このレースの投票へ（テレボート）")
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * 「◎を買った」1タップ記録ボタン。
 * 押すと購入記録（¥100・その時のオッズ・EV）をローカル保存し、収支タブの実戦収支に反映される。
 * 記録済みならタップで取消できる（誤タップ対策）。
 * ファイルの読み書きは Dispatchers.IO で行い、UIスレッドを塞がない。
 */
@Composable
private fun BetRecordButton(race: RacePred, dateYmd: String, pick: WinEvRow) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 記録日付は "yyyy-MM-dd" 形式（収支の集計キーに使う）
    val dateIso = "${dateYmd.substring(0, 4)}-${dateYmd.substring(4, 6)}-${dateYmd.substring(6, 8)}"

    // 記録済みかどうか（レースが変わるたびにファイルから読み直す）
    var recorded by remember(race.stadium, race.raceNo) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(race.stadium, race.raceNo) {
        recorded = withContext(Dispatchers.IO) {
            BetRepository.exists(context, dateIso, race.stadium, race.raceNo)
        }
    }

    when (recorded) {
        null -> Unit  // 読込中は何も出さない（すぐ終わる）
        false -> Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        BetRepository.add(
                            context,
                            BetRecord(
                                date = dateIso,
                                stadium = race.stadium,
                                stadiumName = race.stadiumName,
                                raceNo = race.raceNo,
                                lane = pick.lane,
                                amount = EvPolicy.DEFAULT_BET_AMOUNT,
                                odds = pick.odds ?: 0.0,
                                ev = pick.ev ?: 0.0,
                                boughtAt = LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                            )
                        )
                    }
                    recorded = true
                    Toast.makeText(
                        context,
                        "◎${pick.lane}号艇 ¥${EvPolicy.DEFAULT_BET_AMOUNT} を記録しました",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("◎${pick.lane}号艇を買った（¥${EvPolicy.DEFAULT_BET_AMOUNT}で記録）")
        }
        true -> OutlinedButton(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        BetRepository.remove(context, dateIso, race.stadium, race.raceNo)
                    }
                    recorded = false
                    Toast.makeText(context, "購入記録を取り消しました", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("購入記録済み（タップで取消）")
        }
    }
}

/** 単勝EVの1行表示：「N号 予測XX% × オッズO = EV / バッジ」 */
@Composable
private fun WinEvRowView(row: WinEvRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LaneSquare(lane = row.lane)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (row.odds == null) {
                // 欠場等でオッズが無い
                Text(
                    text = "${row.lane}号　予測${(row.prob * 100).toInt()}%　（オッズなし）",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "${row.lane}号　予測${(row.prob * 100).toInt()}% × オッズ${formatOdds(row.odds)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "EV ${formatEv(row.ev)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // 判定バッジ（オッズがあるときだけ）: ◎1点 ＞ 買い ＞ 人気薄 ＞ 見送り
        if (row.odds != null) {
            WinBadge(row)
        }
    }
}

/** 3連単EVの1行表示：「組番 予測X.X% × オッズO = EV / バッジ」 */
@Composable
private fun TrifectaEvRowView(row: TrifectaEvRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${row.combo}　予測${formatPercent1(row.prob)} × オッズ${formatOdds(row.odds)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "EV ${formatEv(row.ev)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        BuyBadge(buy = row.buy)
    }
}

// バッジの色: ◎1点=深緑 / 買い=緑 / 人気薄注意=橙 / 見送り=灰
private val PickDeepGreen = Color(0xFF1B5E20)
private val BuyGreen = Color(0xFF2E7D32)
private val LongshotOrange = Color(0xFFEF6C00)
private val SkipGray = Color(0xFF757575)

/**
 * 単勝の判定バッジ。優先順位: ◎1点 ＞ 人気薄 ＞ 買い ＞ 見送り。
 * ◎1点 = そのレースでEV最大・EV≥1.2・確率≥0.2（6月実オッズ検証で最も信頼できた買い方）。
 * 人気薄 = EV≥1.2だが確率<0.2（モデルノイズで過大に出やすい。盲信しない）。
 */
@Composable
private fun WinBadge(row: WinEvRow) {
    val (label, color) = when {
        row.isTopPick -> "◎1点" to PickDeepGreen
        row.longshot -> "人気薄" to LongshotOrange
        row.buy -> "買い" to BuyGreen
        else -> "見送り" to SkipGray
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
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

/**
 * 3連単用バッジ。2026-07-13の検証（6月463レース・確定オッズ）で
 * 3連単EV買いは全戦略回収率12〜63%と不成立が確定したため「買い」は出さない。
 * EV≥1.2でも「非推奨」（橙）、未満は「見送り」（灰）の参考表示のみ。
 */
@Composable
private fun BuyBadge(buy: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (buy) LongshotOrange else SkipGray)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (buy) "非推奨" else "見送り",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// ─── 表示フォーマット用のヘルパー ───

/** オッズを見やすく整形（小数1桁）。 */
private fun formatOdds(odds: Double): String = "%.1f".format(odds)

/** EVを見やすく整形（小数2桁）。 */
private fun formatEv(ev: Double?): String = if (ev == null) "-" else "%.2f".format(ev)

/** 確率を小数1桁のパーセントに整形（3連単用。値が小さいため）。 */
private fun formatPercent1(prob: Double): String = "%.1f%%".format(prob * 100)

/**
 * フィード日付("2026-07-08"形式)をオッズURL用の8桁("20260708")へ変換する。
 * 取得できない場合は今日の日付を使う。
 */
@Composable
private fun rememberDateYmd(feedDate: String?): String {
    // 数字だけ抜き出して8桁ならそれを、そうでなければ今日
    val digits = feedDate?.filter { it.isDigit() } ?: ""
    return if (digits.length == 8) {
        digits
    } else {
        LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) // 例 "20260708"
    }
}
