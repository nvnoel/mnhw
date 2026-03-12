package com.shinigami.client.ui

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shinigami.client.extension.WebExtension
import com.shinigami.client.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuComposeSheet(
  url: String,
  onDismiss: () -> Unit,
  onOpenPopup: (String) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  ModalBottomSheet(
    onDismissRequest = { onDismiss() },
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp)
    ) {
      Text(
        text = url,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )

      ImagePreview(url = url)

      MenuItem(text = "Buka di Tab Baru") {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
          if (!sheetState.isVisible) {
            onOpenPopup(url)
            onDismiss()
          }
        }
      }

      MenuItem(text = "Buka di Browser Luar") {
        safeExec(context) {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
      }

      MenuItem(text = "Salin Tautan") {
        safeExec(context) {
          (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("URL", url))
          Toast.makeText(context, "Link disalin", Toast.LENGTH_SHORT).show()
        }
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
      }

      MenuItem(text = "Unduh Gambar") {
        safeExec(context) {
          val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("Mengunduh Gambar")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
              Environment.DIRECTORY_PICTURES,
              "Shinigami/IMG_${System.currentTimeMillis()}.jpg"
            )
          (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
          Toast.makeText(context, "Sedang mengunduh...", Toast.LENGTH_SHORT).show()
        }
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
      }

      MenuItem(text = "Bagikan") {
        safeExec(context) {
          context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
          }, "Bagikan"))
        }
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
      }
    }
  }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
  Text(
    text = text,
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 12.dp),
    style = MaterialTheme.typography.bodyLarge
  )
}

@Composable
fun ImagePreview(url: String) {
  var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

  LaunchedEffect(url) {
    withContext(Dispatchers.IO) {
      try {
        val req = Request.Builder()
          .url(url)
          .apply { CookieManager.getInstance().getCookie(url)?.let { header("Cookie", it) } }
          .build()

        val bmp = WebExtension.sharedClient.newCall(req).execute().use { res ->
          res.body.byteStream().buffered().let { BitmapFactory.decodeStream(it) }
        }

        if (bmp != null) {
          withContext(Dispatchers.Main) {
            imageBitmap = bmp.asImageBitmap()
          }
        }
      } catch (e: Exception) {
        Logger.e("ContextMenuComposeSheet", "ImgErr", e)
      }
    }
  }

  imageBitmap?.let { bitmap ->
    Image(
      bitmap = bitmap,
      contentDescription = "Image Preview",
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 200.dp)
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(8.dp))
    )
  }
}

private fun safeExec(context: Context, block: () -> Unit) {
  try {
    block()
  } catch (e: Exception) {
    Logger.e("ContextMenuComposeSheet", "Action failed", e)
    Toast.makeText(context, "Gagal memproses aksi", Toast.LENGTH_SHORT).show()
  }
}
