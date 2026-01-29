package com.airsaid.logcat.demo

import android.app.Application
import com.airsaid.logcat.AndroidLogcatFormatStrategy
import com.airsaid.logcat.AndroidLogcatLogStrategy
import com.airsaid.logcat.AndroidLogcatLogger
import com.airsaid.logcat.LogPriority

class DemoApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    val formatStrategy = AndroidLogcatFormatStrategy.Builder<AndroidLogcatLogStrategy>()
      .logStrategy(AndroidLogcatLogStrategy())
      .build()

    AndroidLogcatLogger.install(
      minPriority = LogPriority.DEBUG,
      formatStrategy = formatStrategy,
    )
  }
}
