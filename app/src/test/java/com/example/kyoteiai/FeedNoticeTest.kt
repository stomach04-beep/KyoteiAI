package com.example.kyoteiai

import com.example.kyoteiai.data.Feed
import com.example.kyoteiai.ui.FeedUiState
import com.example.kyoteiai.ui.feedNotice
import com.example.kyoteiai.ui.mergeFeedResult
import com.example.kyoteiai.ui.shortDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-11 今日タブの知らせの判定（feedNotice）と、取り直しの結果の重ね方（mergeFeedResult）を固定する試験
 */
class FeedNoticeTest {

    private val today = "2026-11-11"

    // 試験用の予想（中身は日付だけ見る）
    private fun feed(date: String) = Feed(date, "", "v3", "ok", 3, emptyList(), emptyList())

    @Test fun 今日の予想があれば知らせは出さない() {
        assertNull(feedNotice(today, fromRemote = true, loading = false, today = today))
        // 取り直し中でも出さない
        assertNull(feedNotice(today, fromRemote = true, loading = true, today = today))
    }

    @Test fun 初めての読み込み中は知らせを出さない() {
        assertNull(feedNotice(null, fromRemote = false, loading = true, today = today))
    }

    @Test fun 何も取れないときは再試行つき() {
        val n = feedNotice(null, fromRemote = false, loading = false, today = today)
        assertNotNull(n)
        assertEquals("予想を取得できませんでした", n!!.title)
        assertEquals("再試行", n.actionLabel)
    }

    @Test fun 見本を表示中は見本の日付を書く() {
        val n = feedNotice("2026-07-08", fromRemote = false, loading = false, today = today)!!
        assertEquals("予想を取得できませんでした", n.title)
        assertTrue(n.detail.contains("7/8 の見本を表示中"))
        // 説明は2行まで（H1）
        assertTrue(n.detail.lines().size <= 2)
        assertEquals("再試行", n.actionLabel)
    }

    @Test fun 取れたが今日でないときは通信の失敗と文を分ける() {
        val n = feedNotice("2026-11-10", fromRemote = true, loading = false, today = today)!!
        assertEquals("今日の予想がまだありません", n.title)
        assertTrue(n.detail.startsWith("11/10 の予想を表示中"))
        assertEquals("再試行", n.actionLabel)
    }

    @Test fun 取り直し中は題名を替えボタンを隠す() {
        val n = feedNotice("2026-07-08", fromRemote = false, loading = true, today = today)!!
        assertEquals("予想を取り直しています", n.title)
        assertEquals("7/8 の見本を表示中です。", n.detail)
        assertNull(n.actionLabel)
        // 何も無いときの取り直し
        val m = feedNotice(null, fromRemote = false, loading = true, today = today)
        assertNull(m) // 初めての読み込みと同じ扱い（印だけ）
    }

    @Test fun どの説明も2行まで() {
        listOf(
            feedNotice(null, false, false, today),
            feedNotice("2026-07-08", false, false, today),
            feedNotice("2026-11-10", true, false, today),
            feedNotice("2026-11-10", true, true, today),
        ).forEach { assertTrue(it!!.detail.lines().size <= 2) }
    }

    @Test fun 日付を短くする() {
        assertEquals("7/8", shortDate("2026-07-08"))
        assertEquals("11/10", shortDate("2026-11-10"))
        assertEquals("abc", shortDate("abc"))
    }

    @Test fun 取り直しに失敗したらネットから取れていた予想を残す() {
        val prev = FeedUiState(loading = true, feed = feed("2026-11-10"), fromRemote = true)
        // 見本に落ちた
        val a = mergeFeedResult(prev, feed("2026-07-08"), fromRemote = false)
        assertEquals("2026-11-10", a.feed!!.date)
        assertTrue(a.fromRemote)
        assertEquals(false, a.loading)
        // 何も取れない
        val b = mergeFeedResult(prev, null, fromRemote = false)
        assertSame(prev.feed, b.feed)
        assertEquals(false, b.loading)
    }

    @Test fun 見本を表示中に何も取れなければ見本のまま() {
        val prev = FeedUiState(loading = true, feed = feed("2026-07-08"), fromRemote = false)
        val r = mergeFeedResult(prev, null, fromRemote = false)
        assertEquals("2026-07-08", r.feed!!.date)
        assertEquals(false, r.loading)
    }

    @Test fun 取り直しで今日の予想が取れたら置き換える() {
        val prev = FeedUiState(loading = true, feed = feed("2026-07-08"), fromRemote = false)
        val r = mergeFeedResult(prev, feed(today), fromRemote = true)
        assertEquals(today, r.feed!!.date)
        assertTrue(r.fromRemote)
        assertEquals(false, r.loading)
    }
}
