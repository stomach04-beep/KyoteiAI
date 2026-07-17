package com.example.kyoteiai.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kyoteiai.data.Feed
import com.example.kyoteiai.data.RacePred
import com.example.kyoteiai.data.ResultRepository
import com.example.kyoteiai.data.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 「結果」画面のUI状態を管理する ViewModel。
 *
 * 締切を過ぎたレースについてだけ公式から着順を取得し、
 * AI本命（topLane）が1着になったか（的中）を判定する。
 *
 * ネットワークが重いので次の方針で守る：
 *  - 対象は「締切超過レースのみ」（全120レースは叩かない）
 *  - 同時実行は数本に絞る（Semaphore で制限）
 *  - 取得済みのレースは再取得しない（メモリキャッシュ resultCache）
 *  - 取得失敗・未確定はクラッシュさせず「結果待ち」表示にする
 */
class ResultViewModel(app: Application) : AndroidViewModel(app) {

    // 同時に叩く公式サイトへのリクエスト上限（重すぎないよう数本に絞る）
    private val maxConcurrency = 4

    // 取得済み結果のメモリキャッシュ（キー = "stadium-raceNo"）。
    //  値が RaceResult なら確定、キーが無ければ未取得。
    //  「未確定（結果待ち）」はキャッシュしない＝次回更新で再挑戦する。
    private val resultCache = HashMap<String, ResultRepository.RaceResult>()

    // ── 確定結果のディスクキャッシュ（SharedPreferences）─────────────────
    //  競艇の確定着順は「その日のうちは不変」なので端末にも保存し、
    //  アプリ再起動後もネットを叩かずに過去確定分を即復元する。
    //  ディスクキー = "{yyyyMMdd}-{stadium}-{raceNo}"（日付でスコープを分ける）。
    //  値 = "first,second,third,payout"（payout が null なら末尾は空文字）。
    private val prefs: SharedPreferences by lazy {
        getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ディスクキャッシュをメモリへ読み込み済みの日付（yyyyMMdd）。
    //  日付が変わったら読み直す＆前日分のメモリを作り直すための番兵。
    private var loadedDiskDate: String? = null

    // 画面が参照する読み取り専用の状態
    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    // 実行中の取得ジョブ。二重起動（DNS連打の元）を防ぐために保持する。
    private var loadJob: Job? = null

    /**
     * 締切超過レースの結果をまとめて取得する。
     * 画面を開いたとき（force=false）・更新ボタン押下（force=true）で呼ぶ。
     *
     * 無限ロード対策：
     *  - 取得中に自動起動(force=false)が再発火しても二重に走らせない（DNSを数秒ごとに叩き続ける原因を断つ）。
     *  - 1回のロードは「締切超過レースを一巡取得したら必ず loading=false で完了」。
     *    未確定が残っても一巡で終え、無限リトライしない（次の手動更新で再挑戦）。
     *  - 1レースあたり取得は最大1回。失敗/未確定はその回は諦めて「結果待ち」表示にする。
     *
     * @param feed  本日のフィード（レース一覧と日付を持つ）
     * @param force true=更新ボタン。取得中でも前ジョブを畳んで最新で走り直す。
     *              false=タブを開いた時の自動起動。取得中なら何もしない。
     */
    fun loadResults(feed: Feed?, force: Boolean = false) {
        if (feed == null) {
            loadJob?.cancel()
            loadJob = null
            _uiState.value = ResultUiState(loading = false, rows = emptyList())
            return
        }

        // 取得中の二重起動ガード。
        //  自動起動が重なった場合は無視（LaunchedEffect の再発火で数秒おきに走るのを止める）。
        //  手動更新のときだけ前ジョブをキャンセルして最新条件で走り直す。
        if (loadJob?.isActive == true) {
            if (!force) return
            loadJob?.cancel()
        }

        loadJob = viewModelScope.launch {
            // フィード日付（"2026-07-08"）を8桁（"20260708"）へ。無ければ端末日付。
            val dateYmd = toDateYmd(feed.date)

            // ── ディスクキャッシュ（確定結果）をメモリへ復元 ──────────────
            //  当日分だけを SharedPreferences から resultCache に流し込み、
            //  同時に「当日以外の古いエントリ」を掃除する（肥大化防止）。
            //  日付が変わった時だけ実施（毎回 prefs 全走査しない・前日メモリの混入も防ぐ）。
            if (loadedDiskDate != dateYmd) {
                loadDiskCacheForDate(dateYmd)
                loadedDiskDate = dateYmd
            }

            // 締切を過ぎたレースだけを対象にする。
            //  締切が新しい順（＝最近終わったレース）から並べ、その順に先に取得・表示する。
            val now = System.currentTimeMillis()
            val targets = feed.races
                .filter { TimeUtil.isPast(it.deadline, now) }
                .sortedByDescending { it.deadline }

            // ── 逐次描画の土台をまず1回だけ描く ─────────────────────
            //  全対象レースを「結果待ち」行として即座に一覧表示する（スピナーで固まらせない）。
            //  すでにキャッシュ済みのレースはこの時点で確定表示される。
            //  以降は各レースの取得完了ごとに、この行を1件ずつ差し替えていく。
            val initialRows = targets.map { race ->
                val cached = synchronized(resultCache) { resultCache[cacheKey(race)] }
                ResultRow(
                    race = race,
                    result = cached,
                    hit = cached?.let { it.first == race.topLane }
                )
            }
            // キャッシュ済み＝この回はもう処理済み。未取得ぶんだけこれから取りに行く。
            val alreadyDone = initialRows.count { it.result != null }
            _uiState.value = ResultUiState(
                loading = true,
                rows = initialRows,
                decidedCount = alreadyDone,
                hitCount = initialRows.count { it.hit == true },
                totalCount = targets.size,
                processedCount = alreadyDone
            )

            // 同時実行数を制限しつつ、未取得レースだけ並列取得する
            val semaphore = Semaphore(maxConcurrency)
            withContext(Dispatchers.IO) {
                targets.mapIndexed { index, race ->
                    async {
                        val key = cacheKey(race)
                        // 取得済み（確定）は初期描画で反映済みなのでスキップ
                        if (synchronized(resultCache) { resultCache.containsKey(key) }) return@async

                        // 公式サイトから着順を取得（同時実行はセマフォで制限）
                        val result = semaphore.withPermit {
                            ResultRepository.fetchResult(
                                stadium = race.stadium,
                                raceNo = race.raceNo,
                                dateYmd = dateYmd
                            )
                        }
                        // 確定できたものだけキャッシュ（未確定=nullは入れない→次回再挑戦）
                        if (result != null) {
                            synchronized(resultCache) { resultCache[key] = result }
                            // ディスクにも保存（確定は当日不変。apply() で非同期書き込み）。
                            //  → アプリ再起動後もこのレースはネットを叩かず即復元される。
                            prefs.edit()
                                .putString(diskKey(dateYmd, key), serialize(result))
                                .apply()
                        }

                        // ── この1レースの取得が終わった時点で、その都度 StateFlow を部分反映 ──
                        //  update{} は内部で CAS ループ（compareAndSet）により原子的に更新するため、
                        //  複数の取得コルーチンが同時に完了して来ても安全にマージできる。
                        //  該当 index の行だけ差し替え、サマリ（的中/確定数）は毎回リストから再集計する。
                        _uiState.update { st ->
                            val newRows = st.rows.toMutableList()
                            newRows[index] = ResultRow(
                                race = race,
                                result = result,
                                // 本命(topLane)が1着なら的中。未確定(null)は未判定のまま。
                                hit = result?.let { it.first == race.topLane }
                            )
                            st.copy(
                                rows = newRows,
                                decidedCount = newRows.count { it.result != null },
                                hitCount = newRows.count { it.hit == true },
                                processedCount = st.processedCount + 1
                            )
                        }
                    }
                }.awaitAll()
            }

            // 一巡取得が完了：スピナー/進捗を消し、最終取得時刻を添えて完了状態にする。
            //  （途中の逐次更新でサマリ・行は既に反映済みなので、ここでは仕上げのみ）
            _uiState.update { st ->
                st.copy(
                    loading = false,
                    lastUpdated = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                )
            }
        }
    }

    /** キャッシュキー（場コード + レース番号）。メモリキャッシュ resultCache 用（日付を含まない）。 */
    private fun cacheKey(race: RacePred): String = "${race.stadium}-${race.raceNo}"

    /** ディスクキャッシュ（SharedPreferences）用の鍵。"{yyyyMMdd}-{stadium}-{raceNo}"。 */
    private fun diskKey(dateYmd: String, memKey: String): String = "$dateYmd-$memKey"

    /**
     * 指定日の確定結果を SharedPreferences から resultCache（メモリ）へ復元し、
     * 併せて「当日以外の古いエントリ」を削除する（肥大化防止）。
     *
     *  - キーが "{dateYmd}-" で始まるものだけを当日分として読み込む。
     *  - それ以外のキー（＝別日の古い結果）は remove して掃除する。
     *  - 日付が変わった呼び出しに備え、先にメモリキャッシュを作り直して前日分の混入を防ぐ。
     */
    private fun loadDiskCacheForDate(dateYmd: String) {
        val prefix = "$dateYmd-"
        val all = prefs.all               // 全エントリ（当日＋古い日付が混在しうる）
        val editor = prefs.edit()
        synchronized(resultCache) {
            resultCache.clear()           // 日付切替時に前日分が残らないよう作り直す
            for ((key, value) in all) {
                if (key.startsWith(prefix)) {
                    // 当日分：文字列をパースできたものだけメモリへ復元
                    val parsed = (value as? String)?.let { deserialize(it) } ?: continue
                    val memKey = key.removePrefix(prefix)  // 日付を外してメモリ鍵へ
                    resultCache[memKey] = parsed
                } else {
                    // 当日以外の古いエントリは削除
                    editor.remove(key)
                }
            }
        }
        editor.apply()
    }

    /** RaceResult をディスク保存用文字列へ。null の払戻は空文字（5要素目=単勝払戻）。 */
    private fun serialize(r: ResultRepository.RaceResult): String =
        "${r.first},${r.second},${r.third},${r.payout3t ?: ""},${r.payoutWin ?: ""}"

    /**
     * ディスク保存文字列 "first,second,third,payout3t,payoutWin" を RaceResult へ復元。
     * 旧形式（4要素以下）でも壊れず読める（payoutWin は null になるだけ）。
     */
    private fun deserialize(s: String): ResultRepository.RaceResult? {
        val parts = s.split(",")
        if (parts.size < 3) return null
        val first = parts[0].toIntOrNull() ?: return null
        val second = parts[1].toIntOrNull() ?: return null
        val third = parts[2].toIntOrNull() ?: return null
        // 4要素目（3連単払戻）・5要素目（単勝払戻）は任意。空文字・欠損なら null。
        val payout = parts.getOrNull(3)?.toIntOrNull()
        val payoutWin = parts.getOrNull(4)?.toIntOrNull()
        return ResultRepository.RaceResult(
            first = first,
            second = second,
            third = third,
            payout3t = payout,
            payoutWin = payoutWin
        )
    }

    /** フィード日付("yyyy-MM-dd")を8桁("yyyyMMdd")へ。取れなければ端末日付。 */
    private fun toDateYmd(feedDate: String?): String {
        val digits = feedDate?.filter { it.isDigit() } ?: ""
        return if (digits.length == 8) {
            digits
        } else {
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) // 例 "20260708"
        }
    }

    companion object {
        // 確定結果ディスクキャッシュ用の SharedPreferences 名
        private const val PREFS_NAME = "result_cache"
    }
}

/**
 * 結果画面の状態。
 *  loading=取得中 / rows=各レースの結果行 / decidedCount=結果確定レース数(Y) /
 *  hitCount=本命的中数(X) / lastUpdated=最終取得時刻(HH:mm) /
 *  totalCount=対象（締切超過）レース総数 / processedCount=取得処理が終わったレース数（進捗表示用）
 */
data class ResultUiState(
    val loading: Boolean = false,
    val rows: List<ResultRow> = emptyList(),
    val decidedCount: Int = 0,
    val hitCount: Int = 0,
    val lastUpdated: String? = null,
    val totalCount: Int = 0,
    val processedCount: Int = 0
)

/**
 * 結果画面の1レース分の行。
 *  race=対象レース（本命情報を含む） / result=着順（未確定なら null） /
 *  hit=的中判定（true=的中 / false=不的中 / null=結果待ち）
 */
data class ResultRow(
    val race: RacePred,
    val result: ResultRepository.RaceResult?,
    val hit: Boolean?
)
