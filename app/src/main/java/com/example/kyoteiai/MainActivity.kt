package com.example.kyoteiai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kyoteiai.data.RacePred
import com.example.kyoteiai.ui.CalendarScreen
import com.example.kyoteiai.ui.FeedViewModel
import com.example.kyoteiai.ui.RaceDetailScreen
import com.example.kyoteiai.ui.ResultScreen
import com.example.kyoteiai.ui.ResultViewModel
import com.example.kyoteiai.ui.SettingsScreen
import com.example.kyoteiai.ui.TodayScreen
import com.example.kyoteiai.ui.kit.GearButton
import com.example.kyoteiai.ui.kit.HeaderAppIcon
import com.example.kyoteiai.ui.kit.HeaderBackdrop
import androidx.compose.ui.res.painterResource
import com.example.kyoteiai.ui.kit.ScreenHeader
import com.example.kyoteiai.ui.kit.ScreenTabs
import com.example.kyoteiai.ui.theme.KyoteiAITheme
import com.example.kyoteiai.ui.theme.ThemePrefs
import kotlinx.coroutines.launch

// ───────────────────────────────────────────────────────────────
// 共通UI部品：セクション見出し（SettingsScreen などから参照される）
// BargainChecker 統一スタイル：primary色・titleSmall・Bold
// ───────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * メインActivity。
 * テーマ（明暗・配色）を購読し、見出し下の横タブ3枚＋設定（歯車）＋レース詳細の画面遷移をホストする。
 */
class MainActivity : ComponentActivity() {

    companion object {
        // 通知タップで特定レースの詳細を開くためのIntent extras
        const val EXTRA_OPEN_STADIUM = "open_stadium"   // 場コード（例 "11"）
        const val EXTRA_OPEN_RACE_NO = "open_race_no"   // レース番号（1〜12）
    }

    // 通知タップで開くレースの指定（"場コード|R番号"）。Composeが購読して詳細へ遷移する
    private val pendingRaceKey = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Intentから「開くべきレース」を取り出す（通知タップ以外で開いた場合は null） */
    private fun extractRaceKey(intent: android.content.Intent?): String? {
        val stadium = intent?.getStringExtra(EXTRA_OPEN_STADIUM) ?: return null
        val raceNo = intent.getIntExtra(EXTRA_OPEN_RACE_NO, -1)
        return if (raceNo in 1..12) "$stadium|$raceNo" else null
    }

    // アプリが既に起動中に通知をタップした場合（launchMode=singleTop）もレース指定を受け取る
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        extractRaceKey(intent)?.let { pendingRaceKey.value = it }
    }

    // 通知パーミッション要求（Android 13以上で必要）
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 許可の結果に関わらずアプリ機能は継続。通知は許可時のみ表示される */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通知タップから起動された場合のレース指定を受け取る
        extractRaceKey(intent)?.let { pendingRaceKey.value = it }

        // Android 13以上で通知パーミッションをリクエスト
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val ctx = LocalContext.current
            val themePrefs = remember { ThemePrefs.prefs(ctx) }
            var themeMode by remember { mutableStateOf(ThemePrefs.get(ctx)) }
            var paletteId by remember { mutableStateOf(ThemePrefs.getPalette(ctx)) }
            // テーマ設定の変更を購読して即反映
            DisposableEffect(Unit) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        ThemePrefs.KEY -> themeMode = ThemePrefs.get(ctx)
                        ThemePrefs.KEY_PALETTE -> paletteId = ThemePrefs.getPalette(ctx)
                    }
                }
                themePrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { themePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            KyoteiAITheme(themeMode = themeMode, paletteId = paletteId) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(
                        pendingRaceKey = pendingRaceKey.value,
                        onPendingConsumed = { pendingRaceKey.value = null }
                    )
                }
            }
        }
    }
}

// 主画面の横タブの名前（2026-10-25 APP_RULES.md A2・A5。下タブ 今日／結果／カレンダー／設定 から、設定を歯車へ移した）。
// 並び順＝ページ番号（0=今日・1=結果・2=カレンダー）。タブ名の一覧はここ1か所だけ
private val MAIN_TABS = listOf("今日", "結果", "カレンダー")

// 結果タブのページ番号（開いたときに結果を取得する判定に使う）
private const val PAGE_RESULTS = 1

/**
 * 画面全体のルート。
 * 見出し（アプリ名＋歯車）→横タブ3枚（今日・結果・カレンダー）→横スワイプで切り替わるページ、
 * ＋歯車から開く設定画面と「今日」からのレース詳細遷移を管理する。
 * 詳細を開いている間は selectedRace を持ち、戻るで一覧へ復帰する。
 */
@Composable
fun AppRoot(
    pendingRaceKey: String? = null,        // 通知タップで開くレース（"場コード|R番号"）
    onPendingConsumed: () -> Unit = {}     // 遷移処理後に指定をクリアするコールバック
) {
    // フィードの ViewModel（StateFlow で状態管理）
    val vm: FeedViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    // 結果（着順・的中判定）の ViewModel
    val resultVm: ResultViewModel = viewModel()
    val resultState by resultVm.uiState.collectAsState()

    // ページの位置はここで覚える（設定やレース詳細から戻っても直前のタブのまま。2026-10-25 APP_RULES.md A5）
    val pager = rememberPagerState(pageCount = { MAIN_TABS.size })
    val scope = rememberCoroutineScope()
    // 歯車から開く設定画面を表示中か
    var showSettings by remember { mutableStateOf(false) }
    // 「狙い目のみ」トグルの状態（今日タブ用）
    var hotOnly by remember { mutableStateOf(false) }
    // レース詳細を開いている場合の対象レース（null=一覧）
    var selectedRace by remember { mutableStateOf<RacePred?>(null) }
    // 通知経由で詳細を開いたときはオッズを自動取得する（一覧タップ時は従来どおり手動）
    var autoFetchOdds by remember { mutableStateOf(false) }

    // ── 通知タップからのレース直行 ─────────────────────────────
    //  フィードがまだ読めていなければ、読めた時点でこの LaunchedEffect が
    //  再発火して遷移する（キーに feed の日付を含めているため）
    LaunchedEffect(pendingRaceKey, state.feed?.date) {
        val key = pendingRaceKey ?: return@LaunchedEffect
        val feed = state.feed ?: return@LaunchedEffect  // フィード到着待ち（消費しない）
        val parts = key.split("|")
        val race = feed.races.firstOrNull {
            it.stadium == parts.getOrNull(0) && it.raceNo == parts.getOrNull(1)?.toIntOrNull()
        }
        if (race != null) {
            autoFetchOdds = true
            selectedRace = race
        }
        onPendingConsumed()  // 見つからなくても消費（古い通知など）
    }

    // レース詳細を開いているときは詳細画面を最前面に（BackHandler で一覧へ戻る）
    val race = selectedRace
    if (race != null) {
        // edge-to-edge 対策：ステータスバー分の上部余白を確保し、
        // ヘッダが端末上部の時計・通知アイコンと重ならないようにする
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            RaceDetailScreen(
                race = race,
                feedDate = state.feed?.date,
                fetchOnOpen = autoFetchOdds,
                onBack = {
                    selectedRace = null
                    autoFetchOdds = false  // 通知経由フラグは一度使ったら戻す
                }
            )
        }
        return
    }

    // 「結果」タブを開いたら、締切超過レースの結果を取得する
    //  （画面を開いた時のみ・取得済みはキャッシュ利用。feed が来てから走らせる）
    //  キーは feed オブジェクトそのものではなく「日付文字列」にする。
    //  feed が同じ内容で再emされるたびにキーが変わって LaunchedEffect が再発火し、
    //  loadResults が数秒おきに再起動→DNSを叩き続ける無限ロードになっていたため。
    //  日付は同一日なら安定なので、タブ切替や初回表示でだけ1回走る。
    //  2026-10-25：タブの判定を「止まったページ」（settledPage）に置き換えた（取得の中身は従来どおり）。
    //  currentPage だと「今日→カレンダー」の送りの途中で結果ページを通過しただけでも走るため
    LaunchedEffect(pager.settledPage, state.feed?.date) {
        if (pager.settledPage == PAGE_RESULTS && state.feed != null) {
            resultVm.loadResults(state.feed)
        }
    }

    // 歯車から開く設定画面（1段深い画面。戻る矢印・端末の戻るで直前のタブへ。2026-10-25 APP_RULES.md A5）
    //  端末の戻るは SettingsScreen の中の BackHandler が受けて onBack を呼ぶ
    if (showSettings) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            SettingsScreen(onBack = { showSettings = false })
        }
        return
    }

    // 最初のタブ以外で端末の戻るを押したら最初のタブ（今日）へ。今日タブでは従来どおりアプリを閉じる
    BackHandler(enabled = pager.currentPage != 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    // edge-to-edge 対策：ルート全体に上下のシステムバー分の余白を入れ、
    // 見出しが端末上部のステータスバー（時計・通知アイコン）と、
    // 一番下の項目がナビゲーションバーと重ならないようにする。
    // 以前は下部ナビ（NavigationBar）が下の余白を持っていたが、下タブをやめたので systemBarsPadding() にした。
    // 機種ごとのバー高さに自動追従するため固定dpは使わない。
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // 見出し：アプリ名＋歯車（2026-10-25 APP_RULES.md A1・A2・A5。線は下の横タブの列が引く）
        // 見出しとタブの後ろに Gemini で描いた絵を敷く（2026-11-01 案3-3）。
        // ボートの絵が上寄りなので、少し下へずらす（2026-11-01 ユーザー指示）
        HeaderBackdrop(art = painterResource(R.drawable.header_backdrop), artOffsetY = 8.dp) {
            ScreenHeader(
                title = stringResource(R.string.app_name),
                divider = false,
                // 名前の左にアプリアイコン。絵の上に置くので見出し自身の背景は塗らない
                leading = { HeaderAppIcon(painterResource(R.mipmap.ic_launcher)) },
                transparent = true,
            ) {
                GearButton(onClick = { showSettings = true })
            }
            // 見出しのすぐ下の横タブ（押しても横スワイプでも移れる）
            ScreenTabs(MAIN_TABS, pager.currentPage, transparent = true) { i ->
                scope.launch { pager.animateScrollToPage(i) }
            }
        }
        // ── コンテンツ領域（残り全部） ─────────────────────────
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> TodayScreen(
                    state = state,
                    hotOnly = hotOnly,
                    onHotOnlyChange = { hotOnly = it },
                    // 一覧タップは従来どおり手動オッズ取得（自動取得は通知経由のみ）
                    onRaceClick = { autoFetchOdds = false; selectedRace = it }
                )
                PAGE_RESULTS -> ResultScreen(
                    state = resultState,
                    feedLoaded = state.feed != null,
                    // 更新ボタンは force=true：取得中でも最新条件で走り直す
                    onRefresh = { resultVm.loadResults(state.feed, force = true) }
                )
                else -> CalendarScreen(state = state)
            }
        }
    }
}
