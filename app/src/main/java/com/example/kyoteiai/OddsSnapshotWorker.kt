package com.example.kyoteiai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kyoteiai.data.FeedRepository
import com.example.kyoteiai.data.OddsLogStore
import com.example.kyoteiai.data.OddsRepository
import com.example.kyoteiai.data.RunLogStore
import com.example.kyoteiai.data.TimeUtil
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 締切直前オッズの取得ワーカー（1レース1回だけ動く OneTimeWork）。
 *
 * 【役割】
 *  EvPickWorker（15分周期）が「締切3分前ちょうど」に発火するよう予約する。
 *  ここで6艇分の単勝オッズを取り、odds_log.json に保存する。
 *
 * 【なぜ締切3分前か】
 *  早い時間帯のオッズは投票が薄く、あてにならない（実例: 多摩川4R 締切4分前12.9倍→確定2.1倍）。
 *  締切に近いほど確定オッズに近く、実際に買える現実の値になる。
 *  一方で3分あればテレボートで買う時間も残る。
 *  PC版 log_odds.py は締切11分前だったので、それより直前の値が取れる。
 *
 * 【締切を過ぎたら記録しない】
 *  締切後の公式ページは「確定オッズ」を返す。これを締切前オッズとして混ぜると
 *  検証が壊れる（確定オッズで計算すると回収率が126〜149%と過大に出る）。
 *  確定オッズはPCから後でいくらでも取れるので、ここで拾う価値もない。
 */
class OddsSnapshotWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_STADIUM = "stadium"   // 場コード（inputData）
        const val KEY_RACE_NO = "race_no"   // レース番号（inputData）

        private const val MAX_ATTEMPTS = 2  // 取得失敗時の再挑戦はここまで（公式サイトを叩きすぎない）

        // なぜ記録できたか／できなかったかを必ず残す。
        //  ワーカーは「成功」で終わっても記録ゼロのことがある（締切超過・取得失敗・重複）。
        //  理由を残さないと「動いているのにデータが増えない」を後から追えない。
        //  確認: adb logcat -d | grep KyoteiOddsSnap
        const val TAG = "KyoteiOddsSnap"

        /** レースごとに一意な予約名。同じレースの二重予約を防ぐ */
        fun workName(stadium: String, raceNo: Int): String = "odds_snap_${stadium}_$raceNo"
    }

    override suspend fun doWork(): Result {
        val stadium = inputData.getString(KEY_STADIUM) ?: return Result.success()
        val raceNo = inputData.getInt(KEY_RACE_NO, -1)
        if (raceNo < 0) return Result.success()

        // 収集スイッチ（既定ON）。通知のON/OFFとは独立させてある。
        val prefs = applicationContext.getSharedPreferences(
            HotRaceWorker.PREFS_NAME, Context.MODE_PRIVATE
        )
        if (!prefs.getBoolean(EvPickWorker.KEY_ODDS_COLLECT_ENABLED, true)) {
            Log.i(TAG, "$stadium/${raceNo}R 収集OFFのためスキップ")
            RunLogStore.noteSnapshot(applicationContext, RunLogStore.Snap.SKIP, "収集OFF")
            return Result.success()
        }

        // フィード（当日のAI予想＝確率の出どころ）
        // ※どの分岐で終わっても理由をログに残す。黙って終わる分岐があると
        //   「ジョブは成功なのにデータが増えない」の原因調査ができない（2026-07-15の教訓）
        val feed = FeedRepository.load(applicationContext).feed
        if (feed == null) {
            Log.w(TAG, "$stadium/${raceNo}R フィード取得失敗のため中止")
            RunLogStore.noteSnapshot(applicationContext, RunLogStore.Snap.FAIL, "フィード取得失敗")
            return Result.success()
        }
        if (feed.date != LocalDate.now().toString()) {
            Log.i(TAG, "$stadium/${raceNo}R フィードが今日でない(${feed.date})ため中止")
            RunLogStore.noteSnapshot(
                applicationContext, RunLogStore.Snap.SKIP, "フィードが今日でない(${feed.date})"
            )
            return Result.success()
        }
        val race = feed.races.firstOrNull {
            it.stadium == stadium && it.raceNo == raceNo
        }
        if (race == null) {
            Log.w(TAG, "$stadium/${raceNo}R フィード内にレースが見つからない")
            RunLogStore.noteSnapshot(applicationContext, RunLogStore.Snap.SKIP, "フィードにレース不在")
            return Result.success()
        }

        val who = "$stadium/${raceNo}R(締切${race.deadline})"

        // 既に記録済みなら公式サイトを叩かない（通知経路が直前に取得済みの場合など）
        if (OddsLogStore.hasRace(applicationContext, feed.date, stadium, raceNo)) {
            Log.i(TAG, "$who 記録済みのためスキップ")
            RunLogStore.noteSnapshot(applicationContext, RunLogStore.Snap.SKIP, "記録済み")
            return Result.success()
        }

        // 締切超過チェック（上のコメントの理由で、過ぎていたら何もしない）
        val mins = TimeUtil.minutesUntilDeadline(race.deadline)
        if (mins == null) {
            RunLogStore.noteSnapshot(applicationContext, RunLogStore.Snap.SKIP, "締切時刻を解釈できず")
            return Result.success()
        }
        if (mins < 0) {
            Log.i(TAG, "$who 締切超過(${mins}分)のため記録しない＝発火が遅れた")
            // 締切超過＝ジョブの発火が遅れた証拠。省電力による遅延はここに現れる
            RunLogStore.noteSnapshot(
                applicationContext, RunLogStore.Snap.LATE, "$who 締切超過${mins}分＝発火遅れ"
            )
            return Result.success()
        }

        val dateYmd = feed.date.filter { it.isDigit() }
        val t0 = System.currentTimeMillis()
        // 単勝と複勝を同時に取る（同じページなので追加のリクエストは発生しない）。
        // 複勝は「その日に記録しないと永久に失われる」側のデータで、
        // 複勝EVが本物かどうかはこれが貯まらないと判定できない（2026-08-06）
        val fetched = OddsRepository.fetchWinAndPlaceOdds(stadium, raceNo, dateYmd)
        val winOdds = fetched?.win
        val took = (System.currentTimeMillis() - t0) / 1000.0
        if (winOdds == null) {
            // 未発売・通信失敗など。締切まで余裕があるうちだけ、数十秒後に1回だけ再挑戦する
            val willRetry = runAttemptCount < MAX_ATTEMPTS && mins >= 1
            Log.i(TAG, "$who 取得失敗 ${took}秒 ${mins}分前 試行${runAttemptCount} " +
                if (willRetry) "→再挑戦" else "→あきらめる(記録なし)")
            // 再挑戦する分は数えない（同じレースを二重に失敗計上しないため）
            if (!willRetry) {
                RunLogStore.noteSnapshot(
                    applicationContext, RunLogStore.Snap.FAIL, "$who 取得失敗(${took}秒)"
                )
            }
            return if (willRetry) Result.retry() else Result.success()
        }

        OddsLogStore.recordRace(
            context = applicationContext,
            date = feed.date,
            race = race,
            winOdds = winOdds,
            minsToDeadline = mins.toInt(),   // 何分前に取れたか＝この記録の鮮度そのもの
            observedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            placeOdds = fetched.place
        )
        Log.i(TAG, "$who 記録した ${mins}分前 ${took}秒 " +
            "単勝${winOdds.size}艇 複勝${fetched.place.size}艇")
        RunLogStore.noteSnapshot(
            applicationContext, RunLogStore.Snap.OK, "$who 記録${mins}分前"
        )
        return Result.success()
    }
}
