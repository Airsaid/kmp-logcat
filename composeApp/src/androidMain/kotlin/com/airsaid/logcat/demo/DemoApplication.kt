package com.airsaid.logcat.demo

import android.app.Application
import com.airsaid.logcat.AndroidLogcatLogStrategy
import com.airsaid.logcat.AndroidLogcatLogger
import com.airsaid.logcat.LogPriority
import com.airsaid.logcat.NonFormatStrategy

class DemoApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    AndroidLogcatLogger.installOnDebuggableApp(
      application = this,
      minPriority = LogPriority.DEBUG,
    ) {
      NonFormatStrategy(AndroidLogcatLogStrategy())
    }
  }
}
