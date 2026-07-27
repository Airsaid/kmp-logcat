package com.airsaid.logcat.demo

import androidx.compose.ui.window.ComposeUIViewController
import com.airsaid.logcat.IosLogcatLogStrategy
import com.airsaid.logcat.IosLogcatLogger
import com.airsaid.logcat.LogPriority
import com.airsaid.logcat.NonFormatStrategy
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
  IosLogcatLogger.install(
    minPriority = LogPriority.DEBUG,
  ) {
    NonFormatStrategy(IosLogcatLogStrategy())
  }

  return ComposeUIViewController { App() }
}
