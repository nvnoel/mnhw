package com.shinigami.client.ui

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shinigami.client.R
import com.shinigami.client.extension.WebExtension
import com.shinigami.client.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

class ContextMenuSheet : BottomSheetDialogFragment() {

  private var url: String? = null

  // referensi view disimpan manual karena tanpa ViewBinding
  private var imgPreview: ImageView? = null

  companion object {
    private const val TAG = "ContextMenu"
    fun newInstance(url: String) = ContextMenuSheet().apply {
      arguments = Bundle().apply { putString("url", url) }
    }
  }

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    url = arguments?.getString("url")
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    return inflater.inflate(R.layout.fragment_context_menu, container, false)
  }

  override fun onViewCreated(view: View, state: Bundle?) {
    super.onViewCreated(view, state)

    imgPreview = view.findViewById(R.id.img_preview)
    view.findViewById<TextView>(R.id.txt_url).text = url

    view.findViewById<TextView>(R.id.btn_open).setOnClickListener { openBrowser(); dismiss() }
    view.findViewById<TextView>(R.id.btn_copy).setOnClickListener { copyUrl(); dismiss() }
    view.findViewById<TextView>(R.id.btn_download).setOnClickListener { download(); dismiss() }
    view.findViewById<TextView>(R.id.btn_share).setOnClickListener { share(); dismiss() }

    url?.let { loadImg(it) }
  }

  override fun onStart() {
    super.onStart()
    (dialog as? BottomSheetDialog)?.behavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
      override fun onStateChanged(v: View, newState: Int) {
        if (newState == BottomSheetBehavior.STATE_EXPANDED) {
          url?.let { (activity as? PopupHost)?.openPopup(it); dismiss() }
        }
      }
      override fun onSlide(v: View, o: Float) {}
    })
  }

  override fun onDestroyView() {
    super.onDestroyView()
    imgPreview = null
  }

  private fun openBrowser() = safeExec { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

  private fun copyUrl() = safeExec {
    (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
      .setPrimaryClip(ClipData.newPlainText("URL", url))
    toast("Link disalin")
  }

  private fun download() = safeExec {
    val req = DownloadManager.Request(Uri.parse(url))
      .setTitle("Mengunduh Gambar")
      .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
      .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Shinigami/IMG_${System.currentTimeMillis()}.jpg")
    (requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
    toast("Sedang mengunduh...")
  }

  private fun share() = safeExec {
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
    }, "Bagikan"))
  }

  private fun loadImg(imgUrl: String) {
    lifecycleScope.launch(Dispatchers.IO) {
      try {
        val req = Request.Builder()
          .url(imgUrl)
          .apply { CookieManager.getInstance().getCookie(imgUrl)?.let { header("Cookie", it) } }
          .build()

        val bmp = WebExtension.sharedClient.newCall(req).execute().use { res ->
          res.body?.byteStream()?.buffered()?.let { BitmapFactory.decodeStream(it) }
        }

        withContext(Dispatchers.Main) {
          val img = imgPreview ?: return@withContext
          if (bmp != null) {
            img.setImageBitmap(bmp)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.imageTintList = null
          }
        }
      } catch (e: Exception) {
        Logger.e(TAG, "ImgErr", e)
      }
    }
  }

  private fun safeExec(block: () -> Unit) {
    try { block() } catch (e: Exception) {
      Logger.e(TAG, "Action failed", e)
      toast("Gagal memproses aksi")
    }
  }

  private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
