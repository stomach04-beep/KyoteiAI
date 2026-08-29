package com.example.kyoteiai

import com.example.kyoteiai.data.CollectionWindow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 収集の時間帯（〜21:30）と通知の時間帯（〜21:00）が別々に効いているかのテスト。
 *
 * ここが1本にまとまっていたせいで、ナイター最終レース（締切21時台）の
 * 締切前オッズが毎日まるごと欠測していた。「収集は続くが通知は止まる」
 * 21:00〜21:29 の帯がいちばん大事なので必ず確認する。
 */
class CollectionWindowTest {

    private fun at(hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(2026, 8, 29, hour, minute)

    @Test
    fun 昼間は収集も通知もできる() {
        assertTrue(CollectionWindow.canCollect(at(8, 0)))
        assertTrue(CollectionWindow.canNotify(at(8, 0)))
        assertTrue(CollectionWindow.canCollect(at(14, 30)))
        assertTrue(CollectionWindow.canNotify(at(14, 30)))
    }

    @Test
    fun 早朝はどちらも動かない() {
        assertFalse(CollectionWindow.canCollect(at(7, 59)))
        assertFalse(CollectionWindow.canNotify(at(7, 59)))
        assertFalse(CollectionWindow.canCollect(at(3, 0)))
    }

    @Test
    fun 二十一時台は収集だけ続き通知は止まる() {
        // ここが v2.0 の本題。ナイター最終レースの締切前オッズを拾うための帯
        assertTrue(CollectionWindow.canCollect(at(21, 0)))
        assertFalse(CollectionWindow.canNotify(at(21, 0)))
        assertTrue(CollectionWindow.canCollect(at(21, 29)))
        assertFalse(CollectionWindow.canNotify(at(21, 29)))
    }

    @Test
    fun 二十一時三十分で収集も終わる() {
        assertFalse(CollectionWindow.canCollect(at(21, 30)))
        assertFalse(CollectionWindow.canCollect(at(22, 0)))
        assertFalse(CollectionWindow.canNotify(at(21, 30)))
    }

    @Test
    fun 通知の終わりは二十時五十九分まで() {
        assertTrue(CollectionWindow.canNotify(at(20, 59)))
    }
}
