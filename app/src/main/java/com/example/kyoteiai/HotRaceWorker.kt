package com.example.kyoteiai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kyoteiai.data.FeedRepository
import com.example.kyoteiai.data.TimeUtil
import java.time.LocalDate

/**
 * 「狙い目」レースを締切前に通知するバックグラウンドワーカー。
 *
 * 【動作】
 * - 15分間隔の PeriodicWork として KyoteiAIApp から登録される。
 * - 実行のたびにフィードを取得し、
 *     is_hot == true かつ 締切が「今から0〜30分以内」のレースを検出したら通知する。
 * - 同じレースを二重通知しないよう、SharedPreferences に「通知済みキー」を保存してガードする。
 */
class HotRaceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val PREFS_NAME = "kyotei_prefs"
        // 通知済みレースキーの集合を保存するキー
        private const val KEY_NOTIFIED_SET = "hot_race_notified_set"
        // 「通知済み集合」を最後にリセットした日付
        private const val KEY_NOTIFIED_DATE = "hot_race_notified_date"

        // 締切の何分前から通知対象にするか
        private const val NOTIFY_WINDOW_MIN = 30L

        // 通知する確信度のしきい値（％）。この値以上の本命確率のレースだけ通知する
        const val KEY_NOTIFY_MIN_PROB = "hot_notify_min_prob"
        // 既定は70%（従来の「狙い目」＝1着確率70%以上と同じ）
        const val DEFAULT_NOTIFY_MIN_PROB = 70

        /** レースを一意に識別するキー（場コード + レース番号） */
        fun raceKey(stadium: String, raceNo: Int): String = "${stadium}_$raceNo"
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 日付が変わっていたら通知済み集合をリセット（前日分の抑止を持ち越さない）
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_NOTIFIED_DATE, null) != today) {
            prefs.edit()
                .putStringSet(KEY_NOTIFIED_SET, emptySet())
                .putString(KEY_NOTIFIED_DATE, today)
                .apply()
        }

        // 通知がユーザー設定でオフなら何もしない
        // ※既定はOFF（2026-07-12〜）。EV狙い目通知（EvPickWorker）が主役になったため、
        //   確信度ベースの旧通知は「参考情報が欲しい人だけ設定でON」の扱いに変更
        if (!prefs.getBoolean("hot_notify_enabled", false)) {
            return Result.success()
        }

        // ユーザーが設定した「通知する確信度のしきい値（％）」
        val minProb = prefs.getInt(KEY_NOTIFY_MIN_PROB, DEFAULT_NOTIFY_MIN_PROB)

        // フィード取得（失敗時はassetsフォールバック。取れなければ今回はスキップ）
        val result = FeedRepository.load(applicationContext)
        val feed = result.feed

        // 健全性チェック: 「今日の予想をリモートから取得できたか」を記録する。
        // 取得失敗で静かに success 終了すると障害に誰も気づけないため、
        // 2日連続で失敗したら FeedHealthMonitor が警告通知を出す（成功で即リセット）
        if (feed != null && result.fromRemote && feed.date == today) {
            FeedHealthMonitor.recordSuccess(applicationContext)
        } else {
            FeedHealthMonitor.recordFailure(applicationContext)
        }
        if (feed == null) return Result.success()

        // 既に通知済みのレースキー集合（変更するのでミュータブルコピー）
        val notified = prefs.getStringSet(KEY_NOTIFIED_SET, emptySet())!!.toMutableSet()

        val now = System.currentTimeMillis()
        var changed = false

        for (race in feed.races) {
            // 本命の確信度（％）。表示と同じ整数％で比較し、
            // ユーザー設定のしきい値未満のレースは通知しない
            val probPct = (race.topProb * 100).toInt()
            if (probPct < minProb) continue

            // 締切までの残り分。0〜30分以内のものだけ通知対象
            val minutes = TimeUtil.minutesUntilDeadline(race.deadline, now) ?: continue
            if (minutes < 0 || minutes > NOTIFY_WINDOW_MIN) continue

            val key = raceKey(race.stadium, race.raceNo)
            if (notified.contains(key)) continue  // 二重通知ガード

            // 通知本文を組み立て
            //   例：タイトル「徳山7R 締切20分前」
            //       本文  「本命1号艇 松田憲幸 / 確信度81%」
            val title = "${race.stadiumName}${race.raceNo}R 締切${minutes}分前"
            val message = "本命${race.topLane}号艇 ${race.topName} / 確信度${probPct}%"

            // レースキーから安定した通知IDを作る（同一レースは同じIDで上書き）
            val notifyId = key.hashCode()
            NotificationHelper.sendHotRaceNotification(applicationContext, notifyId, title, message)

            notified.add(key)
            changed = true
        }

        // 通知済み集合を保存（変化があった時だけ）
        if (changed) {
            prefs.edit().putStringSet(KEY_NOTIFIED_SET, notified).apply()
        }

        return Result.success()
    }
}
