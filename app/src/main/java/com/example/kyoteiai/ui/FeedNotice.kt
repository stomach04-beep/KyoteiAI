package com.example.kyoteiai.ui

import com.example.kyoteiai.data.Feed
import com.example.kyoteiai.ui.kit.Tone

/**
 * 2026-11-11 状態の出し方（APP_RULES 3-c・UiKit 6章）を今日タブに当てるための判定。
 * 画面を描く所と分け、今日の日付は引数で受け取る純粋な関数にして単体試験（FeedNoticeTest）で固定する。
 * 見本の画面（デバッグ版の DesignPreviewActivity）も本物と同じこの関数で知らせを作る。
 */

/** 今日タブの上に出す知らせ。null なら出さない */
internal data class FeedNotice(
    val title: String,
    val detail: String,
    val tone: Tone,
    val actionLabel: String?,
)

/**
 * 予想データの状態から、今日タブの上に出す知らせを決める。
 *
 * - 今日の予想が取れている → 出さない（取り直し中でも出さない）
 * - 初めての読み込み中で、まだ何も無い → 出さない（読み込みの印だけ）
 * - 何も取れない → 「予想を取得できませんでした」
 * - 通信できず、アプリに入っている見本を表示中（fromRemote=false）→ 見本の日付を書く（前回の値ではない）
 * - 取得はできたが日付が今日でない → 予想を作る側（PC）が止まっている。通信の失敗とは文を分ける
 * - 取り直し中は題名を「予想を取り直しています」、説明を「いま何を表示しているか」にしてボタンを隠す（二重に押させない）
 *
 * @param feedDate 表示中の予想の日付（"2026-07-08"）。予想が無ければ null
 * @param fromRemote 表示中の予想をネットから取れたか（false＝アプリに入っている見本）
 * @param loading 取得中か
 * @param today 今日の日付（"2026-11-11"）
 */
internal fun feedNotice(feedDate: String?, fromRemote: Boolean, loading: Boolean, today: String): FeedNotice? {
    // 今日の予想があれば何も出さない
    if (feedDate != null && feedDate == today) return null
    // 初めての読み込み中（まだ何も無い）は印だけ
    if (feedDate == null && loading) return null

    // 説明は2行まで（APP_RULES H1）。日本語はどの文字の間でも折れて「入ってい／る」のように語が割れたので
    // （2026-11-11 見た目の点検）、1行を短くして区切りの所で改行を入れる
    val title: String
    val detail: String
    val showing: String // 取り直し中にも残す「いま何を表示しているか」
    when {
        feedDate == null -> {
            title = "予想を取得できませんでした"
            detail = "通信の状態を確かめて、\nもう一度お試しください。"
            showing = "しばらくお待ちください。"
        }
        !fromRemote -> {
            title = "予想を取得できませんでした"
            // 「見本」＝アプリに入っている固定のデータ（前回の値ではない）
            showing = "${shortDate(feedDate)} の見本を表示中です。"
            detail = "通信できないため、\n$showing"
        }
        else -> {
            title = "今日の予想がまだありません"
            showing = "${shortDate(feedDate)} の予想を表示中です。"
            detail = "$showing\n締切の表示は当てになりません。"
        }
    }
    return if (loading) {
        // 取り直し中は題名も替える（「取得できませんでした」のままだと説明と食い違う・2026-11-11 見た目の点検）
        FeedNotice("予想を取り直しています", showing, Tone.CAUTION, actionLabel = null)
    } else {
        FeedNotice(title, detail, Tone.CAUTION, actionLabel = "再試行")
    }
}

/** "2026-07-08" → "7/8"。形が違えばそのまま返す */
internal fun shortDate(date: String): String {
    val parts = date.split("-")
    val m = parts.getOrNull(1)?.toIntOrNull()
    val d = parts.getOrNull(2)?.toIntOrNull()
    return if (parts.size == 3 && m != null && d != null) "$m/$d" else date
}

/**
 * 取り直しの結果を今の状態に重ねる（純粋な関数）。
 * 取り直しに失敗したら、前にネットから取れていた予想を残す。
 * アプリに入っている見本（7月の固定データ）や「何も無い」で上書きすると、見ていた予想が消えて
 * 古い見本が「本日のレース」の場所に出てしまうため。
 */
internal fun mergeFeedResult(prev: FeedUiState, feed: Feed?, fromRemote: Boolean): FeedUiState {
    val hadRemote = prev.feed != null && prev.fromRemote
    if (hadRemote && (feed == null || !fromRemote)) return prev.copy(loading = false)
    if (feed == null && prev.feed != null) return prev.copy(loading = false)
    return FeedUiState(loading = false, feed = feed, fromRemote = fromRemote)
}
