package com.shinigami.client.manager

import android.content.SharedPreferences
import com.shinigami.client.extension.WebExtension
import com.shinigami.client.util.BuildConfig
import com.shinigami.client.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class ConfigManager(private val prefs: SharedPreferences) {

  companion object {
    private const val TAG = "ConfigManager"
    private const val KEY_URL = "remote_url"
  }

  // fetch url dari remote config, fallback ke cache -> BASE_URL
  suspend fun getUrl(): String = withContext(Dispatchers.IO) {
    try {
      val req = Request.Builder()
        .url(BuildConfig.CONFIG_URL)
        .build()

      val fetched = WebExtension.sharedClient.newCall(req).execute().use { res ->
        res.body.string().trim().takeIf { it.startsWith("http") }
      }

      if (fetched != null) {
        Logger.i(TAG, "Remote url: $fetched")
        prefs.edit().putString(KEY_URL, fetched).apply()
        fetched
      } else {
        Logger.w(TAG, "Empty or invalid response, using cache")
        cached()
      }
    } catch (e: Exception) {
      Logger.w(TAG, "Fetch failed: ${e.message}")
      cached()
    }
  }

  private fun cached() = prefs.getString(KEY_URL, BuildConfig.BASE_URL) ?: BuildConfig.BASE_URL
}
