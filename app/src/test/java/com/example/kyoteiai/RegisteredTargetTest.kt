package com.example.kyoteiai

import com.example.kyoteiai.data.EvPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 事前登録した判定対象（C'≦5倍・昼）の判別テスト。
 *
 * 昇格判定に数えるのはこの1本だけ。区別を間違えると、
 * 判定に使わない「参考」の◎を同じ重みで買ってしまう。
 */
class RegisteredTargetTest {

    @Test
    fun 五倍以下かつ十七時前は判定対象() {
        assertTrue(EvPolicy.isRegisteredTarget(2.0, "10:30"))
        assertTrue(EvPolicy.isRegisteredTarget(5.0, "16:59"))
        assertEquals("判定対象", EvPolicy.targetLabel(3.4, "12:00"))
    }

    @Test
    fun 五倍を超えたら参考() {
        assertFalse(EvPolicy.isRegisteredTarget(5.1, "10:30"))
        assertEquals("参考", EvPolicy.targetLabel(8.0, "10:30"))
    }

    @Test
    fun 十七時以降のナイターは参考() {
        assertFalse(EvPolicy.isRegisteredTarget(2.0, "17:00"))
        assertFalse(EvPolicy.isRegisteredTarget(2.0, "20:41"))
        assertEquals("参考", EvPolicy.targetLabel(2.0, "18:30"))
    }

    @Test
    fun 締切が読めなければ安全側で参考にする() {
        assertFalse(EvPolicy.isRegisteredTarget(2.0, null))
        assertFalse(EvPolicy.isRegisteredTarget(2.0, ""))
        assertFalse(EvPolicy.isRegisteredTarget(2.0, "こわれた"))
        assertEquals("参考", EvPolicy.targetLabel(2.0, null))
    }
}
