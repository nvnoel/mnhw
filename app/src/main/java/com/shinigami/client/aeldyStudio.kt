package com.shinigami.client

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import com.google.android.material.color.DynamicColors
import com.shinigami.client.ui.DebugActivity
import com.shinigami.client.util.Logger
import kotlin.system.exitProcess

class aeldyStudio : Application() {

  override fun onCreate() {
    super.onCreate()
    Logger.init(this)
    DynamicColors.applyToActivitiesIfAvailable(this)
    setupGlobalErrorHandler()
  }

  private fun setupGlobalErrorHandler() {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, err ->
      try {
        Logger.logCrash(err)
        Logger.e("App", "Crash in ${t.name}", err)
        Logger.flushNow()
        val intent = Intent(this, DebugActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
          putExtra("error", Log.getStackTraceString(err))
        }
        startActivity(intent)
        Thread.sleep(300)
      } catch (e: Exception) {
        Log.e("App", "Failed to handle crash", e)
      } finally {
        // Matikan proses dengan bersih
        Logger.shutdown()
        exitProcess(10)
      }
    }
  }
}