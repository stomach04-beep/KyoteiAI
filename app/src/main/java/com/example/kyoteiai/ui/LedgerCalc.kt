package com.example.kyoteiai.ui

import com.example.kyoteiai.data.EvPolicy
import com.example.kyoteiai.data.PickRecord

/**
 * 収支まわりの計算（純関数だけを集めた置き場）。
 *
 * ViewModel から計算を切り離してあるのは次の2つの理由による。
 *  1. Context もネットワークも要らない＝そのまま単体テストできる
 *  2. 「判定対象とは何か」の条件を EvPolicy の1本に寄せ、画面側で書き写さない
 *     （同じ意味の判定を2か所に書くと必ずズレる。DRY／単一の真実の源）
 */
object LedgerCalc {

    /**
     * S1昇格判定に数える標本（判定対象）だけを抜き出す。
     * 条件は EvPolicy.isJudgeTargetRecord ＝ PC側 settle_odds.py judge_summary と同一。
     */
    fun judgeTargets(picks: List<PickRecord>): List<PickRecord> =
        picks.filter { EvPolicy.isJudgeTargetRecord(it.date, it.odds, it.deadline, it.mins) }

    /**
     * 判定に必要な情報（締切までの分数）を持たない記録の数。
     *
     * mins/deadline は v2.0 から記録し始めたので、それ以前の記録は
     * 「締切5分前以内だったか」を確認できず、判定対象に入れられない。
     * 画面に「対象外・データ不足 N件」と正直に出すための数え上げ。
     */
    fun dataShortageCount(picks: List<PickRecord>): Int =
        picks.count { it.mins == null || it.deadline == null }

    /**
     * ¥100固定で買ったと仮定した収支を1本にまとめる（決着済みのみ集計）。
     *
     * @param label 表示名
     * @param picks 対象の観測記録
     */
    fun tally(label: String, picks: List<PickRecord>): LedgerLine {
        val settled = picks.filter { it.settled }
        return LedgerLine(
            label = label,
            points = settled.size,
            hits = settled.count { it.won == true },
            invest = settled.size * EvPolicy.DEFAULT_BET_AMOUNT,
            refund = settled.sumOf { it.refund },
            pending = picks.size - settled.size
        )
    }

    /**
     * ステージ判定の文言を作る（検討書 第2部のS0/S1基準を機械適用）。
     *
     * 【2026-08-29 修正の要点】
     *  以前は「実戦記録が1点でもあれば S1（少額実戦）」と表示していた。
     *  だが昇格・降格を判定する物差しは実戦の記録数ではなく、
     *  事前登録した判定対象（C'≦5倍・昼・締切5分前以内・PROTOCOL_START以降）の
     *  前向き標本である（PC側 judge_summary と同じ）。
     *  試しに1点買っただけでステージ表示が S1 に変わるのは、
     *  「今どの段階にいるか」を誤って伝えるので判定対象の点数で判断する。
     *
     * @param judge      判定対象だけを集計した収支
     * @param realPoints 実戦（買った記録）の決着済み点数。参考として文末に添える
     */
    fun stageText(judge: LedgerLine, realPoints: Int): String {
        val n = judge.points
        val rate = judge.ratePercent
        val rateText = "%.1f%%".format(rate)
        // 実戦記録は「参考」。ステージ判定そのものには使わない
        val realNote = if (realPoints > 0) "　※実戦の買い記録${realPoints}点は参考値です。" else ""

        return when {
            // 降格ライン: 判定対象200点時点で90%未満
            n >= EvPolicy.S1_DEMOTE_BETS && rate < EvPolicy.S1_DEMOTE_RATE ->
                "ステージ判定: 判定対象${n}点で回収率$rateText → 降格ライン" +
                    "（${EvPolicy.S1_DEMOTE_BETS}点で${EvPolicy.S1_DEMOTE_RATE}%未満）に該当。紙トレ(S0)を継続する。$realNote"
            // 昇格ライン: 判定対象300点で105%超（③の信頼下限はPC側の集計で確認する）
            n >= EvPolicy.S1_PROMOTE_BETS && rate > EvPolicy.S1_PROMOTE_RATE ->
                "ステージ判定: 判定対象${n}点で回収率$rateText → S1昇格条件の①②" +
                    "（${EvPolicy.S1_PROMOTE_BETS}点・${EvPolicy.S1_PROMOTE_RATE}%超）を達成。" +
                    "③日ブロック・ブートストラップの下側90%限界はPC側の集計で確認すること。$realNote"
            // それ以外＝収集中。現方針どおり S0（紙トレ）を継続する
            n == 0 ->
                "ステージ: S0（紙トレ中）。昇格判定に数える標本" +
                    "（${EvPolicy.JUDGE_PROTOCOL_START}以降・締切${EvPolicy.JUDGE_MINS_MAX}分前以内・" +
                    "C'≦${EvPolicy.REGISTERED_ODDS_CAP.toInt()}倍・昼）はまだ0点です。" +
                    "${EvPolicy.S1_PROMOTE_BETS}点まで収集を続けます。$realNote"
            else ->
                "ステージ: S0（紙トレ中・標本を収集中）。判定対象${n}点・回収率$rateText。" +
                    "昇格判定まであと${maxOf(0, EvPolicy.S1_PROMOTE_BETS - n)}点" +
                    "（${EvPolicy.S1_PROMOTE_BETS}点で${EvPolicy.S1_PROMOTE_RATE}%超が条件）。$realNote"
        }
    }
}
