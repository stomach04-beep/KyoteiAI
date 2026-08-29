package com.example.kyoteiai

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import com.example.kyoteiai.data.EvPolicy
import com.example.kyoteiai.data.Feed
import com.example.kyoteiai.data.OddsLogStore
import com.example.kyoteiai.data.PickLogRepository
import com.example.kyoteiai.data.RunLogStore
import com.example.kyoteiai.data.TimeUtil
import java.time.LocalDate

/**
 * ホーム画面ウィジェット。アプリを開かなくても次の3つが分かる。
 *
 *   ① 本日の「判定対象◎」件数と、直近◎の「場R・艇・EV」
 *   ② 本日の記録数と最終記録時刻（＝収集が生きているか）
 *   ③ 次の締切までの分数
 *
 * 【設計上の約束】
 *  ウィジェットの更新処理では通信しない。フィード（レース一覧）は
 *  EvPickWorker が取得したときに saveTodayDeadlines() で端末へ控えておき、
 *  ここではその控えとローカルのJSON（picks.json / odds_log.json / run_log.json）
 *  だけを読む。ウィジェット更新はメインスレッドで走ることがあり、
 *  ここで通信すると固まる・ANRになるため。
 *
 * 【更新契機】
 *  本命は EvPickWorker（15分周期）の末尾から updateAll() を明示的に叩く経路。
 *  AppWidgetProviderInfo の updatePeriodMillis（30分）はアプリが動いていない時の保険。
 */
class KyoteiWidgetProvider : AppWidgetProvider() {

    companion object {
        // ── ウィジェット用の控え（EvPickWorker が書き、ウィジェットが読む）──────
        //  ウィジェットは通信できないので「今日のレースの締切一覧」を端末に置いておく。
        //  形式: "HH:mm|場名R" をカンマ区切りで並べたもの（例 "08:32|大村1R,08:55|徳山2R"）。
        //  絶対時刻ではなく「一覧」を持たせるのは、次の更新までの15分の間に
        //  締切が1つ過ぎても、描画時にその場で次のレースを選び直せるようにするため。
        private const val KEY_DEADLINES = "widget_today_deadlines"
        private const val KEY_DEADLINES_DATE = "widget_today_deadlines_date"

        /** 区切り文字（場名に使われない文字を選ぶ） */
        private const val SEP_ITEM = ","
        private const val SEP_FIELD = "|"

        /**
         * 今日のレースの締切一覧を控える（EvPickWorker がフィードを取れたときに呼ぶ）。
         * ウィジェットの「次の締切まであと何分」はこの控えだけで計算する。
         */
        fun saveTodayDeadlines(prefs: SharedPreferences, feed: Feed) {
            val packed = feed.races
                .sortedBy { it.deadline }
                .joinToString(SEP_ITEM) {
                    "${it.deadline}$SEP_FIELD${it.stadiumName}${it.raceNo}R"
                }
            prefs.edit()
                .putString(KEY_DEADLINES, packed)
                .putString(KEY_DEADLINES_DATE, feed.date)
                .apply()
        }

        /**
         * 控えた締切一覧から「次に締め切るレース」を選ぶ（純関数・テスト対象）。
         *
         * @param packed  saveTodayDeadlines が作った文字列
         * @param nowMillis 現在時刻
         * @return 次のレースの (残り分数, ラベル)。もう無ければ null
         */
        fun nextRace(packed: String?, nowMillis: Long): Pair<Long, String>? {
            if (packed.isNullOrEmpty()) return null
            return packed.split(SEP_ITEM)
                .mapNotNull { item ->
                    val parts = item.split(SEP_FIELD)
                    if (parts.size != 2) return@mapNotNull null
                    val mins = TimeUtil.minutesUntilDeadline(parts[0], nowMillis)
                        ?: return@mapNotNull null
                    if (mins < 0) null else mins to parts[1]   // 締切済みは飛ばす
                }
                .minByOrNull { it.first }
        }

        /**
         * 全てのウィジェットを最新表示に更新する。
         * ワーカーのバックグラウンドスレッドから呼ばれる想定（ファイル読込を含むため）。
         */
        fun updateAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, KyoteiWidgetProvider::class.java)
                )
                // 1つも配置されていなければ何もしない（無駄なファイル読込を避ける）
                if (ids.isEmpty()) return
                val views = buildViews(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } catch (e: Exception) {
                // ウィジェットの更新失敗で収集本体を巻き添えにしない（表示は補助機能）
            }
        }

        /** 表示内容を組み立てた RemoteViews を返す */
        private fun buildViews(context: Context): RemoteViews {
            val today = LocalDate.now().toString()
            val now = System.currentTimeMillis()

            // ── ① 本日の判定対象◎ ─────────────────────────────
            //  「判定対象か」の条件は EvPolicy の単一定義を使う（画面・PC集計と同じ物差し）
            val todayPicks = PickLogRepository.load(context).filter { it.date == today }
            val targets = todayPicks.filter {
                EvPolicy.isJudgeTargetRecord(it.date, it.odds, it.deadline, it.mins)
            }
            val latest = todayPicks.maxByOrNull { it.observedAt }
            val latestText = if (latest == null) {
                "直近◎ まだありません"
            } else {
                val label = EvPolicy.targetLabel(latest.odds, latest.deadline)
                "直近◎ ${latest.stadiumName}${latest.raceNo}R ${latest.lane}号艇 " +
                    "EV%.2f［$label］".format(latest.ev)
            }

            // ── ② 本日の記録数と最終記録時刻 ────────────────────
            val todaySnaps = OddsLogStore.load(context).filter { it.date == today }
            val lastAt = todaySnaps.maxByOrNull { it.observedAt }?.observedAt
            val run = RunLogStore.dayStats(context, today)
            val collectText = "記録 ${todaySnaps.size}件" +
                (if (lastAt != null) "・最終 $lastAt" else "・まだ0件") +
                "（実行${run.runs}回）"

            // ── ③ 次の締切までの分数 ───────────────────────────
            val prefs = context.getSharedPreferences(
                HotRaceWorker.PREFS_NAME, Context.MODE_PRIVATE
            )
            // 控えが今日のものでなければ使わない（昨日の締切で「あと何分」と出さない）
            val packed = if (prefs.getString(KEY_DEADLINES_DATE, null) == today) {
                prefs.getString(KEY_DEADLINES, null)
            } else {
                null
            }
            val next = nextRace(packed, now)
            val nextText = when {
                packed == null -> "次の締切 —（本日の予想が未取得）"
                next == null -> "次の締切 本日は終了"
                else -> "次の締切 ${next.second} まであと${next.first}分"
            }

            return RemoteViews(context.packageName, R.layout.widget_kyotei).apply {
                setTextViewText(
                    R.id.widget_target_count,
                    "判定対象◎ ${targets.size}件（◎全体 ${todayPicks.size}件）"
                )
                setTextViewText(R.id.widget_latest_pick, latestText)
                setTextViewText(R.id.widget_collect, collectText)
                setTextViewText(R.id.widget_next_deadline, nextText)
                // タップでアプリを開く
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            }
        }

        /** ウィジェットをタップしたときにアプリを開く PendingIntent */
        private fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    /**
     * OSからの定期更新・ウィジェット追加時に呼ばれる。
     * ローカルのJSONを読むので、メインスレッドを塞がないよう goAsync() で別スレッドへ逃がす
     * （feedback_onresume_bluetooth_workmanager と同じ考え方＝重い処理をUIスレッドに置かない）。
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        Thread {
            try {
                val views = buildViews(context)
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } catch (e: Exception) {
                // 失敗しても落とさない（次の更新契機で描き直される）
            } finally {
                pending.finish()
            }
        }.start()
    }
}
