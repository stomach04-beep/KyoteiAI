package com.example.kyoteiai

import com.example.kyoteiai.data.TimeUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ウィジェットの「次の締切まであと何分」の選び方のテスト。
 *
 * ウィジェットは通信できないので、EvPickWorker が控えた締切一覧から
 * 描画のたびに次のレースを選び直す。締切済みを飛ばせていないと
 * 「あと -37分」のような表示になって信用を失う。
 */
class WidgetNextRaceTest {

    /** 本日の "HH:mm" をエポックミリ秒に（テスト内の基準時刻を作る） */
    private fun today(hhmm: String): Long = TimeUtil.deadlineToTodayMillis(hhmm)!!

    @Test
    fun 締切済みを飛ばして直近の未締切を選ぶ() {
        val packed = "11:00|大村1R,12:30|徳山2R,13:00|住之江3R"
        val now = today("12:00")
        val next = KyoteiWidgetProvider.nextRace(packed, now)
        assertEquals("徳山2R", next?.second)
        assertEquals(30L, next?.first)
    }

    @Test
    fun 全部締切済みならnull() {
        val packed = "11:00|大村1R,11:30|徳山2R"
        assertNull(KyoteiWidgetProvider.nextRace(packed, today("12:00")))
    }

    @Test
    fun 控えが無い場合もnullで落ちない() {
        assertNull(KyoteiWidgetProvider.nextRace(null, today("12:00")))
        assertNull(KyoteiWidgetProvider.nextRace("", today("12:00")))
    }

    @Test
    fun 壊れた行は読み飛ばして残りから選ぶ() {
        // 途中の1行が壊れていても、そこで諦めずに読める行から選ぶ
        val packed = "こわれた,12:30|徳山2R,,99:99|変な時刻"
        val next = KyoteiWidgetProvider.nextRace(packed, today("12:00"))
        assertEquals("徳山2R", next?.second)
    }

    @Test
    fun 締切ちょうどはまだ次のレースとして扱う() {
        // 0分（締切ちょうど）は「過ぎていない」側。マイナスになって初めて飛ばす
        val next = KyoteiWidgetProvider.nextRace("12:00|大村1R", today("12:00"))
        assertEquals(0L, next?.first)
    }
}
