plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.kyoteiai"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.kyoteiai"
        minSdk = 31
        targetSdk = 36
        // 2026-08-12: 1.1 のまま据え置きだった間に v1.3〜v1.7 相当が入っていたので実態へ。
        // 中身＝EV通知/購入記録/収支(v1.3)・3連単の非推奨表示(v1.4)・締切前オッズ収集をスマホへ移管(v1.5)・
        // 収集ワーカーの実行の足跡 run_log.json・締切前の複勝オッズも記録(v1.7)・通知アイコン刷新。
        // v1.8: 発売前の「オッズ0」を未確定として記録から除外（単勝・複勝とも）。
        // v2.0（2026-08-29）: 収集の取りこぼし対策と可視化。
        //   ・取得失敗時に必ずスナップショット予約へ回す
        //     （締切4分以内に初めて見つけたレースでも再挑戦が残る＝実測データを失わない）
        //   ・ステージ判定を「判定対象の前向き標本」で行う（PC側 judge_summary と同条件）
        //   ・結果タブの取得を並列2＋300〜500ms間隔へ（公式の絞り対策）
        //   ・今日タブに収集ヘルス1行／ホーム画面ウィジェット新設
        //   ・前日欠測のセルフ検知通知／収集のみ21:30まで延長／「判定対象のみ通知」トグル
        // v2.1（2026-09-05）競艇AI研究の打ち切りに伴い、締切前オッズの収集を既定OFFへ。
        //   あわせて収集OFF時は前日欠測アラートを鳴らさない（止めた対象の監視は誤警報になる）
        // v2.2（2026-09-05）EV狙い目通知も既定OFFへ（EV買いは実測81〜91%で本命買いに負ける）。
        //   あわせて通知も収集も無効なら締切のexactアラームを張らない（空打ちで端末を起こさない）
        // v2.3（2026-09-25）フィード取得が戻ったら失敗警告の通知を消す。
        //   狙い目レース通知は実際に出せたときだけ「通知済み」に記録する
        // v2.4（2026-10-23）表示の決まり UiKit を今日タブ・結果画面に適用（見た目だけ。計算は不変）
        // v2.5（2026-10-24）: 収支・回収率の色（プラス緑・マイナス赤）を手直し前の仕様に戻した（ユーザー指示）
        versionCode = 19
        // 2026-10-24 v2.6: 見出し（アプリ名など上の部分）と中身の境目に1本の線を入れた（ユーザー指示・機能は変更なし）
        // 2026-10-25 v2.7: 下タブをやめ見出し下の横タブ（今日／結果／カレンダー・横スワイプ可）＋設定は歯車に。収支の色を＋赤・−青に（APP_RULES.md A2・A5・B3）
        // 2026-11-01 v2.9: 見出しの左にアプリアイコン、見出しとタブの後ろに Gemini で描いた絵を敷いた（案3-3・UiKit の HeaderBackdrop／HeaderAppIcon）
        // 2026-11-11 v2.10: 状態の出し方（UiKit 6章）。今日タブに取得の失敗の知らせ＋再試行、読み込みの印は1秒から・予想があれば一覧を残す、
        //   取り直しに失敗しても前の予想を残す、見本・古い予想のときは主役の件数を出さない。結果タブの更新ボタンは印に替えない
        // 2026-11-11 v2.11: 共通部品の試し版（UiKitTrial.kt）。主役の空の「—」を控えめに、知らせの再試行を太字＋枠線に（APP_RULES F4・承認待ち）
        // 2026-11-12 v2.12: 利用者が2つとも承認→原本の UiKit（HeroStat・StatusNotice）に入ったので配り直し、試し版を消した（見た目は v2.11 と同じ）
        versionName = "2.13"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // ViewModel + Compose 連携（viewModel() ヘルパー・viewModelScope）
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    // WorkManager: background task scheduler (no foreground service needed)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}