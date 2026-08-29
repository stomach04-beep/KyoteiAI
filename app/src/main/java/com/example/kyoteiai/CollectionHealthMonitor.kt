package com.example.kyoteiai

import android.content.SharedPreferences
import android.content.Context
import com.example.kyoteiai.data.RunLogStore
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 「前日の収集が異常だったか」を朝いちばんに自己点検して知らせるモニター。
 *
 * 【なぜ必要か（2026-07-27 の事故の再発防止）】
 *  締切前オッズの収集が丸一日ゼロになったことがあった。
 *  run_log.json に足跡は残るようになったが、それを読む人が居ないので
 *  「気づいたのは数日後」だった。締切前オッズはその日に取らないと永久に失われるため、
 *  欠測に気づくのが遅れるほど前向き実測データの穴がそのまま固定される。
 *  → 翌朝の初回実行で前日の足跡を見て、明らかに異常なら通知して人に知らせる。
 *
 * 【判定】
 *  runs=0（ワーカーが1度も動いていない＝強制停止・省電力・端末オフ）
 *  または snapOk < MIN_SNAP_OK（動いてはいたが記録がほとんど残っていない）
 *  を異常とする。
 *
 * 【1日1回ガード】
 *  EvPickWorker は15分周期なので、日付キーで「その日はもう点検した」を必ず記録する
 *  （feedback_worker_daily_guard）。
 */
object CollectionHealthMonitor {

    /** 前日の記録数がこの数に満たなければ「異常」とみなす */
    private const val MIN_SNAP_OK = 50

    /**
     * 点検を始める時刻。フィード生成前の早朝に鳴らしても対処できないので、
     * EvPickWorker が動き出す8時台の初回実行で判定する
     */
    private const val CHECK_START_HOUR = 8

    /**
     * 前日欠測アラートの通知ID（固定＝同じ通知を上書き更新する）。
     * 既存の通知IDと衝突しないよう、他とは別の文字列からハッシュを作る。
     *  - EvPickWorker.notifyId は "ev_{場}_{R}" 由来
     *  - FeedHealthMonitor は "kyotei_feed_health_alert" 由来
     */
    private val NOTIFY_ID = "kyotei_collect_health_alert".hashCode()

    /**
     * 前日の収集結果を点検し、異常なら通知する（1日1回だけ）。
     *
     * @param prefs        EvPickWorker と同じ SharedPreferences
     * @param checkDateKey 「点検済みの日付」を覚えるキー（EvPickWorker が所有）
     */
    fun checkYesterday(context: Context, prefs: SharedPreferences, checkDateKey: String) {
        // フィード生成前の早朝は点検しない（前日ぶんの判定自体は可能だが、
        // 通知しても人が対処できない時間帯に鳴らさない）
        if (LocalDateTime.now().hour < CHECK_START_HOUR) return

        val today = LocalDate.now().toString()
        // 1日1回ガード（15分周期で呼ばれるため必須）。
        // 先に日付を書いてから判定する＝通知に失敗しても連打しない
        if (prefs.getString(checkDateKey, null) == today) return
        prefs.edit().putString(checkDateKey, today).apply()

        val yesterday = LocalDate.now().minusDays(1).toString()
        val stats = RunLogStore.dayStats(context, yesterday)

        // 前日の足跡が完全に空（runs=0 かつ 記録0）で、しかも
        // アプリを入れた直後のような「そもそも前日が存在しない」ケースもここに来る。
        // 誤報より見逃しの方が高くつく（データは取り返せない）ので、鳴らす側に倒す。
        val abnormal = stats.runs == 0 || stats.snapOk < MIN_SNAP_OK
        if (!abnormal) return

        val reason = if (stats.runs == 0) {
            "ワーカーが1度も動いていません（端末の省電力・強制停止・電源オフの疑い）。"
        } else {
            "動いてはいましたが記録が${stats.snapOk}件しかありません" +
                "（取得失敗${stats.snapFail}件・締切超過${stats.snapLate}件）。"
        }
        NotificationHelper.sendHotRaceNotification(
            context, NOTIFY_ID,
            "⚠️ 昨日の収集が異常（実行${stats.runs}回・記録${stats.snapOk}件）",
            "$yesterday の締切前オッズ収集が正常に行われませんでした。$reason\n" +
                "締切前オッズはその日に取らないと永久に失われます。" +
                "電池最適化の除外とアプリの起動状態を確認してください。"
        )
    }
}
