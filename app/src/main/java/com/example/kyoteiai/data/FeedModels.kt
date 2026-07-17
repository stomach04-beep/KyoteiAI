package com.example.kyoteiai.data

import org.json.JSONObject

/**
 * 競艇AI予想フィードのデータモデル一式。
 *
 * サーバー（またはassets同梱のサンプル）が返すJSONを、
 * org.json（Android標準）でこれらの data class に変換して使う。
 * kotlinx.serialization / Moshi 等の追加依存は使わない方針。
 */

// フィード全体（1日分の予想）
data class Feed(
    val date: String,          // 例 "2026-07-08"
    val generatedAt: String,   // 生成時刻（表示用）
    val model: String,         // モデルのバージョン（例 "v3"）
    val status: String,        // "ok" など
    val hotCount: Int,         // 狙い目レースの件数
    val venues: List<Venue>,   // 本日開催の競艇場一覧
    val races: List<RacePred>  // 全レースの予想一覧
)

// 競艇場（開催場）1つ分
data class Venue(
    val stadium: String,       // 場コード（例 "10"）
    val stadiumName: String,   // 場名（例 "三国"）
    val nRaces: Int,           // その場のレース数
    val firstDeadline: String  // 初レースの締切時刻（例 "08:32"）
)

// 1レースの予想
data class RacePred(
    val stadium: String,       // 場コード
    val stadiumName: String,   // 場名
    val raceNo: Int,           // レース番号（1〜12）
    val deadline: String,      // 締切時刻（例 "08:32"）
    val kind: String,          // レース種別（例 "予選" "準優勝戦"）
    val topLane: Int,          // 本命の艇番（1〜6）
    val topName: String,       // 本命の選手名
    val topProb: Double,       // 本命の勝率（0.0〜1.0）
    val isHot: Boolean,        // 狙い目フラグ（AIが高確信と判断）
    val boats: List<BoatPred>  // 6艇分の予想
)

// 1艇の予想
data class BoatPred(
    val lane: Int,             // 艇番（1〜6）
    val racerName: String,     // 選手名
    val clazz: String,         // 級別（A1/A2/B1/B2）※classは予約語なのでclazz
    val prob: Double           // 勝率（0.0〜1.0）
)

/**
 * JSON文字列を Feed に変換するパーサー。
 *
 * 想定外のキー欠損があっても落ちないよう、optXxx で安全に読み取る。
 * パースに致命的に失敗した場合は null を返す（呼び出し側でフォールバック処理する）。
 */
object FeedParser {

    /** JSON文字列を Feed に変換する。失敗時は null */
    fun parse(jsonText: String): Feed? {
        return try {
            val root = JSONObject(jsonText)

            // 競艇場一覧
            val venuesJson = root.optJSONArray("venues")
            val venues = mutableListOf<Venue>()
            if (venuesJson != null) {
                for (i in 0 until venuesJson.length()) {
                    val v = venuesJson.getJSONObject(i)
                    venues.add(
                        Venue(
                            stadium = v.optString("stadium"),
                            stadiumName = v.optString("stadium_name"),
                            nRaces = v.optInt("n_races"),
                            firstDeadline = v.optString("first_deadline")
                        )
                    )
                }
            }

            // レース一覧
            val racesJson = root.optJSONArray("races")
            val races = mutableListOf<RacePred>()
            if (racesJson != null) {
                for (i in 0 until racesJson.length()) {
                    val r = racesJson.getJSONObject(i)

                    // 6艇分
                    val boatsJson = r.optJSONArray("boats")
                    val boats = mutableListOf<BoatPred>()
                    if (boatsJson != null) {
                        for (j in 0 until boatsJson.length()) {
                            val b = boatsJson.getJSONObject(j)
                            boats.add(
                                BoatPred(
                                    lane = b.optInt("lane"),
                                    racerName = b.optString("racer_name"),
                                    clazz = b.optString("class"),
                                    prob = b.optDouble("prob", 0.0)
                                )
                            )
                        }
                    }

                    races.add(
                        RacePred(
                            stadium = r.optString("stadium"),
                            stadiumName = r.optString("stadium_name"),
                            raceNo = r.optInt("race_no"),
                            deadline = r.optString("deadline"),
                            kind = r.optString("kind"),
                            topLane = r.optInt("top_lane"),
                            topName = r.optString("top_name"),
                            topProb = r.optDouble("top_prob", 0.0),
                            isHot = r.optBoolean("is_hot", false),
                            boats = boats
                        )
                    )
                }
            }

            Feed(
                date = root.optString("date"),
                generatedAt = root.optString("generated_at"),
                model = root.optString("model"),
                status = root.optString("status"),
                hotCount = root.optInt("hot_count"),
                venues = venues,
                races = races
            )
        } catch (e: Exception) {
            // JSON構造が壊れている等。呼び出し側でフォールバックさせる
            null
        }
    }
}
