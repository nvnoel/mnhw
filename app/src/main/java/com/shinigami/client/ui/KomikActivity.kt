package com.shinigami.client.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shinigami.client.R
import com.shinigami.client.extension.WebExtension
import com.shinigami.client.manager.DialogManager
import com.shinigami.client.util.BuildConfig
import com.shinigami.client.util.Logger
import java.io.ByteArrayInputStream
import java.util.Locale

class KomikActivity : ComponentActivity(), PopupHost {

  private val vm: KomikViewModel by viewModels()
  private val ext by lazy { WebExtension() }

  private var mainWeb: WebView? = null

  private var fileCallback: ValueCallback<Array<Uri>>? = null
  private var pendingFileChooserParams: WebChromeClient.FileChooserParams? = null
  private var lastBack = 0L
  private var touchX = 0
  private var touchY = 0

  private var showMenuAction: ((String) -> Unit)? = null
  private var onPopupReadyAction: ((WebView) -> Unit)? = null

  companion object {
    private const val TAG = "KomikActivity"
    private val JS_IMG_DETECT = """(function(x,y){const e=document.elementsFromPoint(x,y);if(!e.length)return null;const g=(n)=>{if(!n)return null;const t=n.tagName;if(t==='IMG')return n.currentSrc||n.srcset?.split(' ')[0]||n.src||n.dataset.src||n.dataset.lazySrc;if(t==='CANVAS'){try{return n.toDataURL()}catch(e){}}if(t==='image'||t==='svg')return n.href?.baseVal||n.getAttribute('xlink:href');const s=getComputedStyle(n).backgroundImage;if(s&&s!=='none'&&s.startsWith('url(')){const m=s.match(/url\(['"]?([^'"]+)['"]?\)/);if(m)return m[1]}return null};for(let i=0;i<e.length;i++){const u=g(e[i]);if(u)return u}let p=e[0];for(let d=0;d<5&&p;d++){const u=g(p);if(u)return u;p=p.parentElement}return null})(%d,%d)""".trimIndent().replace("\n", "")

    private val EMPTY_STREAM = ByteArrayInputStream(ByteArray(0))
  }

  private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
    val cb = fileCallback ?: return@registerForActivityResult
    fileCallback = null
    pendingFileChooserParams = null

    try {
      val uris = if (res.resultCode == RESULT_OK) {
        res.data?.data?.let { arrayOf(it) }
          ?: res.data?.clipData?.let { clip ->
            Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
          }
      } else null

      cb.onReceiveValue(uris)
    } catch (e: Exception) {
      cb.onReceiveValue(null)
    }
  }

  private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
    val granted = perms.all { it.value }

    if (!granted) {
      Toast.makeText(this, "Izin akses file diperlukan untuk upload", Toast.LENGTH_SHORT).show()
      fileCallback?.onReceiveValue(null)
      fileCallback = null
      pendingFileChooserParams = null
    } else {
      pendingFileChooserParams?.let { params ->
        try {
          val intent = params.createIntent().apply { addCategory(Intent.CATEGORY_OPENABLE) }

          if (intent.resolveActivity(packageManager) != null) {
            filePicker.launch(intent)
          } else {
            Toast.makeText(this, "Tidak ada aplikasi file manager", Toast.LENGTH_SHORT).show()
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            pendingFileChooserParams = null
          }
        } catch (e: Exception) {
          Toast.makeText(this, "Gagal membuka file picker", Toast.LENGTH_SHORT).show()
          fileCallback?.onReceiveValue(null)
          fileCallback = null
          pendingFileChooserParams = null
        }
      } ?: run {
        Toast.makeText(this, "Izin diberikan, silakan coba lagi", Toast.LENGTH_SHORT).show()
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(state: Bundle?) {
    installSplashScreen()
    setupWindow()
    super.onCreate(state)

    setContent {
      val uiState by vm.uiState.collectAsStateWithLifecycle()

      var popupUrl by remember { mutableStateOf<String?>(null) }
      var popupWebViewInstance by remember { mutableStateOf<WebView?>(null) }
      var contextMenuUrl by remember { mutableStateOf<String?>(null) }

      showMenuAction = { url -> contextMenuUrl = url }
      onPopupReadyAction = { wv -> popupWebViewInstance = wv }

      val isPopupVisible = popupUrl != null || popupWebViewInstance != null

      BackHandler(enabled = true) {
        when {
          isPopupVisible -> {
            popupUrl = null
            popupWebViewInstance?.let {
              it.stopLoading()
              it.destroy()
            }
            popupWebViewInstance = null
          }
          mainWeb?.canGoBack() == true -> mainWeb?.goBack()
          else -> handleExit()
        }
      }

      Scaffold { paddingValues ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
          PullToRefreshBox(
            isRefreshing = uiState.loading && !uiState.splash,
            onRefresh = {
              if (isPopupVisible) popupWebViewInstance?.reload() else mainWeb?.reload()
            },
            modifier = Modifier.fillMaxSize()
          ) {
            AndroidView(
              factory = { context ->
                WebView(context).apply {
                  mainWeb = this
                  initWebSettings(this)
                  setupWeb(this)

                  state?.let { restoreState(it) }
                }
              },
              update = { webView ->
                val currentUrl = uiState.url
                if (currentUrl != null && webView.url == null) {
                  webView.loadUrl(currentUrl, vm.defaultHeaders)
                }
              },
              onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
                mainWeb = null
              },
              modifier = Modifier.fillMaxSize()
            )
          }

          val currentPopupUrl = popupUrl
          val currentPopupInstance = popupWebViewInstance

          if (currentPopupUrl != null || currentPopupInstance != null) {
            AndroidView(
              factory = { context ->
                currentPopupInstance ?: createPopupWebView(context).apply {
                  if (currentPopupUrl != null) {
                    loadUrl(currentPopupUrl)
                  }
                }
              },
              update = { webView ->
                if (popupWebViewInstance == null) {
                  popupWebViewInstance = webView
                }
              },
              onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
                if (popupWebViewInstance == webView) {
                  popupWebViewInstance = null
                }
              },
              modifier = Modifier.fillMaxSize()
            )
          }

          AnimatedVisibility(
            visible = uiState.splash,
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.fillMaxSize()
          ) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B))
            ) {
              LinearProgressIndicator(
                progress = { uiState.progress / 100f },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(2.dp)
                  .align(Alignment.TopCenter),
                color = Color.Red,
                trackColor = Color.DarkGray,
              )

              Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                  .fillMaxWidth(0.6f)
                  .align(Alignment.Center)
              )

              Text(
                text = "Developed by t.me/aeldy24",
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.05.sp,
                modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .padding(bottom = 32.dp)
              )
            }
          }
        }
      }

      val currentContextMenuUrl = contextMenuUrl
      if (currentContextMenuUrl != null) {
        ContextMenuComposeSheet(
          url = currentContextMenuUrl,
          onDismiss = { contextMenuUrl = null },
          onOpenPopup = { url: String ->
            popupUrl = url
            contextMenuUrl = null
          }
        )
      }

      LaunchedEffect(uiState.splash) {
        if (!uiState.splash) {
          checkFirstRun()
        }
      }
    }
  }

  private fun setupWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
  }

  private fun initWebSettings(w: WebView) {
    w.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      useWideViewPort = true
      loadWithOverviewMode = true
      setSupportZoom(true)
      builtInZoomControls = true
      displayZoomControls = false
      setSupportMultipleWindows(true)
      javaScriptCanOpenWindowsAutomatically = true
      cacheMode = WebSettings.LOAD_DEFAULT
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
      allowFileAccess = true
      allowContentAccess = true

      userAgentString = userAgentString.replace("; wv", "")
    }
  }

  private fun setupWeb(web: WebView) {
    ext.setLang(Locale.getDefault().toLanguageTag())
    ext.setUA(web.settings.userAgentString)

    CookieManager.getInstance().apply {
      setAcceptCookie(true)
      setAcceptThirdPartyCookies(web, true)
    }

    web.webViewClient = AppWebClient()
    web.webChromeClient = AppChromeClient()

    web.setOnTouchListener { _: View, e: MotionEvent ->
      if (e.action == MotionEvent.ACTION_DOWN) {
        touchX = e.x.toInt()
        touchY = e.y.toInt()
      }
      false
    }

    web.setOnLongClickListener {
      detectImg(web)
      true
    }
  }

  private fun detectImg(web: WebView) {
    if (touchX == 0 && touchY == 0) return

    val hit = web.hitTestResult
    if (hit.type == WebView.HitTestResult.IMAGE_TYPE ||
      hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
      hit.extra?.let { url: String -> showMenu(url) }
      return
    }

    val js = JS_IMG_DETECT.format(touchX, touchY)
    web.evaluateJavascript(js) { res: String? ->
      res?.takeIf { it != "null" && it.length > 2 }
        ?.removeSurrounding("\"")
        ?.let { url: String -> showMenu(url) }
    }
  }

  private fun handleExit() {
    val now = System.currentTimeMillis()
    if (now - lastBack < 2000L) {
      finish()
    } else {
      lastBack = now
      Toast.makeText(this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
    }
  }

  private fun hasStoragePerm(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    } else true
  }

  private fun requestStoragePerm() {
    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    permLauncher.launch(perms)
  }

  private fun enableEruda(view: WebView) {
    if (BuildConfig.ENABLE_ERUDA) {
      val erudaScript = """(function(){if(typeof eruda==='undefined'){var script=document.createElement('script');script.src="https://cdn.jsdelivr.net/npm/eruda";document.body.appendChild(script);script.onload=function(){eruda.init();}}})();"""
      view.evaluateJavascript(erudaScript, null)
    }
  }

  private fun createPopupWebView(context: android.content.Context): WebView {
    return WebView(context).apply {
      initWebSettings(this)

      webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
          if (req.url.toString().contains("googletagmanager")) {
            return WebResourceResponse("text/plain", "utf-8", EMPTY_STREAM)
          }
          return super.shouldInterceptRequest(view, req)
        }

        override fun onPageFinished(view: WebView, url: String) {
          enableEruda(view)
        }
      }

      webChromeClient = object : AppChromeClient() {
        override fun onCloseWindow(window: WebView?) {
          // Trigger the back handler logic by delegating to action
          // Since we control popup via state, we will handle this directly or via BackHandler
        }
      }
    }
  }

  override fun openPopup(url: String) {
    // handled by Compose state
  }

  private inner class AppWebClient : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
      val url = req.url.toString()

      if (url.contains("googletagmanager")) {
        return WebResourceResponse("text/plain", "utf-8", EMPTY_STREAM)
      }

      return if (ext.shouldHook(url, req)) ext.hook(req) else null
    }

    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
      val url = req.url.toString()

      if (internal(url)) return false

      return try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
      } catch (e: Exception) {
        Logger.e(TAG, "Can't open external URL", e)
        false
      }
    }

    private fun internal(url: String) =
      url.contains("accounts.google.com") || url.contains("shinigami") || url.contains("shngm")

    override fun onPageFinished(view: WebView, url: String) {
      vm.onPageDone()
      enableEruda(view)
    }

    override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
      if (req.isForMainFrame) Logger.w(TAG, "Page error: ${err.description}")
    }
  }

  private open inner class AppChromeClient : WebChromeClient() {
    override fun onProgressChanged(view: WebView, progress: Int) {
      vm.updateProgress(progress)
    }

    override fun onJsAlert(view: WebView?, url: String?, msg: String?, res: JsResult?): Boolean {
      if (res == null) return false
      DialogManager.info(this@KomikActivity, getDomain(url), msg ?: "", "OK") { res.confirm() }
      return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, msg: String?, res: JsResult?): Boolean {
      if (res == null) return false
      DialogManager.confirm(this@KomikActivity, getDomain(url), msg ?: "", "OK", "Batal",
        { res.confirm() }, { res.cancel() })
      return true
    }

    override fun onJsPrompt(view: WebView?, url: String?, msg: String?, def: String?, res: JsPromptResult?): Boolean {
      if (res == null) return false
      DialogManager.prompt(this@KomikActivity, getDomain(url), msg ?: "", def ?: "",
        onDone = { it: String -> res.confirm(it) }, onCancel = { res.cancel() })
      return true
    }

    override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
      fileCallback?.onReceiveValue(null)
      fileCallback = cb

      if (!hasStoragePerm()) {
        pendingFileChooserParams = params
        requestStoragePerm()
        return true
      }

      try {
        val intent = params.createIntent().apply { addCategory(Intent.CATEGORY_OPENABLE) }

        if (intent.resolveActivity(packageManager) != null) {
          filePicker.launch(intent)
        } else {
          Toast.makeText(this@KomikActivity, "Tidak ada aplikasi file manager", Toast.LENGTH_SHORT).show()
          fileCallback?.onReceiveValue(null)
          fileCallback = null
          return false
        }
      } catch (e: Exception) {
        Toast.makeText(this@KomikActivity, "Gagal membuka file picker", Toast.LENGTH_SHORT).show()
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        return false
      }

      return true
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, gesture: Boolean, msg: Message?): Boolean {
      val newWeb = createPopupWebView(view?.context ?: this@KomikActivity)
      (msg?.obj as? WebView.WebViewTransport)?.webView = newWeb
      msg?.sendToTarget()
      onPopupReadyAction?.invoke(newWeb)
      return true
    }
  }

  private fun showMenu(url: String) {
    showMenuAction?.invoke(url)
  }

  private fun getDomain(url: String?) = try {
    Uri.parse(url).host ?: "Website"
  } catch (e: Exception) {
    "Website"
  }

  private fun checkFirstRun() {
    val prefs = getSharedPreferences("Shinigami", MODE_PRIVATE)
    if (!prefs.getBoolean("welcome_shown", false)) {
      DialogManager.info(this, "Welcome new user!",
        "Login dengan akun Google untuk membuka fitur premium secara gratis.")
      prefs.edit().putBoolean("welcome_shown", true).apply()
    }
  }

  override fun onPause() {
    super.onPause()
    mainWeb?.onPause()
  }

  override fun onResume() {
    super.onResume()
    mainWeb?.onResume()
  }

  override fun onDestroy() {
    mainWeb?.let { webView ->
      webView.stopLoading()
      webView.onPause()
      webView.pauseTimers()
      webView.clearHistory()
      webView.clearCache(false)
      webView.clearFormData()
      webView.loadUrl("about:blank")
      (webView.parent as? ViewGroup)?.removeView(webView)
      webView.destroy()
    }
    mainWeb = null

    showMenuAction = null
    onPopupReadyAction = null

    ext.kill()

    super.onDestroy()
  }
}
