package com.example.kyoteiai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.kyoteiai.data.CollectionWindow
import com.example.kyoteiai.data.EvPolicy
import com.example.kyoteiai.data.FeedRepository
import com.example.kyoteiai.data.OddsLogStore
import com.example.kyoteiai.data.OddsRepository
import com.example.kyoteiai.data.PendingCandidateStore
import com.example.kyoteiai.data.PickLogRepository
import com.example.kyoteiai.data.PickRecord
import com.example.kyoteiai.data.RunLogStore
import com.example.kyoteiai.data.TimeUtil
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * EV狙い目（◎1点）通知ワーカー。検討書「購入方針とアプリ仕様」第3部(A)の実装。
 *
 * 【動作】15分周期の PeriodicWork。役割は「収集」と「通知」の2つで、独立にON/OFFできる。
 *
 *  ＜収集（KEY_ODDS_COLLECT_ENABLED・**2026-09-05 から既定OFF**）＞
 *   1. 締切が「今から0〜18分以内」のレースを対象にする
 *   2. 各レースについて OddsSnapshotWorker を「締切3分前ちょうど」に予約する
 *      （ここでは公式サイトを叩かない＝この Worker は予約係）
 *   3. 実際の取得と odds_log.json への保存は OddsSnapshotWorker が行う
 *   → もともとは「締切前オッズはその場で取らないと永久に失われる」ので常時動かしていたが、
 *     競艇AIの研究打ち切りで貯める先が無くなったため既定OFFにした。
 *     既定値は DEFAULT_ODDS_COLLECT_ENABLED（この1か所だけが実際の挙動を決める）
 *
 *  ＜通知（KEY_EV_NOTIFY_ENABLED・既定ON）＞
 *   1. 対象レースの単勝オッズを公式から取得（逐次・0.8秒間隔の礼儀取得）
 *   2. その場のオッズでEVを計算し、C'条件（EvPolicy: EV≥1.2かつ確率≥0.2のEV最大1点）が
 *      成立したレースだけ通知する
 *   3. C'成立レースは picks.json に記録（C'仮想収支の材料）
 *   → 通知OFFなら、この経路では公式サイトを一切叩かない
 *
 * 【ガード】
 *  - 同一レースは1日1回しか処理しない（チェック済み集合）
 *  - 通知は1日15件まで
 *  - 8時前は動かない。終わりの時刻は収集と通知で別（CollectionWindow 参照）:
 *      収集・スナップショット予約 … 21:30まで（ナイター最終レースを取りこぼさない）
 *      ◎通知                     … 21:00まで（夜遅くに買い推奨を鳴らさない）
 *  - フィードが今日の日付でなければ何もしない（古い予想で誤通知しない）
 *
 * 【取りこぼし防止（v2.0）】
 *  通知経路で直接オッズを取りに行って失敗した場合は、必ず OddsSnapshotWorker の
 *  予約に回す。締切前オッズはその場で取らないと永久に失われるため、
 *  「予約もない・再挑戦もない」状態を作らないことを最優先にする。
 */
class EvPickWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        // HotRaceWorker と同じ SharedPreferences を共用する
        const val KEY_EV_NOTIFY_ENABLED = "ev_notify_enabled"   // 通知ON/OFF（既定ON）

        // オッズ収集のON/OFF。通知とは独立させてある。
        //  以前は「通知OFF＝オッズ取得もしない」だったため、通知を切ると収集まで止まり、
        //  検証用のデータが黙って貯まらなくなっていた（2026-07-14 19:08以降に実際に発生）。
        //  締切前オッズはその場で record しないと永久に失われるので、通知とは切り離してある。
        const val KEY_ODDS_COLLECT_ENABLED = "odds_collect_enabled"

        /**
         * 収集の既定値。2026-09-05 に ON → OFF へ変更した。
         *
         * 理由：競艇AIの研究を打ち切ったため。モデル側は天井97.7%（検証88〜92）、
         * 控除率側もポイント還元の最良1.25%（必要2.3%）で不合格と実測して決着し、
         * 集めた締切前オッズの使い道が無くなった。PC側の取り込み（KyoteiDecaySettle の
         * import_phone_odds.py）も同日に止めたので、集め続けても誰も読まない＝
         * 公式サイトを無駄に叩くだけになる。
         *
         * このキーは設定画面にも保存値にも存在せず、この既定値だけが実際の挙動を決める。
         * だからアプリを更新すれば既存の端末でもそのまま収集が止まる（移行処理は要らない）。
         * 再開したくなったらここを true に戻すだけでよい。
         *
         * 既定値を参照側に直書きすると必ずズレるので、参照はすべてこの定数を通すこと。
         */
        const val DEFAULT_ODDS_COLLECT_ENABLED = false

        // 「判定対象のみ通知」トグル（既定OFF＝現状維持）。
        //  ONにすると、事前登録した判定対象（C'≦5倍・昼）でない◎は通知しない。
        //  ※ 記録（picks.json）とオッズ収集はこの設定に関わらず必ず全部続ける。
        //    通知を絞るのは「鳴る回数を減らす」ためであって、データを間引くためではない。
        const val KEY_TARGET_ONLY_NOTIFY = "target_only_notify"

        private const val KEY_CHECKED_SET = "ev_checked_set"    // 処理済みレースキー集合
        private const val KEY_CHECKED_DATE = "ev_checked_date"  // 集合をリセットした日付
        private const val KEY_NOTIFY_COUNT = "ev_notify_count"  // 当日の通知数
        private const val KEY_YDAY_CHECK_DATE = "ev_yday_check_date" // 前日欠測チェック済みの日

        private const val NOTIFY_WINDOW_MIN = 18L   // 締切の何分前から対象にするか
        // このWorkerの実行周期（KyoteiAIApp の PeriodicWorkRequest と必ず揃える）。
        // 取得失敗時に「次回実行でも間に合うか」を判断するのに使う。
        const val WORK_PERIOD_MIN = 15L
        private const val DAILY_NOTIFY_CAP = 15     // 1日の通知上限
        private const val FETCH_GAP_MS = 800L       // レース間の取得間隔（礼儀）

        // ── 稼働時間帯の境界について ────────────────────────────────
        //  収集（〜21:30）と通知（〜21:00）は止めたい理由が別物なので、
        //  境界の定数は CollectionWindow に分けて置いてある（同じ「21」でも統合しない）。
        //  ここで再定義すると必ずズレるので、判定は必ず CollectionWindow を呼ぶこと。

        // 最終確認（2段階目）を締切の何分前に走らせるか。
        // 早い時間帯のオッズは投票が薄く「幻の高EV」が出やすい（実例: 締切4分前12.9倍→確定2.1倍）。
        // 3分前なら実購入オッズにかなり近く、テレボートで買う時間も残る
        const val FINAL_CONFIRM_LEAD_MIN = 3L

        /** レースキーから通知IDを作る（候補→確定/見送りで同じIDを使い上書き更新する） */
        fun notifyId(key: String): Int = ("ev_$key").hashCode()
    }

    override suspend fun doWork(): Result {
        // ホーム画面ウィジェットは、どの経路でこの実行が終わっても必ず描き直す。
        //  早期return（収集時間外・フィード失敗・対象レース無し）でも古い表示を残さない。
        //  finally に置くのは、return が何か所もあるため書き漏らしを構造で防ぐ狙い。
        return try {
            runCollection()
        } finally {
            KyoteiWidgetProvider.updateAll(applicationContext)
        }
    }

    /** 本体（収集・通知）。ウィジェット更新は doWork 側の finally が担当する */
    private suspend fun runCollection(): Result {
        // 実行の足跡を最初に残す（早期returnより前）。
        //  2026-07-27 に収集が丸一日ゼロになったとき、「ワーカーが動かなかったのか、
        //  動いたが何もしなかったのか」を示す記録が端末に無く原因究明ができなかった。
        //  この1行があれば runs=0 か runs>0 かで即座に切り分けられる。
        RunLogStore.noteRun(applicationContext, "開始")

        val prefs = applicationContext.getSharedPreferences(
            HotRaceWorker.PREFS_NAME, Context.MODE_PRIVATE
        )

        // 通知と収集は独立。両方OFFのときだけ何もしない
        val notifySetting = prefs.getBoolean(KEY_EV_NOTIFY_ENABLED, true)
        val collectEnabled = prefs.getBoolean(
            KEY_ODDS_COLLECT_ENABLED, DEFAULT_ODDS_COLLECT_ENABLED
        )
        // 「判定対象のみ通知」（既定OFF）。通知を絞るだけで、記録・収集には一切影響させない
        val targetOnlyNotify = prefs.getBoolean(KEY_TARGET_ONLY_NOTIFY, false)
        if (!notifySetting && !collectEnabled) {
            RunLogStore.noteStage(applicationContext, "収集も通知もOFF")
            return Result.success()
        }

        // ── 前日の収集が異常だったら知らせる（1日1回・朝の初回実行時）──────────
        //  収集が丸一日ゼロでも、これまでは誰も気づけずデータだけ静かに欠けていた
        //  （2026-07-27の事故）。run_log.json は残っているので読み手を用意する。
        //
        //  ※収集OFFのときは点検しない（2026-09-05 追加）。
        //    意図的に止めているのに「前日の収集がゼロ」と判定され、毎朝アラートが
        //    鳴り続けてしまう。止めた対象の監視を残すと誤警報になるのは、同日にPC側の
        //    health-watchdog で停止タスクを監視リストから外したのとまったく同じ話。
        if (collectEnabled) {
            CollectionHealthMonitor.checkYesterday(applicationContext, prefs, KEY_YDAY_CHECK_DATE)
        }

        // ── 取りこぼし回収スイープ（ネットワーク不要・発売時間外でも実施）───────────
        //  候補通知を出したのに、締切3分前の再判定（EvFinalCheckWorker）が
        //  動かなかった／締切後にずれたレースを、締切超過を根拠に見送りで閉じる。
        //  これで「候補だけ来て確定も見送りも来ない」宙ぶらりんを必ず解消する。
        sweepStalePendingCandidates()

        // ── 稼働時間帯の判定（収集と通知で境界が違う）─────────────────────
        //  収集の窓（〜21:30）から外れていれば、この先は何もしない。
        //  収集の窓の中で通知の窓（〜21:00）から外れていれば、
        //  「収集だけする・◎通知は出さない」状態で処理を続ける（ナイター最終レース対策）。
        val nowTime = LocalDateTime.now()
        if (!CollectionWindow.canCollect(nowTime)) {
            RunLogStore.noteStage(
                applicationContext,
                "収集時間外(%02d:%02d)".format(nowTime.hour, nowTime.minute)
            )
            return Result.success()
        }
        val notifyAllowedNow = CollectionWindow.canNotify(nowTime)
        // 以降の「通知してよいか」はこの1変数に集約する（設定ONかつ通知の時間帯内）
        val notifyEnabled = notifySetting && notifyAllowedNow
        if (notifySetting && !notifyAllowedNow) {
            RunLogStore.noteStage(
                applicationContext,
                "通知時間外(%02d:%02d)＝収集のみ継続".format(nowTime.hour, nowTime.minute)
            )
        }

        // 日付が変わっていたら処理済み集合と通知カウントをリセット
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_CHECKED_DATE, null) != today) {
            prefs.edit()
                .putStringSet(KEY_CHECKED_SET, emptySet())
                .putInt(KEY_NOTIFY_COUNT, 0)
                .putString(KEY_CHECKED_DATE, today)
                .apply()
        }

        // フィード取得（失敗時はassetsフォールバック。取れなければ今回はスキップ）
        val loadResult = FeedRepository.load(applicationContext)
        val feed = loadResult.feed

        // 健全性チェック: 「今日の予想をリモートから取得できたか」を記録する。
        // 取得失敗で静かに success 終了すると障害に誰も気づけないため、
        // 2日連続で失敗したら FeedHealthMonitor が警告通知を出す（成功で即リセット）
        val feedOk = feed != null && loadResult.fromRemote && feed.date == today
        if (feedOk) {
            FeedHealthMonitor.recordSuccess(applicationContext)
        } else {
            FeedHealthMonitor.recordFailure(applicationContext)
        }
        // フィードの成否も日別に残す（収集ゼロの原因が「フィードが読めない」かを切り分ける）
        RunLogStore.noteFeed(applicationContext, feedOk, feed?.date)
        if (feed == null) {
            RunLogStore.noteStage(applicationContext, "フィード取得失敗")
            return Result.success()
        }
        // 古いフィード（昨日以前）の予想で誤通知しない
        if (feed.date != today) {
            RunLogStore.noteStage(applicationContext, "フィードが今日でない(${feed.date})")
            return Result.success()
        }
        val dateYmd = feed.date.filter { it.isDigit() }

        // 今日のレースの締切一覧をウィジェット用に控える。
        //  ウィジェットは通信できないので、フィードを持っているこのタイミングでしか書けない。
        //  「次の締切まであと何分」はこの控えから描画時に計算する
        KyoteiWidgetProvider.saveTodayDeadlines(prefs, feed)

        val checked = prefs.getStringSet(KEY_CHECKED_SET, emptySet())!!.toMutableSet()
        var notifyCount = prefs.getInt(KEY_NOTIFY_COUNT, 0)
        val now = System.currentTimeMillis()

        // 締切が0〜18分以内で、まだ処理していないレースを対象にする
        val targets = feed.races.filter { race ->
            val minutes = TimeUtil.minutesUntilDeadline(race.deadline, now)
            minutes != null && minutes in 0..NOTIFY_WINDOW_MIN &&
                !checked.contains(HotRaceWorker.raceKey(race.stadium, race.raceNo))
        }
        if (targets.isEmpty()) {
            RunLogStore.noteStage(applicationContext, "締切0〜18分のレースなし(全${feed.races.size}R)")
            return Result.success()
        }

        var changed = false
        var fetchedCount = 0
        for (race in targets) {
            val key = HotRaceWorker.raceKey(race.stadium, race.raceNo)
            // 成否に関わらず処理済みにする（15分後の次回実行では締切超過のため再挑戦の意味がない）
            checked.add(key)
            changed = true

            val minutesNow = TimeUtil.minutesUntilDeadline(race.deadline, System.currentTimeMillis())
                ?: continue

            // ── 収集: 締切3分前ちょうどに取りに行く予約（C'かどうかに関係なく全レース）──
            //  ここでは公式サイトを叩かない。15分周期のこの実行は「予約係」に徹する。
            //  締切に近いほど確定オッズに近い＝実戦で買える現実の値になるため、
            //  「見つけた時点(0〜18分前のどこか)」ではなく3分前に寄せる。
            //  ただし通知ONで直後に取得する分は、その結果を使い回すので予約しない（二重取得の回避）。
            val willFetchNowForNotify = notifyEnabled && minutesNow <= FINAL_CONFIRM_LEAD_MIN + 1
            if (collectEnabled && !willFetchNowForNotify) {
                scheduleOddsSnapshot(race.stadium, race.raceNo, minutesNow)
                // 「何件予約したか」を残す。予約数に対して記録数が少なければ
                // 原因は予約側でなく取得側（OddsSnapshotWorker）だと分かる
                RunLogStore.noteScheduled(applicationContext)
            }

            // ここから下は通知のための処理。通知OFFなら公式サイトを叩かない
            if (!notifyEnabled) continue

            if (fetchedCount > 0) delay(FETCH_GAP_MS)  // レース間は礼儀正しい間隔を空ける
            fetchedCount++

            // 単勝ページのみ取得（3連単ページは叩かない＝負荷半減）。
            // 複勝は同じページに載っているので、同時に受け取って収集ログへ残す
            val fetched = OddsRepository.fetchWinAndPlaceOdds(race.stadium, race.raceNo, dateYmd)
            val winOdds = fetched?.win
            if (winOdds == null) {
                // ── 取得失敗。ここが「前向き実測データを永久に失う」急所 ─────────
                //  締切4分以内に初めて見つけたレースは、二重取得を避けるために
                //  スナップショット予約をスキップして「この場の直接取得」に頼っている
                //  （willFetchNowForNotify）。その直接取得が失敗すると、
                //  予約も無い・次回実行（15分後）は締切超過、で再挑戦がゼロになり、
                //  そのレースの締切前オッズは二度と取れない。
                //  → 失敗したら必ずスナップショット予約に回し、リトライを持つ
                //    OddsSnapshotWorker（MAX_ATTEMPTS=2）に拾わせる。
                //  条件を willFetchNowForNotify に限っているのは、それ以外のレースは
                //  上で既に予約済みだから。ここで無条件に呼ぶと予約数だけが二重に増え、
                //  「予約に対して記録が少ない＝取得側が悪い」という切り分けが効かなくなる。
                if (collectEnabled && willFetchNowForNotify) {
                    scheduleOddsSnapshot(race.stadium, race.raceNo, minutesNow)
                    RunLogStore.noteScheduled(applicationContext)
                }
                // 未発売・圏外などは fallback通知を出さない方針は維持する。
                // ただし「処理済み」を取り消せる場合は取り消す：この実行は15分周期なので、
                // 締切まで15分以上あるレースは次回もまだ間に合う。取り消さないと、
                // 一度の通信失敗でそのレースの判定機会が永久に失われる（候補も見送りも
                // 何も通知されない）。締切が近すぎる場合は従来どおり処理済みのままにする。
                if (minutesNow > WORK_PERIOD_MIN) {
                    checked.remove(key)
                }
                continue
            }

            // 直前に取れたオッズは収集ログにも残す（この取得を予約の代わりに使う）
            if (collectEnabled && willFetchNowForNotify) {
                OddsLogStore.recordRace(
                    context = applicationContext,
                    date = feed.date,
                    race = race,
                    winOdds = winOdds,
                    minsToDeadline = minutesNow.toInt(),
                    observedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    placeOdds = fetched.place
                )
                // 通知経路が直前に取ったぶんも「記録できた1件」として数える
                // （予約経由と足して、その日の記録総数が odds_log.json と一致する）
                RunLogStore.noteSnapshot(applicationContext, RunLogStore.Snap.OK, "通知経路で記録")
            }

            // C'判定（しきい値はEvPolicyの単一定義を参照）
            val probByLane = race.boats.associate { it.lane to it.prob }
            val pick = EvPolicy.findTopPick(probByLane, winOdds) ?: continue

            val minutes = TimeUtil.minutesUntilDeadline(race.deadline, System.currentTimeMillis()) ?: 0
            // ※文字列テンプレートに%記号（確率51%等）が含まれるため、String.format は使わず
            //   数値だけ個別に整形してから埋め込む（%が書式指定と誤解釈されて落ちるのを防ぐ）
            val evText = "%.2f".format(pick.ev)
            val oddsText = "%.1f".format(pick.odds)
            // 事前登録した判定対象（C'≦5倍・昼）かどうか。
            // 「参考」の◎を判定対象と同じ重みで買うと、昇格判定の前提が崩れる
            val isTarget = EvPolicy.isRegisteredTarget(pick.odds, race.deadline)
            val target = EvPolicy.targetLabel(pick.odds, race.deadline)

            // 「判定対象のみ通知」がONなら、参考の◎は鳴らさない（既定OFF＝従来どおり全部鳴る）。
            // ここで抑えるのは通知だけ。この下の記録（picks.json）は必ず実行する
            val notifyThisPick = notifyCount < DAILY_NOTIFY_CAP && (!targetOnlyNotify || isTarget)

            if (minutes <= FINAL_CONFIRM_LEAD_MIN + 1) {
                // ── 締切まで4分以内: この場で「確定」判定 ──────────────
                //  オッズはほぼ実購入時の値。記録（C'仮想収支の材料）もここで行う
                PickLogRepository.addIfAbsent(
                    applicationContext,
                    PickRecord(
                        date = feed.date,
                        stadium = race.stadium,
                        stadiumName = race.stadiumName,
                        raceNo = race.raceNo,
                        lane = pick.lane,
                        prob = pick.prob,
                        odds = pick.odds,
                        ev = pick.ev,
                        observedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                        // v2.0: 判定対象（締切5分前以内・昼）かを後から機械判定するために残す。
                        // これが無いと収支画面で「対象外・データ不足」にしかできない
                        mins = minutes.toInt(),
                        deadline = race.deadline
                    )
                )
                if (notifyThisPick) {
                    NotificationHelper.sendEvPickNotification(
                        applicationContext, notifyId(key),
                        "${race.stadiumName}${race.raceNo}R ◎${pick.lane}号艇 確定[$target] EV$evText",
                        "確率${(pick.prob * 100).toInt()}% × オッズ$oddsText / 締切${race.deadline}まで約${minutes}分\n" +
                            "直前オッズでC'成立。買うなら今（単勝1点・手動）。",
                        voteUrl = OddsRepository.raceVoteUrl(race.stadium, race.raceNo, dateYmd),
                        clipText = "${race.stadiumName}${race.raceNo}R 単勝${pick.lane}号艇 ¥${EvPolicy.DEFAULT_BET_AMOUNT}",
                        stadium = race.stadium,
                        raceNo = race.raceNo
                    )
                    notifyCount++
                }
            } else {
                // ── まだ早い: 「候補」通知＋締切3分前の自動再判定を予約 ──────
                //  早い時間帯のオッズは薄く幻の高EVが出やすいので、記録はまだしない。
                //  再判定の結果は同じ通知IDで「◎確定」または「見送りへ変更」に上書きされる。
                //  「判定対象のみ通知」で候補通知を抑えた場合は候補控えにも入れない
                //  （控えに入れると、後のスイープが「見送り」通知を出してしまい抑えた意味がなくなる）。
                //  ただし締切3分前の再判定は必ず予約する＝記録は設定に関わらず取り続ける。
                if (notifyThisPick) {
                    NotificationHelper.sendEvPickNotification(
                        applicationContext, notifyId(key),
                        "${race.stadiumName}${race.raceNo}R ◎${pick.lane}号艇 候補[$target] EV$evText",
                        "確率${(pick.prob * 100).toInt()}% × オッズ$oddsText（暫定・締切${race.deadline}）\n" +
                            "締切${FINAL_CONFIRM_LEAD_MIN}分前に最新オッズで自動再判定→この通知を更新します。" +
                            "更新が来なければ買う前にアプリでオッズ再確認を。",
                        voteUrl = OddsRepository.raceVoteUrl(race.stadium, race.raceNo, dateYmd),
                        clipText = "${race.stadiumName}${race.raceNo}R 単勝${pick.lane}号艇 ¥${EvPolicy.DEFAULT_BET_AMOUNT}",
                        stadium = race.stadium,
                        raceNo = race.raceNo
                    )
                    notifyCount++
                    // 候補を控える。締切3分前の再判定が決着させれば削除される。
                    // 決着しないまま締切を過ぎたら上のスイープが見送りで閉じる。
                    PendingCandidateStore.add(
                        applicationContext,
                        PendingCandidateStore.Pending(
                            key = key,
                            date = feed.date,
                            deadline = race.deadline,
                            stadium = race.stadium,
                            raceNo = race.raceNo,
                            lane = pick.lane,
                            stadiumName = race.stadiumName
                        )
                    )
                }
                scheduleFinalCheck(key, race.stadium, race.raceNo, minutes)
            }
        }

        if (changed) {
            prefs.edit()
                .putStringSet(KEY_CHECKED_SET, checked)
                .putInt(KEY_NOTIFY_COUNT, notifyCount)
                .apply()
        }
        return Result.success()
    }

    /**
     * 候補を出したのに決着していないレースのうち、締切を過ぎたものを見送りで閉じる。
     *
     * 締切3分前の再判定（EvFinalCheckWorker）が正常に決着させた候補は
     * pending から消えているので、ここに残るのは
     *  ・最終確認Workerがそもそも動かなかった（端末の省電力・OEMのバッテリー最適化）
     *  ・締切後にずれて通知できなかった
     * ケースだけ。締切超過を根拠に「見送り（締切）」で必ず閉じる。
     */
    private fun sweepStalePendingCandidates() {
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        for (p in PendingCandidateStore.all(applicationContext)) {
            // 当日でない古い候補は通知せず掃除（前日分の見送り通知を今さら出さない）
            if (p.date != today) {
                PendingCandidateStore.remove(applicationContext, p.key)
                continue
            }
            val minutes = TimeUtil.minutesUntilDeadline(p.deadline, now)
            // まだ締切前なら決着を待つ（最終確認Workerが動く余地がある）
            if (minutes == null || minutes >= 0) continue

            NotificationHelper.sendEvPickNotification(
                applicationContext, notifyId(p.key),
                "${p.stadiumName}${p.raceNo}R 見送り（締切）",
                "◎${p.lane}号艇の候補を出した後、締切3分前の自動再判定が完了しませんでした" +
                    "（オッズ取得失敗 or 端末の省電力）。\nこのレースは締切済みのため見送り扱いです。",
                stadium = p.stadium,
                raceNo = p.raceNo
            )
            PendingCandidateStore.remove(applicationContext, p.key)
        }
    }

    /**
     * 締切3分前に1回だけ動く最終確認Worker（EvFinalCheckWorker）を予約する。
     * 同じレースに二重予約しないよう enqueueUniqueWork(KEEP) を使う。
     */
    private fun scheduleFinalCheck(key: String, stadium: String, raceNo: Int, minutesLeft: Long) {
        val delayMin = (minutesLeft - FINAL_CONFIRM_LEAD_MIN).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<EvFinalCheckWorker>()
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    EvFinalCheckWorker.KEY_STADIUM to stadium,
                    EvFinalCheckWorker.KEY_RACE_NO to raceNo
                )
            )
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "kyotei_final_$key", ExistingWorkPolicy.KEEP, request
        )
    }

    /**
     * 締切直前オッズの取得を「締切3分前ちょうど」に予約する（収集用・全レース対象）。
     *
     * 15分周期のこの Worker で見つけた時点のオッズ（0〜18分前のどこか）ではなく、
     * 3分前に寄せて取ることで、実際に買う瞬間に近い値を残す。
     * 既に3分前を切って見つけたレースは delay=0 になり、すぐ取りに行く。
     */
    private fun scheduleOddsSnapshot(stadium: String, raceNo: Int, minutesLeft: Long) {
        val delayMin = (minutesLeft - FINAL_CONFIRM_LEAD_MIN).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<OddsSnapshotWorker>()
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    OddsSnapshotWorker.KEY_STADIUM to stadium,
                    OddsSnapshotWorker.KEY_RACE_NO to raceNo
                )
            )
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            OddsSnapshotWorker.workName(stadium, raceNo), ExistingWorkPolicy.KEEP, request
        )
    }
}
