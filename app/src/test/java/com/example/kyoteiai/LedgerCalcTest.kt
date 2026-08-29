package com.example.kyoteiai

import com.example.kyoteiai.data.PickRecord
import com.example.kyoteiai.ui.LedgerCalc
import com.example.kyoteiai.ui.LedgerLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 収支計算（判定対象フィルタ・仮想収支・ステージ文言）のテスト。
 *
 * ここが狂うと「まだS0なのにS1と表示される」「参考の◎を昇格判定に数える」といった
 * 判断の土台そのものが誤るので、純関数として切り出して機械的に確認する。
 */
class LedgerCalcTest {

    /** テスト用の観測記録を作るヘルパー（既定は判定対象に入る値） */
    private fun rec(
        date: String = "2026-08-29",
        odds: Double = 3.0,
        deadline: String? = "14:00",
        mins: Int? = 3,
        settled: Boolean = true,
        won: Boolean = false,
        refund: Int = 0
    ) = PickRecord(
        date = date,
        stadium = "24",
        stadiumName = "大村",
        raceNo = 1,
        lane = 1,
        prob = 0.5,
        odds = odds,
        ev = 0.5 * odds,
        observedAt = "13:57",
        settled = settled,
        won = if (settled) won else null,
        refund = refund,
        mins = mins,
        deadline = deadline
    )

    @Test
    fun 判定対象だけを抜き出す() {
        val picks = listOf(
            rec(),                       // 対象
            rec(odds = 8.0),             // 参考（5倍超）
            rec(deadline = "19:30"),     // 参考（ナイター）
            rec(mins = 9),               // 締切から離れている
            rec(date = "2026-07-01"),    // ルール確定前
            rec(mins = null)             // データ不足（v2.0より前の記録）
        )
        assertEquals(1, LedgerCalc.judgeTargets(picks).size)
    }

    @Test
    fun データ不足の件数を数える() {
        val picks = listOf(rec(), rec(mins = null), rec(deadline = null))
        assertEquals(2, LedgerCalc.dataShortageCount(picks))
    }

    @Test
    fun 仮想収支は決着分だけを百円単位で集計する() {
        val picks = listOf(
            rec(settled = true, won = true, refund = 340),   // 的中 +240
            rec(settled = true, won = false, refund = 0),    // 外れ
            rec(settled = false)                             // 結果待ち（集計に入れない）
        )
        val line = LedgerCalc.tally("テスト", picks)
        assertEquals(2, line.points)
        assertEquals(1, line.hits)
        assertEquals(200, line.invest)   // 決着2点 × ¥100
        assertEquals(340, line.refund)
        assertEquals(1, line.pending)
        assertEquals(170.0, line.ratePercent, 0.001)
    }

    @Test
    fun 結果待ちだけなら回収率はゼロ扱いで落ちない() {
        val line = LedgerCalc.tally("テスト", listOf(rec(settled = false)))
        assertEquals(0, line.points)
        assertEquals(0.0, line.ratePercent, 0.001)
    }

    @Test
    fun 実戦を一点買っただけではS1にしない() {
        // 【この修正の本題】以前は実戦記録が1点あるだけで「ステージ: S1（少額実戦）」と
        // 表示していた。判定対象の標本が0点なら、実戦を何点買っていてもS0のまま。
        val judge = LedgerLine("判定対象のみ", 0, 0, 0, 0, 0)
        val text = LedgerCalc.stageText(judge, realPoints = 1)
        assertTrue(text.contains("S0"))
        assertTrue(!text.contains("S1（少額実戦）"))
        // 実戦の点数は参考として添えるだけ
        assertTrue(text.contains("参考"))
    }

    @Test
    fun 標本が集まるまではS0で残り点数を示す() {
        val judge = LedgerLine("判定対象のみ", 120, 40, 12000, 12600, 0)
        val text = LedgerCalc.stageText(judge, realPoints = 0)
        assertTrue(text.contains("S0"))
        assertTrue(text.contains("判定対象120点"))
        assertTrue(text.contains("あと180点"))   // 300 - 120
    }

    @Test
    fun 三百点で百五パーセント超なら昇格条件の達成を示す() {
        // 300点・回収率110%（投資30000・払戻33000）
        val judge = LedgerLine("判定対象のみ", 300, 100, 30000, 33000, 0)
        val text = LedgerCalc.stageText(judge, realPoints = 0)
        assertTrue(text.contains("S1昇格条件"))
        assertTrue(text.contains("300"))
    }

    @Test
    fun 二百点で九十パーセント未満なら降格ラインを示す() {
        // 200点・回収率80%（投資20000・払戻16000）
        val judge = LedgerLine("判定対象のみ", 200, 50, 20000, 16000, 0)
        val text = LedgerCalc.stageText(judge, realPoints = 0)
        assertTrue(text.contains("降格ライン"))
    }
}
