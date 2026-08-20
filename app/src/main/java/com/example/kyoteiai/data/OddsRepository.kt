package com.example.kyoteiai.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 競艇公式サイトからオッズ（単勝・3連単）を取得して解析するリポジトリ。
 *
 * PC側の fetch_odds.py（実データ検証済み）をそのまま Kotlin へ移植したもの。
 * ネットワークは coroutine(Dispatchers.IO) で行い、追加依存（Retrofit等）は使わず
 * java.net.HttpURLConnection と正規表現だけで実装する。
 */
object OddsRepository {

    // 公式サイトのオッズページURL（rno=レース番号 / jcd=場コード2桁 / hd=日付8桁）
    private const val WIN_URL =
        "https://www.boatrace.jp/owpc/pc/race/oddstf?rno=%d&jcd=%s&hd=%s"     // 単勝
    private const val TRIFECTA_URL =
        "https://www.boatrace.jp/owpc/pc/race/odds3t?rno=%d&jcd=%s&hd=%s"     // 3連単

    // ブラウザを装う User-Agent（付けないと弾かれることがある）
    private const val USER_AGENT = "Mozilla/5.0"

    // HTTP取得の成否と理由を残すログタグ（adb logcat -d | grep KyoteiHttp）
    private const val TAG = "KyoteiHttp"

    // HTTP接続のタイムアウト（ミリ秒）
    //  公式サイトは叩かれると応答を絞る。実測で 1秒未満 → 9〜10秒 まで悪化する
    //  （2026-07-15 実機から curl: HTTP=200 time=9.28s）。
    //  10秒だと絞られた瞬間に軒並みタイムアウトし、締切前オッズを取り逃す。
    //  締切前オッズは後から絶対に取れないので、遅くても待って取り切る方を優先する。
    //  PC側の収集スクリプトも同じ理由で20〜30秒にしてある。
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000

    // 未発売時にHTML内に出る文言
    private const val NOT_ON_SALE_MARK = "データはありません"

    /**
     * 該当レースの「投票ページ」URLを作る（単一定義。通知・レース詳細の両方が使う）。
     *
     * レースページに voteTagId=mainVoteTag を付けると、ログイン済みならそのレースの
     * 投票フローへ入れる（公式の投票ボタンのリダイレクト仕様
     * authAfterUrl=/pc/race/oddstf?rno=..&jcd=..&hd=..&voteTagId=mainVoteTag から確認）。
     * 未ログインでもそのレースのページ＋投票ボタンが出るので1タップで続行できる。
     */
    fun raceVoteUrl(stadium: String, raceNo: Int, dateYmd: String): String =
        "https://www.boatrace.jp/owpc/pc/race/oddstf?rno=$raceNo&jcd=$stadium&hd=$dateYmd&voteTagId=mainVoteTag"

    // オッズ値を拾う正規表現（class属性は末尾スペース付き `class="oddsPoint "`）
    // PC側 fetch_odds.py で実データ検証済みのパターン。
    private val ODDS_REGEX = Regex("class=\"oddsPoint[^\"]*\">\\s*([^<]*?)\\s*<")

    /**
     * オッズ取得結果。
     * notOnSale=true のときは未発売（他フィールドは空）。
     * winOdds は 号艇(1..6) → 単勝オッズ（欠場等で数値化不可なら null）。
     * trifectaOdds は 組番文字列("1-2-3") → 3連単オッズ（数値化できたものだけ）。
     */
    data class OddsFetchResult(
        val notOnSale: Boolean,                 // 未発売フラグ
        val winOdds: Map<Int, Double?>,         // 単勝オッズ（号艇→オッズ）
        val trifectaOdds: Map<String, Double>   // 3連単オッズ（組番→オッズ）
    )

    /**
     * 指定レースの単勝・3連単オッズをまとめて取得する。
     *
     * @param stadium 場コード2桁（フィードの stadium。例 "10"）
     * @param raceNo  レース番号（1〜12）
     * @param dateYmd 日付8桁（例 "20260708"）
     * @return 取得結果。どちらか一方でも未発売なら notOnSale=true とみなす。
     */
    suspend fun fetchOdds(
        stadium: String,
        raceNo: Int,
        dateYmd: String
    ): OddsFetchResult = withContext(Dispatchers.IO) {
        // ── 単勝オッズ ──
        val winHtml = httpGet(WIN_URL.format(raceNo, stadium, dateYmd))
        // ── 3連単オッズ ──
        val trifectaHtml = httpGet(TRIFECTA_URL.format(raceNo, stadium, dateYmd))

        // どちらも取得できなければ未発売扱い（通信失敗も含めここで丸める）
        if (winHtml == null || trifectaHtml == null) {
            return@withContext OddsFetchResult(notOnSale = true, emptyMap(), emptyMap())
        }

        // 単勝の解析
        val winParsed = parseWinOdds(winHtml)
        // 3連単の解析
        val trifectaParsed = parseTrifectaOdds(trifectaHtml)

        // どちらかが未発売なら未発売として返す
        if (winParsed == null || trifectaParsed == null) {
            return@withContext OddsFetchResult(notOnSale = true, emptyMap(), emptyMap())
        }

        OddsFetchResult(notOnSale = false, winOdds = winParsed, trifectaOdds = trifectaParsed)
    }

    /**
     * 単勝オッズだけを取得する（通知Worker用の軽量版）。
     *
     * fetchOdds() は単勝＋3連単の2ページを叩くが、EV狙い目通知の判定に
     * 必要なのは単勝だけなので、公式サイトへの負荷を半分にするために分ける。
     *
     * @return 号艇→単勝オッズ。未発売・取得失敗は null
     */
    suspend fun fetchWinOddsOnly(
        stadium: String,
        raceNo: Int,
        dateYmd: String
    ): Map<Int, Double?>? = withContext(Dispatchers.IO) {
        val html = httpGet(WIN_URL.format(raceNo, stadium, dateYmd))
            ?: return@withContext null
        parseWinOdds(html)
    }

    /**
     * 単勝と複勝をまとめて返す（同じページなので**追加のリクエストは発生しない**）。
     *
     * 単勝ページ(oddstf)には複勝表も載っており、oddsPoint は12個並ぶ。
     * 先頭6個が単勝、7〜12個目が複勝。これまで先頭6個だけ使い複勝を捨てていた。
     *
     * 【なぜ複勝も要るか】
     *  2026-08-06 の検証で、複勝の期待値買いは確定オッズ上で回収率148%（上位10本を除いても
     *  121%・30日中27日プラス）と出た。ただしそれは確定オッズでの上限値で、
     *  実際に買う瞬間の締切前オッズは**その日に記録しないと永久に失われる**。
     *  単勝で同じ形（確定136%）が実測84〜87%に落ちた前例があるため、
     *  本当に勝てるかは締切前の複勝オッズを貯めない限り判定できない。
     *
     * @return 単勝と複勝。未発売・取得失敗は null
     */
    data class WinPlaceOdds(
        val win: Map<Int, Double?>,
        /** 号艇 → (複勝オッズ下限, 上限)。複勝は範囲表示なので2値で持つ。取れない艇は null */
        val place: Map<Int, Pair<Double, Double>?>
    )

    suspend fun fetchWinAndPlaceOdds(
        stadium: String,
        raceNo: Int,
        dateYmd: String
    ): WinPlaceOdds? = withContext(Dispatchers.IO) {
        val html = httpGet(WIN_URL.format(raceNo, stadium, dateYmd))
            ?: return@withContext null
        val win = parseWinOdds(html) ?: return@withContext null
        WinPlaceOdds(win, parsePlaceOdds(html))
    }

    /**
     * 複勝オッズを解析する（oddsPoint の7〜12個目）。
     * 表示は「1.0-1.3」のような範囲。単値のこともあるのでその場合は下限＝上限とする。
     * 複勝表が無い（12個に満たない）ページでも単勝は使えるので、ここでは空を返すだけにする。
     */
    private fun parsePlaceOdds(html: String): Map<Int, Pair<Double, Double>?> {
        val values = ODDS_REGEX.findAll(html).map { it.groupValues[1] }.toList()
        if (values.size < 12) {
            Log.i(TAG, "解析: 複勝が取れない(oddsPoint${values.size}個) → 単勝のみ記録")
            return emptyMap()
        }
        val map = HashMap<Int, Pair<Double, Double>?>()
        var zeroCount = 0
        for (lane in 1..6) {
            val raw = values[5 + lane]          // 7個目(index6)から順に1〜6号艇
            val parts = raw.split("-")
            val parsed = if (parts.size == 2) {
                val lo = parts[0].toDoubleOrNull()
                val hi = parts[1].toDoubleOrNull()
                if (lo != null && hi != null) lo to hi else null
            } else {
                raw.toDoubleOrNull()?.let { it to it }   // 範囲でなく単値のことがある
            }
            // オッズは必ず1.0以上。0や負は「発売前で未確定」を意味するので記録しない
            // （0で保存すると検証データが汚れる。ケースバッテリー0%と同じ「0は未確定値」）
            map[lane] = if (parsed != null && parsed.first > 0.0 && parsed.second > 0.0) {
                parsed
            } else {
                if (parsed != null) zeroCount++
                null
            }
        }
        if (zeroCount > 0) {
            Log.i(TAG, "解析: 複勝オッズ0(未確定)を${zeroCount}艇ぶん除外")
        }
        return map
    }

    /**
     * 単勝オッズを解析する。
     * 正規表現マッチの先頭6個が1〜6号艇の単勝オッズ。
     * 6個未満・未発売文言があれば未発売として null を返す。
     * 欠場等で数値化できない艇はその号艇を null にして残す。
     */
    private fun parseWinOdds(html: String): Map<Int, Double?>? {
        if (html.contains(NOT_ON_SALE_MARK)) {
            Log.i(TAG, "解析: 未発売文言あり → null")
            return null
        }

        val values = ODDS_REGEX.findAll(html).map { it.groupValues[1] }.toList()
        if (values.size < 6) {
            // 取得は成功したのに解析で落ちた＝ページ構造変化や締切後ページの疑い
            Log.w(TAG, "解析: oddsPointマッチ${values.size}個(<6) html${html.length}字 → null")
            return null
        }

        // 先頭6個を1〜6号艇へ割り当てる
        val map = HashMap<Int, Double?>()
        var zeroCount = 0
        for (lane in 1..6) {
            val v = values[lane - 1].toDoubleOrNull() // "欠場"等は null
            // 複勝と同じく0以下は未確定。null にして記録から外す
            map[lane] = if (v != null && v > 0.0) {
                v
            } else {
                if (v != null) zeroCount++
                null
            }
        }
        if (zeroCount > 0) {
            Log.i(TAG, "解析: 単勝オッズ0(未確定)を${zeroCount}艇ぶん除外")
        }
        return map
    }

    /**
     * 3連単オッズを解析する。
     * 正規表現マッチがちょうど120個、行メジャー順で並ぶ（PC検証で確定）。
     * 120個でなければ未発売として null を返す。
     */
    private fun parseTrifectaOdds(html: String): Map<String, Double>? {
        if (html.contains(NOT_ON_SALE_MARK)) return null

        val pts = ODDS_REGEX.findAll(html).map { it.groupValues[1] }.toList()
        if (pts.size != 120) return null // 120個そろわない＝未発売

        // 各1着艇について、残り5艇の(2着,3着)順列を「2着昇順→3着昇順」で20通り生成
        val combosByCol = buildTrifectaCombos()

        // pts[i] を i=0..119 で、for row in 0..19 → for first in 1..6 の順に割り当てる
        // つまり index = row*6 + (first-1)
        val map = HashMap<String, Double>()
        for (row in 0 until 20) {
            for (first in 1..6) {
                val idx = row * 6 + (first - 1)
                val (second, third) = combosByCol.getValue(first)[row]
                val odds = pts[idx].toDoubleOrNull() ?: continue // 欠場等は組から除外
                map["$first-$second-$third"] = odds
            }
        }
        return map
    }

    /**
     * 3連単の組番テーブルを作る。
     * combosByCol[first] = 1着=first のときの (2着,3着) ペア20通り（2着昇順→3着昇順）。
     */
    private fun buildTrifectaCombos(): Map<Int, List<Pair<Int, Int>>> {
        val result = HashMap<Int, List<Pair<Int, Int>>>()
        for (first in 1..6) {
            // 残り5艇（昇順）
            val remaining = (1..6).filter { it != first }
            val combos = ArrayList<Pair<Int, Int>>(20)
            // 2着を昇順、その中で3着を昇順に回して20通り生成
            for (second in remaining) {
                for (third in remaining) {
                    if (third == second) continue
                    combos.add(Pair(second, third))
                }
            }
            result[first] = combos
        }
        return result
    }

    /** User-Agent 付きで HTTP GET し本文を返す。失敗時は null（例外を投げない）
     *
     *  失敗をすべて null に畳むと「なぜ取れなかったか」が外から見えず、
     *  取得失敗の原因調査ができない（2026-07-15に実際に詰まった）。
     *  そのため HTTPコード／例外の種類／所要秒を必ず logcat に残す。
     *  確認: adb logcat -d | grep KyoteiHttp
     */
    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        val t0 = System.currentTimeMillis()
        // ログにはURL全体でなくクエリ部分だけ（rno=10&jcd=01&hd=... でレースが分かる）
        val tag = urlStr.substringAfter("?", urlStr.takeLast(30))
        fun took() = "%.1f秒".format((System.currentTimeMillis() - t0) / 1000.0)
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // ブラウザを装う（付けないと弾かれることがある）
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Log.i(TAG, "$tag → 200 ${body.length}字 ${took()}")
                body
            } else {
                Log.w(TAG, "$tag → HTTP $code ${took()}")   // 403/429/503ならサーバ側の拒否
                null
            }
        } catch (e: Exception) {
            // 圏外・タイムアウト等は null を返すが、種類とメッセージは必ず残す
            Log.w(TAG, "$tag → ${e.javaClass.simpleName}: ${e.message?.take(80)} ${took()}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
