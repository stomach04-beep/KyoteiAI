package com.example.kyoteiai.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kyoteiai.EvPickWorker
import com.example.kyoteiai.HotRaceWorker
import com.example.kyoteiai.SectionHeader
import com.example.kyoteiai.ui.theme.AppPalettes
import com.example.kyoteiai.ui.theme.ThemePrefs

/**
 * 設定画面：狙い目通知のON/OFF・配色・テーマ（ライト/ダーク/システム）。
 * トグルが2個以上になるので HomeScreen とは分離した専用画面（feedback_android_settings_screen）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(HotRaceWorker.PREFS_NAME, Context.MODE_PRIVATE)
    }

    // システムの戻るボタンで前の画面へ戻す
    BackHandler { onBack() }

    // EV狙い目（◎1点）通知のON/OFF（既定ON。Phase 2の主役通知）
    var evNotifyEnabled by remember {
        mutableStateOf(prefs.getBoolean(EvPickWorker.KEY_EV_NOTIFY_ENABLED, true))
    }
    // 「判定対象のみ通知」（既定OFF＝従来どおり全部の◎を通知する）
    var targetOnlyNotify by remember {
        mutableStateOf(prefs.getBoolean(EvPickWorker.KEY_TARGET_ONLY_NOTIFY, false))
    }
    // 旧・確信度ベースの狙い目通知ON/OFF（既定OFF。参考情報が欲しい人だけON）
    var hotNotifyEnabled by remember { mutableStateOf(prefs.getBoolean("hot_notify_enabled", false)) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ヘッダー帯（BargainChecker 統一デザイン）
        TopAppBar(
            title = { Text("設定") },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("戻る") }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {

            // ══════════════════════════════════════════════════════════════
            // EV狙い目（◎1点）通知 ── Phase 2の主役通知
            // ══════════════════════════════════════════════════════════════
            SectionHeader(
                title = "EV狙い目通知（◎1点・2段階）",
                subtitle = "C'条件（EV≥1.2・確率≥0.2）成立で「候補」を通知し、締切3分前に最新オッズで" +
                    "自動再判定→同じ通知を「◎確定」か「見送りへ変更」に更新します。" +
                    "実オッズ検証: 6月122%・5月145%（1日15件まで）"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (evNotifyEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "C'成立を通知する", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (evNotifyEnabled) "有効" else "無効",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = evNotifyEnabled,
                        onCheckedChange = {
                            evNotifyEnabled = it
                            prefs.edit().putBoolean(EvPickWorker.KEY_EV_NOTIFY_ENABLED, it).apply()
                        }
                    )
                }
            }

            // ── 判定対象のみ通知（通知ONのときだけ意味があるので、その時だけ出す）──
            if (evNotifyEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader(
                    title = "判定対象のみ通知",
                    subtitle = "ONにすると、事前登録した判定対象（C'≦5倍・昼）の◎だけを通知します。" +
                        "「参考」の◎は鳴りません。※記録とオッズ収集はこの設定に関係なく" +
                        "これまでどおり全部続きます（データは一切減りません）"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (targetOnlyNotify)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "参考の◎は通知しない",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (targetOnlyNotify)
                                    "有効（判定対象の◎だけ通知）"
                                else
                                    "無効（すべての◎を通知・既定）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = targetOnlyNotify,
                            onCheckedChange = {
                                targetOnlyNotify = it
                                prefs.edit()
                                    .putBoolean(EvPickWorker.KEY_TARGET_ONLY_NOTIFY, it)
                                    .apply()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ══════════════════════════════════════════════════════════════
            // 旧・確信度ベースの狙い目通知（参考。既定OFF）
            // ══════════════════════════════════════════════════════════════
            SectionHeader(
                title = "確信度通知（旧・参考）",
                subtitle = "本命の確信度が高いレースを締切30分前に通知します（本命ベタは回収率90%前後の参考情報）"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hotNotifyEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "締切前に通知する", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (hotNotifyEnabled) "有効" else "無効",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hotNotifyEnabled,
                        onCheckedChange = {
                            hotNotifyEnabled = it
                            prefs.edit().putBoolean("hot_notify_enabled", it).apply()
                        }
                    )
                }
            }

            // ── 通知する確信度のしきい値（通知ONのときだけ表示）──
            if (hotNotifyEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader(
                    title = "通知する確信度",
                    subtitle = "本命の1着確率がこの値以上のレースだけ通知します"
                )
                Spacer(modifier = Modifier.height(8.dp))
                var notifyMinProb by remember {
                    mutableStateOf(
                        prefs.getInt(
                            HotRaceWorker.KEY_NOTIFY_MIN_PROB,
                            HotRaceWorker.DEFAULT_NOTIFY_MIN_PROB
                        )
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        listOf(
                            70 to "すべての狙い目（70%以上）",
                            80 to "80%以上（かなり手堅い）",
                            85 to "85%以上（本命ほぼ確実）",
                            90 to "90%以上（ごく少数）"
                        ).forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        notifyMinProb = value
                                        prefs.edit()
                                            .putInt(HotRaceWorker.KEY_NOTIFY_MIN_PROB, value)
                                            .apply()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = notifyMinProb == value,
                                    onClick = {
                                        notifyMinProb = value
                                        prefs.edit()
                                            .putInt(HotRaceWorker.KEY_NOTIFY_MIN_PROB, value)
                                            .apply()
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ══════════════════════════════════════════════════════════════
            // 配色（カラーパレット）
            // ══════════════════════════════════════════════════════════════
            SectionHeader(title = "配色", subtitle = "アプリ全体の色味を選べます")
            Spacer(modifier = Modifier.height(8.dp))
            var paletteId by remember { mutableStateOf(ThemePrefs.getPalette(context)) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    AppPalettes.ALL.forEach { palette ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    paletteId = palette.id
                                    ThemePrefs.setPalette(context, palette.id)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(palette.previewA, palette.previewB, palette.previewC).forEach { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = palette.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = palette.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = paletteId == palette.id,
                                onClick = {
                                    paletteId = palette.id
                                    ThemePrefs.setPalette(context, palette.id)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ══════════════════════════════════════════════════════════════
            // テーマ（ライト/ダーク/システム）
            // ══════════════════════════════════════════════════════════════
            SectionHeader(title = "テーマ", subtitle = "アプリの明るさ（ライト/ダーク）を切り替えます")
            Spacer(modifier = Modifier.height(8.dp))
            var themeMode by remember { mutableStateOf(ThemePrefs.get(context)) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    listOf(
                        ThemePrefs.MODE_SYSTEM to "システムに従う",
                        ThemePrefs.MODE_LIGHT to "ライト (明るい)",
                        ThemePrefs.MODE_DARK to "ダーク (暗い)"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeMode = mode
                                    ThemePrefs.set(context, mode)
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    themeMode = mode
                                    ThemePrefs.set(context, mode)
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 免責の注記
            Text(
                text = "※ 本アプリの予想はAIによる参考情報です。舟券の的中を保証するものではありません。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
