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
        versionCode = 8
        versionName = "2.2"

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