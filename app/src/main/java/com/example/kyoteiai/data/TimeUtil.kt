package com.example.kyoteiai.data

import java.util.Calendar

/**
 * 締切時刻（"HH:mm" 文字列）の扱いに関する共通ユーティリティ。
 *
 * フィードの deadline は "08:32" のような時刻文字列なので、
 * 「今から何分後か」「もう締切を過ぎたか」を計算するために本日の日付と合成する。
 */
object TimeUtil {

    /**
     * "HH:mm" を「本日のその時刻」のエポックミリ秒に変換する。
     * パース失敗時は null。
     */
    fun deadlineToTodayMillis(deadline: String): Long? {
        val parts = deadline.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * 締切までの残り分数を返す（既に過ぎていればマイナス）。
     * パース失敗時は null。
     */
    fun minutesUntilDeadline(deadline: String, nowMillis: Long = System.currentTimeMillis()): Long? {
        val target = deadlineToTodayMillis(deadline) ?: return null
        return (target - nowMillis) / 60000L
    }

    /**
     * 締切を過ぎているか判定する。
     * パース失敗時は false（不明なものは「まだ」扱いにして一覧から消さない）。
     */
    fun isPast(deadline: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val target = deadlineToTodayMillis(deadline) ?: return false
        return target < nowMillis
    }
}
