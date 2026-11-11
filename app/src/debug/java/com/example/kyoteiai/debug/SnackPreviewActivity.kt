package com.example.kyoteiai.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kyoteiai.data.FeedParser
import com.example.kyoteiai.data.EvPolicy
import com.example.kyoteiai.ui.BetMessages
import com.example.kyoteiai.ui.RaceDetailScreen
import com.example.kyoteiai.ui.kit.AppSnackbarHost
import com.example.kyoteiai.ui.theme.KyoteiAITheme
import com.example.kyoteiai.ui.theme.ThemePrefs

/**
 * 2026-11-12 レース詳細の画面下の知らせ（v2.13 で Toast から置き換え）を、前の形と今の形で見比べる画面（デバッグ版にだけ入る）。
 *
 * 本物はオッズを取って「◎を買った」を押したときや、ブラウザが無い端末でしか出ないので、通信せずに撮れるようにした。
 * 後ろにはアプリに入っている見本（feed_sample.json）の最初のレースの詳細を描く（オッズは取らない＝購入記録のボタンは出ない）。
 * prefs・購入記録・通知・締切アラームには触れない。配色は利用者が選んでいる配色を読む。
 *
 * 開き方（mode = light / dark、style = new / old、msg = browser / recorded / cancelled）:
 *   adb shell am start --activity-clear-task -n com.example.kyoteiai/.debug.SnackPreviewActivity --es mode light --es style new --es msg recorded
 * style = old は v2.12 までの Toast を本物のまま出す（約3.5秒で消えるので、開いてすぐ撮る）。
 * 注意：撮影を am start -S や am force-stop で終えない（停止状態になり予約が消える）。終わったら本物の画面を1回開く
 */
class SnackPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeMode = if (intent.getStringExtra("mode") == "light") ThemePrefs.MODE_LIGHT else ThemePrefs.MODE_DARK
        val oldStyle = intent.getStringExtra("style") == "old"
        val palette = ThemePrefs.getPalette(this)
        // 前の形は v2.12 までの Toast の文、今の形は BetMessages の文
        val message = when (intent.getStringExtra("msg")) {
            "browser" -> if (oldStyle) "ブラウザが見つかりませんでした" else BetMessages.BROWSER_MISSING
            "cancelled" -> if (oldStyle) "購入記録を取り消しました" else BetMessages.CANCELLED
            else -> if (oldStyle) "◎1号艇 ¥${EvPolicy.DEFAULT_BET_AMOUNT} を記録しました"
            else BetMessages.recorded(1, EvPolicy.DEFAULT_BET_AMOUNT)
        }
        // 後ろに描くレース（見本の最初のレース）
        val race = assets.open("feed_sample.json").bufferedReader().use { it.readText() }
            .let { FeedParser.parse(it) }?.races?.firstOrNull()
        // 前の形は本物の Toast を出す（Compose の帯は出さない）
        if (oldStyle) Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        setContent {
            KyoteiAITheme(themeMode = themeMode, paletteId = palette) {
                val host = remember { SnackbarHostState() }
                // 撮影のあいだ消えないように出しっぱなしにする
                LaunchedEffect(Unit) {
                    if (!oldStyle) host.showSnackbar(message, duration = SnackbarDuration.Indefinite)
                }
                // 本物（MainActivity）と同じ置き方：Scaffold は余白を足さず、帯の外側にだけナビゲーションバー分の余白
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = { Box(Modifier.navigationBarsPadding()) { AppSnackbarHost(host) } },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { _ ->
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        if (race != null) {
                            RaceDetailScreen(race = race, feedDate = null, fetchOnOpen = false, onBack = { finish() })
                        } else {
                            Text("見本のレースを読めませんでした", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
