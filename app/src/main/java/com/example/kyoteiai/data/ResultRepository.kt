package com.example.kyoteiai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 競艇公式サイトからレース結果（着順・3連単払戻）を取得して解析するリポジトリ。
 *
 * OddsRepository と同じ作法（HttpURLConnection + User-Agent + Dispatchers.IO + 正規表現）で実装。
 * 追加依存（Retrofit / Jsoup 等）は使わない。
 *
 * 着順の取り方（実データ検証済み）:
 *   結果ページのHTMLには「3連単」の勝ち組番があり、それがそのまま 1着-2着-3着 になっている。
 *   「3連単」の出現位置以降 約1200字を取り出し、タグを除去してから
 *   Regex("(\d)\s*[-‐－]\s*(\d)\s*[-‐－]\s*(\d)") で最初にマッチする3数字を着順とする。
 *   例: 三国1R は "3連単 1 - 4 - 2" → 1着=1, 2着=4, 3着=2。
 */
object ResultRepository {

    // 公式サイトの結果ページURL（rno=レース番号 / jcd=場コード2桁 / hd=日付8桁）
    private const val RESULT_URL =
        "https://www.boatrace.jp/owpc/pc/race/raceresult?rno=%d&jcd=%s&hd=%s"

    // ブラウザを装う User-Agent（付けないと弾かれることがある）
    private const val USER_AGENT = "Mozilla/5.0"

    // HTTP接続のタイムアウト（ミリ秒）
    //  OddsRepository と同じ理由。公式サイトは絞られると9〜10秒かかるため10秒では足りない
    //  （こちらは結果ページなので取り逃しても後から取れるが、無駄な失敗を減らす）
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000

    // 結果未掲載（レース未終了 等）のときにHTML内に出る文言
    private const val NO_DATA_MARK = "データはありません"

    // 「3連単」ラベル（この直後に 1着-2着-3着 の組番が来る）
    private const val TRIFECTA_LABEL = "3連単"

    // ラベル以降に切り出す文字数（この範囲に勝ち組番が入る）
    // 生HTMLは「3連単」と着順数字の間に多数の空セル(タグ)が挟まり、タグ除去後に大量の空白が入る。
    // 400字だと3番目の着順に届く前に窓が切れて regex 不一致→全レース未確定(null)になっていた。
    // 実データ検証で 1200字あれば全レース正しくパースできると確認済み（余裕を見て 1200）。
    private const val SLICE_LEN = 1200

    // 着順（1着-2着-3着）を拾う正規表現。区切りは半角ハイフン・全角ハイフン・全角マイナスに対応。
    private val ORDER_REGEX = Regex("(\\d)\\s*[-‐－]\\s*(\\d)\\s*[-‐－]\\s*(\\d)")

    // HTMLタグを除去するための正規表現
    private val TAG_REGEX = Regex("<[^>]*>")

    // 3連単の払戻金額（円）を拾う正規表現。金額はカンマ区切りの数字（例 "1,230"）。
    // is-payout クラス付近に「\1,230」のように出る。難しければ着順だけでよいので任意扱い。
    private val PAYOUT_REGEX = Regex("is-payout[^>]*>\\s*[¥\\\\]?\\s*([0-9,]+)")

    // 「単勝」ラベル（払戻金テーブル内。この直後に単勝の払戻金額が来る）
    // PC側 settle_odds.py で実証済み: 単勝の払戻は「単勝」セルの後の is-payout1 に出る
    private const val WIN_LABEL = "単勝"

    // 単勝払戻の切り出し幅（ラベル直後の近傍だけ見る。複勝以降を誤読しないよう狭めに）
    private const val WIN_SLICE_LEN = 600

    // 単勝の払戻金額（円/100円）を拾う正規表現（&yen; または ¥ 付き数字）
    private val WIN_PAYOUT_REGEX = Regex("is-payout1[^>]*>\\s*(?:&yen;|[¥\\\\])?\\s*([0-9,]+)")

    /**
     * レース結果。
     *   first/second/third … 1着/2着/3着 の艇番（1〜6）
     *   payout3t … 3連単の払戻金額（円）。取れなければ null
     *   payoutWin … 単勝の払戻金額（円/100円あたり）。取れなければ null（収支計算に使う）
     */
    data class RaceResult(
        val first: Int,
        val second: Int,
        val third: Int,
        val payout3t: Int?,
        val payoutWin: Int? = null
    )

    /**
     * 指定レースの結果を取得する。
     *
     * @param stadium 場コード2桁（フィードの stadium。例 "10"）
     * @param raceNo  レース番号（1〜12）
     * @param dateYmd 日付8桁（例 "20260708"）
     * @return 確定できたら RaceResult。未確定（未終了・未掲載・取得失敗）は null。
     */
    suspend fun fetchResult(
        stadium: String,
        raceNo: Int,
        dateYmd: String
    ): RaceResult? = withContext(Dispatchers.IO) {
        val html = httpGet(RESULT_URL.format(raceNo, stadium, dateYmd))
            ?: return@withContext null // 通信失敗 → 未確定扱い

        parseResult(html)
    }

    /**
     * 結果ページHTMLから着順（と払戻）を解析する。
     * 「データはありません」や 3連単組番が取れない場合は null（未確定）。
     */
    private fun parseResult(html: String): RaceResult? {
        // 未掲載（レース未終了 等）
        if (html.contains(NO_DATA_MARK)) return null

        // 「3連単」ラベルの位置を探す
        val labelIdx = html.indexOf(TRIFECTA_LABEL)
        if (labelIdx < 0) return null // ラベルが無ければ未確定

        // ラベル以降 約1200字を切り出す（末尾はみ出しはクランプ）
        val end = minOf(labelIdx + SLICE_LEN, html.length)
        val slice = html.substring(labelIdx, end)

        // タグを除去して、最初にマッチする 1着-2着-3着 を拾う
        val stripped = TAG_REGEX.replace(slice, " ")
        val m = ORDER_REGEX.find(stripped) ?: return null // 組番が取れない → 未確定

        val first = m.groupValues[1].toInt()
        val second = m.groupValues[2].toInt()
        val third = m.groupValues[3].toInt()

        // 3連単の払戻金額（取れれば添える。取れなくても着順だけで返す）
        val payout = PAYOUT_REGEX.find(slice)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toIntOrNull()

        // 単勝の払戻金額（払戻金テーブルの「単勝」セル直後の is-payout1 から拾う）
        // 収支計算（実戦・本命ベタ参考）に使う。取れなくても他は返す。
        // ページ前半の別の「単勝」（リンク等）を掴まないよう、まず「単勝</td>」
        // （払戻金テーブルのセル。settle_odds.py 実証済み）を優先して探す
        val winIdx = html.indexOf("$WIN_LABEL</td>").takeIf { it >= 0 }
            ?: html.indexOf(WIN_LABEL)
        val payoutWin = if (winIdx >= 0) {
            val winEnd = minOf(winIdx + WIN_SLICE_LEN, html.length)
            WIN_PAYOUT_REGEX.find(html.substring(winIdx, winEnd))
                ?.groupValues?.get(1)
                ?.replace(",", "")
                ?.toIntOrNull()
        } else null

        return RaceResult(
            first = first, second = second, third = third,
            payout3t = payout, payoutWin = payoutWin
        )
    }

    /** User-Agent 付きで HTTP GET し本文を返す。失敗時は null（例外を投げない） */
    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // ブラウザを装う（付けないと弾かれることがある）
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            // 圏外・タイムアウト等は黙って null（呼び出し側で未確定扱い）
            null
        } finally {
            conn?.disconnect()
        }
    }
}
