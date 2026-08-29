package com.example.kyoteiai

import com.example.kyoteiai.data.EvPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1昇格判定に数える標本（判定対象）の判別テスト。
 *
 * PC側 settle_odds.py の judge_summary と同じ4条件で絞れているかを確認する。
 *   ① PROTOCOL_START（2026-07-16）以降
 *   ② 締切5分前以内
 *   ③ 表示オッズ ≦5倍
 *   ④ 締切が17時前（昼）
 *
 * ここがズレると、画面の回収率とPCの集計が別々の母集団を数えることになり、
 * 「どちらが本当か」が誰にも分からなくなる。
 */
class JudgeTargetRecordTest {

    @Test
    fun 四条件を全部満たせば判定対象() {
        assertTrue(EvPolicy.isJudgeTargetRecord("2026-08-29", 3.4, "14:30", 3))
        // 境界値もすべて対象に入る（5倍ちょうど・5分ちょうど・16:59）
        assertTrue(EvPolicy.isJudgeTargetRecord("2026-07-16", 5.0, "16:59", 5))
        // 締切0分前（締切ちょうど）も対象（マイナスでなければよい）
        assertTrue(EvPolicy.isJudgeTargetRecord("2026-08-29", 2.0, "12:00", 0))
    }

    @Test
    fun ルール確定前の記録は数えない() {
        // 2026-07-15 以前は条件選びに使った標本なので、数えると自己採点になる
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-07-15", 3.0, "14:00", 3))
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-06-01", 3.0, "14:00", 3))
    }

    @Test
    fun 締切から離れた記録は数えない() {
        // 6分前は層が違う（旧PC収集の9〜11分前と混ざると減衰の幅が濁る）
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-08-29", 3.0, "14:00", 6))
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-08-29", 3.0, "14:00", 11))
        // 締切超過（マイナス）は確定オッズなので数えない
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-08-29", 3.0, "14:00", -1))
    }

    @Test
    fun 五倍超とナイターは数えない() {
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-08-29", 5.1, "14:00", 3))
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-08-29", 3.0, "17:00", 3))
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-08-29", 3.0, "20:41", 3))
    }

    @Test
    fun 分数や締切を持たない古い記録はデータ不足で対象外() {
        // v2.0 より前の記録は mins も deadline も持たない。
        // 0 を既定値にして「締切0分前」と誤読すると、判定対象に紛れ込んで数字が汚れる。
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-08-29", 3.0, "14:00", null))
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-08-29", 3.0, null, 3))
        assertFalse(EvPolicy.isJudgeTargetRecord("2026-08-29", 3.0, null, null))
    }
}
