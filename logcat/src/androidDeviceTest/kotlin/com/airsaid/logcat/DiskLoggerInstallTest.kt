package com.airsaid.logcat

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class DiskLoggerInstallTest {

  @Before
  fun setUp() {
    LogcatLogger.uninstallAll()
  }

  @After
  fun tearDown() {
    LogcatLogger.uninstallAll()
  }

  @Test
  fun lazyInstallCreatesDiskResourcesOnlyOnce() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "disk-logger-lazy-install")
    logDir.deleteRecursively()
    var factoryCalls = 0

    val first = DiskLogger.installOnApp(LogPriority.INFO) {
      factoryCalls++
      NonFormatStrategy(
        DiskLogStrategy.Builder()
          .logFileDirectory(logDir.absolutePath)
          .build(),
      )
    }
    val handlerAfterFirstInstall = Thread.getDefaultUncaughtExceptionHandler()

    val second = DiskLogger.installOnApp(LogPriority.ERROR) {
      factoryCalls++
      error("The duplicate installation factory must not be evaluated.")
    }

    assertEquals(1, factoryCalls)
    assertSame(first, second)
    assertSame(handlerAfterFirstInstall, Thread.getDefaultUncaughtExceptionHandler())
    assertEquals(1, LogcatLogger.loggerArray.count { it is DiskLogger })
  }

}
