package com.example.kyoteiai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kyoteiai.data.Feed
import com.example.kyoteiai.data.FeedRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 予想フィードのUI状態を管理する ViewModel。
 *
 * StateFlow で状態を保持し、Compose 側が collectAsState() で購読する（ViewModelパターン）。
 * データ取得は FeedRepository（coroutine, Dispatchers.IO）に委譲する。
 */
class FeedViewModel(app: Application) : AndroidViewModel(app) {

    // 画面が参照する読み取り専用の状態
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    // 取得中の仕事（二重に走らせないため）
    private var loadJob: Job? = null

    init {
        // 起動時に一度読み込む
        load()
    }

    /**
     * フィードを（再）読み込む。起動時と、今日タブの知らせの「再試行」から呼ぶ。
     * 2026-11-11：取得中は前の予想を消さない（loading だけ立てる）。取り直しに失敗したら
     * 前にネットから取れていた予想を残す（mergeFeedResult）。取得中に押されても二重に走らせない
     */
    fun load() {
        if (loadJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(loading = true)
        loadJob = viewModelScope.launch {
            val result = FeedRepository.load(getApplication())
            _uiState.value = mergeFeedResult(_uiState.value, result.feed, result.fromRemote)
        }
    }
}

/**
 * フィード画面の状態。
 * loading=読み込み中 / feed=取得できた予想 / fromRemote=リモート取得成功か（false＝アプリに入っている見本）
 * 2026-11-11：エラー文（errorMessage）は持たない。画面の知らせは feedNotice が状態から作る
 */
data class FeedUiState(
    val loading: Boolean = true,
    val feed: Feed? = null,
    val fromRemote: Boolean = false,
    // 直前の取り直しが失敗して、前の予想を残したか（知らせの文を分けるため・2026-11-11）
    val lastAttemptFailed: Boolean = false,
)
