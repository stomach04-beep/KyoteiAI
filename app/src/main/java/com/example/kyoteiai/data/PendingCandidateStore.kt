package com.example.kyoteiai.data

import android.content.Context
import com.example.kyoteiai.HotRaceWorker
import org.json.JSONArray
import org.json.JSONObject

/**
 * 2段階EV通知の「候補を出したが、まだ確定/見送りが決まっていないレース」を控えておく置き場。
 *
 * 【なぜ必要か】
 *  候補通知の後に締切3分前の再判定（EvFinalCheckWorker）が
 *    - 端末の省電力でそもそも動かない／締切後にずれる
 *    - 動いてもオッズ取得に失敗する
 *  と「候補だけ来て、確定も見送りも来ない」宙ぶらりんが起きる。
 *  ここに候補を控えておき、EvPickWorker の周期実行が
 *  「締切を過ぎたのに未決着の候補」を見つけて必ず見送り通知で閉じられるようにする。
 *
 * 【出入り】
 *  - 追加: EvPickWorker が候補通知を実際に送ったとき
 *  - 削除: EvFinalCheckWorker が terminal 通知（確定/見送り/再判定できず）を出したとき、
 *          または EvPickWorker のスイープが締切超過分を見送りで閉じたとき
 *
 * StringSet の変更ガチャ（コピー必須）を避けるため、JSON文字列を丸ごと差し替える方式にする。
 */
object PendingCandidateStore {

    // HotRaceWorker と同じ SharedPreferences を共用（プレフ名の二重定義を避ける）
    private const val KEY = "ev_pending_candidates"

    /** 未決着候補1件分。締切後に見送り通知を組み立てるのに必要な情報だけ持つ */
    data class Pending(
        val key: String,          // レースキー（場コード_レース番号）
        val date: String,         // フィード日付（当日以外は掃除する）
        val deadline: String,     // 締切 "HH:mm"
        val stadium: String,      // 場コード
        val raceNo: Int,          // レース番号
        val lane: Int,            // ◎の艇番
        val stadiumName: String   // 場名（通知本文用）
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(HotRaceWorker.PREFS_NAME, Context.MODE_PRIVATE)

    /** 現在控えている全候補を読む */
    fun all(context: Context): List<Pending> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Pending(
                    key = o.getString("key"),
                    date = o.getString("date"),
                    deadline = o.getString("deadline"),
                    stadium = o.getString("stadium"),
                    raceNo = o.getInt("raceNo"),
                    lane = o.getInt("lane"),
                    stadiumName = o.getString("stadiumName")
                )
            }
        } catch (e: Exception) {
            emptyList()  // 壊れていたら空扱い（次の add で作り直される）
        }
    }

    /** 候補を追加（同じ key があれば置き換え） */
    fun add(context: Context, p: Pending) {
        val list = all(context).filter { it.key != p.key } + p
        save(context, list)
    }

    /** 候補を削除（決着が付いたとき） */
    fun remove(context: Context, key: String) {
        val list = all(context).filter { it.key != key }
        save(context, list)
    }

    private fun save(context: Context, list: List<Pending>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("key", p.key)
                    .put("date", p.date)
                    .put("deadline", p.deadline)
                    .put("stadium", p.stadium)
                    .put("raceNo", p.raceNo)
                    .put("lane", p.lane)
                    .put("stadiumName", p.stadiumName)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }
}
