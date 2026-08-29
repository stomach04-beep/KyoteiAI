package com.example.kyoteiai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 収支まわりのローカル保存（JSONファイル）。
 *
 * 2種類の記録を持つ:
 *   1) BetRecord  … ユーザーが「買った」を押した実戦の購入記録（bets.json）
 *   2) PickRecord … EvPickWorkerが観測した「C'成立レース」の記録（picks.json）
 *                    → 買わなくても記録され、「C'仮想収支」の材料になる
 *
 * 保存は必ず「一時ファイルに書いてからrename」の原子保存
 * （書き込み途中でプロセスが死んでもファイルが壊れない。feedback_json_atomic_save）。
 */

/** 実戦の購入記録1件 */
data class BetRecord(
    val date: String,         // "2026-07-12"
    val stadium: String,      // 場コード "10"
    val stadiumName: String,  // 場名 "三国"
    val raceNo: Int,          // レース番号
    val lane: Int,            // 買った艇番
    val amount: Int,          // 購入金額（円）
    val odds: Double,         // 購入時の単勝オッズ
    val ev: Double,           // 購入時のEV
    val boughtAt: String,     // 記録時刻 "HH:mm"
    val settled: Boolean = false,  // 結果突合済みか
    val won: Boolean? = null,      // 的中したか（未確定はnull）
    val refund: Int = 0            // 実際の払戻額（円）。外れは0
)

/** Workerが観測したC'成立レースの記録1件（¥100仮想購入として集計する） */
data class PickRecord(
    val date: String,
    val stadium: String,
    val stadiumName: String,
    val raceNo: Int,
    val lane: Int,
    val prob: Double,         // 観測時のモデル確率
    val odds: Double,         // 観測時の単勝オッズ
    val ev: Double,           // 観測時のEV
    val observedAt: String,   // 観測時刻 "HH:mm"
    val settled: Boolean = false,
    val won: Boolean? = null,
    val refund: Int = 0,      // ¥100買ったと仮定した払戻額（円）
    // ── ここから下は v2.0（2026-08-29）で追加。過去の記録は持たない（null）──
    //  S1昇格判定の標本かどうかはPC側と揃えて
    //  「PROTOCOL_START以降・締切5分前以内・C'≦5倍・昼」で決まるが、
    //  そのうち mins（締切まで何分か）と deadline（締切時刻）はこれまで
    //  記録していなかったため、過去分は判定できない＝「対象外・データ不足」になる。
    //  ここから前向きに記録し、以後の記録だけで判定対象ラインを計算する。
    val mins: Int? = null,        // 観測時点で締切まで何分だったか（＝この記録の鮮度）
    val deadline: String? = null  // 締切時刻 "HH:mm"（昼/ナイターの判定に使う）
)

/** 実戦購入記録の保存・読込（bets.json） */
object BetRepository {
    private const val FILE_NAME = "bets.json"
    private val lock = Any()

    /** 全件読込（壊れていれば空リスト） */
    fun load(context: Context): List<BetRecord> = synchronized(lock) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                BetRecord(
                    date = o.optString("date"),
                    stadium = o.optString("stadium"),
                    stadiumName = o.optString("stadiumName"),
                    raceNo = o.optInt("raceNo"),
                    lane = o.optInt("lane"),
                    amount = o.optInt("amount"),
                    odds = o.optDouble("odds", 0.0),
                    ev = o.optDouble("ev", 0.0),
                    boughtAt = o.optString("boughtAt"),
                    settled = o.optBoolean("settled", false),
                    won = if (o.has("won") && !o.isNull("won")) o.optBoolean("won") else null,
                    refund = o.optInt("refund", 0)
                )
            }
        } catch (e: Exception) {
            emptyList() // JSONが壊れていても落とさない
        }
    }

    /** 1件追加 */
    fun add(context: Context, record: BetRecord) = synchronized(lock) {
        saveAll(context, loadUnlocked(context) + record)
    }

    /** 指定レースの記録を削除（「取消」用） */
    fun remove(context: Context, date: String, stadium: String, raceNo: Int) = synchronized(lock) {
        saveAll(context, loadUnlocked(context).filterNot {
            it.date == date && it.stadium == stadium && it.raceNo == raceNo
        })
    }

    /** 指定レースの購入記録があるか */
    fun exists(context: Context, date: String, stadium: String, raceNo: Int): Boolean =
        load(context).any { it.date == date && it.stadium == stadium && it.raceNo == raceNo }

    /** 全件置き換え保存（結果突合の更新用） */
    fun saveAll(context: Context, records: List<BetRecord>) = synchronized(lock) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("date", r.date); put("stadium", r.stadium)
                put("stadiumName", r.stadiumName); put("raceNo", r.raceNo)
                put("lane", r.lane); put("amount", r.amount)
                put("odds", r.odds); put("ev", r.ev); put("boughtAt", r.boughtAt)
                put("settled", r.settled)
                if (r.won != null) put("won", r.won)
                put("refund", r.refund)
            })
        }
        atomicWrite(File(context.filesDir, FILE_NAME), arr.toString())
    }

    // synchronized(lock) の中から呼ぶ内部用（再ロック回避のため分離）
    private fun loadUnlocked(context: Context): List<BetRecord> {
        // load() は同じlockを取るが、Kotlinのsynchronizedは再入可能なのでそのまま使う
        return load(context)
    }
}

/** Worker観測ログ（C'成立レース）の保存・読込（picks.json） */
object PickLogRepository {
    private const val FILE_NAME = "picks.json"
    private val lock = Any()

    fun load(context: Context): List<PickRecord> = synchronized(lock) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PickRecord(
                    date = o.optString("date"),
                    stadium = o.optString("stadium"),
                    stadiumName = o.optString("stadiumName"),
                    raceNo = o.optInt("raceNo"),
                    lane = o.optInt("lane"),
                    prob = o.optDouble("prob", 0.0),
                    odds = o.optDouble("odds", 0.0),
                    ev = o.optDouble("ev", 0.0),
                    observedAt = o.optString("observedAt"),
                    settled = o.optBoolean("settled", false),
                    won = if (o.has("won") && !o.isNull("won")) o.optBoolean("won") else null,
                    refund = o.optInt("refund", 0),
                    // v2.0 で増えた項目。古い記録にはキーが無いので null のまま読む
                    // （0 を既定値にすると「締切0分前に取った」と誤読され、
                    //   判定対象に混ざってしまうので必ず null と区別する）
                    mins = if (o.has("mins") && !o.isNull("mins")) o.optInt("mins") else null,
                    deadline = if (o.has("deadline") && !o.isNull("deadline"))
                        o.optString("deadline") else null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 1件追加（同一レースの重複は追加しない） */
    fun addIfAbsent(context: Context, record: PickRecord) = synchronized(lock) {
        val current = load(context)
        val dup = current.any {
            it.date == record.date && it.stadium == record.stadium && it.raceNo == record.raceNo
        }
        if (!dup) saveAll(context, current + record)
    }

    fun saveAll(context: Context, records: List<PickRecord>) = synchronized(lock) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("date", r.date); put("stadium", r.stadium)
                put("stadiumName", r.stadiumName); put("raceNo", r.raceNo)
                put("lane", r.lane); put("prob", r.prob)
                put("odds", r.odds); put("ev", r.ev); put("observedAt", r.observedAt)
                put("settled", r.settled)
                if (r.won != null) put("won", r.won)
                put("refund", r.refund)
                // v2.0 追加分。持っていない記録（過去分）はキーごと書かない＝
                // 読み戻したときも null のままになり「データ不足」と正しく判別できる
                if (r.mins != null) put("mins", r.mins)
                if (r.deadline != null) put("deadline", r.deadline)
            })
        }
        atomicWrite(File(context.filesDir, FILE_NAME), arr.toString())
    }
}

/** 送信した通知の記録1件（時系列ログ用） */
data class NotifRecord(
    val time: String,     // 送信時刻 "07/12 16:01"
    val title: String,    // 通知タイトル（例「びわこ11R ◎1号艇 確定 EV1.52」）
    val message: String   // 通知本文
)

/**
 * 通知ログの保存・読込（notif_log.json）。
 * アプリが送った通知（候補/確定/見送り/旧確信度）を新しい順に最大300件保持し、
 * 結果タブの「通知ログ」欄で時系列にそのまま見られるようにする。
 */
object NotifLogRepository {
    private const val FILE_NAME = "notif_log.json"
    private const val MAX_RECORDS = 300   // 古いものから捨てる（肥大化防止）
    private val lock = Any()

    /** 1件追記（先頭=最新）。呼び出しはバックグラウンドスレッドから行うこと */
    fun add(context: Context, time: String, title: String, message: String) = synchronized(lock) {
        val current = load(context)
        val updated = (listOf(NotifRecord(time, title, message)) + current).take(MAX_RECORDS)
        val arr = JSONArray()
        updated.forEach { r ->
            arr.put(JSONObject().apply {
                put("time", r.time); put("title", r.title); put("message", r.message)
            })
        }
        atomicWrite(File(context.filesDir, FILE_NAME), arr.toString())
    }

    /** 全件読込（新しい順）。壊れていれば空リスト */
    fun load(context: Context): List<NotifRecord> = synchronized(lock) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                NotifRecord(
                    time = o.optString("time"),
                    title = o.optString("title"),
                    message = o.optString("message")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * 原子的なファイル書き込み（一時ファイル→rename）。
 * 書き込み途中でプロセスが死んでも元ファイルは無傷で残る。
 *
 * internal にしてあるのは OddsLogStore.kt からも同じ実装を使うため。
 * 同じ意味の処理を2か所に書くと必ずズレるので、保存の作法はここ1本に集約する。
 */
internal fun atomicWrite(target: File, text: String) {
    val tmp = File(target.parentFile, target.name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(target)) {
        // renameが失敗する環境向けフォールバック（同一ディレクトリなら通常成功する）
        target.writeText(text)
        tmp.delete()
    }
}
