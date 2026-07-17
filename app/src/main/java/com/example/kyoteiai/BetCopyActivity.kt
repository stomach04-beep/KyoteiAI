package com.example.kyoteiai

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * 通知の「投票へ」ボタンから呼ばれる中継Activity（画面は出ない・一瞬で閉じる）。
 *
 * やること:
 *   1. 買い目テキスト（例「多摩川5R 単勝1号艇 ¥100」）をクリップボードへコピー
 *   2. 該当レースの投票ページ（voteTagId付きレースページ）をブラウザで開く
 *   3. 自分は即終了
 *
 * なぜActivityを挟むのか:
 *   Android 10以降、バックグラウンド（通知やWorker）からのクリップボード書き込みは
 *   禁止されている。前面のActivityからなら書けるので、透明なActivityを一瞬だけ
 *   前面に立ててコピーしてから投票ページへ渡す。
 *
 * ※投票そのものは必ず人間がテレボート画面で行う（自動投票はしない・規約遵守）
 */
class BetCopyActivity : Activity() {

    companion object {
        const val EXTRA_CLIP_TEXT = "clip_text"  // クリップボードへ入れる買い目テキスト
        const val EXTRA_VOTE_URL = "vote_url"    // 開く投票ページURL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clipText = intent.getStringExtra(EXTRA_CLIP_TEXT)
        val voteUrl = intent.getStringExtra(EXTRA_VOTE_URL)

        // 1) 買い目をクリップボードへ（前面Activityなので書き込み可能）
        if (!clipText.isNullOrEmpty()) {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("kyotei_bet", clipText))
            // Android 13以降はOS標準の「コピーしました」表示が出るため、12以下だけ自前Toast
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, "買い目をコピー: $clipText", Toast.LENGTH_SHORT).show()
            }
        }

        // 2) 該当レースの投票ページを開く
        if (!voteUrl.isNullOrEmpty()) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(voteUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "ブラウザが見つかりませんでした", Toast.LENGTH_SHORT).show()
            }
        }

        // 3) 自分はすぐ消える（履歴にも残らない。Manifestで noHistory 指定）
        finish()
    }
}
