package com.example.kyoteiai

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 予想フィード取得の健全性を見張るモニター（PanicPilot の DailyCheckWorker と同方式）。
 *
 * 【なぜ必要か】（2026-07-17 監査で判明した問題への対処）
 * HotRace/EvPick の各 Worker はフィードが取れないと Result.success() で静かに終わるため、
 * Gist側の障害・PCのフィード生成停止・ネットワーク断が続いても誰も気づけない
 * （「シグナル無し（平穏）」と「取得失敗」が区別できない＝無言死）。
 * → 「今日の日付のフィードをリモートから取得できたか」を日単位でカウントし、
 *   2日連続で取得できなければ警告通知を出す。成功したら即リセット。
 *
 * 【失敗の定義】
 * feed==null だけでなく「assetsフォールバック（fromRemote=false）」や
 * 「取れたが今日の日付ではない（PCが2日以上フィードを作っていない）」も失敗に数える。
 * assetsのサンプルは必ずパースできるため、feed==null だけを見ていると
 * リモート全滅でも永久に検知できないため。
 */
object FeedHealthMonitor {

    // 健全性チェック専用の SharedPreferences（本体の kyotei_prefs とは分離）
    private const val PREFS_NAME = "kyotei_feed_health"
    private const val KEY_FAIL_STREAK = "feed_fail_streak"        // 連続失敗日数
    private const val KEY_LAST_FAIL_DATE = "feed_last_fail_date"  // 最後に失敗を数えた日

    // この日数連続で失敗したら警告通知を出す
    private const val FAIL_NOTIFY_THRESHOLD = 2

    // 警告通知のID（固定＝再通知は同じ通知を上書き更新する）
    private val NOTIFY_ID = "kyotei_feed_health_alert".hashCode()

    // フィードはPCが朝に生成する。深夜・早朝（生成前）に「今日の予想が無い」のは
    // 当たり前なので、この時間帯の失敗はカウントしない（EvPickWorker の発売時間帯と同じ）
    private const val COUNT_START_HOUR = 8
    private const val COUNT_END_HOUR = 21

    /**
     * 「今日の予想フィードをリモートから取得できなかった」ことを記録する。
     * 同じ日に何度失敗しても1日分としか数えない（15分周期で呼ばれるため日付でガード）。
     * 連続失敗が閾値に達したら警告通知を出す（日付ガードにより1日1回だけ）。
     */
    fun recordFailure(context: Context) {
        // フィード生成前の深夜・早朝は「失敗」に数えない
        val hour = LocalDateTime.now().hour
        if (hour < COUNT_START_HOUR || hour >= COUNT_END_HOUR) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_LAST_FAIL_DATE, null) == today) return  // 今日は数えた

        val streak = prefs.getInt(KEY_FAIL_STREAK, 0) + 1
        prefs.edit()
            .putString(KEY_LAST_FAIL_DATE, today)
            .putInt(KEY_FAIL_STREAK, streak)
            .apply()

        if (streak >= FAIL_NOTIFY_THRESHOLD) {
            NotificationHelper.sendHotRaceNotification(
                context, NOTIFY_ID,
                "⚠️ フィード取得が失敗しています",
                "今日の予想フィードを${streak}日連続で取得できていません。" +
                    "PC側のフィード生成（publish_feed）とネット接続を確認してください。"
            )
        }
    }

    /** 今日の予想フィードをリモートから取得できた＝連続失敗カウンタをリセットする */
    fun recordSuccess(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 無変化なら書き込まない（15分ごとに呼ばれるので無駄な書込を避ける）
        if (prefs.getInt(KEY_FAIL_STREAK, 0) != 0 ||
            prefs.getString(KEY_LAST_FAIL_DATE, null) != null
        ) {
            prefs.edit()
                .putInt(KEY_FAIL_STREAK, 0)
                .remove(KEY_LAST_FAIL_DATE)
                .apply()
        }
    }
}
