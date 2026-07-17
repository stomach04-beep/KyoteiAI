package com.example.kyoteiai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * 締切約15分前のexactアラーム（DeadlineAlarmScheduler が登録）を受信して、
 * 既存の HotRace / EvPick の判定ロジックを即時実行するレシーバー。
 *
 * 【二重通知にならない理由】
 * 15分周期の PeriodicWork（保険として存置）と同じ Worker を走らせるが、
 *  - HotRaceWorker … 通知済みレースキー集合（hot_race_notified_set）
 *  - EvPickWorker  … 処理済みレースキー集合（ev_checked_set）
 * で同一レースの二重処理を弾くため、どちらの経路が先に走っても1回しか通知されない。
 *
 * 【expedited（優先実行）にする理由】
 * アラームで端末が起きても、普通の OneTimeWork は Doze 中は先送りされうる。
 * setExpedited なら起床中に即実行される。優先枠を使い切っていた場合は
 * RUN_AS_NON_EXPEDITED_WORK_REQUEST で普通のワークとして実行される（無いよりまし）。
 */
class DeadlineAlarmReceiver : BroadcastReceiver() {

    companion object {
        // アラームPendingIntentに載せる専用アクション
        const val ACTION = "com.example.kyoteiai.DEADLINE_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val workManager = WorkManager.getInstance(context)

        // EV狙い目（◎1点）チェック＝主役。締切0〜18分前の窓内レースを処理する
        workManager.enqueueUniqueWork(
            "kyotei_deadline_ev_check",
            ExistingWorkPolicy.KEEP,  // 直前のアラーム分が実行中ならそちらに任せる
            OneTimeWorkRequestBuilder<EvPickWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        )

        // 旧・確信度ベースの狙い目チェック（設定でONの人向け）。窓は締切0〜30分前
        workManager.enqueueUniqueWork(
            "kyotei_deadline_hot_check",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<HotRaceWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        )
    }
}
