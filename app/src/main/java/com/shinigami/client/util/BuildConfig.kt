package com.shinigami.client.util

object BuildConfig {

  const val DEBUG = true

  const val ENABLE_ERUDA = false

  val ENABLE_LOGGER = DEBUG
  val ENABLE_CRASH_LOG = DEBUG
  val ENABLE_NETWORK_LOG = DEBUG
  val ENABLE_WEBVIEW_DEBUG = DEBUG

  const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L
  const val MAX_LOG_FILES = 3

  const val VERSION_NAME = "1.7.0"
  const val VERSION_CODE = 170

  // fallback url — dipakai kalau CONFIG_URL gagal dan belum ada cache
  const val BASE_URL = "https://shinigami.to/"

  const val CONFIG_URL = "https://gist.githubusercontent.com/nvnoel/0f8d0eb0181ab79690cae43a21b41471/raw/e7c809930ed9fca4e138817b5b72a34cd82d4a5c/url.txt"
}
