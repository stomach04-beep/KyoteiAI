package com.example.kyoteiai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.example.kyoteiai.data.NotifLogRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 通知チャンネルと通知送信を司るヘルパー（競艇「狙い目」通知用）。
 *
 * 【設定変更がOSにキャッシュされる問題への対策】（feedback_notification_channel_versioning）
 * NotificationChannel は Android 8+ で「作成後の音/振動/重要度の変更が不可」。
 * 同じIDで delete + create しても OS がキャッシュを持ち続ける。
 * → チャンネル設定（重要度・振動有無）からバージョンハッシュを作り、
 *   IDに含めることで「設定が変わったら自動的に別チャンネル」になる。
 *
 * v1では設定が固定なのでハッシュも固定だが、将来サウンド/振動をユーザー設定にした時に
 * IDの付け替えだけで安全に反映できるよう、最初からバージョン付きIDにしておく。
 */
object NotificationHelper {

    /** 送信した通知を時系列ログ（notif_log.json）へ残す。結果タブの「通知ログ」欄で見られる */
    private fun logNotification(context: Context, title: String, message: String) {
        try {
            NotifLogRepository.add(
                context,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")),
                title, message
            )
        } catch (e: Exception) {
            // ログ失敗で通知本体を止めない（記録は補助機能）
        }
    }

    // チャンネル設定のバージョン素材。振動あり・重要度HIGH を固定採用。
    // 将来この値を変えれば OS 側で別チャンネルが作られる。
    private const val CHANNEL_VERSION_MATERIAL = "vibrate=true|imp=high"

    // 「狙い目レース」通知チャンネルのID（バージョンハッシュ付き）
    val HOT_RACE_CHANNEL_ID: String =
        "kyotei_hot_race_v" + Integer.toHexString(CHANNEL_VERSION_MATERIAL.hashCode())

    // 「EV狙い目（◎1点）」通知チャンネルのID（バージョンハッシュ付き）
    val EV_PICK_CHANNEL_ID: String =
        "kyotei_ev_pick_v" + Integer.toHexString(CHANNEL_VERSION_MATERIAL.hashCode())

    // 旧テンプレ（バッテリーアプリ）のチャンネルIDを掃除対象として列挙
    private val LEGACY_CHANNEL_IDS = listOf(
        "battery_alert",
        "battery_alert_v2",
        "battery_service_channel",
        "battery_service_channel_v2"
    )

    /**
     * 通知チャンネルの存在を保証する（冪等）。
     * 旧チャンネルの掃除もここで行う。
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // 旧テンプレのチャンネルを削除（不在なら no-op）
        LEGACY_CHANNEL_IDS.forEach { manager.deleteNotificationChannel(it) }

        // 狙い目レース通知チャンネル
        NotificationChannel(
            HOT_RACE_CHANNEL_ID,
            "狙い目レース通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "AIが高確信と判断したレースの締切前に通知します"
            enableVibration(true)
        }.also { manager.createNotificationChannel(it) }

        // EV狙い目（◎1点）通知チャンネル
        NotificationChannel(
            EV_PICK_CHANNEL_ID,
            "EV狙い目通知（◎1点）",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "締切前のオッズで◎1点（EV≥1.2・確率≥0.2）が成立したレースを通知します"
            enableVibration(true)
        }.also { manager.createNotificationChannel(it) }

        // 現行ID以外の kyotei_hot_race_ / kyotei_ev_pick_ チャンネル（旧バージョン）を掃除
        manager.notificationChannels
            .filter {
                (it.id.startsWith("kyotei_hot_race_") && it.id != HOT_RACE_CHANNEL_ID) ||
                (it.id.startsWith("kyotei_ev_pick_") && it.id != EV_PICK_CHANNEL_ID)
            }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    /**
     * 狙い目レースの通知を送信する。
     *
     * @param notifyId 通知ID（レースごとに一意にして重ならないようにする）
     * @param title 例「徳山7R 締切20分前」
     * @param message 例「本命1号艇 松田憲幸 / 確信度81%」
     */
    fun sendHotRaceNotification(
        context: Context,
        notifyId: Int,
        title: String,
        message: String
    ) {
        // 送信前にチャンネルの存在を保証（冪等）
        ensureChannels(context)

        val manager = context.getSystemService(NotificationManager::class.java)

        val notification = NotificationCompat.Builder(context, HOT_RACE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_boat)
            .setColor(android.graphics.Color.parseColor("#00B0FF"))
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(notifyId, notification)
        logNotification(context, title, message)  // 時系列ログにも残す
    }

    /**
     * EV狙い目（◎1点）の通知を送信する。
     * タップでアプリを開き、アクションボタンで投票ページへ直行できる。
     *
     * @param notifyId 通知ID（レースごとに一意。候補→確定/見送りは同じIDで上書き）
     * @param title 例「若松12R ◎1号艇 確定 EV1.69」
     * @param message 例「確率51% × オッズ3.3 / 締切15:29まで約8分」
     * @param voteUrl 該当レースの投票ページURL（あればアクションはここへ直行。
     *                無ければテレボートのトップを開く従来動作）
     * @param clipText 買い目テキスト（あれば投票ページを開く直前にクリップボードへコピー）
     * @param stadium/raceNo 指定するとタップでアプリ内の該当レース詳細へ直行する
     */
    fun sendEvPickNotification(
        context: Context,
        notifyId: Int,
        title: String,
        message: String,
        voteUrl: String? = null,
        clipText: String? = null,
        stadium: String? = null,
        raceNo: Int? = null
    ) {
        // 送信前にチャンネルの存在を保証（冪等）
        ensureChannels(context)

        val manager = context.getSystemService(NotificationManager::class.java)

        // タップ→アプリを開く（レース指定があれば該当レースの詳細へ直行）
        val openApp = PendingIntent.getActivity(
            context, notifyId,
            Intent(context, MainActivity::class.java).apply {
                // singleTop: アプリが起動中でも onNewIntent でレース指定を受け取れる
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (stadium != null && raceNo != null) {
                    putExtra(MainActivity.EXTRA_OPEN_STADIUM, stadium)
                    putExtra(MainActivity.EXTRA_OPEN_RACE_NO, raceNo)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // アクションボタン→投票ページへ（投票は必ず手動。自動投票はしない）
        //  voteUrl があれば BetCopyActivity 経由（買い目コピー→該当レースの投票ページ直行）、
        //  無ければ従来どおりテレボートのトップページ
        val voteIntent = if (voteUrl != null) {
            Intent(context, BetCopyActivity::class.java).apply {
                putExtra(BetCopyActivity.EXTRA_VOTE_URL, voteUrl)
                putExtra(BetCopyActivity.EXTRA_CLIP_TEXT, clipText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.teleboat.jp/"))
        }
        val openVote = PendingIntent.getActivity(
            context, notifyId + 1, voteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, EV_PICK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_boat)
            .setColor(android.graphics.Color.parseColor("#00B0FF"))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .addAction(0, if (voteUrl != null) "このレースの投票へ" else "テレボートで投票", openVote)
            .setAutoCancel(true)
            .build()

        manager.notify(notifyId, notification)
        logNotification(context, title, message)  // 時系列ログにも残す（候補→確定/見送りも全部）
    }
}
