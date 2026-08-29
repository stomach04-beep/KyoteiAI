package com.example.kyoteiai.data

import java.time.LocalDateTime

/**
 * 「いつ収集してよいか」「いつ通知してよいか」の時刻境界。
 *
 * 【なぜ2つに分けるか（2026-08-29 v2.0）】
 *  もともとは QUIET_START_HOUR=21 の1本で、21時になると収集も通知も同時に止まっていた。
 *  だがこの2つは止めたい理由がまったく別物である。
 *
 *   ・収集を止める理由 … 発売していない時間帯に公式サイトを叩かないため（礼儀）
 *   ・通知を止める理由 … 夜遅くに「買うなら今」と鳴らさないため（生活上の都合）
 *
 *  ナイター最終レースの締切は21:00を過ぎるので、1本にまとめていたせいで
 *  その分の締切前オッズが毎日まるごと欠測していた。締切前オッズはその場で
 *  取らないと永久に失われるため、収集だけ21:30まで延ばし、通知は21:00で締める。
 *
 *  値を1つの定数で共有すると、片方を動かしたときにもう片方を必ず巻き添えにする。
 *  だから同じ「21」でも定数を分けてある（統合しないこと）。
 */
object CollectionWindow {

    /** 【共通】この時刻より前は何もしない。早朝はPCがフィードを作る前で叩いても無駄なため */
    const val ACTIVE_START_HOUR = 8

    /** 【収集】オッズ収集・スナップショット予約を許可する終了時刻（21:30まで） */
    const val COLLECT_END_HOUR = 21
    const val COLLECT_END_MINUTE = 30

    /** 【通知】◎通知を出す終了時刻（21:00・従来どおり据え置き） */
    const val NOTIFY_END_HOUR = 21

    /** 収集（＝スナップショット予約と記録）を動かしてよい時間帯か（8:00〜21:29） */
    fun canCollect(now: LocalDateTime): Boolean {
        if (now.hour < ACTIVE_START_HOUR) return false
        if (now.hour > COLLECT_END_HOUR) return false
        if (now.hour == COLLECT_END_HOUR && now.minute >= COLLECT_END_MINUTE) return false
        return true
    }

    /** ◎通知を出してよい時間帯か（8:00〜20:59） */
    fun canNotify(now: LocalDateTime): Boolean =
        now.hour in ACTIVE_START_HOUR until NOTIFY_END_HOUR
}
