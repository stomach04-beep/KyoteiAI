package com.example.kyoteiai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 締切直前オッズの収集ログ（odds_log.json）。
 *
 * 【なぜ要るか】
 *  競艇の過去オッズのうち「確定オッズ」は公式に残るので後から何日でも遡れる。
 *  だが実際に賭ける瞬間の「締切前オッズ」はどこにも残らず、その場で記録しないと永久に失われる。
 *  そして検証で効くのはこちら側（確定オッズだけで計算すると126〜149%と出るのに、
 *  締切前オッズで実測すると84〜87%まで落ちる＝確定オッズだけでは「勝てる」と誤診する）。
 *
 *  PC版の log_odds.py が同じ仕事をしていたが、PCが日中スリープすると収集ゼロになる。
 *  端末はレース時間帯もずっと生きているので、こちらで貯める。
 *
 * 【PC版 odds_log.csv との対応】
 *  1レース1レコード・6艇分のオッズと確率をまとめて持つ（CSVは1艇1行なので取り出し時に展開する）。
 *
 * 保存は「一時ファイルに書いてからrename」の原子保存（feedback_json_atomic_save）。
 */

/** あるレースの締切前オッズ1件（6艇まとめて） */
data class OddsSnapshot(
    val date: String,               // "2026-07-15"
    val stadium: String,            // 場コード "24"
    val stadiumName: String,        // 場名 "大村"
    val raceNo: Int,                // レース番号
    val deadline: String,           // 締切時刻 "22:41"
    val observedAt: String,         // 実際に取得できた時刻 "HH:mm"
    val minsToDeadline: Int,        // 取得時点で締切まで何分だったか（＝この記録の鮮度そのもの）
    val odds: Map<Int, Double>,     // 艇番 → 単勝オッズ
    val probs: Map<Int, Double>     // 艇番 → モデル確率（フィードの値）
)

object OddsLogStore {
    private const val FILE_NAME = "odds_log.json"
    private const val MAX_RECORDS = 10_000   // 約2ヶ月分。超えたら古い順に捨てる
    private val lock = Any()

    /** 全件読込（壊れていれば空リスト＝収集を止めない） */
    fun load(context: Context): List<OddsSnapshot> = synchronized(lock) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                OddsSnapshot(
                    date = o.optString("date"),
                    stadium = o.optString("stadium"),
                    stadiumName = o.optString("stadiumName"),
                    raceNo = o.optInt("raceNo"),
                    deadline = o.optString("deadline"),
                    observedAt = o.optString("observedAt"),
                    minsToDeadline = o.optInt("minsToDeadline", -1),
                    odds = readLaneMap(o.optJSONObject("odds")),
                    probs = readLaneMap(o.optJSONObject("probs"))
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 艇番→数値 のJSONを Map に戻す */
    private fun readLaneMap(o: JSONObject?): Map<Int, Double> {
        if (o == null) return emptyMap()
        val m = mutableMapOf<Int, Double>()
        o.keys().forEach { k ->
            val lane = k.toIntOrNull()
            if (lane != null) m[lane] = o.optDouble(k, 0.0)
        }
        return m
    }

    /**
     * 1件追加。同一レース（日付＋場＋R）が既にあれば追加しない。
     * ＝ PC版 log_odds.py の「1レース1回だけ記録」と同じ約束。
     */
    fun addIfAbsent(context: Context, record: OddsSnapshot) = synchronized(lock) {
        val current = load(context)
        val dup = current.any {
            it.date == record.date && it.stadium == record.stadium && it.raceNo == record.raceNo
        }
        if (dup) return
        // 上限を超えたら古い方から捨てる（端末のストレージを圧迫しない）
        val next = (current + record).takeLast(MAX_RECORDS)
        saveAll(context, next)
    }

    fun saveAll(context: Context, records: List<OddsSnapshot>) = synchronized(lock) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("date", r.date)
                put("stadium", r.stadium)
                put("stadiumName", r.stadiumName)
                put("raceNo", r.raceNo)
                put("deadline", r.deadline)
                put("observedAt", r.observedAt)
                put("minsToDeadline", r.minsToDeadline)
                put("odds", JSONObject().apply { r.odds.forEach { (k, v) -> put(k.toString(), v) } })
                put("probs", JSONObject().apply { r.probs.forEach { (k, v) -> put(k.toString(), v) } })
            })
        }
        atomicWrite(File(context.filesDir, FILE_NAME), arr.toString())
    }

    /** 収集済み件数（設定画面などで「今いくつ貯まったか」を見せる用） */
    fun count(context: Context): Int = load(context).size

    /** そのレースを既に記録済みか（無駄な再取得を避けるための事前チェック用） */
    fun hasRace(context: Context, date: String, stadium: String, raceNo: Int): Boolean =
        load(context).any { it.date == date && it.stadium == stadium && it.raceNo == raceNo }

    /**
     * 取得したオッズをレコードに整えて保存する。
     *
     * 締切3分前のワーカーからも、通知経路が直前に取得したついででも、
     * ここを通す。レコードの作り方を2か所に書くと必ずズレるため入口を1本にする。
     *
     * @param winOdds 公式から取れた 艇番→単勝オッズ（取れなかった艇は null が入る）
     * @param minsToDeadline 取得時点で締切まで何分だったか（この記録の鮮度そのもの）
     */
    fun recordRace(
        context: Context,
        date: String,
        race: RacePred,
        winOdds: Map<Int, Double?>,
        minsToDeadline: Int,
        observedAt: String
    ) {
        // 取れなかった艇（null）は捨てる。6艇そろわなくても取れた分は残す
        val oddsMap = winOdds.filterValues { it != null }.mapValues { it.value!! }
        if (oddsMap.isEmpty()) return
        addIfAbsent(
            context,
            OddsSnapshot(
                date = date,
                stadium = race.stadium,
                stadiumName = race.stadiumName,
                raceNo = race.raceNo,
                deadline = race.deadline,
                observedAt = observedAt,
                minsToDeadline = minsToDeadline,
                odds = oddsMap,
                probs = race.boats.associate { it.lane to it.prob }
            )
        )
    }
}
