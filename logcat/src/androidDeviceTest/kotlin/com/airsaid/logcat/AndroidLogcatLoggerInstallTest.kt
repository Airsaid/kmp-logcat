package com.airsaid.logcat

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidLogcatLoggerInstallTest {

  @Before
  fun setUp() {
    LogcatLogger.uninstallAll()
  }

  @After
  fun tearDown() {
    LogcatLogger.uninstallAll()
  }

  @Test
  fun installOnDebuggableApp_installsForDebuggableApplication() {
    withDebuggableFlag(enabled = true) { application ->
      AndroidLogcatLogger.installOnDebuggableApp(
        application = application,
        formatStrategy = createFormatStrategy(),
      )

      assertTrue(LogcatLogger.loggerArray.any { it is AndroidLogcatLogger })
    }
  }

  @Test
  fun installOnDebuggableApp_skipsNonDebuggableApplication() {
    withDebuggableFlag(enabled = false) { application ->
      AndroidLogcatLogger.installOnDebuggableApp(
        application = application,
        formatStrategy = createFormatStrategy(),
      )

      assertFalse(LogcatLogger.loggerArray.any { it is AndroidLogcatLogger })
    }
  }

  @Test
  fun install_stillInstallsForNonDebuggableApplication() {
    withDebuggableFlag(enabled = false) {
      AndroidLogcatLogger.install(formatStrategy = createFormatStrategy())

      assertTrue(LogcatLogger.loggerArray.any { it is AndroidLogcatLogger })
    }
  }

  private fun createFormatStrategy(): FormatStrategy<AndroidLogcatLogStrategy> =
    NonFormatStrategy(AndroidLogcatLogStrategy())

  private fun withDebuggableFlag(
    enabled: Boolean,
    block: (Application) -> Unit,
  ) {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val applicationInfo = application.applicationInfo
    val originalFlags = applicationInfo.flags
    applicationInfo.flags = if (enabled) {
      originalFlags or ApplicationInfo.FLAG_DEBUGGABLE
    } else {
      originalFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }
    try {
      block(application)
    } finally {
      applicationInfo.flags = originalFlags
    }
  }
}
