package com.shinigami.client.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shinigami.client.manager.ConfigManager
import com.shinigami.client.manager.NetworkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class KomikUiState(
  val url: String? = null,
  val loading: Boolean = true,
  val progress: Int = 0,
  val splash: Boolean = true
)

class KomikViewModel(app: Application) : AndroidViewModel(app) {

  private val net = NetworkManager(app)
  private val config = ConfigManager(
    app.getSharedPreferences("Shinigami", Context.MODE_PRIVATE)
  )

  private val _state = MutableStateFlow(KomikUiState())
  val uiState: StateFlow<KomikUiState> = _state.asStateFlow()

  val defaultHeaders: Map<String, String> = mapOf("Accept-Language" to Locale.getDefault().language)

  init {
    setup()
  }

  private fun setup() {
    viewModelScope.launch {
      net.networkStatus.collect { connected ->
        if (connected && _state.value.url == null) {
          // fetch url dari remote config dulu
          val url = config.getUrl()
          _state.update { it.copy(url = url) }
        }
      }
    }
  }

  fun updateProgress(p: Int) {
    _state.update { it.copy(progress = p) }
    if (p == 100) onPageDone()
  }

  fun onPageDone() {
    _state.update { it.copy(loading = false, splash = false) }
  }
}
