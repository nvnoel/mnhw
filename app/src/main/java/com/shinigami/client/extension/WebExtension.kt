package com.shinigami.client.extension

import android.util.LruCache
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.shinigami.client.util.BuildConfig
import com.shinigami.client.util.Logger
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

class WebExtension {

  private val dead = AtomicBoolean(false)

  private val cache = object : LruCache<String, CachedRes>(getCacheSize()) {
    override fun sizeOf(key: String, value: CachedRes): Int {
      return value.data.size
    }
  }

  private val headersToSkip = setOf("host", "content-length", "accept-encoding", "user-agent", "connection")
  private val allowedDomains = setOf("shinigami.asia", "shngm.io")

  private val staticHeaders = mapOf(
    "Access-Control-Allow-Origin" to "*",
    // fix #3: izinkan WebView cache 5 menit — mengurangi re-fetch saat navigasi ulang
    "Cache-Control" to "max-age=300"
  )

  @Volatile private var langHeader = "en-US,en;q=0.9"
  @Volatile private var uaHeader: String? = null

  fun setLang(l: String) {
    langHeader = l
    if (BuildConfig.ENABLE_NETWORK_LOG) Logger.d(TAG, "Language set to: $l")
  }

  fun setUA(agent: String) {
    uaHeader = agent.replace("; wv", "")
    if (BuildConfig.ENABLE_NETWORK_LOG) Logger.d(TAG, "User-Agent updated")
  }

  fun shouldHook(url: String, req: WebResourceRequest): Boolean {
    if (dead.get()) return false

    val host = req.url.host ?: return false
    if (allowedDomains.none { host == it || host.endsWith(".$it") }) return false

    val accept = req.requestHeaders["Accept"] ?: return false
    // fix #2: hapus ignoreCase — WebView selalu kirim format standar, tidak perlu konversi
    return accept.contains("text/html")
  }

  fun hook(req: WebResourceRequest): WebResourceResponse? {
    if (dead.get()) return null
    val urlStr = req.url.toString()

    cache.get(urlStr)?.let {
      if (BuildConfig.ENABLE_NETWORK_LOG) Logger.v(TAG, "Cache hit (HTML): $urlStr")
      return it.toWebRes()
    }

    val start = if (BuildConfig.ENABLE_NETWORK_LOG) System.currentTimeMillis() else 0L

    return try {
      exec(urlStr, req)?.also {
        if (BuildConfig.ENABLE_NETWORK_LOG) {
          Logger.logNetwork(req.method, urlStr, it.code, System.currentTimeMillis() - start)
        }
      }?.toWebRes()
    } catch (e: Exception) {
      Logger.e(TAG, "Hook failed: $urlStr", e)
      null
    }
  }

  private fun exec(url: String, req: WebResourceRequest): CachedRes? {
    val method = req.method
    if (!method.equals("GET", true) && !method.equals("HEAD", true)) return null

    val b = Request.Builder()
      .url(url)
      .method(method, null)
      .header("Accept-Language", langHeader)

    uaHeader?.let { b.header("User-Agent", it) }

    CookieManager.getInstance().getCookie(url)?.let { b.header("Cookie", it) }

    req.requestHeaders.forEach { (k, v) ->
      if (k.lowercase() !in headersToSkip) b.header(k, v)
    }

    // fix #4: removeHeader("Accept-Encoding") dihapus
    // OkHttp auto-inject gzip dan auto-decompress sebelum .string() — hemat bandwidth signifikan

    return try {
      sharedClient.newCall(b.build()).execute().use { res ->
        if (!res.isSuccessful) return null

        saveCookies(url, res.headers)

        val contentType = res.header("Content-Type")
          ?.takeIf { it.contains("html", ignoreCase = true) }
          ?: return null

        val html = res.body.use { it.string() }

        // fix #1: hapus early return saat flag tidak ditemukan
        // sebelumnya return null di sini → WebView bikin duplicate request ke URL yang sama
        val patched = html.replace("is_premium:false", "is_premium:true")

        val cached = CachedRes(patched, res.code, contentType)
        cache.put(url, cached)

        if (BuildConfig.ENABLE_NETWORK_LOG) Logger.i(TAG, "PATCHED (HTML) -> $url")

        cached
      }
    } catch (e: Exception) {
      Logger.e(TAG, "Network error", e)
      null
    }
  }

  private fun saveCookies(url: String, h: Headers) {
    val cookies = h.values("Set-Cookie")
    if (cookies.isNotEmpty()) {
      val mgr = CookieManager.getInstance()
      cookies.forEach { mgr.setCookie(url, it) }
    }
  }

  fun kill() {
    if (dead.getAndSet(true)) return
    cache.evictAll()
    if (BuildConfig.ENABLE_NETWORK_LOG) Logger.i(TAG, "Extension killed and cleaned up")
  }

  private inner class CachedRes(
    val data: ByteArray,
    val code: Int,
    type: String?
  ) {
    private val mime = type?.substringBefore(';')?.trim() ?: "text/html"

    constructor(txt: String, c: Int, t: String?) : this(
      txt.toByteArray(StandardCharsets.UTF_8), c, t
    )

    fun toWebRes(): WebResourceResponse =
      WebResourceResponse(mime, "UTF-8", code, "OK", staticHeaders, ByteArrayInputStream(data))
  }

  companion object {
    private const val TAG = "Extension"

    private fun getCacheSize(): Int {
      val maxMemory = Runtime.getRuntime().maxMemory()
      val cacheSize = (maxMemory / 8).toInt()
      val maxCacheSize = 16 * 1024 * 1024
      val finalSize = cacheSize.coerceAtMost(maxCacheSize)

      if (BuildConfig.ENABLE_NETWORK_LOG) {
        Logger.i(TAG, "LruCache size: ${finalSize / (1024 * 1024)}MB (max heap: ${maxMemory / (1024 * 1024)}MB)")
      }

      return finalSize
    }

    val sharedClient: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .cache(null)
        .build()
    }
  }
}
