package com.example.kyoteiai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kyoteiai.data.Feed
import com.example.kyoteiai.data.FeedRepository
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

    init {
        // 起動時に一度読み込む
        load()
    }

    /** フィードを（再）読み込む。プル更新ボタンからも呼ぶ */
    fun load() {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val result = FeedRepository.load(getApplication())
            _uiState.value = FeedUiState(
                loading = false,
                feed = result.feed,
                fromRemote = result.fromRemote,
                errorMessage = if (result.feed == null) "予想データを取得できませんでした" else null
            )
        }
    }
}

/**
 * フィード画面の状態。
 * loading=読み込み中 / feed=取得できた予想 / fromRemote=リモート取得成功か / errorMessage=エラー文言
 */
data class FeedUiState(
    val loading: Boolean = true,
    val feed: Feed? = null,
    val fromRemote: Boolean = false,
    val errorMessage: String? = null
)
