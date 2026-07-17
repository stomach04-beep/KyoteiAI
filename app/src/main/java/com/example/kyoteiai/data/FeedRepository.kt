package com.example.kyoteiai.data

import android.content.Context
import android.util.Log
import com.example.kyoteiai.DeadlineAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 予想フィードの取得を担うリポジトリ。
 *
 * 取得の優先順位：
 *   (1) feed_today.json（PCがその朝に作る当日分）… 日付が今日ならこれ
 *   (2) feed_next.json（PCが前の日に先回りして作る翌日分）… 日付が今日ならこれ
 *       ＝PCを今朝つけなかった日でも、前日に仕込んだ予想でスマホが動ける
 *   (3) どちらも今日でなければ取れた方をそのまま返す（古さの判断は呼び出し側）
 *   (4) 通信そのものが駄目なら assets/feed_sample.json にフォールバック
 *
 * assets に必ずサンプルがあるので、オフラインでも最低限アプリは動く。
 * ネットワーク・ファイルI/Oは coroutine(Dispatchers.IO) で行う。
 */
object FeedRepository {

    // 予想フィードの配信元（PCのpublish_feed.pyが更新するシークレットGist）。
    //  URLを知っている人だけが読める非公開Gist。認証不要でHTTP GET可能。
    //  ※GitHubのCDNは数分キャッシュするが、日次フィードなので問題なし。
    private const val GIST_RAW_BASE =
        "https://gist.githubusercontent.com/stomach04-beep/6d4fac6594e13e69ffdee79e49122707/raw/"

    //  feed_today.json … PCがその日の朝に作る当日分
    //  feed_next.json  … PCが前の日に先回りして作っておく翌日分
    //    予想を作れるのはモデルと履歴を持つPCだけなので、PCを朝つけない日は
    //    feed_today.json が昨日のまま古くなる。その日は前日に仕込んだ feed_next を使う。
    //    （番組表は翌日分までしか公開されないので、稼げる余裕は1日だけ）
    private const val REMOTE_FEED_URL = GIST_RAW_BASE + "feed_today.json"
    private const val REMOTE_FEED_NEXT_URL = GIST_RAW_BASE + "feed_next.json"

    // assets 内のフォールバック用サンプルファイル名
    private const val ASSET_FALLBACK = "feed_sample.json"

    // どのフィードを採用したかを残すログタグ（adb logcat -d | grep KyoteiFeed）。
    // 「予想が今日の日付でないと1レースも収集しない」という急所なので、
    // どの経路で今日の予想を得たか（当日分か・前日仕込みの予備か）が読めるようにする
    private const val TAG = "KyoteiFeed"

    // HTTP接続のタイムアウト（ミリ秒）
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    /**
     * フィード取得結果。
     * どちらの経路から取れたか（fromRemote）も返して、UIで「最新／オフライン」を出し分けられるようにする。
     */
    data class Result(
        val feed: Feed?,       // 取得できた予想（両経路とも失敗なら null）
        val fromRemote: Boolean // true=リモート取得成功 / false=assetsフォールバック
    )

    /**
     * フィードを取得する。まずリモート、失敗時はassetsサンプル。
     * どちらの経路もパースできなければ feed=null を返す。
     */
    suspend fun load(context: Context): Result = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()

        // (1) 当日フィード。今日の日付ならこれで確定
        val todayFeed = fetchRemote(REMOTE_FEED_URL)?.let { FeedParser.parse(it) }
        if (todayFeed != null && todayFeed.date == today) {
            Log.i(TAG, "feed_today.json を採用（$today・${todayFeed.races.size}R）")
            // 当日フィードが取れたこの機会に、各レースの締切前exactアラームを一括登録する
            // （Dozeで15分周期Workerが遅延しても通知窓を逃さないための本命経路。
            //   内容が前回と同じなら署名ガードにより何もしないので毎回呼んで安全）
            DeadlineAlarmScheduler.scheduleFromFeed(context, todayFeed)
            return@withContext Result(todayFeed, fromRemote = true)
        }

        // (2) 当日フィードが古い＝PCが今朝動いていない。
        //     前の日にPCが先回りして作っておいた翌日分を見る（それが今日なら使える）
        val nextFeed = fetchRemote(REMOTE_FEED_NEXT_URL)?.let { FeedParser.parse(it) }
        if (nextFeed != null && nextFeed.date == today) {
            Log.i(TAG, "feed_today が古い(${todayFeed?.date})ため feed_next.json を採用" +
                "（$today・${nextFeed.races.size}R）＝PCが今朝動いていない日の予備経路")
            // 予備経路（前日仕込みの翌日分）でも今日の予想が取れたのでアラームを登録する
            DeadlineAlarmScheduler.scheduleFromFeed(context, nextFeed)
            return@withContext Result(nextFeed, fromRemote = true)
        }

        // (3) どちらも今日ではない（PCが2日以上動いていない等）。
        //     取れた方を返す。「今日の予想でなければ動かない」判断は呼び出し側が
        //     feed.date で行うので、ここでは握りつぶさずそのまま渡す
        if (todayFeed != null) {
            Log.w(TAG, "今日(${today})の予想がどこにも無い（today=${todayFeed.date} / " +
                "next=${nextFeed?.date}）＝PCが2日以上動いていない。収集は行われない")
            return@withContext Result(todayFeed, fromRemote = true)
        }
        if (nextFeed != null) return@withContext Result(nextFeed, fromRemote = true)

        // (4) 通信そのものが駄目 → assets のサンプルにフォールバック
        Log.w(TAG, "フィードを取得できずassetsのサンプルにフォールバック")
        val assetText = readAsset(context)
        val fallbackFeed = assetText?.let { FeedParser.parse(it) }
        Result(fallbackFeed, fromRemote = false)
    }

    /** リモートURLから本文を取得する。失敗時は null（例外を投げない） */
    private fun fetchRemote(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            // 圏外・DNS失敗・タイムアウト等は黙ってフォールバックへ
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** assets からフォールバック用サンプルを読む。失敗時は null */
    private fun readAsset(context: Context): String? {
        return try {
            context.assets.open(ASSET_FALLBACK).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}
