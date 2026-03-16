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
    val isLoading: Boolean = true,
    val loadingProgress: Int = 0,
    val isSplashVisible: Boolean = true
)

class KomikViewModel(application: Application) : AndroidViewModel(application) {

    private val networkManager = NetworkManager(application)
    private val configManager = ConfigManager(
        application.getSharedPreferences("Shinigami", Context.MODE_PRIVATE)
    )

    private val _uiState = MutableStateFlow(KomikUiState())
    val uiState: StateFlow<KomikUiState> = _uiState.asStateFlow()

    val defaultHeaders: Map<String, String> = mapOf("Accept-Language" to Locale.getDefault().language)

    init {
        initializeData()
    }

    private fun initializeData() {
        viewModelScope.launch {
            networkManager.networkStatus.collect { isConnected ->
                if (isConnected && _uiState.value.url == null) {
                    val remoteUrl = configManager.getUrl()
                    _uiState.update { currentState ->
                        currentState.copy(url = remoteUrl)
                    }
                }
            }
        }
    }

    fun updateLoadingProgress(progress: Int) {
        _uiState.update { currentState ->
            currentState.copy(loadingProgress = progress)
        }
        if (progress == 100) {
            onPageFinished()
        }
    }

    fun onPageFinished() {
        _uiState.update { currentState ->
            currentState.copy(
                isLoading = false,
                isSplashVisible = false
            )
        }
    }
}