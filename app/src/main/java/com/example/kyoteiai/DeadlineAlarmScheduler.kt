package com.example.kyoteiai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.kyoteiai.data.Feed
import com.example.kyoteiai.data.TimeUtil
import java.time.LocalDate

/**
 * レース締切前の「正確な時刻」に通知チェックを走らせるアラーム登録係。
 *
 * 【なぜ必要か】（2026-07-17 監査で判明した問題への対処）
 * HotRace/EvPick の15分周期 PeriodicWork は、Doze（端末の省電力スリープ）に入ると
 * 60分級に遅延することがある。通知窓は「締切0〜30分前（HotRace）」「0〜18分前（EvPick）」
 * なので、遅延すると窓を丸ごと跨いで無通知になる。
 * → 各レースの締切から逆算した AlarmManager.setExactAndAllowWhileIdle（Dozeでも
 *   正確に発火する）を張り、発火時に既存の判定ロジックを即時実行する。
 *   15分周期の PeriodicWork は保険としてそのまま残す（feedback_workmanager_doze_delay）。
 *
 * 【登録タイミング】
 * FeedRepository.load() が「今日の日付のフィード」をリモートから取得できたときに
 * まとめて登録する（Worker・画面表示のどの経路でも通る）。
 * 日付が変われば署名（下記）が変わるので、翌日のフィード取得後に自動で再登録される。
 *
 * 【重複登録の抑止】
 * 15分周期の Worker が毎回 load() を呼ぶため、フィード内容から作った署名を
 * SharedPreferences に控え、同じ内容なら張り直さない。
 * 端末再起動・アプリ更新でアラームは全部消えるので、BootReceiver が署名をクリアして
 * 次回ロード時に必ず再登録させる。
 */
object DeadlineAlarmScheduler {

    private const val TAG = "KyoteiAlarm"

    // 締切の何分前にアラームを発火させるか。
    // EvPickWorker の対象窓が「締切0〜18分前」なので、20分前だと窓の外（minutes=20>18）に
    // なり空振りする。15分前なら EvPick(0〜18)・HotRace(0〜30) の両方の窓に確実に入る。
    const val ALARM_LEAD_MIN = 15L

    // 登録済みアラームの署名（日付+トリガー時刻一覧）を保存するキー
    private const val KEY_SIGNATURE = "deadline_alarm_signature"

    /**
     * 署名をクリアする（BootReceiver から呼ぶ）。
     * 端末再起動・アプリ更新で OS 側のアラームは全部消えるのに署名だけ残ると、
     * 「登録済みと思い込んで再登録しない」まま1日無通知になるため。
     */
    fun clearSignature(context: Context) {
        context.getSharedPreferences(HotRaceWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_SIGNATURE).apply()
    }

    /**
     * フィード内の当日レースの締切から逆算して、exactアラームをまとめて登録する。
     * 今日の日付のフィードでなければ何もしない（古い予想でアラームを張らない）。
     */
    fun scheduleFromFeed(context: Context, feed: Feed) {
        val today = LocalDate.now().toString()
        if (feed.date != today) return

        // 各レースの「締切15分前」を計算。同じ分に複数場の締切が重なることが多いので、
        // 分単位で重複排除して1本のアラームにまとめる（発火時のWorkerはフィード全体を
        // 走査して窓内の全レースを処理するため、レースごとに張る必要はない）
        val allTriggers = feed.races
            .mapNotNull { TimeUtil.deadlineToTodayMillis(it.deadline) }
            .map { it - ALARM_LEAD_MIN * 60_000L }
            .toSortedSet()

        // 署名が前回と同じなら再登録しない（15分周期のWorkerが毎回load()を呼ぶため）
        val signature = feed.date + "|" + allTriggers.joinToString(",")
        val prefs = context.getSharedPreferences(HotRaceWorker.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SIGNATURE, null) == signature) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        var count = 0
        for (triggerAt in allTriggers) {
            // 過去のトリガーは張らない。「今まさに窓内」のレースは、このload()を呼んだ
            // 周期Worker自身（またはアプリ画面を開いたユーザー）が処理するので取りこぼさない
            if (triggerAt <= now) continue
            scheduleOne(context, alarmManager, triggerAt)
            count++
        }

        prefs.edit().putString(KEY_SIGNATURE, signature).apply()
        Log.i(TAG, "締切${ALARM_LEAD_MIN}分前のexactアラームを${count}本登録（$today）")
    }

    /** 1本のexactアラームを登録する。権限が無いときは不正確アラームで代用する */
    private fun scheduleOne(context: Context, alarmManager: AlarmManager, triggerAt: Long) {
        val intent = Intent(context, DeadlineAlarmReceiver::class.java).apply {
            action = DeadlineAlarmReceiver.ACTION
            // PendingIntent は extras では別物と認識されない（filterEquals は extras を見ない）
            // ため、data URI にトリガー時刻を埋めて1本ずつ確実に区別する
            data = Uri.parse("kyoteiai://deadline/$triggerAt")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (triggerAt / 60_000L).toInt(),  // 分単位で一意な requestCode
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 権限取消時(SecurityException)・端末独自制限(IllegalStateException)で
        // アプリが落ちないよう必ず捕捉する（BargainChecker と同じ流儀）
        try {
            if (!alarmManager.canScheduleExactAlarms()) {
                // SCHEDULE_EXACT_ALARM が取り消されている場合は不正確アラームで代用
                // （多少ずれても通知窓18分の中に入る可能性を残す）
                Log.w(TAG, "exactアラーム権限なし→不正確アラームで代用")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                // Dozeでも正確に発火する（feedback_workmanager_doze_delay 対策の本命）
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "アラーム登録失敗（権限なし）: $triggerAt", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "アラーム登録失敗（登録上限など）: $triggerAt", e)
        }
    }
}
