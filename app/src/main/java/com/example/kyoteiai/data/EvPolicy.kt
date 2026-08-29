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

    // ── 事前登録した判定対象戦略（検討書 2-1b・2026-07-16改訂）──────────
    // 昇格判定に数えるのは「C'≦5倍・昼」1本だけ。
    // それ以外のC'成立は観測用（参考）であって、判定には使わない。
    // 結果を見てから戦略を乗り換えるのは多重比較になるため。
    // ※ PC側 settle_odds.py の ODDS_CAP / NIGHT_HOUR と同じ値。
    //   片方だけ変えると集計と画面がズレるので、変えるときは必ず両方直す。

    /** 判定対象に入る表示オッズの上限（減衰が中立な帯） */
    const val REGISTERED_ODDS_CAP = 5.0

    /** ナイター除外の境界。この時刻以降に締切るレースは判定対象外（減衰が最悪） */
    const val REGISTERED_NIGHT_HOUR = 17

    /**
     * この◎が事前登録した判定対象（C'≦5倍・昼）に当たるか。
     *
     * @param odds 表示オッズ
     * @param deadline 締切時刻 "HH:mm"。読めない場合は判定対象外（安全側）
     */
    fun isRegisteredTarget(odds: Double, deadline: String?): Boolean {
        if (odds > REGISTERED_ODDS_CAP) return false
        val hour = deadline?.substringBefore(":")?.toIntOrNull() ?: return false
        return hour < REGISTERED_NIGHT_HOUR
    }

    /** 画面・通知に付ける区別の印。判定対象なら「判定対象」、それ以外は「参考」 */
    fun targetLabel(odds: Double, deadline: String?): String =
        if (isRegisteredTarget(odds, deadline)) "判定対象" else "参考"

    // ── S1昇格判定に数える「記録」の条件（PC側 settle_odds.py judge_summary と同一）──
    // ※ 片方だけ変えると集計と画面がズレる。変えるときは必ずPC側と両方直す。

    /**
     * ルール確定日（PC側 PROTOCOL_START と同値）。
     * これより前の記録は「オッズ≦5倍」「昼のみ」の条件選びに使った標本なので、
     * 同じ標本で採点すると自己採点（必ず良く見える）になるため数えない。
     */
    const val JUDGE_PROTOCOL_START = "2026-07-16"

    /**
     * 締切この分数前以内の記録だけ数える（PC側 JUDGE_MINS_MAX と同値）。
     * 旧PC収集（9〜11分前）はオッズ減衰の幅が別物で、混ぜると数字が濁る。
     */
    const val JUDGE_MINS_MAX = 5

    /**
     * 観測記録1件が「S1昇格判定の標本（判定対象）」に入るか。
     * 条件は4つとも必要（PC側 settle_odds.py judge_summary と同じ順・同じ値）:
     *   ① PROTOCOL_START以降  ② 締切 JUDGE_MINS_MAX 分前以内
     *   ③ 表示オッズ ≦ REGISTERED_ODDS_CAP  ④ 締切が REGISTERED_NIGHT_HOUR 時前（昼）
     *
     * 純関数（Context非依存）にしてあるのは、画面（LedgerViewModel）と
     * 単体テストが同じ1本の定義を見るため。条件を画面側に書き写すと必ずズレる。
     *
     * @param date     記録日 "2026-07-16"
     * @param odds     観測時の単勝オッズ
     * @param deadline 締切時刻 "HH:mm"。v2.0より前の記録は持たない（null）→ 判定対象外
     * @param mins     観測時点の締切までの分数。v2.0より前の記録は持たない（null）
     *                 → 締切≦5分を確認できないので判定対象外（データ不足・安全側）
     */
    fun isJudgeTargetRecord(date: String, odds: Double, deadline: String?, mins: Int?): Boolean {
        // ① ルール確定前の標本は自己採点になるので数えない
        if (date < JUDGE_PROTOCOL_START) return false
        // ② 締切5分前以内の層に統一（旧PC収集の9〜11分前とは減衰の幅が別物）。
        //    mins を持たない古い記録はここで落ちる＝「対象外・データ不足」扱い
        if (mins == null || mins < 0 || mins > JUDGE_MINS_MAX) return false
        // ③④ C'≦5倍・昼（deadline が null / 壊れていれば安全側で対象外）
        return isRegisteredTarget(odds, deadline)
    }

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
