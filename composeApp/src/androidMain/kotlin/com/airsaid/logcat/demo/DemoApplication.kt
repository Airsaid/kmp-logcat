package com.airsaid.logcat.demo

import android.app.Application
import com.airsaid.logcat.AndroidLogcatLogStrategy
import com.airsaid.logcat.AndroidLogcatLogger
import com.airsaid.logcat.LogPriority
import com.airsaid.logcat.NonFormatStrategy

class DemoApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    val formatStrategy = NonFormatStrategy(AndroidLogcatLogStrategy())
    AndroidLogcatLogger.install(
      minPriority = LogPriority.DEBUG,
      formatStrategy = formatStrategy,
    )
  }
}
