package com.example.kyoteiai.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.kyoteiai.data.FeedParser
import com.example.kyoteiai.ui.FeedUiState
import com.example.kyoteiai.ui.TodayScreen
import com.example.kyoteiai.ui.kit.LocalKitTrialOff
import com.example.kyoteiai.ui.theme.KyoteiAITheme
import com.example.kyoteiai.ui.theme.ThemePrefs
import java.time.LocalDate

/**
 * 2026-11-11 状態の出し方（取得の失敗・読み込み中）を、見本のデータで描くだけの画面（デバッグ版にだけ入る）。
 *
 * 通信の失敗や「今日の予想が無い」は本物では作りにくいので、今日タブ（TodayScreen）に状態を渡して描く。
 * 予想の取得（FeedRepository.load）は呼ばない。呼ぶと本物の締切アラームを登録してしまうため。
 * 知らせの文は本物と同じ判定（feedNotice）が作る。
 *
 * 開き方（mode = light / dark、case = ok / sample / stale / failed / next / none / loading / retry）:
 *   adb shell am start --activity-clear-task -n com.example.kyoteiai/.debug.DesignPreviewActivity --es mode light --es case sample
 * 2026-11-11 共通部品の試し版（UiKitTrial.kt）と今の形を見比べるときは --ez old true で今の形（原本の部品）にする
 * 注意：撮影を am start -S や am force-stop で終えない（停止状態になり予約が消える）。終わったら本物の画面を1回開く
 */
class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeMode = if (intent.getStringExtra("mode") == "light") ThemePrefs.MODE_LIGHT else ThemePrefs.MODE_DARK
        val case = intent.getStringExtra("case") ?: "sample"
        // true なら試し版を使わず原本の部品で描く（見比べ用）
        val old = intent.getBooleanExtra("old", false)
        // アプリに入っている見本（2026-07-08）を読む。日付だけ差し替えて「今日」「昨日」を作る
        val sample = assets.open("feed_sample.json").bufferedReader().use { it.readText() }.let { FeedParser.parse(it) }
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        val initial = when (case) {
            "ok" -> FeedUiState(loading = false, feed = sample?.copy(date = today), fromRemote = true)
            "stale" -> FeedUiState(loading = false, feed = sample?.copy(date = yesterday), fromRemote = true)
            // 昨日の予想を表示中に取り直しが通信の失敗で終わった
            "failed" -> FeedUiState(loading = false, feed = sample?.copy(date = yesterday), fromRemote = true, lastAttemptFailed = true)
            // 明日の予想を受け取った（feed_today の取得だけが失敗）
            "next" -> FeedUiState(loading = false, feed = sample?.copy(date = LocalDate.now().plusDays(1).toString()), fromRemote = true)
            "none" -> FeedUiState(loading = false, feed = null, fromRemote = false)
            "loading" -> FeedUiState(loading = true, feed = null, fromRemote = false)
            "retry" -> FeedUiState(loading = true, feed = sample, fromRemote = false)
            else -> FeedUiState(loading = false, feed = sample, fromRemote = false) // sample：通信できず見本を表示
        }
        setContent {
            KyoteiAITheme(themeMode = themeMode, paletteId = ThemePrefs.DEFAULT_PALETTE) {
                Surface(modifier = Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
                    // 「再試行」を押すと取り直し中の見え方に替わる（押した結果が見えるように。取得はしない）
                    var state by remember { mutableStateOf(initial) }
                    CompositionLocalProvider(LocalKitTrialOff provides old) {
                        TodayScreen(
                            state = state,
                            hotOnly = false,
                            onHotOnlyChange = {},
                            onRaceClick = {},
                            onRetry = { state = state.copy(loading = true) },
                        )
                    }
                }
            }
        }
    }
}
