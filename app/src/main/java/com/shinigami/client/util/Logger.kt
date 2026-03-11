package com.shinigami.client.util

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Logger {

  private const val TAG = "Logger"
  private const val LOG_DIR = "log"
  private const val MAX_QUEUE_SIZE = 1000

  private val timeFmt = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("HH:mm:ss", Locale.US)
  }

  private val dateFmt = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  }

  private val stringBuilder = ThreadLocal.withInitial { StringBuilder(256) }

  private val queue = ConcurrentLinkedQueue<String>()
  private val worker = Executors.newSingleThreadExecutor()

  private var file: File? = null
  // writer di-keep open, ditutup hanya saat shutdown atau rotate
  private var writer: BufferedWriter? = null
  @Volatile private var ready = false

  fun init(context: Context) {
    if (!BuildConfig.ENABLE_LOGGER || ready) return

    try {
      val root = context.getExternalFilesDir(null) ?: context.filesDir
      val dir = File(root, LOG_DIR)

      if (!dir.exists()) dir.mkdirs()

      val dateStr = dateFmt.get()?.format(Date()) ?: "unknown"
      file = File(dir, "shngm-log_$dateStr.txt")
      cleanOld(dir)

      writer = BufferedWriter(FileWriter(file, true))

      if (file?.exists() == false) {
        write("=== Shinigami v${BuildConfig.VERSION_NAME} ===\n")
      }

      ready = true
      Log.i(TAG, "Logger initialized at: ${file?.absolutePath}")
    } catch (e: Exception) {
      Log.e(TAG, "Init failed: ${e.message}")
    }
  }

  private fun cleanOld(dir: File) {
    try {
      dir.listFiles()
        ?.sortedByDescending { it.lastModified() }
        ?.drop(BuildConfig.MAX_LOG_FILES)
        ?.forEach { it.delete() }
    } catch (e: Exception) {
      Log.e(TAG, "Cleanup failed: ${e.message}")
    }
  }

  fun v(tag: String, msg: String) = log("V", tag, msg)
  fun d(tag: String, msg: String) = log("D", tag, msg)
  fun i(tag: String, msg: String) = log("I", tag, msg)
  fun w(tag: String, msg: String) = log("W", tag, msg)
  fun e(tag: String, msg: String, err: Throwable? = null) {
    log("E", tag, err?.let { "$msg: ${it.message}" } ?: msg)
    err?.let { logErr(it) }
  }

  private fun log(lvl: String, tag: String, msg: String) {
    if (!BuildConfig.ENABLE_LOGGER) return

    when (lvl) {
      "V" -> Log.v(tag, msg)
      "D" -> Log.d(tag, msg)
      "I" -> Log.i(tag, msg)
      "W" -> Log.w(tag, msg)
      "E" -> Log.e(tag, msg)
    }

    if (ready) {
      val time = timeFmt.get()?.format(Date()) ?: "00:00:00"
      offerBounded("$time [$lvl] $tag: $msg\n")
      flush()
    }
  }

  private fun offerBounded(text: String) {
    if (queue.size >= MAX_QUEUE_SIZE) queue.poll()
    queue.offer(text)
  }

  private fun logErr(err: Throwable) {
    if (!ready) return

    val builder = stringBuilder.get()!!.apply { setLength(0) }
    builder.append("  ↳ ${err.javaClass.simpleName}: ${err.message}\n")
    err.stackTrace.take(5).forEach { builder.append("  at $it\n") }

    offerBounded(builder.toString())
    flush()
  }

  private fun flush() {
    if (worker.isShutdown) return
    worker.execute { writeQueue() }
  }

  fun flushNow() {
    if (!ready || worker.isShutdown) return
    try {
      val future = worker.submit { writeQueue() }
      future.get(1000, TimeUnit.MILLISECONDS)
    } catch (e: Exception) {
      Log.e(TAG, "Flush failed: ${e.message}")
    }
  }

  private fun writeQueue() {
    while (!queue.isEmpty()) {
      queue.poll()?.let { write(it) }
    }
    // flush buffer sekali setelah drain queue
    try { writer?.flush() } catch (e: Exception) { Log.e(TAG, "Flush err: ${e.message}") }
    checkSize()
  }

  // tulis ke writer yang sudah open — tidak buka/tutup tiap kali
  private fun write(text: String) {
    try {
      writer?.write(text)
    } catch (e: Exception) {
      Log.e(TAG, "Write failed: ${e.message}")
    }
  }

  private fun checkSize() {
    file?.let { f ->
      if (f.length() > BuildConfig.MAX_LOG_FILE_SIZE) {
        val backup = File(f.parent, "${f.nameWithoutExtension}_${System.currentTimeMillis()}.txt")
        // tutup writer sebelum rename file
        try { writer?.close() } catch (_: Exception) {}
        f.renameTo(backup)
        // buka writer baru untuk file baru
        try {
          writer = BufferedWriter(FileWriter(f, true))
          write("=== Rotated from ${backup.name} ===\n")
        } catch (e: Exception) {
          Log.e(TAG, "Rotate failed: ${e.message}")
        }
      }
    }
  }

  fun logCrash(err: Throwable) {
    if (!BuildConfig.ENABLE_CRASH_LOG) return

    val crash = buildString {
      append("\n╔═══ CRASH ═══════════════════════════════════════════════╗\n")
      append("║ ${err.javaClass.simpleName}: ${err.message}\n")
      err.stackTrace.take(15).forEach { append("║   $it\n") }
      append("╚═════════════════════════════════════════════════════════╝\n")
    }

    offerBounded(crash)
    flush()
  }

  fun logNetwork(method: String, url: String, code: Int, time: Long) {
    if (!BuildConfig.ENABLE_NETWORK_LOG) return
    d("Network", "$method $url → $code (${time}ms)")
  }

  fun path() = file?.absolutePath

  fun shutdown() {
    try {
      ready = false
      worker.shutdown()
      writer?.flush()
      writer?.close()
      writer = null
    } catch (e: Exception) {
      Log.e(TAG, "Shutdown error: ${e.message}")
    }
  }
}
