package com.airsaid.logcat

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

  @Suppress("DEPRECATION")
  @Test
  fun eagerDuplicateDoesNotCloseCallerOwnedStrategy() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val firstLogDir = File(context.cacheDir, "disk-logger-eager-first")
    val rejectedLogDir = File(context.cacheDir, "disk-logger-eager-rejected")
    firstLogDir.deleteRecursively()
    rejectedLogDir.deleteRecursively()

    val firstStrategy = DiskLogStrategy.Builder()
      .logFileDirectory(firstLogDir.absolutePath)
      .build()
    val rejectedStrategy = DiskLogStrategy.Builder()
      .logFileDirectory(rejectedLogDir.absolutePath)
      .logBufferMaxSize(1024)
      .build()

    try {
      DiskLogger.installOnApp(LogPriority.INFO, NonFormatStrategy(firstStrategy))
      DiskLogger.installOnApp(LogPriority.INFO, NonFormatStrategy(rejectedStrategy))

      rejectedStrategy.log(LogPriority.INFO, "Test", "caller-owned-strategy")
      rejectedStrategy.flush()

      assertTrue(awaitLogContent(rejectedLogDir).contains("caller-owned-strategy"))
      assertEquals(1, LogcatLogger.loggerArray.count { it is DiskLogger })
    } finally {
      rejectedStrategy.close()
    }
  }

  private fun awaitLogContent(directory: File, timeoutMs: Long = 2000L): String {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val content = directory.walkTopDown()
        .filter { it.isFile }
        .joinToString(separator = "") { it.readText() }
      if (content.isNotEmpty()) return content
      Thread.sleep(50)
    }
    return ""
  }
}
