package com.example.kyoteiai.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 収集ワーカーの「実行の足跡」を日別に残す（run_log.json）。
 *
 * 【なぜ要るか（2026-07-27 の事故）】
 *  スマホの締切前オッズ収集が 2026-07-27 だけ丸一日ゼロになった。
 *  フィードは正常に配信されており（クラウドのログで確認済み）、PCの取り込みも無実で、
 *  「アプリのジョブが日中1回も走らなかった」ところまでは分かったのに、
 *  **なぜ走らなかったのかを示す記録が端末に1つも残っていなかった**ため原因究明ができなかった。
 *
 *  logcat には理由を出していたが、logcat は数時間で流れて消える＝翌日には追えない。
 *  そこで「実行したか／フィードは読めたか／何件予約して何件取れたか」を
 *  日別のファイルに残す。次に穴が空いたときは、この記録を見れば
 *   ・runs=0        → ワーカー自体が動いていない（強制停止・省電力・端末オフ）
 *   ・feedNg が多い → フィードが読めていない（ネット・Gist側）
 *   ・snapFail 多数 → 公式サイトの取得が失敗している
 *  のどれなのかが一目で切り分けられる。
 *
 * 【PC側との連携】
 *  import_phone_odds.py が adb でこのファイルも吸い上げ、
 *  data/phone_run_log.json に保存＋欠測日の通知本文に添える。
 *
 * 保存は OddsLogStore と同じ原子保存（一時ファイル→rename）。
 */
object RunLogStore {

    private const val FILE_NAME = "run_log.json"
    private const val KEEP_DAYS = 45          // 保持する日数（それより古い日は捨てる）
    private val lock = Any()

    // 1日ぶんのカウンタのキー（JSON内の名前。PC側もこの名前で読む）
    private const val K_RUNS = "runs"           // EvPickWorker（15分周期）が動いた回数
    private const val K_FIRST_AT = "firstAt"    // その日の初回実行時刻
    private const val K_LAST_AT = "lastAt"      // その日の最終実行時刻
    private const val K_FEED_OK = "feedOk"      // 「今日の日付のフィード」を読めた回数
    private const val K_FEED_NG = "feedNg"      // 読めなかった／古かった回数
    private const val K_FEED_DATE = "feedDate"  // 最後に読めたフィードの日付
    private const val K_SCHEDULED = "scheduled" // 締切3分前スナップショットを予約した数
    private const val K_SNAP_OK = "snapOk"      // 実際に記録できた数
    private const val K_SNAP_FAIL = "snapFail"  // 公式から取得できなかった数
    private const val K_SNAP_LATE = "snapLate"  // 締切超過で記録しなかった数（発火遅れ）
    private const val K_SNAP_SKIP = "snapSkip"  // 記録済み・収集OFF・レース不在などで何もしなかった数
    private const val K_NOTE = "lastNote"       // 最後の出来事（人が読む用の短い文）

    /** スナップショットの結末。OddsSnapshotWorker の各分岐と1対1で対応させる */
    enum class Snap { OK, FAIL, LATE, SKIP }

    private fun hhmm(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** 全体を読む（壊れていれば空＝収集そのものは止めない） */
    private fun loadRoot(context: Context): JSONObject = try {
        val f = file(context)
        if (f.exists()) JSONObject(f.readText()) else JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    /**
     * 今日のぶんを取り出して編集し、保存する共通処理。
     * 「読む→書く」を1か所に集約して、カウンタの数え方が分岐ごとにズレるのを防ぐ。
     */
    private fun edit(context: Context, block: (JSONObject) -> Unit) = synchronized(lock) {
        val root = loadRoot(context)
        val today = LocalDate.now().toString()
        val day = root.optJSONObject(today) ?: JSONObject()
        block(day)
        root.put(today, day)
        pruneOldDays(root)
        atomicWrite(file(context), root.toString())
    }

    /** 保持日数を超えた古い日を捨てる（端末のストレージを圧迫しない） */
    private fun pruneOldDays(root: JSONObject) {
        val limit = LocalDate.now().minusDays(KEEP_DAYS.toLong()).toString()
        val old = root.keys().asSequence().filter { it < limit }.toList()
        old.forEach { root.remove(it) }
    }

    private fun JSONObject.bump(key: String, by: Int = 1) {
        put(key, optInt(key, 0) + by)
    }

    /**
     * 15分周期ワーカーが動いたことを記録する。
     * doWork の一番最初に呼ぶこと（早期returnの前）。
     * 「動いたのか、そもそも動いていないのか」がこの1件で切り分けられる。
     *
     * @param stage どこまで進んだか（"start" / "quiet" / "off" など人が読む短い文）
     */
    fun noteRun(context: Context, stage: String) = edit(context) { day ->
        day.bump(K_RUNS)
        val now = hhmm()
        if (day.optString(K_FIRST_AT).isEmpty()) day.put(K_FIRST_AT, now)
        day.put(K_LAST_AT, now)
        day.put(K_NOTE, stage)
    }

    /**
     * 「どこまで進んで終わったか」だけを上書きする（実行回数は増やさない）。
     * 早期return する分岐ごとに理由を残すために使う。
     * 黙って終わる分岐を残すと「成功なのにデータが増えない」を後から追えない。
     */
    fun noteStage(context: Context, stage: String) = edit(context) { day ->
        day.put(K_NOTE, stage)
        day.put(K_LAST_AT, hhmm())
    }

    /** フィード（当日のAI予想）を読めたか。読めなければ収集は1件も始まらないので必ず記録する */
    fun noteFeed(context: Context, ok: Boolean, feedDate: String?) = edit(context) { day ->
        day.bump(if (ok) K_FEED_OK else K_FEED_NG)
        if (!feedDate.isNullOrEmpty()) day.put(K_FEED_DATE, feedDate)
    }

    /** 締切3分前スナップショットを予約した数（＝その日に取りに行く予定のレース数） */
    fun noteScheduled(context: Context, count: Int = 1) = edit(context) { day ->
        day.bump(K_SCHEDULED, count)
    }

    /** スナップショットの結末。予約したのに記録が増えないときの原因がここに出る */
    fun noteSnapshot(context: Context, result: Snap, note: String? = null) = edit(context) { day ->
        day.bump(
            when (result) {
                Snap.OK -> K_SNAP_OK
                Snap.FAIL -> K_SNAP_FAIL
                Snap.LATE -> K_SNAP_LATE
                Snap.SKIP -> K_SNAP_SKIP
            }
        )
        if (!note.isNullOrEmpty()) day.put(K_NOTE, note)
    }

    /**
     * 1日ぶんの集計値（画面表示・前日欠測チェック用の読み出し口）。
     *
     * これまで run_log.json は「書く一方」で読み手が無く、収集が止まっていても
     * 端末の画面からは分からなかった（データは残っていたのに読み手が居ないだけ）。
     */
    data class DayStats(
        val date: String,      // "2026-08-29"
        val runs: Int,         // EvPickWorker が動いた回数
        val scheduled: Int,    // スナップショットを予約した数
        val snapOk: Int,       // 実際に記録できた数
        val snapFail: Int,     // 公式から取得できなかった数
        val snapLate: Int,     // 締切超過で記録しなかった数（発火遅れ）
        val firstAt: String?,  // その日の初回実行時刻 "HH:mm"
        val lastAt: String?    // その日の最終実行時刻 "HH:mm"
    )

    /**
     * 指定日の集計を返す（その日の記録が無ければ runs=0 の空集計）。
     * 「記録が残っていない」と「0回だった」はどちらも異常なので区別せず runs=0 で返す。
     */
    fun dayStats(context: Context, date: String): DayStats = synchronized(lock) {
        val day = loadRoot(context).optJSONObject(date) ?: JSONObject()
        return DayStats(
            date = date,
            runs = day.optInt(K_RUNS, 0),
            scheduled = day.optInt(K_SCHEDULED, 0),
            snapOk = day.optInt(K_SNAP_OK, 0),
            snapFail = day.optInt(K_SNAP_FAIL, 0),
            snapLate = day.optInt(K_SNAP_LATE, 0),
            firstAt = day.optString(K_FIRST_AT).ifEmpty { null },
            lastAt = day.optString(K_LAST_AT).ifEmpty { null }
        )
    }

    /** 今日ぶんの集計（今日タブの収集ヘルス表示用） */
    fun todayStats(context: Context): DayStats = dayStats(context, LocalDate.now().toString())

    /** 直近 days 日ぶんを人が読める1行ずつの文字列にする（設定画面や adb での確認用） */
    fun recentSummary(context: Context, days: Int = 7): String = synchronized(lock) {
        val root = loadRoot(context)
        val keys = root.keys().asSequence().sortedDescending().take(days).toList()
        if (keys.isEmpty()) return "（実行ログはまだありません）"
        return keys.joinToString("\n") { d ->
            val o = root.optJSONObject(d) ?: JSONObject()
            "$d 実行${o.optInt(K_RUNS)}回 " +
                "フィード○${o.optInt(K_FEED_OK)}/×${o.optInt(K_FEED_NG)} " +
                "予約${o.optInt(K_SCHEDULED)} " +
                "記録${o.optInt(K_SNAP_OK)} 失敗${o.optInt(K_SNAP_FAIL)} " +
                "締切超過${o.optInt(K_SNAP_LATE)} 対象外${o.optInt(K_SNAP_SKIP)}"
        }
    }
}
