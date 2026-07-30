package com.example.kyoteiai

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * アプリケーション全体のクラス。
 *
 * 起動時（およびBootReceiver経由の再起動後）に：
 *   - 通知チャンネルを作成
 *   - 「狙い目レース」を締切前に通知する HotRaceWorker を15分周期で登録
 *
 * WorkManager の enqueueUniquePeriodicWork(KEEP) なので、
 * 何度呼ばれても重複登録されず安全。
 *
 * 【通知の本命経路はexactアラーム（2026-07-17〜）】
 * 15分周期の PeriodicWork は Doze で60分級に遅延し、通知窓（締切0〜18/30分前）を
 * 跨いで無通知になることがある。そのため、締切前の正確な時刻に発火する
 * exactアラーム（DeadlineAlarmScheduler がフィード取得成功時に登録）が本命で、
 * ここで登録する15分周期はアラーム全滅時（権限取消等）の保険という位置づけ。
 */
class KyoteiAIApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 通知チャンネルを用意（狙い目レース通知用）
        NotificationHelper.ensureChannels(this)

        // 狙い目レースの定期チェック（15分周期）
        // プロセスが生きていなくてもOSがスケジュールしてくれる
        val workRequest = PeriodicWorkRequestBuilder<HotRaceWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "kyotei_hot_race_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // EV狙い目（◎1点）の定期チェック（15分周期）
        // 締切前オッズでC'条件（EV≥1.2・確率≥0.2）が成立したレースだけ通知する
        // 周期は EvPickWorker.WORK_PERIOD_MIN を唯一の定義とする（取得失敗時の
        // 「次回でも間に合うか」判定が同じ値を見るため、二重定義するとズレる）
        val evRequest = PeriodicWorkRequestBuilder<EvPickWorker>(
            EvPickWorker.WORK_PERIOD_MIN, TimeUnit.MINUTES
        )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "kyotei_ev_pick_check",
            ExistingPeriodicWorkPolicy.KEEP,
            evRequest
        )
    }
}
