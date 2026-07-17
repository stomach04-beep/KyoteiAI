package com.example.kyoteiai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kyoteiai.data.EvPolicy
import com.example.kyoteiai.data.OddsRepository
import com.example.kyoteiai.data.RacePred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * オッズ取得と期待値（EV）計算のUI状態を管理する ViewModel。
 *
 * レース詳細画面の「オッズ取得して期待値を見る」ボタンから fetch() を呼ぶ。
 * ネットワーク取得は OddsRepository（Dispatchers.IO）に委譲し、
 * 取得できたオッズとフィードのモデル確率(prob)から EV を計算して StateFlow へ流す。
 */
class OddsViewModel(app: Application) : AndroidViewModel(app) {

    // 買い判定のしきい値・人気薄除外の下限確率は EvPolicy の単一定義を参照する
    // （通知Worker・収支計算と同じ値。二重定義するとズレるためここでは再定義しない）
    // 実オッズ検証: C'は 2026-06=122.1% / 2026-05=145.1% と両月で唯一安定して100%超
    private val buyThreshold = EvPolicy.BUY_THRESHOLD
    private val minProbForPick = EvPolicy.MIN_PROB_FOR_PICK

    // 画面が参照する読み取り専用の状態
    private val _uiState = MutableStateFlow(OddsUiState())
    val uiState: StateFlow<OddsUiState> = _uiState.asStateFlow()

    /** 別レースを開いたときに前回のオッズ結果を消す */
    fun reset() {
        _uiState.value = OddsUiState()
    }

    /**
     * オッズを取得して期待値を計算する。
     *
     * @param race    対象レース（boats のモデル確率を EV 計算に使う）
     * @param dateYmd 日付8桁（例 "20260708"）
     */
    fun fetch(race: RacePred, dateYmd: String) {
        _uiState.value = OddsUiState(loading = true)
        viewModelScope.launch {
            val result = OddsRepository.fetchOdds(race.stadium, race.raceNo, dateYmd)
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

            // 未発売
            if (result.notOnSale) {
                _uiState.value = OddsUiState(notOnSale = true, fetchedAt = now)
                return@launch
            }

            // 号艇→モデル確率のマップ（フィードの prob。レース内で正規化済み）
            val probByLane = race.boats.associate { it.lane to it.prob }

            // ── 単勝EV ──
            val basicRows = (1..6).map { lane ->
                val prob = probByLane[lane] ?: 0.0
                val odds = result.winOdds[lane]
                val ev = if (odds != null) prob * odds else null
                WinEvRow(
                    lane = lane,
                    prob = prob,
                    odds = odds,
                    ev = ev,
                    buy = ev != null && ev >= buyThreshold,
                    // EVは高いが確率が低い「人気薄」＝モデルノイズの可能性が高く盲信は危険
                    longshot = ev != null && ev >= buyThreshold && prob < minProbForPick,
                    isTopPick = false
                )
            }
            // 「◎1点」＝そのレースでEV最大の1艇。EV≥1.2かつ確率≥0.2（人気薄除外）を
            // 満たすときだけ立てる（6月検証で最も信頼できた買い方）。満たさないレースは見送り
            val topPickLane = basicRows
                .filter { it.ev != null }
                .maxByOrNull { it.ev!! }
                ?.takeIf { it.ev!! >= buyThreshold && it.prob >= minProbForPick }
                ?.lane
            val winRows = basicRows.map {
                if (it.lane == topPickLane) it.copy(isTopPick = true) else it
            }

            // ── 3連単EV（ハービル式で並び確率を出し、オッズを掛ける）──
            val trifectaRows = result.trifectaOdds.mapNotNull { (combo, odds) ->
                val (i, j, k) = combo.split("-").map { it.toInt() }
                // ハービル式の並び確率（異常値は null → 除外）
                val prob = harvilleProb(probByLane, i, j, k) ?: return@mapNotNull null
                val ev = prob * odds
                TrifectaEvRow(
                    combo = combo,
                    prob = prob,
                    odds = odds,
                    ev = ev,
                    buy = ev >= buyThreshold
                )
            }
                .sortedByDescending { it.ev } // EV降順
                .take(10)                     // 上位10件だけ表示

            _uiState.value = OddsUiState(
                loaded = true,
                winRows = winRows,
                trifectaRows = trifectaRows,
                fetchedAt = now
            )
        }
    }

    /**
     * ハービル式で並び確率 P(i→j→k) を求める。
     *   P = p_i × p_j/(1−p_i) × p_k/(1−p_i−p_j)
     * 分母が0以下になる異常値は null を返す（除外）。
     */
    private fun harvilleProb(
        probByLane: Map<Int, Double>,
        i: Int, j: Int, k: Int
    ): Double? {
        val pi = probByLane[i] ?: return null
        val pj = probByLane[j] ?: return null
        val pk = probByLane[k] ?: return null
        val denom1 = 1.0 - pi
        val denom2 = 1.0 - pi - pj
        if (denom1 <= 0.0 || denom2 <= 0.0) return null
        return pi * (pj / denom1) * (pk / denom2)
    }
}

/**
 * オッズ画面の状態。
 * loading=取得中 / loaded=取得成功 / notOnSale=未発売 / fetchedAt=取得時刻(HH:mm)。
 */
data class OddsUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val notOnSale: Boolean = false,
    val winRows: List<WinEvRow> = emptyList(),
    val trifectaRows: List<TrifectaEvRow> = emptyList(),
    val fetchedAt: String? = null
)

/**
 * 単勝EVの1行（号艇・モデル確率・単勝オッズ・EV・買い判定）。odds/ev は欠場時 null。
 * isTopPick=そのレースの「◎1点」（EV最大かつEV≥1.2かつ確率≥0.2）、
 * longshot=EV≥1.2だが確率が低い人気薄（ノイズ疑い・注意表示）。
 */
data class WinEvRow(
    val lane: Int,
    val prob: Double,
    val odds: Double?,
    val ev: Double?,
    val buy: Boolean,
    val longshot: Boolean = false,
    val isTopPick: Boolean = false
)

/** 3連単EVの1行（組番・並び確率・3連単オッズ・EV・買い判定）。 */
data class TrifectaEvRow(
    val combo: String,
    val prob: Double,
    val odds: Double,
    val ev: Double,
    val buy: Boolean
)
