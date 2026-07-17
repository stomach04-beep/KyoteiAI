package com.example.kyoteiai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kyoteiai.data.BetRepository
import com.example.kyoteiai.data.EvPolicy
import com.example.kyoteiai.data.PickLogRepository
import com.example.kyoteiai.data.PickRecord
import com.example.kyoteiai.data.ResultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 収支セクションのViewModel。検討書 第3部(C)の実装。
 *
 * 3本の収支を計算する:
 *   実戦     … ユーザーが「買った」を記録した舟券（bets.json）
 *   C'仮想   … Workerが観測した全C'成立レースを¥100買ったと仮定（picks.json）
 *   A参考    … 本命ベタ（当日分は結果タブの行から別途計算して表示）
 *
 * 未突合（結果が出ていない）記録は、このViewModelが公式結果と突合して
 * 勝敗・払戻を書き戻す（settle-once方式: 一度確定したら再取得しない）。
 */
class LedgerViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    /** 収支を再計算する（未突合の記録は公式結果と突合してから集計） */
    fun refresh() {
        if (job?.isActive == true) return  // 二重起動ガード
        job = viewModelScope.launch {
            val context = getApplication<Application>()

            withContext(Dispatchers.IO) {
                // ── 0) まず手元のファイル内容だけで即時表示 ─────────────
                //  結果突合はネット越しで時間がかかる（サイトが重い時間帯は数十秒）。
                //  先に既存の記録を出しておかないと、その間ずっと「0件」に見えて
                //  「記録が消えた」と誤解させてしまうため、2段階表示にする。
                run {
                    val bets0 = BetRepository.load(context)
                    val picks0 = PickLogRepository.load(context)
                    _uiState.value = buildState(bets0, picks0, loading = true)
                }

                // ── 1) 実戦記録の未突合分を結果と突合 ──────────────────
                val bets = BetRepository.load(context).toMutableList()
                var betsChanged = false
                for (i in bets.indices) {
                    val b = bets[i]
                    if (b.settled) continue
                    val result = ResultRepository.fetchResult(
                        b.stadium, b.raceNo, b.date.filter { it.isDigit() }
                    ) ?: continue  // 未確定は次回に持ち越し
                    val won = result.first == b.lane
                    // 払戻: 公式の単勝払戻（円/100円）× 口数。取れなければ購入時オッズで概算
                    val refund = if (!won) 0 else {
                        val perHundred = result.payoutWin ?: (b.odds * 100).toInt()
                        perHundred * b.amount / 100
                    }
                    bets[i] = b.copy(settled = true, won = won, refund = refund)
                    betsChanged = true
                }
                if (betsChanged) BetRepository.saveAll(context, bets)

                // ── 2) C'観測ログの未突合分を結果と突合（¥100仮定）────────
                val picks = PickLogRepository.load(context).toMutableList()
                var picksChanged = false
                for (i in picks.indices) {
                    val p = picks[i]
                    if (p.settled) continue
                    val result = ResultRepository.fetchResult(
                        p.stadium, p.raceNo, p.date.filter { it.isDigit() }
                    ) ?: continue
                    val won = result.first == p.lane
                    val refund = if (!won) 0 else (result.payoutWin ?: (p.odds * 100).toInt())
                    picks[i] = p.copy(settled = true, won = won, refund = refund)
                    picksChanged = true
                }
                if (picksChanged) PickLogRepository.saveAll(context, picks)

                // ── 3) 集計して確定表示 ─────────────────────────────────
                _uiState.value = buildState(bets, picks, loading = false)
            }
        }
    }

    /** 記録一覧から表示用の状態を組み立てる（即時表示・確定表示の共通処理） */
    private fun buildState(
        bets: List<com.example.kyoteiai.data.BetRecord>,
        picks: List<PickRecord>,
        loading: Boolean
    ): LedgerUiState {
        val settledBets = bets.filter { it.settled }
        val realLine = LedgerLine(
            label = "実戦（買った記録）",
            points = settledBets.size,
            hits = settledBets.count { it.won == true },
            invest = settledBets.sumOf { it.amount },
            refund = settledBets.sumOf { it.refund },
            pending = bets.size - settledBets.size
        )
        val settledPicks = picks.filter { it.settled }
        val virtualLine = LedgerLine(
            label = "C'仮想（観測全部を¥100）",
            points = settledPicks.size,
            hits = settledPicks.count { it.won == true },
            invest = settledPicks.size * 100,
            refund = settledPicks.sumOf { it.refund },
            pending = picks.size - settledPicks.size
        )
        return LedgerUiState(
            loading = loading,
            real = realLine,
            virtual = virtualLine,
            stageText = buildStageText(realLine),
            // 通知履歴: C'成立の観測記録を新しい順に（日付降順→観測時刻降順）
            history = picks.sortedWith(
                compareByDescending<PickRecord> { it.date }
                    .thenByDescending { it.observedAt }
            )
        )
    }

    /**
     * ステージ判定の文言を作る（検討書 第2部のS0/S1基準をそのまま機械適用）。
     * 判断を感情から切り離すため、基準と現在地を数字で明示する。
     */
    private fun buildStageText(real: LedgerLine): String {
        val n = real.points
        val rate = real.ratePercent
        if (n == 0) {
            return "ステージ: S0（紙トレ中）。実戦記録はまだありません。" +
                "S1開始は4月分の再現確認後を推奨。"
        }
        val rateText = "%.1f%%".format(rate)
        return when {
            // 降格ライン: 200点時点で90%未満
            n >= EvPolicy.S1_DEMOTE_BETS && rate < EvPolicy.S1_DEMOTE_RATE ->
                "ステージ判定: 実戦${n}点で回収率$rateText → 降格ライン" +
                    "（${EvPolicy.S1_DEMOTE_BETS}点で${EvPolicy.S1_DEMOTE_RATE}%未満）に該当。紙トレ(S0)へ戻す。"
            // 昇格ライン: 300点で105%超
            n >= EvPolicy.S1_PROMOTE_BETS && rate > EvPolicy.S1_PROMOTE_RATE ->
                "ステージ判定: 実戦${n}点で回収率$rateText → S2（増額）昇格条件" +
                    "（${EvPolicy.S1_PROMOTE_BETS}点で${EvPolicy.S1_PROMOTE_RATE}%超）を達成。"
            else ->
                "ステージ: S1（少額実戦）。実戦${n}点・回収率$rateText。" +
                    "昇格まであと${maxOf(0, EvPolicy.S1_PROMOTE_BETS - n)}点" +
                    "（${EvPolicy.S1_PROMOTE_BETS}点で${EvPolicy.S1_PROMOTE_RATE}%超が条件）。"
        }
    }
}

/** 収支1本分（実戦 or C'仮想）。pending=結果待ちの記録数 */
data class LedgerLine(
    val label: String,
    val points: Int,     // 決着済み点数
    val hits: Int,       // 的中数
    val invest: Int,     // 投資額（円）
    val refund: Int,     // 払戻額（円）
    val pending: Int     // 結果待ち
) {
    /** 回収率（%）。投資0なら0 */
    val ratePercent: Double get() = if (invest > 0) refund * 100.0 / invest else 0.0
    /** 的中率（%）。0点なら0 */
    val hitPercent: Double get() = if (points > 0) hits * 100.0 / points else 0.0
}

/** 収支セクションの状態。history=EV通知（C'成立観測）の履歴・新しい順 */
data class LedgerUiState(
    val loading: Boolean = false,
    val real: LedgerLine = LedgerLine("実戦（買った記録）", 0, 0, 0, 0, 0),
    val virtual: LedgerLine = LedgerLine("C'仮想（観測全部を¥100）", 0, 0, 0, 0, 0),
    val stageText: String = "",
    val history: List<PickRecord> = emptyList()
)
