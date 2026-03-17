package com.shinigami.client.util

object AppConfig {
    const val DEBUG = true
    const val ENABLE_ERUDA = false

    const val ENABLE_LOGGER = DEBUG
    const val ENABLE_CRASH_LOG = DEBUG
    const val ENABLE_NETWORK_LOG = DEBUG
    const val ENABLE_WEBVIEW_DEBUG = DEBUG

    const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L
    const val MAX_LOG_FILES = 3

    const val VERSION_NAME = "1.7.0"
    const val VERSION_CODE = 170

    const val BASE_URL = "https://shinigami.to"
    const val CONFIG_URL = "https://gist.githubusercontent.com/nvnoel/03f49361806a77c36c813ec1898c1240/raw/1b4be5c1d19a92abf5517da0b098e4ed856d5dcc/url.shngm"
}
