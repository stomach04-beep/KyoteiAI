package com.example.kyoteiai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 端末の再起動・アプリ再インストール後に定期通知を自動復元するレシーバー
 *
 * ACTION_BOOT_COMPLETED / MY_PACKAGE_REPLACED を受信すると Android がアプリプロセスを起動し、
 * KyoteiAIApp.onCreate() が自動で呼ばれる
 * → 通知チャンネルと HotRaceWorker（15分周期）が再登録される（WorkManagerは自動復元）
 *
 * 一方、AlarmManager のexactアラーム（締切前通知チェック）は再起動・アプリ更新で
 * OS側から全部消える。「登録済み」の署名だけ残ると再登録されないまま
 * その日一日無通知になるため、ここで署名をクリアして次回フィード取得時に
 * 必ず張り直させる。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 再起動・更新でexactアラームは消えているので、登録済み署名をクリアする
        // → 次に FeedRepository.load() が成功した時点で当日分がまとめて再登録される
        DeadlineAlarmScheduler.clearSignature(context)

        // 通知チャンネルと WorkManager の定期チェック（15分周期の保険）は
        // KyoteiAIApp.onCreate() が起動時に呼ばれて自動で再登録される
    }
}
