package com.example.kyoteiai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kyoteiai.data.EvPolicy
import com.example.kyoteiai.data.FeedRepository
import com.example.kyoteiai.data.OddsRepository
import com.example.kyoteiai.data.PendingCandidateStore
import com.example.kyoteiai.data.PickLogRepository
import com.example.kyoteiai.data.PickRecord
import com.example.kyoteiai.data.TimeUtil
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * C'候補レースの最終確認ワーカー（2段階判定の2段階目）。
 *
 * EvPickWorkerが候補を見つけると、締切3分前を狙ってこのWorkerを1回だけ予約する。
 * 実行時に最新オッズでC'条件を再評価し、候補通知を同じIDで上書き更新する:
 *   - 成立維持 → 「◎確定 買うなら今」＋ picks.json に記録（C'仮想収支・履歴・◎バッジの材料）
 *   - 消滅     → 「見送りへ変更」（記録しない＝幻の高EVを成績に混ぜない）
 *
 * 背景: 早い時間帯の単勝オッズは投票が薄く過大なEVが出る（実例: 締切4分前12.9倍→確定2.1倍）。
 * 検証済みの回収率(5月145%/6月122%)は確定オッズ基準なので、判定は締切に近いほど正確になる。
 *
 * 遅延実行への備え: OSの省電力で実行が締切後にずれた場合、通知は出さない（もう買えないため）が、
 * オッズは確定値が取れるので記録だけは行う（収支データの正確性を優先）。
 */
class EvFinalCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_STADIUM = "stadium"    // 場コード（inputData）
        const val KEY_RACE_NO = "race_no"    // レース番号（inputData）
    }

    override suspend fun doWork(): Result {
        val stadium = inputData.getString(KEY_STADIUM) ?: return Result.success()
        val raceNo = inputData.getInt(KEY_RACE_NO, -1)
        if (raceNo < 0) return Result.success()

        val prefs = applicationContext.getSharedPreferences(
            HotRaceWorker.PREFS_NAME, Context.MODE_PRIVATE
        )
        // 通知機能がOFFなら何もしない（EvPickWorkerと同じ方針）
        if (!prefs.getBoolean(EvPickWorker.KEY_EV_NOTIFY_ENABLED, true)) return Result.success()
        // 「判定対象のみ通知」（既定OFF）。ONなら参考の◎は鳴らさない。
        // 記録（picks.json）はこの設定に関わらず必ず行う
        val targetOnlyNotify = prefs.getBoolean(EvPickWorker.KEY_TARGET_ONLY_NOTIFY, false)

        // フィードから対象レースを引き直す（確率・締切・場名はフィードが持っている）
        val feed = FeedRepository.load(applicationContext).feed ?: return Result.success()
        if (feed.date != LocalDate.now().toString()) return Result.success()
        val race = feed.races.firstOrNull { it.stadium == stadium && it.raceNo == raceNo }
            ?: return Result.success()
        val dateYmd = feed.date.filter { it.isDigit() }

        val key = HotRaceWorker.raceKey(stadium, raceNo)
        val minutes = TimeUtil.minutesUntilDeadline(race.deadline, System.currentTimeMillis())
        val beforeDeadline = minutes != null && minutes >= 0

        // 候補通知を実際に出していたか（＝控えに載っているか）。
        //  「判定対象のみ通知」で候補通知を抑えたレースは控えに載っていない。
        //  そのレースに「見送りへ変更」を送ると、出していない通知を訂正する形になり
        //  ユーザーには唐突な通知として届くので、決着系の通知は控えがある時だけ出す。
        val hadCandidateNotice =
            PendingCandidateStore.all(applicationContext).any { it.key == key }

        // 最新の単勝オッズで再判定
        val winOdds = OddsRepository.fetchWinOddsOnly(stadium, raceNo, dateYmd)
        if (winOdds == null) {
            // 取得失敗: 黙って終わると「候補だけ来て何も来ない」宙ぶらりんになる。
            // 締切前なら「再判定できず・手動確認を」で必ず決着させる（まだ買う時間がある）。
            // 締切後なら見送り扱い。どちらも候補控えを外す（スイープの二重通知を防ぐ）。
            if (beforeDeadline && hadCandidateNotice) {
                NotificationHelper.sendEvPickNotification(
                    applicationContext, EvPickWorker.notifyId(key),
                    "${race.stadiumName}${raceNo}R ⚠再判定できず",
                    "締切前の最新オッズを取得できませんでした（公式サイトの一時的な混雑など）。\n" +
                        "買う前にアプリでオッズを開いてC'成立か確認してください（締切${race.deadline}）。",
                    voteUrl = OddsRepository.raceVoteUrl(stadium, raceNo, dateYmd),
                    stadium = stadium,
                    raceNo = raceNo
                )
            } else if (hadCandidateNotice) {
                NotificationHelper.sendEvPickNotification(
                    applicationContext, EvPickWorker.notifyId(key),
                    "${race.stadiumName}${raceNo}R 見送り（締切）",
                    "自動再判定のオッズ取得が締切までに間に合いませんでした。締切済みのため見送り扱いです。"
                )
            }
            PendingCandidateStore.remove(applicationContext, key)
            return Result.success()
        }
        val probByLane = race.boats.associate { it.lane to it.prob }
        val pick = EvPolicy.findTopPick(probByLane, winOdds)

        if (pick != null) {
            // ── 成立維持: 記録（直前オッズ＝実購入にほぼ等しい値）──────────
            PickLogRepository.addIfAbsent(
                applicationContext,
                PickRecord(
                    date = feed.date,
                    stadium = stadium,
                    stadiumName = race.stadiumName,
                    raceNo = raceNo,
                    lane = pick.lane,
                    prob = pick.prob,
                    odds = pick.odds,
                    ev = pick.ev,
                    observedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    // v2.0: S1昇格判定の標本（締切5分前以内・昼）かを後から機械判定するために残す。
                    // これが無いと収支画面で「対象外・データ不足」にしかできない
                    mins = minutes?.toInt(),
                    deadline = race.deadline
                )
            )
            // この◎が事前登録の判定対象（C'≦5倍・昼）か。「判定対象のみ通知」の判断に使う
            val isTarget = EvPolicy.isRegisteredTarget(pick.odds, race.deadline)
            if (beforeDeadline && targetOnlyNotify && !isTarget) {
                // 「判定対象のみ通知」ON かつ 参考の◎: 記録は上で済ませ、通知だけ出さない。
                // 候補通知を出していた場合だけは、宙ぶらりんを残さないよう決着を伝える
                if (hadCandidateNotice) {
                    NotificationHelper.sendEvPickNotification(
                        applicationContext, EvPickWorker.notifyId(key),
                        "${race.stadiumName}${raceNo}R 参考（通知対象外）",
                        "最新オッズではC'成立ですが、事前登録した判定対象（C'≦5倍・昼）ではないため" +
                            "「判定対象のみ通知」設定により買い推奨は出しません。記録だけ残しています。"
                    )
                }
            } else if (beforeDeadline) {
                // 締切前: 「◎確定」へ通知を上書き（同じ通知ID）
                val evText = "%.2f".format(pick.ev)
                val oddsText = "%.1f".format(pick.odds)
                // 事前登録した判定対象（C'≦5倍・昼）かどうかを見出しに出す。
                // 区別しないと、昇格判定に数えない「参考」の◎を同じ重みで買ってしまう
                val target = EvPolicy.targetLabel(pick.odds, race.deadline)
                NotificationHelper.sendEvPickNotification(
                    applicationContext, EvPickWorker.notifyId(key),
                    "${race.stadiumName}${raceNo}R ◎${pick.lane}号艇 確定[$target] EV$evText",
                    "最新オッズで再判定→C'成立を維持。確率${(pick.prob * 100).toInt()}% × オッズ$oddsText\n" +
                        "締切${race.deadline}まで約${minutes}分。買うなら今（単勝1点・手動）。",
                    voteUrl = OddsRepository.raceVoteUrl(stadium, raceNo, dateYmd),
                    clipText = "${race.stadiumName}${raceNo}R 単勝${pick.lane}号艇 ¥${EvPolicy.DEFAULT_BET_AMOUNT}",
                    stadium = stadium,
                    raceNo = raceNo
                )
            } else if (hadCandidateNotice) {
                // 締切後（再判定が省電力で遅延）: もう買えないので見送りで閉じる。
                //  記録は上で済んでいる（確定オッズ＝収支データとして正しい）。
                NotificationHelper.sendEvPickNotification(
                    applicationContext, EvPickWorker.notifyId(key),
                    "${race.stadiumName}${raceNo}R 見送り（締切）",
                    "自動再判定が締切に間に合いませんでした（確定オッズではC'成立）。\n" +
                        "締切済みのため買えません。見送り扱いです。"
                )
            }
        } else if (hadCandidateNotice) {
            // ── 消滅: 見送りへ変更（記録しない）─────────────────────────
            //  これは候補通知の訂正なので、候補通知を出していたときだけ送る
            //  （「判定対象のみ通知」で候補を抑えたレースに訂正だけ届くのを防ぐ）
            if (beforeDeadline) {
                NotificationHelper.sendEvPickNotification(
                    applicationContext, EvPickWorker.notifyId(key),
                    "${race.stadiumName}${raceNo}R 見送りへ変更",
                    "最新オッズで再判定→C'条件が外れました（オッズ変動でEV低下 or 確率条件割れ）。\n" +
                        "このレースは買わないでください。"
                )
            } else {
                // 締切後かつ不成立: 見送りで閉じる
                NotificationHelper.sendEvPickNotification(
                    applicationContext, EvPickWorker.notifyId(key),
                    "${race.stadiumName}${raceNo}R 見送り（締切）",
                    "自動再判定が締切に間に合わず、確定オッズではC'条件も外れました。見送り扱いです。"
                )
            }
        }
        // どの分岐でも決着通知を出したので候補控えを外す（スイープの二重通知を防ぐ）
        PendingCandidateStore.remove(applicationContext, key)
        // ◎の記録が増えるのはこの経路が主なので、ウィジェットもここで描き直す
        // （EvPickWorker の15分周期だけに任せると「直近◎」が最大15分古いまま出る）
        KyoteiWidgetProvider.updateAll(applicationContext)
        return Result.success()
    }
}
