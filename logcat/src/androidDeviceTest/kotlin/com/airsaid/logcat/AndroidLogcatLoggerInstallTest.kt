package com.airsaid.logcat

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

  @Test
  fun install_repeatedCallsKeepFirstLogger() {
    AndroidLogcatLogger.install(formatStrategy = createFormatStrategy())
    val first = LogcatLogger.loggerArray.single { it is AndroidLogcatLogger }

    AndroidLogcatLogger.install(formatStrategy = createFormatStrategy())

    assertEquals(1, LogcatLogger.loggerArray.count { it is AndroidLogcatLogger })
    assertSame(first, LogcatLogger.loggerArray.single { it is AndroidLogcatLogger })
  }

  @Test
  fun install_factoryRunsOnlyForFirstInstallation() {
    var factoryCalls = 0

    val first = AndroidLogcatLogger.install {
      factoryCalls++
      createFormatStrategy()
    }
    val second = AndroidLogcatLogger.install {
      factoryCalls++
      createFormatStrategy()
    }

    assertEquals(1, factoryCalls)
    assertSame(first, second)
  }

  @Test
  fun installOnDebuggableApp_doesNotRunFactoryForNonDebuggableApplication() {
    withDebuggableFlag(enabled = false) { application ->
      var factoryCalls = 0

      val logger = AndroidLogcatLogger.installOnDebuggableApp(application) {
        factoryCalls++
        createFormatStrategy()
      }

      assertEquals(0, factoryCalls)
      assertEquals(null, logger)
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
