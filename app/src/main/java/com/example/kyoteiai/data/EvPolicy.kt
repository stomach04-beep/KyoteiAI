package com.example.kyoteiai.data

/**
 * 購入方針（C'戦略）の単一定義。
 *
 * 検討書「購入方針とアプリ仕様」（2026-07-12）に基づく:
 *   C' = そのレースでEV最大の艇が「EV≥1.2 かつ 1着確率≥0.2」のとき、その1艇の単勝を1点買い。
 *   実オッズ検証: 2026-06 = 122.1% ／ 2026-05 = 145.1%（両月とも唯一安定して100%超）。
 *
 * 【重要】しきい値・判定はここだけに置く。
 * 画面表示（OddsViewModel）・通知（EvPickWorker）・収支（LedgerViewModel）は
 * すべてこの定義を参照する（値の二重定義は必ずズレるため。DRY）。
 */
object EvPolicy {

    /** 買いのEVしきい値（EV = 1着確率 × 単勝オッズ がこれ以上で「買い」） */
    const val BUY_THRESHOLD = 1.2

    /** 人気薄除外の下限確率（これ未満の高EVはモデルノイズが乗りやすく買わない） */
    const val MIN_PROB_FOR_PICK = 0.2

    /** 1点あたりの既定購入金額（円）。S1ステージは¥100固定 */
    const val DEFAULT_BET_AMOUNT = 100

    // ── ステージ判定基準（検討書 第2部 2-1）────────────────────────
    /** S1昇格の条件: 実戦この点数で回収率がS1_PROMOTE_RATE超 */
    const val S1_PROMOTE_BETS = 300
    const val S1_PROMOTE_RATE = 105.0
    /** S1降格の条件: 実戦この点数時点で回収率がS1_DEMOTE_RATE未満ならS0へ戻す */
    const val S1_DEMOTE_BETS = 200
    const val S1_DEMOTE_RATE = 90.0

    /**
     * C'判定の結果（そのレースの「◎1点」）。
     * lane=艇番 / prob=モデル1着確率 / odds=単勝オッズ / ev=期待値
     */
    data class TopPick(
        val lane: Int,
        val prob: Double,
        val odds: Double,
        val ev: Double
    )

    /**
     * C'条件で「◎1点」を探す。
     *
     * @param probByLane 号艇→モデル1着確率（フィードのprob）
     * @param winOdds    号艇→単勝オッズ（欠場等はnull）
     * @return C'成立ならその1艇、不成立（見送りレース）ならnull
     */
    fun findTopPick(
        probByLane: Map<Int, Double>,
        winOdds: Map<Int, Double?>
    ): TopPick? {
        // 全艇のEVを計算し、EV最大の艇を探す
        var best: TopPick? = null
        for (lane in 1..6) {
            val prob = probByLane[lane] ?: continue
            val odds = winOdds[lane] ?: continue   // 欠場等は対象外
            val ev = prob * odds
            if (best == null || ev > best.ev) {
                best = TopPick(lane = lane, prob = prob, odds = odds, ev = ev)
            }
        }
        // EV最大の1艇が「EV≥1.2 かつ 確率≥0.2」を満たすときだけ◎（それ以外は見送り）
        return best?.takeIf { it.ev >= BUY_THRESHOLD && it.prob >= MIN_PROB_FOR_PICK }
    }
}
